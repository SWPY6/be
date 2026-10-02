package com.swyp.ploutos.industry.flow.service;

import java.time.LocalDateTime;
import java.util.List;
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
import com.swyp.ploutos.news.RelatedNews;
import com.swyp.ploutos.news.service.NewsReader;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;

import lombok.RequiredArgsConstructor;

/**
 * 오늘의 핵심 뉴스 카드 2장을 만든다. 저장된 산업 흐름에서 상승·하락 각 1건을 고르고
 * 그 산업에만 관련 뉴스를 붙인다.
 *
 * <p>외부 시세를 호출하지 않는다. 값은 {@link IndustryFlowRefresher}가 미리 계산해 저장해 둔다.
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
    private final NewsReader newsReader;

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
    private List<RelatedNews> newsOf(RankedIndustryFlow flow, Country country) {
        try {
            // 국가로 걸러 읽는다. 산업 하나에 국내·해외가 함께 매핑되므로 전부를 받으면
            // 다른 시장의 거래일과 뉴스가 섞인다.
            List<Long> stockIds = industryReader.readStockIds(
                    industryReader.read(flow.code()).industryId(), country);
            return window(stockIds, flow, country)
                    .map(from -> search(flow, stockIds, from, flow.calculatedAt().toLocalDateTime()))
                    .orElseGet(List::of);
        } catch (RuntimeException e) {
            log.warn("관련 뉴스를 읽지 못해 카드에서 생략한다. industry={}", flow.code(), e);
            return List.of();
        }
    }

    /**
     * 대표 종목에서 먼저 찾고, 없으면 산업 전체로 넓힌다.
     *
     * <p>카드가 이미 대표 종목의 이름과 등락률을 보여주므로, 그 종목의 뉴스가 실리면 사용자가
     * 보는 숫자와 읽는 기사가 같은 종목을 가리킨다. 소속 종목이 수십 개인 산업에서 아무 종목의
     * 최신 뉴스를 집으면 산업 움직임과 무관한 소형주 기사가 뽑힐 수 있다.
     *
     * <p>넓히는 단계가 있어야 손실이 없다. 대표 종목에 뉴스가 없다는 이유로 영역을 비우면,
     * 다른 종목에 뉴스가 있는데도 보여주지 않는 경우가 생긴다.
     */
    private List<RelatedNews> search(RankedIndustryFlow flow, List<Long> stockIds,
            LocalDateTime from, LocalDateTime to) {
        List<Long> majorStockIds = flow.majorStocks().stream()
                .map(IndustryFlowStock::stockId)
                .filter(Objects::nonNull)
                .toList();
        List<RelatedNews> fromMajor = majorStockIds.isEmpty()
                ? List.of()
                : newsReader.readByStockIds(majorStockIds, from, to);
        if (!fromMajor.isEmpty()) {
            return limit(fromMajor);
        }
        return limit(newsReader.readByStockIds(stockIds, from, to));
    }

    private static List<RelatedNews> limit(List<RelatedNews> news) {
        return news.stream().limit(NEWS_LIMIT).toList();
    }

    /**
     * 시간 창의 시작 — 직전 거래일의 종가 산정 시각(RQ-0403).
     *
     * <p>직전 거래일을 달력으로 계산하지 않고 <b>저장된 확정 일봉의 마지막 거래일</b>로 안다.
     * 당일 봉은 저장하지 않으므로 그것이 곧 직전 거래일이고, 주말·공휴일·조기 폐장을
     * 공휴일 목록 없이 자동으로 비껴간다.
     *
     * <p>소속 종목 중 일봉이 있는 첫 종목에서 읽는다. 호출자가 국가로 걸러 넘기므로 같은 시장이고
     * 거래일 달력이 같다 — 거르지 않으면 국내 종목의 거래일에 해외 마감 시각을 붙이게 된다.
     * 하나도 없으면 창을 정할 수 없으므로 비어 있다 — 그 경우 뉴스를 싣지 않는다.
     */
    private Optional<LocalDateTime> window(List<Long> stockIds, RankedIndustryFlow flow,
            Country country) {
        if (flow.calculatedAt() == null) {
            return Optional.empty();
        }
        return stockIds.stream()
                .map(stockId -> dailyPriceReader.readStoredLatest(stockId, LATEST_TRADE_DAY))
                .map(DailyPrices::lastTradeAt)
                .flatMap(Optional::stream)
                .findFirst()
                .map(country::closedAt);
    }
}
