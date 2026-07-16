# TicketingFlow — 대기열 기반 티켓 예매 시스템

배포 : https://gks0405.synology.me:8080
콘서트 티켓처럼 짧은 시간에 트래픽이 몰리는 예매를, 오버셀 없이 처리하는 것을 목표로 만든 개인 프로젝트다.

> 평소 실무에서 안 써본 인메모리 저장소(Redis)를 실제로 다뤄보는 게 주된 목적이었다.
> ZSET·Hash·Streams 같은 자료구조로 대기열과 좌석 상태를 직접 설계해봤고, 배포까지 Docker로 해봤다.
> 주제는 KIA 타이거즈 굿즈샵 대기열을 보다가 잡았다. 낯선 개념은 [study 노트](study/)에 정리하며 익혔다.

배포 환경은 개인 NAS(시놀로지, 2코어) 한 대다. 인스턴스를 늘리기 어려운 제약 안에서
**스케일아웃 대신 설계로 부하를 흡수하는 방법**을 고민한 게 핵심이다.

---

## 아키텍처

```
브라우저 ──▶ nginx ──┬─▶ queue-api   (대기열/입장)
   (Flutter Web)     └─▶ booking-api (좌석·재고 선점/확정)
                            │
              Redis 3계통 ──┴──▶ worker ──▶ MySQL
           (대기열·입장·예매)      (Streams 소비)  (확정 원장)
```

- **queue-api** — 대기열 진입, 순번 조회, 입장 스케줄링
- **booking-api** — 좌석/재고 선점·확정, 조회
- **worker** — 확정 이벤트를 소비해 원장에 비동기 적재
- **Redis** — 대기열·좌석·재고·이벤트 큐. 역할별 3계통으로 분리
- **MySQL** — INSERT-only 확정 원장

---

## 설계 포인트

### 대기열 — Redis ZSET

대기열은 진입 시각을 score로 넣은 ZSET으로 구현했다. MQ 대신 ZSET을 쓴 이유는
사용자에게 실시간 순번(ZRANK)을 보여줘야 하고, 새로고침 시 ZADD NX로 기존 순번이
유지돼야 하기 때문이다. 입장 허가는 TTL 키로 발급해 만료를 Redis에 맡겼다.

순번 폴링은 어댑티브 방식이다. 응답에 다음 폴링 간격을 실어, 입장이 멀수록 간격을 늘린다.
대기 인원이 많을수록 서버로 들어오는 요청이 오히려 줄도록 했다.

### 오버셀 방지 — Lua 원자 실행

좌석 선점을 "확인 → 차감" 두 단계로 나누면 경합 시 오버셀이 난다.
Lua 스크립트로 확인과 차감을 원자적으로 묶어, DB 락 없이 오버셀을 차단했다.
싱글 스레드 Redis 특성상 스크립트 한 덩어리는 중간 개입 없이 실행된다.

### 비동기 적재 — Redis Streams (이벤트 드리븐)

확정 시 DB에 동기로 쓰면 트래픽 스파이크에 DB가 병목이 된다.
확정 이벤트를 Streams에 발행하고 즉시 응답한 뒤, worker가 consumer group으로 소비해
원장에 적재하는 이벤트 드리븐 구조다. consumer group은 한 이벤트를 그룹 내 한 컨슈머에게만
전달하므로(competing consumers) 워커를 늘리면 부하가 분산된다 — 브로드캐스트 pub/sub이 아니라
Kafka consumer group·SQS와 같은 모델이다. Kafka 대신 Streams를 택한 건 2코어 NAS에 브로커를
추가하지 않고 consumer group·ACK·pending 재처리를 얻기 위해서다.
(개념 정리는 [study/redis.md](study/redis.md)에 있다.)

유실 방지는 처리 성공 시에만 커밋하고(XACK), 죽은 컨슈머가 남긴 이벤트는 다른 워커가
넘겨받아 재처리하며(XCLAIM), 원장 PK로 중복 적재를 막는(INSERT IGNORE) 구조로 했다.
(명령어 대응은 study 노트에 정리)

### Redis 역할별 3계통 분리

대기열·입장·예매 상태를 각각 별도 Redis 인스턴스로 분리했다.

| 인스턴스 | 저장 | 특성 |
|---|---|---|
| queue | 대기열 ZSET | 휘발성 |
| active | 입장 허가 | TTL 자동 만료 |
| booking | 좌석/재고 Hash + 이벤트 Streams | AOF 영속 |

부하·장애 도메인을 나눠, 대기열 폭주가 좌석 원자연산이나 이벤트 큐에 영향을 주지 않게 했다.

---

## 부하 테스트

가상 사용자 200명 / 60초로 부하를 줬다. 실무에서 쓰던 JMeter 대신, 스크립트가 가벼운 k6를 사용했다.

첫 실행에서 요청 70%가 실패했는데, 병목은 예매 로직이 아니라 로그인 시 회원번호 채번이었다.
매 가입마다 MAX(USR_ID)로 테이블을 풀스캔하고 있었고, 채번을 UUID로 바꿔(중앙 채번·스캔 제거) 해결했다.

| | 개선 전 | 개선 후 |
|---|---|---|
| 요청 실패율 | 70% | 0.02% |
| 응답 지연(중앙값) | 6.1s | 0.1s |

핵심 검증인 오버셀은 좌석 수보다 사용자를 많이 몰아넣어도 **중복 판매 0건**이었고,
Redis에서 확정된 수와 MySQL 적재 수가 일치했다(유실 0). 스크립트와 검증 SQL은 loadtest 폴더에 있다.

---

## 기술 스택

| 영역 | 사용 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.5, MyBatis |
| 저장/동시성 | Redis 7 (ZSET·Hash·Streams·TTL·Lua), 역할별 3계통 |
| DB | MySQL 8 (INSERT-only 원장) |
| 프론트 | Flutter Web |
| 인프라 | Docker Compose, nginx, 시놀로지 NAS |
| 인증 | JWT (HS256) |
| 부하 | k6 |

---

## 폴더 구조

```
ticketingFlow/
├── backend/    Spring Boot 멀티모듈 (queue-api·booking-api·worker·common)
├── frontend/   Flutter Web
├── infra/      Docker Compose, nginx, 배포 스크립트
├── loadtest/   k6 시나리오 + 검증 SQL
└── study/      Docker·Redis 학습 노트
```

## 실행

```bash
cd backend && ./gradlew build
cd ../frontend && flutter build web --release
cd ../infra && cp .env.example .env    # 비밀값 입력
docker-compose up -d                   # → http://localhost:8080
```

---

## 한계

- 2코어 NAS 기준 수치다. 처리량 자체엔 하드웨어 상한이 있다.
- 입장 스케줄러는 단일 인스턴스 전제다. 다중화하려면 분산 락이 필요하다.
- 로그인은 이름 기반 데모 수준이다. 정식 회원 인증은 범위에서 제외했다.
