package com.swyp.ploutos.industry.flow.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.service.IndustryNewsDetail;
import com.swyp.ploutos.news.RelatedNews;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "핵심 뉴스 카드 한 장")
record IndustryNewsResponse(

        @Schema(description = "산업 코드. 산업 식별자로 이 값을 쓴다", example = "AUTOMOBILE")
        IndustryCode code,

        @Schema(description = "한글 산업명", example = "자동차")
        String displayName,

        @Schema(description = "RISING은 상승 카드, FALLING은 하락 카드. 등락률의 부호가 아니라 카드의 자리다",
                example = "RISING")
        Direction direction,

        @Schema(description = "평균 등락률 순위. 1이 가장 높다. 선정 순서와 무관하므로 1이 아닐 수 있다",
                example = "1")
        int rank,

        @Schema(description = "소속 종목 등락률의 단순평균 %. 음수 가능", example = "1.61")
        BigDecimal avgChangeRate,

        @Schema(description = "평균에 실제로 반영된 종목 수", example = "4")
        int stockCount,

        @Schema(description = "그중 오른 종목 수", example = "3")
        int risingCount,

        @Schema(description = """
                그중 내린 종목 수. 보합은 어느 쪽에도 세지 않으므로 stockCount − risingCount 로
                역산할 수 없다.""",
                example = "1")
        int fallingCount,

        @Schema(description = """
                가격 움직임 전후에 발표된 관련 뉴스. 직전 거래일 종가 산정 시점부터 calculatedAt
                까지 발표되고 이 산업 종목에 연결된 것이다. 없으면 빈 배열이며 화면은 이 영역을
                생략한다. 함께 확인된 맥락이고 가격 변동의 원인이 아니다.""")
        List<RelatedNewsResponse> news,

        // Jackson은 읽을 때 시각을 컨텍스트 타임존으로 옮긴다. 시장 현지 오프셋을 그대로 내보낸다.
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(description = "계산 시각(시장 현지). 한 번도 계산되지 않은 산업은 null",
                example = "2026-09-04T15:30:00+09:00")
        OffsetDateTime calculatedAt
) {

    static IndustryNewsResponse from(IndustryNewsDetail detail, Country country) {
        RankedIndustryFlow flow = detail.flow();
        return new IndustryNewsResponse(
                flow.code(),
                flow.displayName(),
                detail.direction(),
                flow.rank(),
                flow.avgChangeRate(),
                flow.stockCount(),
                flow.risingCount(),
                flow.fallingCount(),
                detail.news().stream().map(news -> RelatedNewsResponse.from(news, country)).toList(),
                flow.calculatedAt());
    }

    @Schema(description = "관련 뉴스")
    record RelatedNewsResponse(

            @Schema(description = "제목", example = "자동차 수출 증가 발표")
            String title,

            @Schema(description = "출처", example = "산업통상자원부")
            String publisher,

            // 저장된 발표 시각에는 오프셋이 없다. 시장 타임존을 붙여 내려준다.
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            @Schema(description = "발표 시각(시장 현지)", example = "2026-09-04T09:00:00+09:00")
            OffsetDateTime publishedAt,

            @Schema(description = "원문 링크", example = "https://example.com/news/1")
            String url
    ) {

        static RelatedNewsResponse from(RelatedNews news, Country country) {
            return new RelatedNewsResponse(news.title(), news.publisher(),
                    news.publishedAt().atZone(country.zoneId()).toOffsetDateTime(),
                    news.url());
        }
    }
}
