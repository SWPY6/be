package com.swyp.ploutos.stock.movers.controller;

import java.math.BigDecimal;
import java.util.List;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.service.StockMoverDetail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주요 변동 종목 목록")
record StockMoverResponse(

        @Schema(description = "요청한 조건을 그대로 돌려준다. 화면의 강조 표시에 쓴다",
                example = "RISING")
        MoverCondition condition,

        @Schema(description = """
                조건에 맞는 종목 수. stocks 의 길이와 같다 — 서버가 페이지를 나누지 않고
                한 번에 모두 보낸다.""",
                example = "60")
        int totalCount,

        @Schema(description = "표시 순서대로다. 순위는 이 배열의 순서이며 따로 내려보내지 않는다")
        List<MoverStockResponse> stocks
) {

    static StockMoverResponse from(StockMoverDetail detail) {
        List<MoverStockResponse> stocks = detail.stocks().stream()
                .map(MoverStockResponse::from)
                .toList();
        return new StockMoverResponse(detail.condition(), stocks.size(), stocks);
    }

    @Schema(description = "목록의 한 줄")
    record MoverStockResponse(

            @Schema(description = """
                    종목 식별자. 종목 상세로 이동할 때 쓴다. 우리 종목 정보에 없는 종목은
                    null 이며 그 줄은 상세로 이동할 수 없다 — ETF·우선주·신규 상장이 여기 해당한다.""",
                    example = "10")
            Long stockId,

            @Schema(description = "종목 코드. 화면에 표시한다", example = "005380")
            String ticker,

            @Schema(description = "종목명", example = "현대차")
            String name,

            @Schema(description = "산업. 분류되지 않은 종목과 우리 종목 정보에 없는 종목은 null",
                    example = "AUTOMOBILE")
            IndustryCode industry,

            @Schema(description = "현재가", example = "324500")
            BigDecimal price,

            @Schema(description = "직전 거래일 종가 대비 등락률 %. 음수 가능", example = "-3.42")
            BigDecimal changeRate,

            @Schema(description = "당일 누적 거래량", example = "685775")
            long volume,

            @Schema(description = """
                    당일 누적 거래대금. 공급자가 주지 않는 조건에서는 null 이다 —
                    임의의 수치로 채우지 않는다.""",
                    example = "222000000000")
            BigDecimal tradingValue,

            @Schema(description = "시가총액. 거래대금과 같은 이유로 null 일 수 있다",
                    example = "68000000000000")
            BigDecimal marketCap
    ) {

        /**
         * 거래량 배수는 싣지 않는다. 분자가 당일 누적이라 장중에는 1보다 작게 나와,
         * 숫자로 보이면 "거래가 한산하다"로 읽힌다. 줄을 세우는 데만 쓴다.
         */
        static MoverStockResponse from(StockMover mover) {
            return new MoverStockResponse(mover.stockId(), mover.ticker(), mover.name(),
                    mover.industry(), mover.price(), mover.changeRate(), mover.volume(),
                    mover.tradingValue(), mover.marketCap());
        }
    }
}
