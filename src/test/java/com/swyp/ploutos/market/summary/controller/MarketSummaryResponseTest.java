package com.swyp.ploutos.market.summary.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.IndicatorUnit;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

class MarketSummaryResponseTest {

    private static final OffsetDateTime VALUE_AT = OffsetDateTime.parse("2026-09-30T10:15:03+09:00");

    @Test
    void 현재값과_등락폭을_소수_둘째자리로_반올림한다() {
        // given 환율은 KIS에서 소수 넷째 자리로 온다
        IndicatorQuote quote = quote(MarketIndicator.USD_KRW, "1354.0000", "1359.9000");

        // when
        MarketSummaryResponse.IndicatorCard card = MarketSummaryResponse.IndicatorCard.from(quote);

        // then
        assertThat(card.value()).isEqualTo(new BigDecimal("1354.00"));
        assertThat(card.change()).isEqualTo(new BigDecimal("-5.90"));
        assertThat(card.changeRate()).isEqualTo(new BigDecimal("-0.43"));
    }

    @Test
    void 등락폭은_원값으로_계산한_뒤_반올림한다() {
        // given 반올림을 먼저 하면 1354.01 - 1359.90 = -5.89 가 되어 1전이 어긋난다
        IndicatorQuote quote = quote(MarketIndicator.USD_KRW, "1354.0050", "1359.9040");

        // when
        MarketSummaryResponse.IndicatorCard card = MarketSummaryResponse.IndicatorCard.from(quote);

        // then 원값끼리 뺀 -5.8990 을 반올림한 값이다
        assertThat(card.change()).isEqualTo(new BigDecimal("-5.90"));
    }

    @Test
    void 카드에_표시명과_단위를_담는다() {
        // given
        IndicatorQuote index = quote(MarketIndicator.KOSPI, "6870.81", "6889.74");
        IndicatorQuote exchangeRate = quote(MarketIndicator.USD_KRW, "1354.0000", "1359.9000");

        // when
        MarketSummaryResponse.IndicatorCard indexCard = MarketSummaryResponse.IndicatorCard.from(index);
        MarketSummaryResponse.IndicatorCard exchangeRateCard =
                MarketSummaryResponse.IndicatorCard.from(exchangeRate);

        // then
        assertThat(indexCard.name()).isEqualTo("코스피");
        assertThat(indexCard.unit()).isEqualTo(IndicatorUnit.POINT);
        assertThat(exchangeRateCard.name()).isEqualTo("원/달러 환율");
        assertThat(exchangeRateCard.unit()).isEqualTo(IndicatorUnit.KRW);
        assertThat(indexCard.valueAt()).isEqualTo(VALUE_AT);
    }

    private static IndicatorQuote quote(MarketIndicator indicator, String value, String previousClose) {
        BigDecimal amount = new BigDecimal(value);
        return new IndicatorQuote(
                indicator, amount, new BigDecimal(previousClose), amount, amount, amount, VALUE_AT);
    }
}
