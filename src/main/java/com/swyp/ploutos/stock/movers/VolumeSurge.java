package com.swyp.ploutos.stock.movers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 거래량 급증 판정(RQ-0704). 당일 거래량이 기준 거래량의 몇 배인지 재고, 2배에 못 미치면 뺀다.
 *
 * <p><b>KIS 가 계산해 준 비율을 쓰지 않는다.</b> 국내 {@code vol_inrt}와 해외 {@code n_rate}는
 * 모두 {@code 9999.99}에서 포화해 100배를 넘는 종목이 같은 값이 된다 — 실측에서 357배인 종목이
 * 101배로 읽혔다. 분자·분모를 직접 나누면 상한도 반올림 손실도 없다.
 */
public final class VolumeSurge {

    /** 급증으로 보는 최소 배수. */
    public static final BigDecimal MIN_RATIO = new BigDecimal("2.0");

    private static final int SCALE = 2;

    private VolumeSurge() {
    }

    /**
     * 기준 거래량 대비 배수. 기준이 0이면 나눌 수 없어 비어 있다 —
     * 전일 거래가 없었거나 평균을 낼 만큼 거래일이 쌓이지 않은 종목이다.
     */
    public static Optional<BigDecimal> ratio(long volume, long baseline) {
        if (baseline <= 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(volume)
                .divide(BigDecimal.valueOf(baseline), SCALE, RoundingMode.HALF_UP));
    }

    /** 급증인지. 배수를 재지 못한 종목은 급증이 아니다. */
    public static boolean surged(BigDecimal ratio) {
        return ratio != null && ratio.compareTo(MIN_RATIO) >= 0;
    }
}
