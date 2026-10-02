package com.swyp.ploutos.market.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrice;

class MarketDailyPricesTest {

    @Test
    void 일봉으로_바꾸면_거래량은_0이다() {
        // given 지표에는 거래량이 없어 저장하지 않는다
        DailyPrice saved = new DailyPrice(
                LocalDate.of(2026, 9, 29),
                new BigDecimal("6844.4100"),
                new BigDecimal("6898.3600"),
                new BigDecimal("6782.9900"),
                new BigDecimal("6870.8100"),
                0L);
        MarketDailyPrices entity = new MarketDailyPrices(MarketIndicator.KOSPI, saved);

        // when
        DailyPrice price = entity.toDailyPrice();

        // then
        assertThat(price.volume()).isZero();
        assertThat(price.tradeAt()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(price.open()).isEqualByComparingTo("6844.41");
        assertThat(price.high()).isEqualByComparingTo("6898.36");
        assertThat(price.low()).isEqualByComparingTo("6782.99");
        assertThat(price.close()).isEqualByComparingTo("6870.81");
    }
}
