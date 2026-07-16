package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.auth.JwtSupport;
import com.ticketingflow.booking.domain.UsrRow;
import com.ticketingflow.booking.dto.Requests.LoginReq;
import com.ticketingflow.booking.mapper.UserMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 간이 로그인(get-or-create). 이름만으로 식별하는 데모용 로그인이다.
 * 인증 성사 후 신원 JWT를 발급하고, 이후 요청은 이 토큰으로 usrId를 증명한다.
 * 회원번호는 UUID로 발급한다(중앙 채번 없이 충돌 없는 식별자).
 * 동시 가입 충돌은 UNIQUE 제약에 맡기고 재조회로 수습한다.
 */
@Service("handle|" + UsrLoginHandle.BEAN_NM)
@RequiredArgsConstructor
public class UsrLoginHandle extends HandleTemplate {

    public static final String BEAN_NM = "usrLogin";

    private final UserMapper userMapper;
    private final JwtSupport jwtSupport;

    @Override
    protected void validate(TxData tx) {
        Reqs.required(tx.inString("usrNm"), "usrNm");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain.next(t -> {
            String usrNm = t.asReq(LoginReq.class).usrNm().trim();
            UsrRow usr = getOrCreate(usrNm);
            t.out("usrId", usr.usrId());
            t.out("usrNm", usr.usrNm());
            t.out("accessToken", jwtSupport.issue(usr.usrId(), usr.usrNm()));
            t.out("expiresInSec", jwtSupport.expirySeconds());
        });
    }

    private UsrRow getOrCreate(String usrNm) {
        UsrRow usr = userMapper.selectByNm(usrNm);
        if (usr != null) {
            return usr;
        }
        try {
            userMapper.insertUsr(UUID.randomUUID().toString(), usrNm);
        } catch (DuplicateKeyException ignore) {
            // 동시 가입 — 이미 만들어진 계정을 그대로 쓴다
        }
        usr = userMapper.selectByNm(usrNm);
        if (usr == null) {
            throw new BizException(RsltCd.SYSTEM_ERROR, "회원 생성 실패");
        }
        return usr;
    }
}
