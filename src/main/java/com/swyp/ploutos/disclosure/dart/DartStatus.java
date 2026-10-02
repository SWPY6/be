package com.swyp.ploutos.disclosure.dart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * DART 응답 본문의 status 판정. DART는 오류도 HTTP 200으로 주므로 status를 따로 봐야 한다.
 */
final class DartStatus {

    private static final Logger log = LoggerFactory.getLogger(DartStatus.class);

    static final String OK = "000";
    static final String NO_DATA = "013";
    private static final String RATE_LIMITED = "020";
    private static final String MAINTENANCE = "800";

    private DartStatus() {
    }

    /** 정상(000)이 아니면 예외를 던진다. 조회 없음(013)을 정상 0건으로 볼지는 호출하는 쪽이 정한다. */
    static void requireOk(String status, String api) {
        if (OK.equals(status)) {
            return;
        }
        if (RATE_LIMITED.equals(status) || MAINTENANCE.equals(status)) {
            log.error("DART {} 호출이 제한됐다. status={}", api, status);
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
        // 010·011·012·901은 인증키·IP 문제, 100·101은 요청 문제, 900은 공급자 오류다. 원인은 로그로 구분한다.
        log.error("DART {} 응답 오류. status={}", api, status);
        throw new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }
}
