package com.swyp.ploutos.market.chart.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.market.IndicatorUnit;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.chart.service.MarketChartDetail;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartCandle;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "구간·봉 단위별 지표 캔들 차트")
record MarketChartResponse(
        @Schema(description = "지표 식별자. 요청 경로의 값. 값이 늘어날 수 있다", example = "KOSPI")
        MarketIndicator indicator,
        @Schema(description = "한글 표시명", example = "코스피") String name,
        @Schema(description = "가격 단위. 값이 늘어날 수 있다", example = "POINT") IndicatorUnit unit,
        @Schema(description = "적용된 봉 단위. 생략 요청이면 1D", example = "1D") String interval,
        @Schema(description = "첫 봉의 거래일. 봉이 없으면 null", example = "2026-09-28", nullable = true)
        LocalDate from,
        @Schema(description = "마지막 봉의 거래일. 봉이 없으면 null", example = "2026-09-30", nullable = true)
        LocalDate to,
        @Schema(description = "진행 중인 봉의 기준 시각. 진행 중인 봉이 없으면 null",
                example = "2026-09-30T10:15:03+09:00", nullable = true)
        // IndicatorQuote.valueAt과 같은 이유다. 그대로 두면 오프셋이 UTC로 바뀌어 카드와 다른 시각이 나간다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime asOf,
        @Schema(description = "거래일 오름차순. 빈 배열 가능") List<Candle> candles
) {

    static MarketChartResponse from(MarketChartDetail detail) {
        MarketIndicator indicator = detail.indicator();
        Chart chart = detail.chart();
        return new MarketChartResponse(
                indicator,
                indicator.displayName(),
                indicator.unit(),
                detail.interval().code(),
                chart.from().orElse(null),
                chart.to().orElse(null),
                chart.asOf().orElse(null),
                chart.candles().stream().map(Candle::from).toList()
        );
    }

    @Schema(description = "봉 하나. 라인 차트는 close만 쓴다. 지표에는 거래량이 없어 volume을 내보내지 않는다")
    record Candle(
            @Schema(description = "봉에 포함된 첫 거래일", example = "2026-09-28") LocalDate tradeAt,
            @Schema(description = "시가 — 봉의 첫 거래일 시가. 소수 둘째 자리", example = "7057.86") BigDecimal open,
            @Schema(description = "고가 — 봉 구간의 최댓값. 소수 둘째 자리", example = "7065.90") BigDecimal high,
            @Schema(description = "저가 — 봉 구간의 최솟값. 소수 둘째 자리", example = "6889.68") BigDecimal low,
            @Schema(description = "종가 — 봉의 마지막 거래일 종가. 진행 중인 봉은 현재값. 소수 둘째 자리",
                    example = "6889.74")
            BigDecimal close,
            @Schema(description = "true 확정 봉, false 진행 중인 봉", example = "true") boolean closed
    ) {

        /** 표시용 소수 자릿수. 지표마다 같다. 환율은 넷째 자리까지 저장되지만 둘째 자리로 내보낸다. */
        private static final int SCALE = 2;

        /** 반올림은 여기서 한다. 도메인은 KIS 원값을 그대로 들고 있어야 봉을 다시 묶을 때 오차가 쌓이지 않는다. */
        static Candle from(ChartCandle candle) {
            return new Candle(
                    candle.tradeAt(),
                    display(candle.open()),
                    display(candle.high()),
                    display(candle.low()),
                    display(candle.close()),
                    candle.closed()
            );
        }

        private static BigDecimal display(BigDecimal value) {
            return value.setScale(SCALE, RoundingMode.HALF_UP);
        }
    }
}
