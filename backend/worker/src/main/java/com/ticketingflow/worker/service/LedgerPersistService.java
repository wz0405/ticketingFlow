package com.ticketingflow.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.event.OrdEvent;
import com.ticketingflow.event.RsvEvent;
import com.ticketingflow.worker.mapper.LedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stream 이벤트 → 원장 적재.
 * 마스터 INSERT IGNORE가 0건이면 이미 처리된 메시지로 보고 하위 적재를 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPersistService {

    private final LedgerMapper ledgerMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persist(String type, String payloadJson) throws Exception {
        switch (type) {
            case "RSV" -> persistRsv(read(payloadJson, RsvEvent.class), "0", "예매확정");
            case "RSV_CNCL" -> persistRsv(read(payloadJson, RsvEvent.class), "2", "예매취소");
            case "ORD" -> persistOrd(read(payloadJson, OrdEvent.class), "0", 1);
            case "ORD_CNCL" -> persistOrd(read(payloadJson, OrdEvent.class), "2", -1);
            default -> log.warn("unknown stream type: {}", type);
        }
    }

    private void persistRsv(RsvEvent e, String rsvStCd, String histMsg) {
        int inserted = ledgerMapper.insertRsv(e.rsvNo(), e.orsvNo(), e.usrId(), e.schdNo(),
                rsvStCd, e.seats().size(), e.totAmt(), e.trxDt());
        if (inserted == 0) {
            log.info("duplicate rsv skipped: {}", e.rsvNo());
            return;
        }
        ledgerMapper.insertRsvSeats(e.rsvNo(), e.seats());
        ledgerMapper.insertRsvHist(e.rsvNo(), rsvStCd, histMsg);
    }

    private void persistOrd(OrdEvent e, String ordStCd, int sign) {
        int inserted = ledgerMapper.insertOrd(e.ordNo(), e.oordNo(), e.usrId(), e.schdNo(),
                ordStCd, e.itemCnt(), e.totAmt(), e.trxDt());
        if (inserted == 0) {
            log.info("duplicate ord skipped: {}", e.ordNo());
            return;
        }
        ledgerMapper.insertOrdItems(e.ordNo(), sign, e.items());
    }

    private <T> T read(String json, Class<T> type) throws Exception {
        return objectMapper.readValue(json, type);
    }
}
