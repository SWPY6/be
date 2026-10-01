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
import com.swyp.ploutos.industry.flow.MajorStock;
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

    private IndustryNewsService industryNewsService;

    /** 선정 규칙은 실물을 쓴다. 가짜로 바꾸면 이 테스트가 규칙을 검증하지 못한다. */
    @BeforeEach
    void setUp() {
        industryNewsService = new IndustryNewsService(industryFlowService, new IndustryCardSelector(),
                industryReader, dailyPriceReader, newsReader);
    }

    private void stubIndustry(IndustryCode code, Long industryId, List<Long> stockIds) {
        given(industryReader.read(code)).willReturn(new Industries(industryId, code));
        given(industryReader.readStockIds(industryId)).willReturn(stockIds);
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
        stubIndustry(IndustryCode.AUTOMOBILE, 1L, List.of(10L));
        stubIndustry(IndustryCode.CHEMICAL, 9L, List.of(90L));
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

    /** 자동차 상승 1위 · 건설 중간 · 화학 하락 최하위. 거래대금은 모두 평소 이상이다. */
    private static List<RankedIndustryFlow> flows() {
        return List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", "52.65"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.45", "10.00"),
                flow(IndustryCode.CHEMICAL, 9, "-0.35", "96.03"));
    }

    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate,
            String tradingValueChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                new BigDecimal(tradingValueChangeRate),
                List.of(new MajorStock("005380", "현대차", new BigDecimal("3.24"))),
                CALCULATED_AT);
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
