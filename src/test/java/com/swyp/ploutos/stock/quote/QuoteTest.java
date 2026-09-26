package com.swyp.ploutos.stock.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Currency;

import tools.jackson.databind.json.JsonMapper;

class QuoteTest {

    private static final OffsetDateTime PRICE_AT =
            OffsetDateTime.of(2026, 8, 12, 14, 31, 5, 0, ZoneOffset.ofHours(9));

    @Test
    void 현재가와_전일종가를_받으면_등락률을_소수_둘째자리로_계산한다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        BigDecimal changeRate = quote.changeRate();

        // then
        assertThat(quote.change()).isEqualByComparingTo("7783");
        assertThat(changeRate).isEqualTo(new BigDecimal("3.24"));
    }

    @Test
    void 전일종가와_같으면_등락률은_0이다() {
        // given
        Quote quote = quote(new BigDecimal("240217"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        BigDecimal changeRate = quote.changeRate();

        // then
        assertThat(quote.change()).isEqualByComparingTo("0");
        assertThat(changeRate).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void 당일_거래량을_20거래일_평균으로_나눠_배수를_계산한다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        BigDecimal ratio = quote.volumeRatio(Optional.of(258_000L));

        // then
        assertThat(ratio).isEqualTo(new BigDecimal("0.95"));
    }

    @Test
    void 평균_거래량이_없으면_배수는_null이다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        BigDecimal ratio = quote.volumeRatio(Optional.empty());

        // then
        assertThat(ratio).isNull();
    }

    @Test
    void 시가가_0이면_당일_개장_전으로_본다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), BigDecimal.ZERO, 0);

        // when
        boolean notOpened = quote.notOpenedToday();

        // then
        assertThat(notOpened).isTrue();
    }

    @Test
    void JSON으로_저장했다가_같은_값으로_읽는다() {
        // given
        JsonMapper mapper = JsonMapper.builder().build();
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        Quote restored = mapper.readValue(mapper.writeValueAsString(quote), Quote.class);

        // then
        assertThat(restored).isEqualTo(quote);
    }

    @Test
    void 전일종가가_0이면_등락률은_0이다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), BigDecimal.ZERO, new BigDecimal("244280"), 245_000);

        // when
        BigDecimal changeRate = quote.changeRate();

        // then
        assertThat(changeRate).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void 평균_거래량이_0이면_배수는_null이다() {
        // given
        Quote quote = quote(new BigDecimal("248000"), new BigDecimal("240217"), new BigDecimal("244280"), 245_000);

        // when
        BigDecimal ratio = quote.volumeRatio(Optional.of(0L));

        // then
        assertThat(ratio).isNull();
    }

    private static Quote quote(BigDecimal price, BigDecimal previousClose, BigDecimal open, long volume) {
        return new Quote(
                price,
                previousClose,
                open,
                new BigDecimal("251224"),
                new BigDecimal("241056"),
                volume,
                new BigDecimal("60800000000"),
                new BigDecimal("86600000000000"),
                Currency.KRW,
                PRICE_AT,
                PriceTiming.REALTIME
        );
    }
}
