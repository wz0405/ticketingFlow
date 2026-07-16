SET NAMES utf8mb4;
-- =====================================================================
-- TicketingFlow 시드 데이터
--   S0001 : 소규모 데모 회차 (200석, 좌석맵 UI 확인용)
--   S0002 : 부하테스트 회차 (10,000석)
-- =====================================================================

INSERT IGNORE INTO TBAD_CODE (GRP_CD, CODE, DESC1, SORT_NO) VALUES
 ('RSV_ST_CD',  '0', '예매확정', 1),
 ('RSV_ST_CD',  '2', '취소',     2),
 ('EVNT_ST_CD', '0', '판매예정', 1),
 ('EVNT_ST_CD', '1', '판매중',   2),
 ('EVNT_ST_CD', '2', '종료',     3),
 ('SEAT_GRD_CD','V', 'VIP',      1),
 ('SEAT_GRD_CD','R', '일반',     2),
 ('ORD_ST_CD',  '0', '주문확정', 1),
 ('ORD_ST_CD',  '2', '취소',     2),
 ('PRD_TYPE_CD','T', '수량제티켓', 1),
 ('PRD_TYPE_CD','G', '굿즈',     2);

INSERT IGNORE INTO TBEV_EVNT (EVNT_NO, EVNT_NM, VENUE_NM, EVNT_ST_CD) VALUES
 ('E0001', '2026 TicketingFlow LIVE 콘서트', '올림픽 체조경기장', '1'),
 ('E0002', '부하테스트 GRAND OPEN',        '가상 스타디움',     '1');

INSERT IGNORE INTO TBEV_SCHD (SCHD_NO, EVNT_NO, SCHD_DT, OPEN_DT, TOT_SEAT_CNT) VALUES
 ('S0001', 'E0001', '2026-08-15 19:00:00', '2026-07-01 20:00:00', 200),
 ('S0002', 'E0002', '2026-09-01 18:00:00', '2026-07-01 20:00:00', 10000);

-- S0001: 10행(A~J) x 20열, A~B행은 VIP 150,000원 / 나머지 일반 99,000원
INSERT IGNORE INTO TBEV_SEAT (SCHD_NO, SEAT_NO, SEAT_GRD_CD, SEAT_PRC)
SELECT 'S0001',
       CONCAT(r.ch, '-', LPAD(c.n, 2, '0')),
       CASE WHEN r.ch IN ('A','B') THEN 'V' ELSE 'R' END,
       CASE WHEN r.ch IN ('A','B') THEN 150000 ELSE 99000 END
FROM (SELECT 'A' ch UNION SELECT 'B' UNION SELECT 'C' UNION SELECT 'D' UNION SELECT 'E'
      UNION SELECT 'F' UNION SELECT 'G' UNION SELECT 'H' UNION SELECT 'I' UNION SELECT 'J') r
JOIN (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 20) SELECT n FROM seq) c;

-- 수량제 재고 상품 (스탠딩 티켓 + 굿즈)
INSERT IGNORE INTO TBPD_PRD (PRD_NO, SCHD_NO, PRD_NM, PRD_TYPE_CD, PRD_PRC, STOCK_QTY) VALUES
 ('P0001', 'S0001', '스탠딩존 입장권',        'T',  77000, 500),
 ('P0002', 'S0001', '공식 응원봉',            'G',  35000, 300),
 ('P0003', 'S0001', '한정판 포토카드 세트',   'G',  15000, 100),
 ('P0004', 'S0002', '부하테스트 스탠딩권',    'T',  50000, 50000);

-- S0002: 100행(Z01~Z100) x 100열, 전좌석 88,000원
INSERT IGNORE INTO TBEV_SEAT (SCHD_NO, SEAT_NO, SEAT_GRD_CD, SEAT_PRC)
SELECT 'S0002',
       CONCAT('Z', LPAD(r.n, 3, '0'), '-', LPAD(c.n, 3, '0')),
       'R', 88000
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 100) SELECT n FROM seq) r
JOIN (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 100) SELECT n FROM seq) c;
