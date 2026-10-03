package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.swyp.ploutos.news.RelatedNews;
import com.swyp.ploutos.news.service.NewsReader;
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
    private NewsReader newsReader;

    @Captor
    private ArgumentCaptor<LocalDateTime> from;

    @Captor
    private ArgumentCaptor<LocalDateTime> to;

    @Captor
    private ArgumentCaptor<List<Long>> stockIds;

    private IndustryNewsService industryNewsService;

    /** 선정 규칙은 실물을 쓴다. 가짜로 바꾸면 이 테스트가 규칙을 검증하지 못한다. */
    @BeforeEach
    void setUp() {
        industryNewsService = new IndustryNewsService(industryFlowService, new IndustryCardSelector(),
                industryReader, dailyPriceReader, newsReader);
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
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

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
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

        // when
        industryNewsService.read(Country.KR);

        // then
        then(newsReader).should(atLeastOnce())
                .readByStockIds(anyList(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 3, 15, 30));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 4, 15, 30));
    }

    @Test
    void 미국은_마감_시각이_16시다() {
        // given
        given(industryFlowService.read(Country.US)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.US, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, Country.US, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

        // when
        industryNewsService.read(Country.US);

        // then
        then(newsReader).should(atLeastOnce())
                .readByStockIds(anyList(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 3, 16, 0));
    }

    @Test
    void 뉴스는_카드마다_한_건만_싣는다() {
        // given 세 건이 걸려 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of(
                news(1L, "가장 최근"), news(2L, "그다음"), news(3L, "더 예전")));

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
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

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
        given(newsReader.readByStockIds(anyList(), any(), any()))
                .willThrow(new IllegalStateException("DB 장애"));

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
        then(newsReader).should(never()).readByStockIds(anyList(), any(), any());
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
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

        // when
        industryNewsService.read(Country.KR);

        // then
        then(newsReader).should(atLeastOnce())
                .readByStockIds(anyList(), from.capture(), any());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 3, 15, 30));
    }

    @Test
    void 선정된_산업의_소속_종목으로만_뉴스를_찾는다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

        // when
        industryNewsService.read(Country.KR);

        // then 뽑히지 않은 산업(건설)의 종목은 조회하지 않는다
        then(newsReader).should().readByStockIds(eq(List.of(10L, 11L)), any(), any());
        then(newsReader).should().readByStockIds(eq(List.of(90L)), any(), any());
        then(industryReader).should(never()).read(IndustryCode.CONSTRUCTION);
    }

    @Test
    void 대표_종목의_뉴스를_먼저_찾는다() {
        // given 대표 종목(10L)에 뉴스가 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L, 12L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(eq(List.of(10L)), any(), any()))
                .willReturn(List.of(news(1L, "현대차 수출 증가")));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 산업 전체로 넓히지 않는다
        assertThat(details.getFirst().news()).extracting(RelatedNews::title)
                .containsExactly("현대차 수출 증가");
        then(newsReader).should(never()).readByStockIds(eq(List.of(10L, 11L, 12L)), any(), any());
    }

    @Test
    void 대표_종목에_뉴스가_없으면_산업_전체로_넓힌다() {
        // given 대표 종목에는 없고 소속 종목 전체에는 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L, 12L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(eq(List.of(10L)), any(), any())).willReturn(List.of());
        given(newsReader.readByStockIds(eq(List.of(10L, 11L, 12L)), any(), any()))
                .willReturn(List.of(news(2L, "한온시스템 수주")));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 대표 종목에 없다는 이유로 영역을 비우지 않는다
        assertThat(details.getFirst().news()).extracting(RelatedNews::title)
                .containsExactly("한온시스템 수주");
    }

    @Test
    void 어느_종목에도_뉴스가_없으면_빈_목록이다() {
        // given 두 단계 모두 비어 있다
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(anyList(), any(), any())).willReturn(List.of());

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 카드는 그대로 2장이다
        assertThat(details).hasSize(2);
        assertThat(details).allSatisfy(detail -> assertThat(detail.news()).isEmpty());
    }

    @Test
    void 대표_종목이_없는_산업은_곧바로_전체에서_찾는다() {
        // given 시세를 한 종목도 구하지 못해 대표 종목이 비어 있다
        given(industryFlowService.read(Country.KR)).willReturn(List.of(
                flowWithoutIndustryFlowStocks(IndustryCode.AUTOMOBILE, 1, "1.61"),
                flow(IndustryCode.CHEMICAL, 9, "-0.35")));
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L, 11L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);
        given(newsReader.readByStockIds(eq(List.of(10L, 11L)), any(), any()))
                .willReturn(List.of(news(3L, "자동차 수출 증가 발표")));

        // when
        List<IndustryNewsDetail> details = industryNewsService.read(Country.KR);

        // then 빈 목록으로 조회하면 SQL 이 깨진다. 1단계를 건너뛴다
        assertThat(details.getFirst().news()).extracting(RelatedNews::title)
                .containsExactly("자동차 수출 증가 발표");
    }

    @Test
    void 국내_카드는_해외_종목의_뉴스를_찾지_않는다() {
        // given 한 산업에 국내 2개와 해외 2개가 함께 매핑돼 있다 (시드가 산업마다 국내 4 + 해외 4)
        given(industryFlowService.read(Country.KR)).willReturn(flows());
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.KR, List.of(10L, 11L));
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, Country.US, List.of(50L, 51L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, Country.KR, List.of(90L));
        stubPreviousTradeDay(10L, PREVIOUS_TRADE_DAY);
        stubPreviousTradeDay(90L, PREVIOUS_TRADE_DAY);

        // when 대표 종목(10L)에 뉴스가 없어 산업 전체로 넓힌다
        industryNewsService.read(Country.KR);

        // then 넓힌 조회에도 해외 종목(50L·51L)이 섞이지 않는다
        then(newsReader).should(atLeastOnce()).readByStockIds(stockIds.capture(), any(), any());
        assertThat(stockIds.getAllValues()).allSatisfy(
                ids -> assertThat(ids).doesNotContain(50L, 51L));
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
        then(newsReader).should(atLeastOnce()).readByStockIds(anyList(), from.capture(), any());
        assertThat(from.getAllValues())
                .containsOnly(LocalDateTime.of(2026, 9, 2, 16, 0));
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

    private static RelatedNews news(Long newsId, String title) {
        return new RelatedNews(newsId, title, "연합뉴스",
                LocalDateTime.of(2026, 9, 4, 9, 0), "https://example.com/news/" + newsId);
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(tradeAt, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 1L);
    }
}
