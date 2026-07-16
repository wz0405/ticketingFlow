# Redis 노트

이 프로젝트에서 Redis를 쓰며 정리한 것들. 자료구조 선택 근거와, 덜 익숙했던
Lua 원자성·Streams consumer group 위주로 적었다.

## 자료구조 선택

| 용도 | 자료구조 | 이유 |
|---|---|---|
| 대기열 | ZSET | score=진입 epoch로 FIFO. ZRANK로 순번 조회, ZADD NX로 재접속 멱등 |
| 입장 허가 | String + TTL | 만료를 Redis에 위임(청소 배치 불필요) |
| 좌석/재고 | Hash | 회차 하나 아래 좌석/상품 field로 상태 관리 |
| 확정 이벤트 | Streams | consumer group 기반 비동기 적재 |

## 대기열에 MQ 대신 ZSET

MQ는 소비 전 위치 조회가 안 된다. 이 서비스는 사용자에게 실시간 순번(ZRANK)을 보여줘야 하고,
새로고침 시 순번이 유지돼야 한다(ZADD NX). 둘 다 ZSET에서 자연스럽다.
입장 스케줄러는 ZPOPMIN으로 선두 N명을 원자적으로 꺼내 active 키(TTL)를 발급한다.

## Lua 원자성으로 오버셀 차단

좌석 선점을 "확인 → 차감" 두 명령으로 나누면 경합 시 두 요청이 같은 좌석을 잡는다(오버셀).
Redis는 싱글 스레드로 명령을 처리하므로, 확인+차감을 Lua 스크립트 한 덩어리로 실행하면
그 사이 다른 명령이 끼어들 수 없다. DB 비관적 락(SELECT FOR UPDATE) 없이 동시성을 제어한다.

좌석 상태 표현(Hash value):
- `A` 가능 / `H:{usrId}:{expireMs}` 선점 / `S:{usrId}` 판매완료
- 선점에 만료시각을 박아, 다음 요청이 만졌을 때 만료면 재선점 허용(lazy expiry)

스크립트: seat_hold(다좌석 all-or-nothing 선점), rsv_confirm(HOLD 검증→SOLD→XADD),
ord_confirm(다품목 재고 원자 차감).

## Streams consumer group

확정 시 DB 동기 쓰기는 트래픽 스파이크에 병목이 된다. 확정 이벤트를 XADD로 발행하고 즉시
응답한 뒤, worker가 XREADGROUP으로 소비해 원장에 적재한다(이벤트 드리븐).

consumer group은 **한 이벤트를 그룹 내 한 컨슈머에게만** 전달한다(competing consumers).
워커를 늘리면 부하가 분산된다. Kafka consumer group·SQS와 같은 모델이며, 모든 구독자가
모든 메시지를 받는 pub/sub(브로드캐스트)과는 다르다. 서로 다른 서비스가 같은 이벤트를
각자 처리해야 하면 consumer group을 여러 개 둔다(그룹마다 전체 수신).

Streams 명령어 (Kafka에 비유):

| 명령 | 하는 일 | Kafka 대응 |
|---|---|---|
| XADD | 스트림에 이벤트 추가(발행) | produce |
| XREADGROUP | consumer group으로 이벤트 읽기(소비) | poll |
| XACK | 처리 완료 확인. 안 하면 pending에 남아 재처리 대상 | offset commit |
| XPENDING | 아직 XACK 안 된(처리 중/실패) 이벤트 목록 | 미커밋 조회 |
| XCLAIM | 죽은 컨슈머의 pending 이벤트를 넘겨받기 | 리밸런싱 |
| XGROUP CREATE | consumer group 생성 (MKSTREAM: 스트림 없으면 같이 생성) | 그룹 생성 |

내구성:
- 처리 성공 시에만 XACK. 실패 건은 pending에 남아 재처리
- 죽은 컨슈머 pending은 XCLAIM으로 회수, 재전달 초과는 DLQ로 격리
- 원장 PK로 멱등 적재(INSERT IGNORE) → 재전달돼도 중복 없음

주의: XGROUP CREATE는 스트림이 없으면 실패한다. MKSTREAM 플래그로 스트림째 생성하고,
운영 중 Redis가 초기화돼 NOGROUP이 나면 그룹을 재생성하도록 방어 코드를 뒀다.

## Redis 3계통 분리

대기열/입장/예매를 별도 인스턴스로 나눴다.

| 인스턴스 | 데이터 | 영속화 정책 |
|---|---|---|
| queue | 대기열 ZSET | 없음(재구성 가능), allkeys-lru |
| active | 입장 TTL 키 | 없음, volatile-ttl |
| booking | 좌석/재고 Hash + Streams + 채번 | AOF, noeviction |

부하·장애·영속화 요구가 계통마다 달라서 분리했다. 대기열 폭주가 좌석 원자연산이나
이벤트 스트림에 영향을 주지 않는다. 각 인스턴스는 정책(eviction/AOF)도 목적에 맞게 다르게 뒀다.

트레이드오프: active와 booking이 다른 인스턴스라, "입장 검증 + 좌석 선점"을 한 Lua로 묶을 수
없다. 입장 검증은 좌석 선점 직전 별도 조회로 처리했다(오버셀 방지의 핵심인 좌석 원자성은 유지).
