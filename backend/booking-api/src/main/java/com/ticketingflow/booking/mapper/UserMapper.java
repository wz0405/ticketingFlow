package com.ticketingflow.booking.mapper;

import com.ticketingflow.booking.domain.UsrRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UsrRow selectByNm(@Param("usrNm") String usrNm);

    int insertUsr(@Param("usrId") String usrId, @Param("usrNm") String usrNm);
}
