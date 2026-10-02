package com.swyp.ploutos.industry.flow.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "산업 하나의 오늘 흐름")
record IndustryFlowResponse(

        @Schema(description = "산업 코드. 산업 식별자로 이 값을 쓴다", example = "AUTOMOBILE")
        IndustryCode code,

        @Schema(description = "한글 산업명", example = "자동차")
        String displayName,

        @Schema(description = "평균 등락률 순위. 1이 가장 높다", example = "1")
        int rank,

        @Schema(description = "소속 종목 등락률의 단순평균 %. 음수 가능", example = "1.61")
        BigDecimal avgChangeRate,

        @Schema(description = "평균에 실제로 반영된 종목 수", example = "87")
        int stockCount,

        @Schema(description = "시가총액 상위 대표 종목. 0~2개")
        List<MajorStockResponse> majorStocks,

        // Jackson은 읽을 때 시각을 컨텍스트 타임존으로 옮긴다. 시장 현지 오프셋을 그대로 내보낸다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(description = "계산 시각(시장 현지). 한 번도 계산되지 않은 산업은 null",
                example = "2026-09-28T10:00:07+09:00")
        OffsetDateTime calculatedAt
) {

    static IndustryFlowResponse from(RankedIndustryFlow flow) {
        return new IndustryFlowResponse(
                flow.code(),
                flow.displayName(),
                flow.rank(),
                flow.displayAvgChangeRate(),
                flow.stockCount(),
                flow.stocks().stream().map(MajorStockResponse::from).toList(),
                flow.calculatedAt());
    }

    @Schema(description = "산업의 대표 종목")
    record MajorStockResponse(

            @Schema(description = "종목 코드", example = "005380")
            String ticker,

            @Schema(description = "종목명. 계산 시점의 값", example = "현대차")
            String name,

            @Schema(description = "그 종목의 등락률 %", example = "3.24")
            BigDecimal changeRate
    ) {

        static MajorStockResponse from(IndustryFlowStock stock) {
            return new MajorStockResponse(stock.ticker(), stock.name(), stock.changeRate());
        }
    }
}
