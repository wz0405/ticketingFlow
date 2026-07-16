package com.ticketingflow.booking.mapper;

import com.ticketingflow.booking.domain.MyRsvRow;
import com.ticketingflow.booking.domain.RsvForCancel;
import com.ticketingflow.event.SeatLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RsvMapper {

    RsvForCancel selectRsvForCancel(@Param("rsvNo") String rsvNo);

    List<SeatLine> selectRsvSeats(@Param("rsvNo") String rsvNo);

    List<MyRsvRow> selectMyRsvs(@Param("usrId") String usrId);
}
