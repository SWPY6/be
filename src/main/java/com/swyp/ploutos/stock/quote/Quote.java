package com.swyp.ploutos.stock.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.Currency;

/**
 * 한 시점의 종목 시세 스냅샷. 금액은 모두 {@code currency}의 기본 단위(원·달러)이며,
 * 억원 같은 표시 단위 환산은 이 객체를 만들기 전에 끝나 있다.
 */
public record Quote(
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        long volume,
        BigDecimal tradingValue,
        BigDecimal marketCap,
        Currency currency,
        // Jackson은 읽을 때 시각을 컨텍스트 타임존으로 옮긴다. 그대로 두면 캐시에서 읽은 시각의
        // 오프셋이 UTC로 바뀌어, 캐시 히트 응답만 시장 타임존과 다른 값이 나간다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime priceAt,
        PriceTiming priceTiming
) {

    private static final int SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    /** 직전 정규장 종가 대비 등락폭. 음수일 수 있다. */
    public BigDecimal change() {
        return price.subtract(previousClose);
    }

    /** 등락률 %를 소수 둘째 자리로. 전일 종가가 0이면 0.00이다. */
    public BigDecimal changeRate() {
        if (previousClose.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return price.subtract(previousClose)
                .divide(previousClose, DIVISION_SCALE, RoundingMode.HALF_UP)
                .movePointRight(SCALE)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 당일 거래량이 최근 20거래일 평균의 몇 배인지. 평균을 낼 수 없으면 null이다. */
    public BigDecimal volumeRatio(Optional<Long> averageVolume20d) {
        if (averageVolume20d.isEmpty()) {
            return null;
        }
        long average = averageVolume20d.get();
        if (average == 0) {
            return null;
        }
        return BigDecimal.valueOf(volume)
                .divide(BigDecimal.valueOf(average), SCALE, RoundingMode.HALF_UP);
    }

    /** 당일 시가가 없으면 아직 개장하지 않은 것으로 본다. */
    public boolean notOpenedToday() {
        return open.signum() == 0;
    }
}
