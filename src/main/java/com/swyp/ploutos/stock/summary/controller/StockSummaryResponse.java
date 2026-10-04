package com.swyp.ploutos.stock.summary.controller;

import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.stock.summary.StockSummary;

import io.swagger.v3.oas.annotations.media.Schema;

sealed interface StockSummaryResponse
        permits StockSummaryResponse.Summary, StockSummaryResponse.Profile, StockSummaryResponse.Industry {

    @Schema(description = "종목 기본정보. 가격은 담지 않는다 — 같은 stockId로 /quote를 따로 조회해 조합한다")
    record Summary(
            @Schema(description = "종목 ID", example = "1") Long stockId,
            @Schema(description = "종목 식별 정보") Profile profile,
            @Schema(description = "시장 국가", example = "KR") Country market,
            @Schema(description = "가격 통화", example = "KRW") Currency currency,
            @Schema(description = "시장 현지 시간대(IANA)", example = "Asia/Seoul") String timezone
    ) implements StockSummaryResponse {

        static Summary from(StockSummary summary) {
            return new Summary(
                    summary.stockId(),
                    new Profile(summary.name(), summary.ticker(), summary.logoUrl(),
                            summary.industries().stream().map(Industry::from).toList()),
                    summary.market(),
                    summary.currency(),
                    summary.timezone().getId()
            );
        }
    }

    @Schema(description = "종목 식별 정보")
    record Profile(
            @Schema(description = "종목명", example = "삼성전자") String name,
            @Schema(description = "종목 코드·티커. 선행 0을 보존한다", example = "005930") String ticker,
            @Schema(description = "로고 이미지 URL. 없으면 null이며 프론트가 대체 이미지를 쓴다",
                    example = "https://example.com/logo/005930.png", nullable = true)
            String logoUrl,
            @Schema(description = "연결된 산업. 한글 표시명 가나다순이며 연결이 없으면 빈 배열이다") List<Industry> industries
    ) implements StockSummaryResponse {
    }

    @Schema(description = "종목에 연결된 산업")
    record Industry(
            @Schema(description = "산업 코드", example = "AUTOMOBILE") IndustryCode code,
            @Schema(description = "산업 한글 표시명", example = "자동차") String name
    ) implements StockSummaryResponse {

        static Industry from(IndustryCode code) {
            return new Industry(code, code.displayName());
        }
    }
}
