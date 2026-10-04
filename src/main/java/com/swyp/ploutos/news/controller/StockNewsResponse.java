package com.swyp.ploutos.news.controller;

import java.time.OffsetDateTime;
import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsArticle.LinkKind;
import com.swyp.ploutos.news.service.StockNewsResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "종목 관련 뉴스. 함께 확인된 맥락이며 가격 변동의 원인으로 단정하지 않는다")
record StockNewsResponse(
        @Schema(description = "종목 ID", example = "1") Long stockId,
        @Schema(description = "종목 시장의 국가(KR/US)", example = "KR") Country country,
        @Schema(description = "적용한 조회 기간. from 제외, to 포함") Window window,
        @Schema(description = "네이버에서 검색 결과를 받은 시각(시장 현지 시각). 최대 10분 캐시라 현재보다 과거일 수 있다",
                example = "2026-09-30T13:55:12+09:00")
        OffsetDateTime fetchedAt,
        @Schema(description = "기간 시작까지 검색 결과를 다 훑었는지. false면 기간 앞쪽 기사가 빠졌을 수 있다", example = "true")
        boolean windowCovered,
        @Schema(description = "items 건수(필터·중복 제거 후, 최대 20). 네이버 검색 전체 건수가 아니다", example = "1")
        int total,
        @Schema(description = "최신순 기사. 없으면 빈 배열") List<Item> items
) {

    static StockNewsResponse from(StockNewsResult news) {
        return new StockNewsResponse(
                news.stockId(),
                news.country(),
                new Window(news.window().from(), news.window().to()),
                news.fetchedAt(),
                news.feed().windowCovered(),
                news.feed().total(),
                news.feed().items().stream().map(Item::from).toList()
        );
    }

    @Schema(description = "조회 기간")
    record Window(
            @Schema(example = "2026-09-23T14:00:00+09:00") OffsetDateTime from,
            @Schema(example = "2026-09-30T14:00:00+09:00") OffsetDateTime to
    ) {
    }

    @Schema(description = "뉴스 기사")
    record Item(
            @Schema(description = "원문 URL 기반 문서 ID. 같은 원문이면 같은 값", example = "3f2a9c01b7d4e865")
            String documentId,
            @Schema(description = "기사 제목(HTML 제거된 일반 텍스트)", example = "삼성전자, HBM 공급 확대") String title,
            @Schema(description = "네이버 검색 요약(일반 텍스트). 기사 전체 요약이 아니다. 없으면 null",
                    example = "삼성전자가 HBM 공급을 늘린다", nullable = true)
            String summary,
            @Schema(description = "표시용 출처. 원문 호스트", example = "news.mt.co.kr") String source,
            @Schema(description = "언론사명. 서버 사전에 없는 도메인이면 null", example = "머니투데이", nullable = true)
            String publisherName,
            @Schema(description = "네이버가 제공한 기사 시각. 원문 최초 발표 시각이라고 단정하지 않는다",
                    example = "2026-09-30T09:12:00+09:00")
            OffsetDateTime publishedAt,
            @Schema(description = "publishedAt의 의미. 항상 NAVER_PROVIDED", example = "NAVER_PROVIDED")
            String timestampBasis,
            @Schema(description = "기사 링크", example = "https://news.mt.co.kr/mtview.php?no=2026093009120012345")
            String url,
            @Schema(description = "ORIGINAL: 언론사 원문, NAVER: 원문이 없어 대신 준 네이버 뉴스 링크", example = "ORIGINAL")
            LinkKind linkKind
    ) {

        private static final String NAVER_PROVIDED = "NAVER_PROVIDED";

        static Item from(NewsArticle article) {
            return new Item(
                    article.documentId(),
                    article.title(),
                    article.summary(),
                    article.source(),
                    article.publisherName(),
                    article.publishedAt(),
                    NAVER_PROVIDED,
                    article.url(),
                    article.linkKind()
            );
        }
    }
}
