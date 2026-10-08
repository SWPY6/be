package com.swyp.ploutos.industry.flow.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.IndustryCard;
import com.swyp.ploutos.industry.flow.IndustryCardSelector;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.service.StockNewsService;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;

import lombok.RequiredArgsConstructor;

/**
 * 오늘의 핵심 뉴스 카드 2장을 만든다. 저장된 산업 흐름에서 상승·하락 각 1건을 고르고
 * 그 산업에만 관련 뉴스를 붙인다.
 *
 * <p>외부 시세를 호출하지 않는다. 값은 {@link IndustryFlowRefresher}가 미리 계산해 저장해 둔다.
 *
 * <p>뉴스는 {@link StockNewsService}에서 온다. 그쪽이 종목별 캐시·일일 호출 예산·기간 필터·
 * 관련성 필터를 모두 가지고 있어 여기서 다시 거르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class IndustryNewsService {

    private static final Logger log = LoggerFactory.getLogger(IndustryNewsService.class);

    /** 카드에 싣는 뉴스 건수. 목업이 1건을 보여준다. 늘리려면 이 값만 고친다. */
    private static final int NEWS_LIMIT = 1;

    /** 직전 거래일을 알아내는 데만 쓴다. 가장 최근 확정 일봉 한 건이면 된다. */
    private static final int LATEST_TRADE_DAY = 1;

    private final IndustryFlowService industryFlowService;
    private final IndustryCardSelector selector;
    private final IndustryReader industryReader;
    private final DailyPriceReader dailyPriceReader;
    private final StockNewsService stockNewsService;

    /** 언제나 2건이고 {@code [RISING, FALLING]} 순이다. */
    public List<IndustryNewsDetail> read(Country country) {
        List<RankedIndustryFlow> flows = industryFlowService.read(country);
        return selector.select(flows).stream()
                .map(card -> toDetail(card, country))
                .toList();
    }

    private IndustryNewsDetail toDetail(IndustryCard card, Country country) {
        return new IndustryNewsDetail(card.flow(), card.direction(), card.selectedBy(),
                newsOf(card.flow(), country));
    }

    /**
     * 선정된 산업의 관련 뉴스. 조회가 실패해도 빈 목록으로 돌려준다 — 뉴스는 부가 정보이고
     * 요구사항이 생략을 허용하므로, 여기서 예외를 올리면 카드 전체를 잃는다.
     */
    private List<NewsArticle> newsOf(RankedIndustryFlow flow, Country country) {
        try {
            return window(flow, country)
                    .map(from -> search(flow, from, flow.calculatedAt()))
                    .orElseGet(List::of);
        } catch (RuntimeException e) {
            log.warn("관련 뉴스를 읽지 못해 카드에서 생략한다. industry={}", flow.code(), e);
            return List.of();
        }
    }

    /**
     * 대표 종목마다 한 번씩 찾는다. 산업 전체로 넓히지 않는다 — 외부 검색은 종목당 한 번이라
     * 소속 종목이 수십 개인 산업에서 호출이 그만큼 늘어난다.
     *
     * <p>카드가 이미 대표 종목의 이름과 등락률을 보여주므로, 그 종목의 뉴스가 실리면 사용자가
     * 보는 숫자와 읽는 기사가 같은 종목을 가리킨다.
     */
    private List<NewsArticle> search(RankedIndustryFlow flow, OffsetDateTime from, OffsetDateTime to) {
        Map<String, NewsArticle> unique = new LinkedHashMap<>();
        flow.majorStocks().stream()
                .map(IndustryFlowStock::stockId)
                .filter(Objects::nonNull)
                .flatMap(stockId -> stockNewsService.read(stockId, from, to).feed().items().stream())
                .forEach(article -> unique.putIfAbsent(article.documentId(), article));
        return unique.values().stream()
                .sorted(Comparator.comparing(NewsArticle::publishedAt,
                        OffsetDateTime.timeLineOrder()).reversed())
                .limit(NEWS_LIMIT)
                .toList();
    }

    /**
     * 시간 창의 시작 — 직전 거래일의 종가 산정 시각(RQ-0403).
     *
     * <p>직전 거래일을 달력으로 계산하지 않고 <b>저장된 확정 일봉의 마지막 거래일</b>로 안다.
     * 당일 봉은 저장하지 않으므로 그것이 곧 직전 거래일이고, 주말·공휴일·조기 폐장을
     * 공휴일 목록 없이 자동으로 비껴간다.
     *
     * <p>소속 종목 중 일봉이 있는 첫 종목에서 읽는다. 국가로 걸러 읽으므로 같은 시장이고
     * 거래일 달력이 같다 — 거르지 않으면 국내 종목의 거래일에 해외 마감 시각을 붙이게 된다.
     * 하나도 없으면 창을 정할 수 없으므로 비어 있다 — 그 경우 뉴스를 싣지 않는다.
     */
    private Optional<OffsetDateTime> window(RankedIndustryFlow flow, Country country) {
        if (flow.calculatedAt() == null) {
            return Optional.empty();
        }
        List<Long> stockIds = industryReader.readStockIds(
                industryReader.read(flow.code()).industryId(), country);
        return stockIds.stream()
                .map(stockId -> dailyPriceReader.readStoredLatest(stockId, LATEST_TRADE_DAY))
                .map(DailyPrices::lastTradeAt)
                .flatMap(Optional::stream)
                .findFirst()
                .map(country::closedAt)
                .map(closedAt -> closedAt.atZone(country.zoneId()).toOffsetDateTime());
    }
}
