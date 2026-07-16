package com.ticketingflow.booking.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Lua 반환 규약([0]=코드, [1]=실패 대상) → 업무 예외 매핑 검증.
 * 이 매핑이 어긋나면 사용자에게 엉뚱한 실패 사유가 전달된다.
 */
class LuaSupportTest {

    private final LuaSupport lua = new LuaSupport(new ObjectMapper());

    @Test
    @DisplayName("코드 0은 정상 통과한다")
    void success_passes() {
        assertThatCode(() -> lua.guard(List.of(0L))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("코드 2(선점 불가)는 SEAT_TAKEN으로, 실패 좌석을 메시지에 싣는다")
    void seatTaken_carriesSeatNo() {
        BizException e = catchThrowableOfType(BizException.class,
                () -> lua.guard(List.of(2L, "A-01")));

        assertThat(e.getRsltCd()).isEqualTo(RsltCd.SEAT_TAKEN);
        assertThat(e.getMessage()).contains("A-01");
    }

    @Test
    @DisplayName("코드 3(선점 만료·비소유)은 HOLD_EXPIRED로 매핑된다")
    void holdExpired_mapped() {
        BizException e = catchThrowableOfType(BizException.class,
                () -> lua.guard(List.of(3L, "A-01")));

        assertThat(e.getRsltCd()).isEqualTo(RsltCd.HOLD_EXPIRED);
    }

    @Test
    @DisplayName("코드 4(재고 부족)는 SEAT_TAKEN 계열로, 부족 상품을 메시지에 싣는다")
    void stockShort_carriesPrdNo() {
        BizException e = catchThrowableOfType(BizException.class,
                () -> lua.guard(List.of(4L, "PRD-9")));

        assertThat(e.getRsltCd()).isEqualTo(RsltCd.SEAT_TAKEN);
        assertThat(e.getMessage()).contains("PRD-9");
    }

    @Test
    @DisplayName("null·빈 결과는 시스템 오류로 처리한다")
    void nullOrEmpty_isSystemError() {
        BizException e = catchThrowableOfType(BizException.class, () -> lua.guard(null));

        assertThat(e.getRsltCd()).isEqualTo(RsltCd.SYSTEM_ERROR);
    }
}
