package com.swyp.ploutos.market.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrice;

/**
 * 한 시점의 시장 지표 시세 스냅샷. 값은 KIS 원값 그대로이고(환율은 소수 넷째 자리까지),
 * 표시용 반올림은 응답을 만드는 쪽이 한다.
 */
public record IndicatorQuote(
        MarketIndicator indicator,
        BigDecimal value,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        // Quote.priceAt과 같은 이유다. 그대로 두면 캐시에서 읽은 시각의 오프셋이 UTC로 바뀐다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime valueAt
) {

    private static final int SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    /** 지표에는 거래량이 없다. */
    private static final long NO_VOLUME = 0L;

    /** 직전 거래일 종가 대비 등락폭. 음수일 수 있다. */
    public BigDecimal change() {
        return value.subtract(previousClose);
    }

    /** 등락률 %를 소수 둘째 자리로. 전일 종가가 0이면 0.00이다. */
    public BigDecimal changeRate() {
        if (previousClose.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return value.subtract(previousClose)
                .divide(previousClose, DIVISION_SCALE, RoundingMode.HALF_UP)
                .movePointRight(SCALE)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 아직 개장하지 않았는가. KIS는 장 시작 전 시가를 0으로 준다.
     *
     * <p>시가가 0이 아니어도 전날 시세일 수 있다. 그 경우는 확정 봉과의 연속성으로 가려낸다.
     */
    public boolean notOpenedToday() {
        return open.signum() == 0;
    }

    /**
     * 진행 중인 봉으로 쓸 당일자 일봉. 종가 자리에 현재값이 들어간다.
     * 필드를 하나씩 꺼내 바깥에서 조립하지 않도록 변환을 여기 둔다.
     */
    public DailyPrice asDailyPrice(LocalDate tradeAt) {
        return new DailyPrice(tradeAt, open, high, low, value, NO_VOLUME);
    }
}
