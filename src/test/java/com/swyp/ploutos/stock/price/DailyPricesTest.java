package com.swyp.ploutos.stock.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class DailyPricesTest {

    private static final LocalDate BASE = LocalDate.of(2026, 8, 1);

    @Test
    void 거래일_오름차순으로_정렬한다() {
        // given
        List<DailyPrice> unordered = List.of(
                price(BASE.plusDays(2), 3),
                price(BASE, 1),
                price(BASE.plusDays(1), 2)
        );

        // when
        DailyPrices prices = DailyPrices.of(unordered);

        // then
        assertThat(prices.values()).extracting(DailyPrice::volume).containsExactly(1L, 2L, 3L);
        assertThat(prices.firstTradeAt()).contains(BASE);
        assertThat(prices.lastTradeAt()).contains(BASE.plusDays(2));
    }

    @Test
    void 최근_20거래일_거래량의_평균을_반환한다() {
        // given
        // 25일치. 마지막 20일의 거래량은 6..25, 합 310, 평균 15.5 → 버림 15
        List<DailyPrice> prices = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> price(BASE.plusDays(i), i))
                .toList();

        // when
        var average = DailyPrices.of(prices).averageVolumeOfLast(20);

        // then
        assertThat(average).contains(15L);
    }

    @Test
    void 거래일이_20일_미만이면_평균은_없다() {
        // given
        List<DailyPrice> prices = IntStream.rangeClosed(1, 19)
                .mapToObj(i -> price(BASE.plusDays(i), 100))
                .toList();

        // when
        var average = DailyPrices.of(prices).averageVolumeOfLast(20);

        // then
        assertThat(average).isEmpty();
    }

    @Test
    void 당일_행을_제거한다() {
        // given
        DailyPrices prices = DailyPrices.of(List.of(
                price(BASE, 1),
                price(BASE.plusDays(1), 2),
                price(BASE.plusDays(2), 3)
        ));

        // when
        DailyPrices withoutToday = prices.without(BASE.plusDays(2));

        // then
        assertThat(withoutToday.values()).extracting(DailyPrice::volume).containsExactly(1L, 2L);
        assertThat(withoutToday.lastTradeAt()).contains(BASE.plusDays(1));
    }

    @Test
    void 저장된_거래일은_제외한다() {
        // given
        DailyPrices prices = DailyPrices.of(List.of(
                price(BASE, 1),
                price(BASE.plusDays(1), 2),
                price(BASE.plusDays(2), 3)
        ));

        // when
        DailyPrices remaining = prices.excluding(Set.of(BASE, BASE.plusDays(2)));

        // then
        assertThat(remaining.values()).extracting(DailyPrice::volume).containsExactly(2L);
    }

    @Test
    void 최근_20거래일_평균은_기본_기간을_쓴다() {
        // given
        List<DailyPrice> prices = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> price(BASE.plusDays(i), i))
                .toList();

        // when
        var average = DailyPrices.of(prices).averageVolume20d();

        // then
        assertThat(DailyPrices.AVERAGE_DAYS).isEqualTo(20);
        assertThat(average).contains(10L);
    }

    @Test
    void 그_거래일의_봉이_있는지_스스로_답한다() {
        // given
        DailyPrices prices = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 8, 11), 1),
                price(LocalDate.of(2026, 8, 13), 3)));

        // when & then 꺼내서 바깥에서 판단하지 않게 한다
        assertThat(prices.hasTradeOn(LocalDate.of(2026, 8, 13))).isTrue();
        assertThat(prices.hasTradeOn(LocalDate.of(2026, 8, 12))).isFalse();
        assertThat(DailyPrices.of(List.of()).hasTradeOn(LocalDate.of(2026, 8, 13))).isFalse();
    }

    @Test
    void 비어_있으면_첫_거래일과_마지막_거래일이_없다() {
        // when
        DailyPrices empty = DailyPrices.of(List.of());

        // then
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.firstTradeAt()).isEmpty();
        assertThat(empty.lastTradeAt()).isEmpty();
    }

    @Test
    void 마지막_확정_봉_종가와_같으면_참이다() {
        // given 마지막 봉과 그 앞 봉의 종가가 다르다
        DailyPrices prices = DailyPrices.of(List.of(closedAt(BASE, "48000"), closedAt(BASE.plusDays(1), "49000")));

        // when
        boolean continues = prices.lastCloseIs(new BigDecimal("49000"));

        // then
        assertThat(continues).isTrue();
    }

    @Test
    void 마지막_확정_봉_종가와_다르면_거짓이다() {
        // given 시세의 전일 종가가 그 앞 봉의 종가다 — 이미 확정된 거래일의 시세다
        DailyPrices prices = DailyPrices.of(List.of(closedAt(BASE, "48000"), closedAt(BASE.plusDays(1), "49000")));

        // when
        boolean continues = prices.lastCloseIs(new BigDecimal("48000"));

        // then
        assertThat(continues).isFalse();
    }

    @Test
    void 자릿수가_달라도_같은_값이면_참이다() {
        // given 저장 정밀도는 소수 넷째 자리고 KIS 전일 종가는 정수로 온다
        DailyPrices prices = DailyPrices.of(List.of(closedAt(BASE, "49000.0000")));

        // when
        boolean continues = prices.lastCloseIs(new BigDecimal("49000"));

        // then
        assertThat(continues).isTrue();
    }

    @Test
    void 비어_있으면_거짓이다() {
        // given 비교할 종가가 없다
        DailyPrices empty = DailyPrices.of(List.of());

        // when
        boolean continues = empty.lastCloseIs(new BigDecimal("49000"));

        // then
        assertThat(continues).isFalse();
    }

    private static DailyPrice closedAt(LocalDate tradeAt, String close) {
        BigDecimal closing = new BigDecimal(close);
        return new DailyPrice(tradeAt, closing, closing, closing, closing, 100L);
    }

    private static DailyPrice price(LocalDate tradeAt, long volume) {
        return new DailyPrice(tradeAt, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TWO, volume);
    }
}
