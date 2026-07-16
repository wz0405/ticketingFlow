package com.ticketingflow.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 트랜잭션 컨텍스트. 요청 한 건이 스텝 체인을 흐르는 동안의 단일 운반체.
 *
 *   inData   클라이언트 요청 파라미터 (dispatcher가 적재, 이후 read-only)
 *   procData 스텝 간 중간 산출물 (타입 안전하게 DTO를 담는다)
 *   outData  응답 페이로드
 *
 * inData는 프레임워크 경계라 map으로 받되, 각 업무는 진입 시 asReq()로
 * 요청 DTO로 변환해 이후 로직은 타입 안전하게 다룬다.
 */
public class TxData {

    // 디스패처가 인증 usrId를 inData에 주입하므로, DTO에 없는 필드는 무시한다.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, Object> inData = new HashMap<>();
    private final Map<String, Object> procData = new HashMap<>();
    private final Map<String, Object> outData = new LinkedHashMap<>();

    public static TxData of(Map<String, Object> in) {
        TxData tx = new TxData();
        if (in != null) {
            tx.inData.putAll(in);
        }
        return tx;
    }

    /** 요청 파라미터를 DTO로 변환 */
    public <T> T asReq(Class<T> type) {
        return MAPPER.convertValue(inData, type);
    }

    public String inString(String key) {
        Object v = inData.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public void proc(String key, Object value) {
        procData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T proc(String key) {
        return (T) procData.get(key);
    }

    public void out(String key, Object value) {
        outData.put(key, value);
    }

    public Map<String, Object> getInData() {
        return inData;
    }

    public Map<String, Object> getProcData() {
        return procData;
    }

    public Map<String, Object> getOutData() {
        return outData;
    }

    static BizException invalid(String field) {
        return new BizException(RsltCd.INVALID_PARAM, "필수 값 누락: " + field);
    }
}
