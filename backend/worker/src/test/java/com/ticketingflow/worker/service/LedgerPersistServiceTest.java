package com.ticketingflow.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.event.RsvEvent;
import com.ticketingflow.event.SeatLine;
import com.ticketingflow.worker.mapper.LedgerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 스트림 재전달(중복 소비) 멱등성 검증.
 * consumer group은 at-least-once라 같은 이벤트가 두 번 올 수 있고,
 * 그 방어선은 "마스터 INSERT IGNORE가 0건이면 하위 적재를 건너뛴다" 하나뿐이다.
 */
class LedgerPersistServiceTest {

    private final LedgerMapper mapper = mock(LedgerMapper.class);
    private final ObjectMapper om = new ObjectMapper();
    private final LedgerPersistService service = new LedgerPersistService(mapper, om);

    private String rsvPayload() throws Exception {
        return om.writeValueAsString(new RsvEvent(
                "R1", "R1", "u1", "SCHD1", 1234L,
                new BigDecimal("10000"),
                List.of(new SeatLine("A-01", new BigDecimal("10000")))));
    }

    @Test
    @DisplayName("신규 이벤트는 마스터·좌석·이력까지 전부 적재한다")
    void freshEvent_persistsAll() throws Exception {
        when(mapper.insertRsv(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), any(), anyLong())).thenReturn(1);

        service.persist("RSV", rsvPayload());

        verify(mapper).insertRsvSeats(any(), anyList());
        verify(mapper).insertRsvHist("R1", "0", "예매확정");
    }

    @Test
    @DisplayName("중복 이벤트(INSERT 0건)는 하위 적재 없이 건너뛴다 — 멱등")
    void duplicateEvent_skippedIdempotently() throws Exception {
        when(mapper.insertRsv(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), any(), anyLong())).thenReturn(0);

        service.persist("RSV", rsvPayload());

        verify(mapper, never()).insertRsvSeats(any(), anyList());
        verify(mapper, never()).insertRsvHist(any(), any(), any());
    }
}
