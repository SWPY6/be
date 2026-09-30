package com.swyp.ploutos.stock.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class ChartIntervalTest {

    @Test
    void 봉_단위를_생략하면_일봉이다() {
        // when & then
        assertThat(ChartInterval.from(null)).isEqualTo(ChartInterval.DAY);
        assertThat(ChartInterval.from("")).isEqualTo(ChartInterval.DAY);
        assertThat(ChartInterval.from("  ")).isEqualTo(ChartInterval.DAY);
    }

    @ParameterizedTest
    @CsvSource({"1D,DAY", "1W,WEEK", "1M,MONTH", "3M,QUARTER", "1Y,YEAR"})
    void 코드로_봉_단위를_찾는다(String code, ChartInterval expected) {
        // when
        ChartInterval interval = ChartInterval.from(code);

        // then
        assertThat(interval).isEqualTo(expected);
        assertThat(interval.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2W", "1m", "5Y", "D", "1일"})
    void 허용되지_않은_봉_단위면_예외다(String code) {
        // when & then
        assertThatThrownBy(() -> ChartInterval.from(code))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 일봉은_거래일을_그대로_쓴다() {
        // given
        LocalDate wednesday = LocalDate.of(2026, 9, 16);

        // when & then
        assertThat(ChartInterval.DAY.bucketStart(wednesday)).isEqualTo(wednesday);
    }

    @Test
    void 주봉은_월요일을_기준으로_묶는다() {
        // given
        // 2026-09-14(월) ~ 2026-09-18(금)은 같은 주다.
        LocalDate monday = LocalDate.of(2026, 9, 14);
        LocalDate friday = LocalDate.of(2026, 9, 18);
        LocalDate nextMonday = LocalDate.of(2026, 9, 21);

        // when & then
        assertThat(ChartInterval.WEEK.bucketStart(monday)).isEqualTo(monday);
        assertThat(ChartInterval.WEEK.bucketStart(friday)).isEqualTo(monday);
        assertThat(ChartInterval.WEEK.bucketStart(nextMonday)).isEqualTo(nextMonday);
    }

    @Test
    void 월봉은_같은_달_거래일을_같은_버킷으로_묶는다() {
        // given
        LocalDate first = LocalDate.of(2026, 9, 1);
        LocalDate last = LocalDate.of(2026, 9, 30);
        LocalDate nextMonth = LocalDate.of(2026, 10, 1);

        // when & then
        assertThat(ChartInterval.MONTH.bucketStart(first)).isEqualTo(first);
        assertThat(ChartInterval.MONTH.bucketStart(last)).isEqualTo(first);
        assertThat(ChartInterval.MONTH.bucketStart(nextMonth)).isEqualTo(nextMonth);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-15,2026-01-01",
            "2026-03-31,2026-01-01",
            "2026-04-01,2026-04-01",
            "2026-09-29,2026-07-01",
            "2026-12-31,2026-10-01"
    })
    void 분기봉은_분기_첫_달_1일을_기준으로_묶는다(LocalDate tradeAt, LocalDate expected) {
        // when & then
        assertThat(ChartInterval.QUARTER.bucketStart(tradeAt)).isEqualTo(expected);
    }

    @Test
    void 연봉은_1월_1일을_기준으로_묶는다() {
        // given
        LocalDate midYear = LocalDate.of(2026, 9, 29);
        LocalDate yearEnd = LocalDate.of(2026, 12, 31);
        LocalDate nextYear = LocalDate.of(2027, 1, 1);

        // when & then
        assertThat(ChartInterval.YEAR.bucketStart(midYear)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ChartInterval.YEAR.bucketStart(yearEnd)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ChartInterval.YEAR.bucketStart(nextYear)).isEqualTo(nextYear);
    }
}
