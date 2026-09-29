package com.swyp.ploutos.stock.quote.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.StockQuoteDetail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "종목 현재가와 주요 지표")
record StockQuoteResponse(
        @Schema(description = "종목 ID", example = "1") Long stockId,
        @Schema(description = "종목 코드", example = "005380") String ticker,
        @Schema(description = "종목명", example = "현대차") String name,
        @Schema(description = "아래 모든 금액의 통화. 표기(원/달러, 조·억 축약)는 프론트가 한다", example = "KRW")
        Currency currency,
        @Schema(description = "현재가", example = "248000") BigDecimal price,
        @Schema(description = "직전 정규장 종가 대비 등락폭. 음수 가능", example = "7783") BigDecimal change,
        @Schema(description = "등락률(%), 소수 둘째 자리", example = "3.24") BigDecimal changeRate,
        @Schema(description = "가격 기준 시각(서버가 시세를 받은 시각). 시장 타임존 오프셋 포함",
                example = "2026-08-12T14:31:05+09:00")
        OffsetDateTime priceAt,
        @Schema(description = "실시간·지연 여부", example = "REALTIME") PriceTiming priceTiming,
        @Schema(description = "주요 지표") Indicators indicators
) {

    static StockQuoteResponse from(StockQuoteDetail detail) {
        Quote quote = detail.quote();
        return new StockQuoteResponse(
                detail.stockId(),
                detail.ticker(),
                detail.name(),
                quote.currency(),
                quote.price(),
                quote.change(),
                quote.changeRate(),
                quote.priceAt(),
                quote.priceTiming(),
                new Indicators(
                        quote.previousClose(),
                        quote.open(),
                        quote.high(),
                        quote.low(),
                        quote.volume(),
                        detail.volumeRatio20d(),
                        quote.marketCap(),
                        quote.tradingValue()
                )
        );
    }

    @Schema(description = "주요 지표. 금액은 currency의 기본 단위(원·달러)")
    record Indicators(
            @Schema(description = "전일 종가", example = "240217") BigDecimal previousClose,
            @Schema(description = "당일 시가", example = "244280") BigDecimal open,
            @Schema(description = "당일 고가", example = "251224") BigDecimal high,
            @Schema(description = "당일 저가", example = "241056") BigDecimal low,
            @Schema(description = "당일 누적 거래량(주)", example = "245000") long volume,
            @Schema(description = "당일 거래량 ÷ 최근 20거래일 평균 거래량, 소수 둘째 자리. 거래일 20일 미만이면 null",
                    example = "0.95", nullable = true)
            BigDecimal volumeRatio20d,
            @Schema(description = "시가총액", example = "86600000000000") BigDecimal marketCap,
            @Schema(description = "당일 누적 거래대금", example = "60800000000") BigDecimal tradingValue
    ) {
    }
}
