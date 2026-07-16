-- 부하테스트 후 오버셀 불변식 검증. 모든 결과가 0/정상이어야 한다.
-- 실행: mysql -h <host> -P 13306 -u hhhan -p ticketingflow < verify_invariants.sql

-- 1) 한 좌석이 두 번 이상 유효 판매됐는가 (오버셀). 0행이어야 정상.
SELECT '중복 판매 좌석(오버셀)' AS check_name, tbtrRsvSeat.SEAT_NO, COUNT(*) AS sold_cnt
  FROM TBTR_RSV tbtrRsv
  JOIN TBTR_RSV_SEAT tbtrRsvSeat ON tbtrRsvSeat.RSV_NO = tbtrRsv.RSV_NO
  LEFT JOIN TBTR_RSV cnclRsv ON cnclRsv.ORSV_NO = tbtrRsv.RSV_NO AND cnclRsv.RSV_ST_CD = '2'
 WHERE tbtrRsv.RSV_ST_CD = '0' AND cnclRsv.RSV_NO IS NULL
 GROUP BY tbtrRsvSeat.SEAT_NO
HAVING COUNT(*) > 1;

-- 2) 유효 판매 좌석 수가 회차 총 좌석 수를 넘는가. over_capacity <= 0 이어야 정상.
SELECT '판매 대비 정원 초과' AS check_name,
       tbevSchd.TOT_SEAT_CNT AS capacity,
       COUNT(*) AS sold_seats,
       COUNT(*) - tbevSchd.TOT_SEAT_CNT AS over_capacity
  FROM TBTR_RSV tbtrRsv
  JOIN TBTR_RSV_SEAT tbtrRsvSeat ON tbtrRsvSeat.RSV_NO = tbtrRsv.RSV_NO
  JOIN TBEV_SCHD tbevSchd ON tbevSchd.SCHD_NO = tbtrRsv.SCHD_NO
  LEFT JOIN TBTR_RSV cnclRsv ON cnclRsv.ORSV_NO = tbtrRsv.RSV_NO AND cnclRsv.RSV_ST_CD = '2'
 WHERE tbtrRsv.RSV_ST_CD = '0' AND cnclRsv.RSV_NO IS NULL
 GROUP BY tbtrRsv.SCHD_NO, tbevSchd.TOT_SEAT_CNT;

-- 3) 상품 재고가 음수로 팔렸는가 (오버셀). remaining >= 0 이어야 정상.
SELECT '상품 재고 음수' AS check_name,
       tbpdPrd.PRD_NO, tbpdPrd.STOCK_QTY,
       IFNULL(SUM(tbtrOrdItem.ORD_QTY), 0) AS net_ordered,
       tbpdPrd.STOCK_QTY - IFNULL(SUM(tbtrOrdItem.ORD_QTY), 0) AS remaining
  FROM TBPD_PRD tbpdPrd
  LEFT JOIN TBTR_ORD_ITEM tbtrOrdItem ON tbtrOrdItem.PRD_NO = tbpdPrd.PRD_NO
 GROUP BY tbpdPrd.PRD_NO, tbpdPrd.STOCK_QTY
HAVING remaining < 0;

-- 4) 확정 건수 요약
SELECT '확정 예매 수' AS check_name, COUNT(*) AS confirmed_rsv
  FROM TBTR_RSV WHERE RSV_ST_CD = '0';
