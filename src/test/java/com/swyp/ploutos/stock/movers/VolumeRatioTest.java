package com.swyp.ploutos.stock.movers;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class VolumeRatioTest {

    @Test
    void 당일_거래량이_기준의_두_배면_배수가_2가_된다() {
        // given 기준 1만주, 당일 2만주
        long volume = 20_000L;
        long baseline = 10_000L;

        // when
        Optional<BigDecimal> ratio = VolumeRatio.of(volume, baseline);

        // then
        assertThat(ratio).contains(new BigDecimal("2.00"));
    }

    @Test
    void 기준_거래량이_0이면_배수를_재지_못한다() {
        // given 전일 거래가 없었다
        long baseline = 0L;

        // when
        Optional<BigDecimal> ratio = VolumeRatio.of(20_000L, baseline);

        // then 0으로 나눌 수 없다
        assertThat(ratio).isEmpty();
    }

    @Test
    void 두_배에_못_미쳐도_줄을_세울_수_있다() {
        // given 장중에는 배수가 1보다 작게 나오는 것이 정상이다
        BigDecimal ratio = new BigDecimal("0.30");

        // when
        boolean measurable = VolumeRatio.measurable(ratio);

        // then 고정 숫자로 거르지 않는다 — 거르면 장 초반 목록이 통째로 빈다
        assertThat(measurable).isTrue();
    }

    @Test
    void 배수를_재지_못한_종목은_줄을_세울_수_없다() {
        // given 기준 거래량이 없어 배수가 비어 있다
        BigDecimal ratio = null;

        // when
        boolean measurable = VolumeRatio.measurable(ratio);

        // then
        assertThat(measurable).isFalse();
    }

    @Test
    void KIS가_포화시킨_구간도_실제_배수로_나온다() {
        // given 실측값 — KIS n_rate 는 9999.99 로 잘려 101배로 읽혔다
        long volume = 36_999_894L;
        long averageVolume = 103_380L;

        // when
        Optional<BigDecimal> ratio = VolumeRatio.of(volume, averageVolume);

        // then 원재료를 직접 나누면 상한이 없다
        assertThat(ratio).contains(new BigDecimal("357.90"));
    }
}
