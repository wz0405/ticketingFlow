package com.ticketingflow.worker.mapper;

import com.ticketingflow.event.ItemLine;
import com.ticketingflow.event.SeatLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface LedgerMapper {

    int insertRsv(@Param("rsvNo") String rsvNo,
                  @Param("orsvNo") String orsvNo,
                  @Param("usrId") String usrId,
                  @Param("schdNo") String schdNo,
                  @Param("rsvStCd") String rsvStCd,
                  @Param("seatCnt") int seatCnt,
                  @Param("totAmt") BigDecimal totAmt,
                  @Param("trxDt") long trxDt);

    int insertRsvSeats(@Param("rsvNo") String rsvNo,
                       @Param("seats") List<SeatLine> seats);

    int insertRsvHist(@Param("rsvNo") String rsvNo,
                      @Param("rsvStCd") String rsvStCd,
                      @Param("histMsg") String histMsg);

    int insertOrd(@Param("ordNo") String ordNo,
                  @Param("oordNo") String oordNo,
                  @Param("usrId") String usrId,
                  @Param("schdNo") String schdNo,
                  @Param("ordStCd") String ordStCd,
                  @Param("itemCnt") int itemCnt,
                  @Param("totAmt") BigDecimal totAmt,
                  @Param("trxDt") long trxDt);

    int insertOrdItems(@Param("ordNo") String ordNo,
                       @Param("sign") int sign,
                       @Param("items") List<ItemLine> items);
}
