# Docker / 배포 노트

컨테이너 구성 결정과, 2코어 NAS에 배포하며 실제로 부딪힌 것들.

## 컨테이너 토폴로지

```
nginx            8080. 정적 FE 서빙 + /api 리버스프록시(L/B) + /ws 업그레이드
queue-api        대기열/입장
booking-api      좌석·재고 선점/확정
worker           Streams 소비 → 원장 적재
redis x3         queue / active / booking (역할별 분리)
mysql            확정 원장
```

역할별로 나눈 이유는 부하 성격이 달라서다. 대기열과 예매는 몰리는 구간이 다르므로,
nginx upstream에 인스턴스를 추가해 필요한 쪽만 스케일아웃할 수 있게 했다.

## 이미지 전략 — 호스트 빌드 + 얇은 런타임

2코어 NAS에서 Gradle/Flutter 빌드를 돌리면 느리다. 빌드는 개발 PC에서 하고 산출물(JAR,
정적 파일)만 전송해, JRE만 담은 얇은 이미지에 얹었다. 힙도 `-Xmx384m`로 제한해 컨테이너
여러 개가 10GB 안에서 공존하게 했다. (규모가 커지면 멀티스테이지 빌드로 가는 게 정석)

## compose에서 배운 것

- 서비스 이름이 곧 DNS다. booking-api는 `tr-mysql:3306`, `tr-redis-booking:6379`로 접근한다.
- MySQL initdb: `/docker-entrypoint-initdb.d`에 마운트한 `.sql`을 **첫 기동에만** 알파벳 순
  실행한다. `01_schema.sql`, `02_seed.sql`로 순서를 잡았다. 볼륨이 이미 있으면 실행 안 됨.
- 비밀값은 compose에 `${VAR}` 참조만 두고 `.env`(gitignore)로 주입.

## 배포하며 실제로 겪은 것

- **initdb 한글 깨짐**: mysql 클라이언트가 시드 파일을 latin1로 읽어 UTF-8이 이중 인코딩됐다.
  SQL 파일 상단에 `SET NAMES utf8mb4;`를 넣어 해결. (Flutter http도 charset 없는 응답을
  latin1로 디코딩하므로, FE에서 `utf8.decode(bodyBytes)`로 별도 처리)
- **MySQL 첫 기동이 느림**: 2코어에서 InnoDB 초기화 + 시드까지 약 8분. 그동안 앱은 커넥션
  거부. 시드가 큰 초기화는 mysql ready를 기다렸다 앱을 붙여야 한다.
- **nginx upstream DNS 캐싱**: 앱 컨테이너를 recreate하면 IP가 바뀌는데 nginx가 기동 시
  해석한 옛 IP를 물고 있어 502가 났다. 재배포 후 nginx도 reload/restart하거나 resolver를
  둬야 한다.
- **Redis 초기화 시 consumer group 소실**: FLUSHALL 하면 스트림/그룹이 날아가 워커가 멈춘다.
  MKSTREAM 생성 + NOGROUP 자동 재생성으로 방어(자세한 건 [redis.md](redis.md)).

## 자주 쓴 명령

```bash
docker-compose up -d [service]
docker logs -f <name>
docker exec -it tr-mysql mysql -u...
docker-compose down          # 볼륨 유지
```
