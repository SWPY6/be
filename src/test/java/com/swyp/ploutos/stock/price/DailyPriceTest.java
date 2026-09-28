package com.swyp.ploutos.stock.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DailyPriceTest {

    private static final LocalDate TRADE_AT = LocalDate.of(2026, 8, 12);

    @Test
    void 같은_날이면_그날_거래된_봉이다() {
        // given
        DailyPrice price = price(TRADE_AT);

        // when & then
        assertThat(price.tradedOn(TRADE_AT)).isTrue();
        assertThat(price.tradedOn(TRADE_AT.plusDays(1))).isFalse();
    }

    @Test
    void 거래일이_구간의_양끝이면_구간_안에서_거래된_봉이다() {
        // given
        DailyPrice price = price(TRADE_AT);

        // when & then
        assertThat(price.tradedBetween(TRADE_AT, TRADE_AT.plusDays(1))).isTrue();
        assertThat(price.tradedBetween(TRADE_AT.minusDays(1), TRADE_AT)).isTrue();
    }

    @Test
    void 거래일이_구간_밖이면_구간_안에서_거래된_봉이_아니다() {
        // given
        DailyPrice price = price(TRADE_AT);

        // when & then
        assertThat(price.tradedBetween(TRADE_AT.plusDays(1), TRADE_AT.plusDays(2))).isFalse();
        assertThat(price.tradedBetween(TRADE_AT.minusDays(2), TRADE_AT.minusDays(1))).isFalse();
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(tradeAt, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L);
    }
}
