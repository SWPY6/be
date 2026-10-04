package com.swyp.ploutos.market.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrice;

class IndicatorQuoteTest {

    private static final OffsetDateTime VALUE_AT =
            OffsetDateTime.of(2026, 9, 30, 10, 15, 3, 0, ZoneOffset.ofHours(9));

    @Test
    void 현재값과_전일종가로_등락률을_소수_둘째자리로_계산한다() {
        // given
        IndicatorQuote quote = quote(MarketIndicator.KOSPI, new BigDecimal("6870.81"), new BigDecimal("6889.74"));

        // when
        BigDecimal changeRate = quote.changeRate();

        // then
        assertThat(quote.change()).isEqualByComparingTo("-18.93");
        assertThat(changeRate).isEqualTo(new BigDecimal("-0.27"));
    }

    @Test
    void 전일종가가_0이면_등락률은_0이다() {
        // given
        IndicatorQuote quote = quote(MarketIndicator.KOSPI, new BigDecimal("100"), BigDecimal.ZERO);

        // when
        BigDecimal changeRate = quote.changeRate();

        // then
        assertThat(changeRate).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void 환율은_소수_넷째자리까지_보존한다() {
        // given
        IndicatorQuote quote = quote(MarketIndicator.USD_KRW, new BigDecimal("1354.0000"), new BigDecimal("1359.9000"));

        // when
        BigDecimal change = quote.change();

        // then
        assertThat(change).isEqualTo(new BigDecimal("-5.9000"));
        assertThat(quote.changeRate()).isEqualTo(new BigDecimal("-0.43"));
    }

    @Test
    void 현재값을_당일자_일봉으로_바꾼다() {
        // given 진행 중인 봉은 종가 자리에 현재값이 들어간다. 지표에는 거래량이 없다
        IndicatorQuote quote = new IndicatorQuote(
                MarketIndicator.KOSPI,
                new BigDecimal("6902.33"),
                new BigDecimal("6870.81"),
                new BigDecimal("6875.20"),
                new BigDecimal("6910.45"),
                new BigDecimal("6861.02"),
                VALUE_AT
        );

        // when
        DailyPrice price = quote.asDailyPrice(LocalDate.of(2026, 9, 30));

        // then
        assertThat(price.tradeAt()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(price.open()).isEqualByComparingTo("6875.20");
        assertThat(price.high()).isEqualByComparingTo("6910.45");
        assertThat(price.low()).isEqualByComparingTo("6861.02");
        assertThat(price.close()).isEqualByComparingTo("6902.33");
        assertThat(price.volume()).isZero();
    }

    private static IndicatorQuote quote(MarketIndicator indicator, BigDecimal value, BigDecimal previousClose) {
        return new IndicatorQuote(indicator, value, previousClose, value, value, value, VALUE_AT);
    }
}
