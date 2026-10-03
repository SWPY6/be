package com.swyp.ploutos.stock.chart.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartCandle;
import com.swyp.ploutos.stock.chart.service.StockChartDetail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "구간·봉 단위별 가격·거래량 차트")
record StockChartResponse(
        @Schema(description = "종목 ID", example = "1") Long stockId,
        @Schema(description = "적용된 봉 단위. 생략 요청이면 1D", example = "1M") String interval,
        @Schema(description = "가격 통화", example = "KRW") Currency currency,
        @Schema(description = "첫 봉의 거래일. 봉이 없으면 null", example = "2026-05-12", nullable = true)
        LocalDate from,
        @Schema(description = "마지막 봉의 거래일. 봉이 없으면 null",
                example = "2026-08-12", nullable = true)
        LocalDate to,
        @Schema(description = "진행 중 봉의 기준 시각. 진행 중 봉이 없으면 null",
                example = "2026-08-12T14:31:05+09:00", nullable = true)
        // Quote.priceAt과 같은 이유다. 그대로 두면 오프셋이 UTC로 바뀌어 /quote와 다른 시각이 나간다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime asOf,
        @Schema(description = "확정 봉 중 마지막 최대 20개의 평균 거래량. 확정 봉이 없으면 null",
                example = "84210", nullable = true)
        Long averageVolume,
        @Schema(description = "거래일 오름차순. 빈 배열 가능") List<Candle> candles
) {

    static StockChartResponse from(StockChartDetail detail) {
        Chart chart = detail.chart();
        return new StockChartResponse(
                detail.stockId(),
                detail.interval().code(),
                detail.currency(),
                chart.from().orElse(null),
                chart.to().orElse(null),
                chart.asOf().orElse(null),
                chart.averageVolume().orElse(null),
                chart.candles().stream().map(Candle::from).toList()
        );
    }

    @Schema(description = "봉 하나. 라인 차트는 close만 쓴다")
    record Candle(
            @Schema(description = "봉에 포함된 첫 거래일", example = "2026-05-12") LocalDate tradeAt,
            @Schema(description = "시가 — 봉의 첫 거래일 시가", example = "244280") BigDecimal open,
            @Schema(description = "고가 — 봉 구간의 최댓값", example = "251224") BigDecimal high,
            @Schema(description = "저가 — 봉 구간의 최솟값", example = "241056") BigDecimal low,
            @Schema(description = "종가 — 봉의 마지막 거래일 종가. 진행 중 봉은 현재가", example = "248000") BigDecimal close,
            @Schema(description = "거래량(주) — 봉 구간의 합계", example = "245000") long volume,
            @Schema(description = "true 확정 봉, false 진행 중 봉", example = "true") boolean closed
    ) {

        static Candle from(ChartCandle candle) {
            return new Candle(
                    candle.tradeAt(),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume(),
                    candle.closed()
            );
        }
    }
}
