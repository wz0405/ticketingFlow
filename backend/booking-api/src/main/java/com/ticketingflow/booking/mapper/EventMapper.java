package com.ticketingflow.booking.mapper;

import com.ticketingflow.booking.domain.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EventMapper {

    List<EventRow> selectEventList();

    List<String> selectSeatNos(@Param("schdNo") String schdNo);

    List<SoldSeat> selectSoldSeats(@Param("schdNo") String schdNo);

    List<SeatMaster> selectSeatMaster(@Param("schdNo") String schdNo);

    List<SeatPrice> selectSeatPrices(@Param("schdNo") String schdNo,
                                     @Param("seatNos") List<String> seatNos);

    List<PrdRow> selectPrdList(@Param("schdNo") String schdNo);

    List<PrdRemain> selectPrdRemains(@Param("schdNo") String schdNo);

    List<PrdPrice> selectPrdPrices(@Param("schdNo") String schdNo,
                                   @Param("prdNos") List<String> prdNos);
}
