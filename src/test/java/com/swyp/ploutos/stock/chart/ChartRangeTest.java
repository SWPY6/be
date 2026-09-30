package com.swyp.ploutos.stock.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class ChartRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Test
    void 구간을_생략하면_오늘까지_최근_2개월이다() {
        // when
        ChartRange range = ChartRange.of(null, null, TODAY);

        // then
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    void 시작일만_주면_종료일은_오늘이다() {
        // given
        LocalDate from = LocalDate.of(2026, 1, 2);

        // when
        ChartRange range = ChartRange.of(from, null, TODAY);

        // then
        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    void 종료일만_주면_시작일은_그로부터_2개월_전이다() {
        // given
        LocalDate to = LocalDate.of(2026, 6, 30);

        // when
        ChartRange range = ChartRange.of(null, to, TODAY);

        // then
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(range.to()).isEqualTo(to);
    }

    @Test
    void 시작일과_종료일이_같으면_하루짜리_구간이다() {
        // when
        ChartRange range = ChartRange.of(TODAY, TODAY, TODAY);

        // then
        assertThat(range.from()).isEqualTo(TODAY);
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    void 시작일이_종료일보다_뒤면_예외다() {
        // given
        LocalDate from = LocalDate.of(2026, 9, 29);
        LocalDate to = LocalDate.of(2026, 9, 1);

        // when & then
        assertThatThrownBy(() -> ChartRange.of(from, to, TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 구간이_정확히_오년이면_허용한다() {
        // given
        LocalDate from = LocalDate.of(2021, 9, 29);

        // when
        ChartRange range = ChartRange.of(from, TODAY, TODAY);

        // then
        assertThat(range.from()).isEqualTo(from);
    }

    @Test
    void 구간이_오년을_넘으면_예외다() {
        // given
        LocalDate from = LocalDate.of(2021, 9, 28);

        // when & then
        assertThatThrownBy(() -> ChartRange.of(from, TODAY, TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 상한은_오늘이_아니라_종료일을_기준으로_잰다() {
        // given
        // 종료일이 과거여도 그로부터 5년까지는 허용한다.
        LocalDate to = LocalDate.of(2024, 1, 2);
        LocalDate from = LocalDate.of(2019, 1, 2);

        // when
        ChartRange range = ChartRange.of(from, to, TODAY);

        // then
        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
    }
}
