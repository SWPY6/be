package com.swyp.ploutos.market.summary.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.market.IndicatorUnit;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.summary.service.MarketSummary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시장 탭별 지표 카드")
record MarketSummaryResponse(
        @Schema(description = "요청한 탭", example = "DOMESTIC") MarketRegion region,
        @Schema(description = "탭의 지표 카드. 표시 순서대로 온다") List<IndicatorCard> indicators
) {

    static MarketSummaryResponse from(MarketSummary summary) {
        return new MarketSummaryResponse(
                summary.region(),
                summary.quotes().stream().map(IndicatorCard::from).toList()
        );
    }

    @Schema(description = "지표 카드 하나. 부호·% ·단위 표기는 프론트가 한다")
    record IndicatorCard(
            @Schema(description = "지표 식별자. 차트 API 경로에 그대로 쓴다. 값이 늘어날 수 있다", example = "KOSPI")
            MarketIndicator indicator,
            @Schema(description = "한글 표시명", example = "코스피") String name,
            @Schema(description = "value와 change의 단위. 값이 늘어날 수 있다", example = "POINT") IndicatorUnit unit,
            @Schema(description = "현재값. 소수 둘째 자리", example = "6870.81") BigDecimal value,
            @Schema(description = "직전 거래일 종가 대비 등락폭. 소수 둘째 자리. 음수 가능", example = "-18.93")
            BigDecimal change,
            @Schema(description = "등락률(%). 소수 둘째 자리. 음수 가능", example = "-0.27") BigDecimal changeRate,
            @Schema(description = "값의 기준 시각(서버가 KIS에서 받은 시각). 지표 타임존 오프셋 포함",
                    example = "2026-09-30T10:15:03+09:00")
            // IndicatorQuote.valueAt과 같은 이유다. 그대로 두면 오프셋이 UTC로 바뀐다.
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            OffsetDateTime valueAt
    ) {

        /** 표시용 소수 자릿수. 지표마다 같다. */
        private static final int SCALE = 2;

        /**
         * 반올림은 여기서 한다. 등락폭은 {@code IndicatorQuote}가 원값끼리 계산한 뒤 반올림해야 한다 —
         * 반올림한 값끼리 빼면 환율(소수 넷째 자리)에서 1전씩 어긋난다.
         */
        static IndicatorCard from(IndicatorQuote quote) {
            MarketIndicator indicator = quote.indicator();
            return new IndicatorCard(
                    indicator,
                    indicator.displayName(),
                    indicator.unit(),
                    quote.value().setScale(SCALE, RoundingMode.HALF_UP),
                    quote.change().setScale(SCALE, RoundingMode.HALF_UP),
                    quote.changeRate(),
                    quote.valueAt()
            );
        }
    }
}
