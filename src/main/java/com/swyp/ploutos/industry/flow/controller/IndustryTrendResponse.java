package com.swyp.ploutos.industry.flow.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "산업별 동향 카드 한 장")
record IndustryTrendResponse(

        @Schema(description = "산업 코드. 산업 식별자로 이 값을 쓴다", example = "AUTOMOBILE")
        IndustryCode code,

        @Schema(description = "한글 산업명", example = "자동차")
        String displayName,

        @Schema(description = """
                9개 산업 전체를 놓고 매긴 평균 등락률 순위. 1이 가장 높다.
                filter 와 무관하므로 FALLING 으로 3건을 받아도 7·8·9 가 올 수 있다 —
                배열 인덱스로 세면 안 된다.""",
                example = "1")
        int rank,

        @Schema(description = "소속 종목 등락률의 단순평균 %. 음수 가능", example = "1.61")
        BigDecimal avgChangeRate,

        @Schema(description = "평균에 실제로 반영된 종목 수", example = "87")
        int stockCount,

        @Schema(description = "현재가의 표시 단위. 국내는 KRW, 해외는 USD", example = "KRW")
        Currency currency,

        @Schema(description = """
                시가총액 상위 종목. 0~4개로 가변이다 — 시세를 구하지 못한 산업은 빈 배열이므로
                4개를 가정하면 안 된다.""")
        List<TrendStockResponse> stocks,

        // Jackson은 읽을 때 시각을 컨텍스트 타임존으로 옮긴다. 시장 현지 오프셋을 그대로 내보낸다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(description = "계산 시각(시장 현지). 한 번도 계산되지 않은 산업은 null",
                example = "2026-09-28T10:00:07+09:00")
        OffsetDateTime calculatedAt
) {

    static IndustryTrendResponse from(RankedIndustryFlow flow, Country country) {
        return new IndustryTrendResponse(
                flow.code(),
                flow.displayName(),
                flow.rank(),
                flow.displayAvgChangeRate(),
                flow.stockCount(),
                country.currency(),
                flow.stocks().stream().map(TrendStockResponse::from).toList(),
                flow.calculatedAt());
    }

    @Schema(description = "산업 카드에 표시하는 종목")
    record TrendStockResponse(

            @Schema(description = "종목 식별자. 종목 상세·현재가·차트 조회에 이 값을 쓴다",
                    example = "10")
            Long stockId,

            @Schema(description = "종목 코드. 화면에 표시한다", example = "005380")
            String ticker,

            @Schema(description = "종목명. 계산 시점의 값", example = "현대차")
            String name,

            @Schema(description = "현재가. 단위는 바깥의 currency 가 정한다", example = "248000")
            BigDecimal price,

            @Schema(description = "그 종목의 등락률 %", example = "3.24")
            BigDecimal changeRate
    ) {

        /**
         * 종목 API가 {@code /api/v1/stocks/{stockId}}만 받고 {@code ticker}로 조회하는 경로를
         * 주지 않아, 이 값이 없으면 프론트가 종목 상세로 이동할 수 없다.
         *
         * <p>auto_increment 값이라 환경마다 다를 수 있으므로 프론트는 저장하지 않고 그 화면에서
         * 이동용으로만 쓴다.
         */
        static TrendStockResponse from(IndustryFlowStock stock) {
            return new TrendStockResponse(stock.stockId(), stock.ticker(), stock.name(),
                    stock.price(), stock.changeRate());
        }
    }
}
