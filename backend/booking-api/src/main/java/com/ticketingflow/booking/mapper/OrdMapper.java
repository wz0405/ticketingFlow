package com.ticketingflow.booking.mapper;

import com.ticketingflow.booking.domain.MyOrdRow;
import com.ticketingflow.booking.domain.OrdForCancel;
import com.ticketingflow.booking.domain.OrdItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrdMapper {

    OrdForCancel selectOrdForCancel(@Param("ordNo") String ordNo);

    List<OrdItemRow> selectOrdItems(@Param("ordNo") String ordNo);

    List<MyOrdRow> selectMyOrds(@Param("usrId") String usrId);
}
