package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCardSelector;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.StockNewsFeed;
import com.swyp.ploutos.news.service.StockNewsResult;
import com.swyp.ploutos.news.service.StockNewsService;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndustryNewsServiceTest {

    private static final java.math.BigDecimal PRICE = new java.math.BigDecimal("248000");

    /** 계산 시각 = 2026-09-04 15:30 KST. 직전 거래일은 09-03 이다. */
    private static final OffsetDateTime CALCULATED_AT =
            OffsetDateTime.of(2026, 9, 4, 15, 30, 0, 0, ZoneOffset.ofHours(9));
    private static final LocalDate PREVIOUS_TRADE_DAY = LocalDate.of(2026, 9, 3);

    @Mock
    private IndustryFlowService industryFlowService;

    @Mock
    private IndustryReader industryReader;

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private StockNewsService stockNewsService;

    @Captor
    private ArgumentCaptor<OffsetDateTime> from;

    @Captor
    private ArgumentCaptor<OffsetDateTime> to;

    @Captor
    private ArgumentCaptor<Long> stockId;

    private IndustryNewsService industryNewsService;

    /** 선정 규칙은 실물을 쓴다. 가짜로 바꾸면 이 테스트가 규칙을 검증하지 못한다. */
    @BeforeEach
    void setUp() {
        industryNewsService = new IndustryNewsService(industryFlowService, new IndustryCardSelector(),
                industryReader, dailyPriceReader, stockNewsService);
    }

    private void stubIndustry(IndustryCode code, Long industryId, List<Long> stockIds) {
        stubIndustry(code, industryId, Country.KR, stockIds);
    }

    private void stubIndustry(IndustryCode code, Long industryId, Country country,
            List<Long> stockIds) {
        given(industryReader.read(code)).willReturn(new Industries(industryId, code));
        given(industryReader.readStockIds(industryId, country)).willReturn(stockIds);
    }

    private void stubPreviousTradeDay(Long stockId, LocalDate tradeAt) {
        given(dailyPriceReader.readStoredLatest(eq(stockId), anyInt()))
                .willReturn(DailyPrices.of(List.of(price(tradeAt))));
    }

    @Test
    void 상승과_하락_카드_두_장을_만든다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then
        assertThat(details).hasSize(2);
        assertThat(details.get(0).direction()).isEqualTo(Direction.RISING);
        assertThat(details.get(1).direction()).isEqualTo(Direction.FALLING);
        assertThat(details.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(details.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
    }

    @Test
    void 시간_창은_직전_거래일_마감부터_계산_시각까지다() {
        // given 직전 거래일이 09-03 이고 국내 마감은 15:30 이다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        industryNewsService.read(Country.KR);

        // then
        then(stockNewsService).should(atLeastOnce())
                .read(anyLong(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(
                OffsetDateTime.of(2026, 9, 3, 15, 30, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(to.getValue()).isEqualTo(CALCULATED_AT);
    }

    @Test
    void 미국은_마감_시각이_16시다() {
        // given
        given(industryFlowService.read(Country.US)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.US, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, Country.US, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        industryNewsService.read(Country.US);

        // then
        then(stockNewsService).should(atLeastOnce())
                .read(anyLong(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(
                OffsetDateTime.of(2026, 9, 3, 16, 0, 0, 0, ZoneOffset.ofHours(-4)));
    }

    @Test
    void 뉴스는_카드마다_한_건만_싣는다() {
        // given 세 건이 걸려 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found(
                article("c", "더 예전", 9, 0), article("a", "가장 최근", 11, 0),
                article("b", "그다음", 10, 0)));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 목록이 발표 시각 내림차순이므로 첫 건이 가장 최근이다
        assertThat(details.get(0).news()).hasSize(1);
        assertThat(details.get(0).news().getFirst().title()).isEqualTo("가장 최근");
    }

    @Test
    void 뉴스가_없으면_빈_목록을_싣는다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 카드는 그대로 2장이다
        assertThat(details).hasSize(2);
        assertThat(details).allSatisfy(detail -> assertThat(detail.news()).isEmpty());
    }

    @Test
    void 뉴스_조회가_실패해도_카드는_응답한다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any()))
                .willThrow(new IllegalStateException("네이버 장애"));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then
        assertThat(details).hasSize(2);
        assertThat(details).allSatisfy(detail -> assertThat(detail.news()).isEmpty());
        assertThat(details.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
    }

    @Test
    void 저장된_일봉이_없으면_시간_창을_정할_수_없어_뉴스를_싣지_않는다() {
        // given 아무도 조회하지 않은 종목이라 일봉이 0행이다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        given(dailyPriceReader.readStoredLatest(any(), anyInt())).willReturn(DailyPrices.of(List.of()));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 창을 모르면 조회하지 않는다. 카드는 그대로 나온다
        then(stockNewsService).should(never()).read(anyLong(), any(), any());
        assertThat(details).hasSize(2);
        assertThat(details).allSatisfy(detail -> assertThat(detail.news()).isEmpty());
    }

    @Test
    void 일봉이_있는_첫_종목에서_직전_거래일을_읽는다() {
        // given 첫 종목은 일봉이 없고 두 번째에 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        given(dailyPriceReader.readStoredLatest(eq(10L), anyInt())).willReturn(DailyPrices.of(List.of()));
        stubPreviousTradeDay(11L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        industryNewsService.read(Country.KR);

        // then
        then(stockNewsService).should(atLeastOnce()).read(anyLong(), from.capture(), any());
        assertThat(from.getValue()).isEqualTo(
                OffsetDateTime.of(2026, 9, 3, 15, 30, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void 선정된_산업의_대표_종목으로만_뉴스를_찾는다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        industryNewsService.read(Country.KR);

        // then 뽑히지 않은 산업(건설)은 건드리지 않는다
        then(stockNewsService).should(atLeastOnce()).read(stockId.capture(), any(), any());
        assertThat(stockId.getAllValues()).containsOnly(10L);
        then(industryReader).should(never()).read(IndustryCode.CONSTRUCTION);
    }

    @Test
    void 대표_종목이_아닌_소속_종목으로는_찾지_않는다() {
        // given 산업에 종목이 셋인데 대표 종목은 10L 하나다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L, 12L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(eq(10L), any(), any()))
                .willReturn(found(article("a", "현대차 수출 증가", 9, 0)));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 외부 검색은 종목당 한 번이라 산업 전체로 넓히지 않는다
        assertThat(details.getFirst().news()).extracting(NewsArticle::title)
                .containsExactly("현대차 수출 증가");
        then(stockNewsService).should(never()).read(eq(11L), any(), any());
        then(stockNewsService).should(never()).read(eq(12L), any(), any());
    }

    @Test
    void 두_대표_종목의_기사를_합쳐_한_건을_싣는다() {
        // given 두 대표 종목이 같은 기사를 문다.
        // 중복 제거 자체는 NEWS_LIMIT 이 1인 동안 이 테스트로 증명되지 않는다 — 명세의 「알려진 한계」 참고
        given(industryFlowService.read(Country.KR)).willReturn(twoMajorStockFlows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any()))
                .willReturn(found(article("same", "자동차 업황 개선", 9, 0)));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then documentId 가 같으면 한 건이다
        assertThat(details.getFirst().news()).hasSize(1);
    }

    @Test
    void 대표_종목에_뉴스가_없으면_빈_목록이다() {
        // given 넓히는 단계가 없으므로 대표 종목에 없으면 그대로 비어 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L, 12L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 카드는 그대로 2장이다
        assertThat(details).hasSize(2);
        assertThat(details).allSatisfy(detail -> assertThat(detail.news()).isEmpty());
    }

    @Test
    void 계산된_적_없는_산업은_뉴스를_찾지_않는다() {
        // given calculatedAt 이 null 이면 시간 창의 끝을 정할 수 없다
        given(industryFlowService.read(Country.KR)).willReturn(List.of(
                new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal("1.61"), 4, 3, 1,
                        tradedAsUsual(), List.of(new IndustryFlowStock(20L, "005380", "현대차",
                                PRICE, new BigDecimal("3.24"))), null),
                flow(IndustryCode.CHEMICAL, 9, "-0.35"),
                flow(IndustryCode.CONSTRUCTION, 5, "0.10")));
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 그 산업만 비어 있고 외부도 부르지 않는다. 카드는 그대로 나온다
        assertThat(details.getFirst().news()).isEmpty();
        then(stockNewsService).should(never()).read(eq(20L), any(), any());
    }

    @Test
    void 대표_종목이_없는_산업은_뉴스를_찾지_않는다() {
        // given 시세를 한 종목도 구하지 못해 대표 종목이 비어 있다
        given(industryFlowService.read(Country.KR)).willReturn(List.of(
                flowWithoutIndustryFlowStocks(IndustryCode.AUTOMOBILE, 1, "1.61"),
                flow(IndustryCode.CHEMICAL, 9, "-0.35")));
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(stockNewsService.read(anyLong(), any(), any())).willReturn(found());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 찾을 종목이 없으므로 빈 목록이다. 산업 전체로 대체하지 않는다
        assertThat(details.getFirst().news()).isEmpty();
    }

    @Test
    void 해외_카드는_국내_일봉으로_시간_창을_정하지_않는다() {
        // given 해외 산업인데 국내 종목이 같은 산업에 매핑돼 있고, 둘의 최근 거래일이 다르다
        given(industryFlowService.read(Country.US)).willReturn(usFlows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.KR, List.of(10L));
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.US, List.of(50L, 51L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, Country.US, List.of(95L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);              // 국내 09-03
        stubPreviousTradeDay(50L, LocalDate.of(2026, 9, 2));        // 해외 09-02
        stubPreviousTradeDay(95L, LocalDate.of(2026, 9, 2));

        // when
        industryNewsService.read(Country.US);

        // then 해외 종목의 거래일(09-02)에 미국 마감을 붙인다. 국내 거래일(09-03)을 쓰면 안 된다
        then(stockNewsService).should(atLeastOnce()).read(anyLong(), from.capture(), any());
        assertThat(from.getAllValues()).containsOnly(
                OffsetDateTime.of(2026, 9, 2, 16, 0, 0, 0, ZoneOffset.ofHours(-4)));
    }

    /** 해외 카드. 대표 종목은 refresher 가 이미 국가로 걸러 저장하므로 해외 종목이다. */
    private static List<RankedIndustryFlow> usFlows() {
        return List.of(
                new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal("1.61"), 4, 3, 1,
                        tradedAsUsual(),
                        List.of(new IndustryFlowStock(50L, "F", "Ford", PRICE, new BigDecimal("3.24"))),
                        CALCULATED_AT),
                new RankedIndustryFlow(IndustryCode.CHEMICAL, 9, new BigDecimal("-0.35"), 4, 1, 3,
                        tradedAsUsual(),
                        List.of(new IndustryFlowStock(95L, "DOW", "Dow", PRICE, new BigDecimal("-1.10"))),
                        CALCULATED_AT));
    }

    private static RankedIndustryFlow flowWithoutIndustryFlowStocks(IndustryCode code, int rank,
            String avgChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 0, 0, 0,
                tradedAsUsual(), List.of(), CALCULATED_AT);
    }

    /**
     * 자동차 상승 1위 · 건설 중간 · 화학 하락 최하위.
     * 세 산업의 거래대금 비율이 같아 모두 시장과 같은 속도이고, 관문을 통과한다 —
     * 이 테스트의 관심사는 선정 규칙이 아니라 뉴스 연결이다.
     */
    private static List<RankedIndustryFlow> flows() {
        return List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.45"),
                flow(IndustryCode.CHEMICAL, 9, "-0.35"));
    }

    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                tradedAsUsual(),
                List.of(new IndustryFlowStock(10L, "005380", "현대차", PRICE, new BigDecimal("3.24"))),
                CALCULATED_AT);
    }

    /** 시장과 같은 속도. 모든 산업이 같은 비율이면 상대비율이 1.000 이라 관문을 통과한다. */
    private static IndustryTradingValue tradedAsUsual() {
        return new IndustryTradingValue(new BigDecimal("100"), new BigDecimal("100"));
    }

    private static StockNewsResult found(NewsArticle... articles) {
        return new StockNewsResult(10L, Country.KR, null, CALCULATED_AT,
                new StockNewsFeed(List.of(articles), true));
    }

    private static NewsArticle article(String id, String title, int hour, int minute) {
        return new NewsArticle(id, title, "요약", "https://example.com/news/" + id,
                NewsArticle.LinkKind.ORIGINAL, "example.com", "연합뉴스",
                OffsetDateTime.of(2026, 9, 4, hour, minute, 0, 0, ZoneOffset.ofHours(9)));
    }

    /** 대표 종목이 둘인 카드. 두 종목이 같은 기사를 무는 경우를 만든다. */
    private static List<RankedIndustryFlow> twoMajorStockFlows() {
        return List.of(
                new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal("1.61"), 4, 3, 1,
                        tradedAsUsual(), List.of(
                                new IndustryFlowStock(10L, "005380", "현대차", PRICE,
                                        new BigDecimal("3.24")),
                                new IndustryFlowStock(11L, "000270", "기아", PRICE,
                                        new BigDecimal("1.85"))),
                        CALCULATED_AT),
                flow(IndustryCode.CHEMICAL, 9, "-0.35"),
                flow(IndustryCode.CONSTRUCTION, 5, "0.10"));
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(tradeAt, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 1L);
    }
}
