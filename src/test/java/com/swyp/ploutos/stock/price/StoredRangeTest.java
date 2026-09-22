package com.swyp.ploutos.stock.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StoredRangeTest {

    private static final LocalDate BASE = LocalDate.of(2026, 8, 12);

    @Test
    void 가장_이른_거래일이_기준일_이전이면_시작을_덮는다() {
        // given
        StoredRange range = new StoredRange(Optional.of(BASE.minusDays(1)), Optional.of(BASE));

        // when & then
        assertThat(range.startsOnOrBefore(BASE)).isTrue();
        assertThat(range.startsOnOrBefore(BASE.minusDays(2))).isFalse();
    }

    @Test
    void 가장_늦은_거래일이_기준일_이후이면_끝을_덮는다() {
        // given
        StoredRange range = new StoredRange(Optional.of(BASE.minusDays(10)), Optional.of(BASE));

        // when & then
        assertThat(range.endsOnOrAfter(BASE)).isTrue();
        assertThat(range.endsOnOrAfter(BASE.plusDays(1))).isFalse();
    }

    @Test
    void 저장된_봉이_없으면_어느_쪽도_덮지_못한다() {
        // given
        StoredRange empty = StoredRange.empty();

        // when & then
        assertThat(empty.startsOnOrBefore(BASE)).isFalse();
        assertThat(empty.endsOnOrAfter(BASE)).isFalse();
    }
}
