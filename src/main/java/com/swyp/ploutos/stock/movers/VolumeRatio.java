package com.swyp.ploutos.stock.movers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 당일 거래량이 기준 거래량의 몇 배인지(RQ-0704).
 *
 * <p><b>"2배 이상"이라는 관문을 두지 않는다.</b> 분자는 장이 진행된 만큼만 쌓인 당일 누적이고
 * 분모는 하루 전체의 평균이라, 장중에는 배수가 거의 언제나 1보다 작게 나온다 — 고정 숫자와
 * 비교하면 장 초반에는 아무도 넘지 못하고 마감 직전에만 우르르 넘는다.
 *
 * <p>대신 <b>배수가 큰 순서</b>로 보여준다. 경과율은 모든 종목에 똑같이 곱해지므로 순서에서는
 * 대체로 약분된다. 절대값까지 되살리려면 시장 전체 비율로 한 번 더 나눠야 하는데
 * ({@code IndustryTradingValue.relativeTo}가 쓰는 방법), 그러려면 모집단 전체의 20거래일
 * 평균이 있어야 해서 일봉 적재가 선행 조건이다.
 *
 * <p>KIS 가 계산해 준 비율({@code vol_inrt}·{@code n_rate})은 쓰지 않는다. 둘 다
 * {@code 9999.99}에서 포화해 100배를 넘는 종목이 같은 값이 된다 — 실측에서 357배인 종목이
 * 101배로 읽혔다. 분자·분모를 직접 나누면 상한도 반올림 손실도 없다.
 */
public final class VolumeRatio {

    private static final int SCALE = 2;

    private VolumeRatio() {
    }

    /**
     * 기준 거래량 대비 배수. 기준이 0이거나 없으면 잴 수 없어 비어 있다 —
     * 전일 거래가 없었거나 평균을 낼 만큼 거래일이 쌓이지 않은 종목이다.
     */
    public static Optional<BigDecimal> of(long volume, long baseline) {
        if (baseline <= 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(volume)
                .divide(BigDecimal.valueOf(baseline), SCALE, RoundingMode.HALF_UP));
    }

    /** 배수로 줄을 세울 수 있는 종목인지. 재지 못한 종목은 거래량 급증 목록에 넣지 않는다. */
    public static boolean measurable(BigDecimal ratio) {
        return ratio != null;
    }
}
