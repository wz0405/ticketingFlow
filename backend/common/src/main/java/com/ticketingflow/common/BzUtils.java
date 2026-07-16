package com.ticketingflow.common;

/**
 * 업무 공통 유틸.
 */
public final class BzUtils {

    private BzUtils() {
    }

    public static String nvl(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
