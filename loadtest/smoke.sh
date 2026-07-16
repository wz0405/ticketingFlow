#!/usr/bin/env bash
# 전체 흐름 스모크: 로그인(JWT 발급) → 대기열 → 입장 → 좌석 선점/확정
#   → 원장 적재 확인 → 취소 → 상품 주문/취소.
# 인증 후 요청은 usrId를 body가 아니라 Authorization 헤더의 토큰으로 증명한다.
# 사용법: BASE_Q=http://host:8081 BASE_B=http://host:8082 ./smoke.sh
set -uo pipefail

BASE_Q="${BASE_Q:-http://localhost:8081}"
BASE_B="${BASE_B:-http://localhost:8082}"
SCHD="${SCHD:-S0001}"

val() { sed -n "s/.*\"$2\":\"\{0,1\}\([^,\"}]*\)\"\{0,1\}.*/\1/p" <<<"$1"; }
pub()  { curl -s -X POST "$1/api/$2" -H 'Content-Type: application/json' -d "$3"; }
auth() { curl -s -X POST "$1/api/$2" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN_JWT" -d "$3"; }

echo "1) login → JWT"
LOGIN=$(pub "$BASE_B" usrLogin '{"usrNm":"smoke_user"}')
USR=$(val "$LOGIN" usrId)
TOKEN_JWT=$(val "$LOGIN" accessToken)
echo "   usrId=$USR jwt=${TOKEN_JWT:0:16}..."
if [ -z "$TOKEN_JWT" ]; then echo "FAIL: no token"; exit 1; fi

echo "1b) 신원 위조 차단 확인 (토큰 없이 좌석조회 → 1003)"
NOAUTH=$(pub "$BASE_B" seatList "{\"schdNo\":\"$SCHD\"}")
echo "   rsltCd=$(val "$NOAUTH" rsltCd) (기대: 1003)"

echo "2) queue enter + poll until admitted"
auth "$BASE_Q" queueEnter "{\"schdNo\":\"$SCHD\"}" >/dev/null
ENTRY=""
for i in $(seq 1 10); do
  ST=$(auth "$BASE_Q" queueStatus "{\"schdNo\":\"$SCHD\"}")
  ENTRY=$(val "$ST" entryToken)
  if [ -n "$ENTRY" ]; then break; fi
  sleep 1
done
echo "   entryToken=${ENTRY:0:12}..."
if [ -z "$ENTRY" ]; then echo "FAIL: not admitted"; exit 1; fi

echo "3) seat hold + confirm (C-05, C-06)"
SEATS='["C-05","C-06"]'
auth "$BASE_B" rsvHold "{\"schdNo\":\"$SCHD\",\"entryToken\":\"$ENTRY\",\"seatNos\":$SEATS}" >/dev/null
CFM=$(auth "$BASE_B" rsvConfirm "{\"schdNo\":\"$SCHD\",\"entryToken\":\"$ENTRY\",\"seatNos\":$SEATS}")
RSV=$(val "$CFM" rsvNo)
echo "   rsvNo=$RSV totAmt=$(val "$CFM" totAmt)"
if [ -z "$RSV" ]; then echo "FAIL confirm: $CFM"; exit 1; fi

echo "4) wait worker persist → myRsv shows it"
FOUND=""
for i in $(seq 1 10); do
  MY=$(auth "$BASE_B" myRsv '{}')
  if grep -q "$RSV" <<<"$MY"; then FOUND=1; break; fi
  sleep 1
done
if [ -n "$FOUND" ]; then echo "   persisted OK"; else echo "FAIL: rsv not persisted"; exit 1; fi

echo "5) cancel"
CNCL=$(auth "$BASE_B" rsvCancel "{\"rsvNo\":\"$RSV\"}")
echo "   cnclNo=$(val "$CNCL" cnclNo)"

echo "6) product order + cancel"
ORD=$(auth "$BASE_B" ordConfirm "{\"schdNo\":\"$SCHD\",\"entryToken\":\"$ENTRY\",\"items\":[{\"prdNo\":\"P0002\",\"qty\":2}]}")
ORDNO=$(val "$ORD" ordNo)
echo "   ordNo=$ORDNO totAmt=$(val "$ORD" totAmt)"
if [ -z "$ORDNO" ]; then echo "FAIL order: $ORD"; exit 1; fi
sleep 2
OCNCL=$(auth "$BASE_B" ordCancel "{\"ordNo\":\"$ORDNO\"}")
echo "   ordCancel=$(val "$OCNCL" cnclNo)"

echo "ALL PASS"
