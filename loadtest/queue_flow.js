// k6 부하테스트: 로그인 → 대기열 → 어댑티브 폴링 → 입장 → 좌석 선점/확정
// 목적은 RPS 자랑이 아니라 "경합 하 오버셀 0" 불변식 확인이다.
// 좌석 수보다 VU를 많이 붙여 의도적으로 경합시키고, 확정/선점실패 비율을 계측한다.
//
// 실행: k6 run -e BASE=http://<host>:8080 -e SCHD=S0002 --vus 300 --duration 120s queue_flow.js
// 이후 loadtest/verify_invariants.sql 로 DB 불변식을 검증한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const SCHD = __ENV.SCHD || 'S0002';

const admitted = new Counter('queue_admitted');
const confirmed = new Counter('rsv_confirmed');
const seatTaken = new Counter('rsv_seat_taken');
const waitTime = new Trend('queue_wait_ms', true);

function post(bizNm, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = http.post(`${BASE}/api/${bizNm}`, JSON.stringify(body), { headers });
  check(res, { [`${bizNm} http 200`]: (r) => r.status === 200 });
  return res.json();
}

export default function () {
  const login = post('usrLogin', { usrNm: `vu${__VU}_${__ITER}` });
  if (login.rsltCd !== '0000') return;
  const token = login.data.accessToken;

  post('queueEnter', { schdNo: SCHD }, token);
  const enterTs = Date.now();

  // 서버가 내려주는 pollAfterMs를 따르는 어댑티브 폴링
  let st = { admitted: false };
  for (let i = 0; i < 60 && !st.admitted; i++) {
    const r = post('queueStatus', { schdNo: SCHD }, token);
    if (r.rsltCd !== '0000') return;
    st = r.data;
    if (st.admitted) break;
    if (!st.inQueue) return;
    sleep((st.pollAfterMs || 2000) / 1000);
  }
  if (!st.admitted) return;
  admitted.add(1);
  waitTime.add(Date.now() - enterTs);

  // 좌석 공간을 일부러 좁혀(40행) 경합을 만든다: 확정+선점실패가 시도 수와 맞아야 한다
  const contendedRows = 40;
  const row = 1 + ((__VU * 7 + __ITER) % contendedRows);
  const col = 1 + ((__VU * 13 + __ITER * 3) % 98);
  const pad = (n) => String(n).padStart(3, '0');
  const seats = [`Z${pad(row)}-${pad(col)}`, `Z${pad(row)}-${pad(col + 1)}`];

  const hold = post('rsvHold', { schdNo: SCHD, entryToken: st.entryToken, seatNos: seats }, token);
  if (hold.rsltCd === '3001') { seatTaken.add(1); return; }
  if (hold.rsltCd !== '0000') return;

  const cfm = post('rsvConfirm', { schdNo: SCHD, entryToken: st.entryToken, seatNos: seats }, token);
  if (cfm.rsltCd === '0000') confirmed.add(1);
  else if (cfm.rsltCd === '3001' || cfm.rsltCd === '3002') seatTaken.add(1);
}
