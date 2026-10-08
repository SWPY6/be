package com.swyp.ploutos.stock.movers;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class VolumeSurgeTest {

    @Test
    void 당일_거래량이_기준의_두_배면_배수가_2가_된다() {
        // given 전일 1만주, 당일 2만주
        long volume = 20_000L;
        long baseline = 10_000L;

        // when
        Optional<BigDecimal> ratio = VolumeSurge.ratio(volume, baseline);

        // then
        assertThat(ratio).contains(new BigDecimal("2.00"));
    }

    @Test
    void 기준_거래량이_0이면_배수를_재지_못한다() {
        // given 전일 거래가 없었다
        long baseline = 0L;

        // when
        Optional<BigDecimal> ratio = VolumeSurge.ratio(20_000L, baseline);

        // then 0으로 나눌 수 없다 — 급증 목록에서 빠진다
        assertThat(ratio).isEmpty();
    }

    @Test
    void 두_배에_못_미치면_급증이_아니다() {
        // given
        BigDecimal ratio = new BigDecimal("1.99");

        // when
        boolean surged = VolumeSurge.surged(ratio);

        // then
        assertThat(surged).isFalse();
    }

    @Test
    void 정확히_두_배면_급증이다() {
        // given
        BigDecimal ratio = new BigDecimal("2.00");

        // when
        boolean surged = VolumeSurge.surged(ratio);

        // then
        assertThat(surged).isTrue();
    }

    @Test
    void 배수를_재지_못한_종목은_급증이_아니다() {
        // given 기준이 없어 배수가 비어 있다
        BigDecimal ratio = null;

        // when
        boolean surged = VolumeSurge.surged(ratio);

        // then
        assertThat(surged).isFalse();
    }

    @Test
    void KIS가_포화시킨_구간도_실제_배수로_나온다() {
        // given 실측값 — KIS n_rate 는 9999.99 로 잘려 101배로 읽혔다
        long volume = 36_999_894L;
        long averageVolume = 103_380L;

        // when
        Optional<BigDecimal> ratio = VolumeSurge.ratio(volume, averageVolume);

        // then 원재료를 직접 나누면 상한이 없다
        assertThat(ratio).contains(new BigDecimal("357.90"));
    }
}
