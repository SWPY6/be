package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.service.QuoteReader;
import com.swyp.ploutos.stock.service.StockReader;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndustryFlowRefresherTest {

    private static final Industries AUTOMOBILE = new Industries(1L, IndustryCode.AUTOMOBILE);
    private static final Industries CONSTRUCTION = new Industries(2L, IndustryCode.CONSTRUCTION);
    private static final BigDecimal PREVIOUS_CLOSE = new BigDecimal("100");

    @Mock
    private IndustryReader industryReader;

    @Mock
    private StockReader stockReader;

    @Mock
    private QuoteReader quoteReader;

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private IndustryFlowWriter industryFlowWriter;

    @Captor
    private ArgumentCaptor<Country> savedCountry;

    @Captor
    private ArgumentCaptor<IndustryFlowSnapshot> savedSnapshot;

    @Captor
    private ArgumentCaptor<LocalDateTime> savedCalculatedAt;

    private IndustryFlowRefresher refresher;

    @BeforeEach
    void setUp() {
        // 초당 1000건 = 호출 사이 1ms. 테스트가 기다리지 않게 한다.
        refresher = new IndustryFlowRefresher(industryReader, stockReader, quoteReader, dailyPriceReader,
                new IndustryFlowCalculator(), industryFlowWriter, new IndustryFlowProperties(1000),
                Clock.fixed(Instant.parse("2026-09-28T01:00:07Z"), ZoneOffset.UTC));
        // 기본은 저장된 일봉 없음. 거래대금을 보는 테스트만 따로 stub 한다.
        given(dailyPriceReader.readStoredLatest(any(), anyInt())).willReturn(DailyPrices.of(List.of()));
    }

    @Test
    void 한_번_실행하면_산업_하나만_처리한다() {
        // given 산업이 둘이다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE, CONSTRUCTION));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);

        // when
        refresher.refreshNext();

        // then 자동차만 조회했다
        then(industryReader).should().readStockIds(1L);
        then(industryReader).should(never()).readStockIds(2L);
    }

    @Test
    void 마지막_산업_다음에는_처음_산업으로_돌아간다() {
        // given
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE, CONSTRUCTION));
        given(industryReader.readStockIds(any())).willReturn(List.of());

        // when 세 번 실행한다
        refresher.refreshNext();
        refresher.refreshNext();
        refresher.refreshNext();

        // then 자동차 → 건설 → 자동차
        then(industryReader).should(org.mockito.Mockito.times(2)).readStockIds(1L);
        then(industryReader).should(org.mockito.Mockito.times(1)).readStockIds(2L);
    }

    @Test
    void 한_종목이_실패해도_나머지를_계속_조회하고_평균에서_제외한다() {
        // given 세 종목 중 가운데가 실패한다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L, 20L, 30L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        given(stockReader.read(20L)).willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));
        stub(30L, "000270", "기아", Country.KR, "1.00", 349);

        // when
        refresher.refreshNext();

        // then 30번도 조회했고, 국내 평균은 성공한 둘의 것이다
        then(stockReader).should().read(30L);
        then(industryFlowWriter).should(org.mockito.Mockito.times(2))
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        IndustryFlowSnapshot domestic = savedOf(Country.KR);
        assertThat(domestic.stockCount()).isEqualTo(2);
        assertThat(domestic.avgChangeRate()).isEqualByComparingTo("2.00");
    }

    @Test
    void 매핑된_종목의_시세를_하나도_구하지_못하면_저장하지_않는다() {
        // given 국내 종목 둘이 매핑돼 있는데 시세 조회가 전부 실패한다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L, 20L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        stub(20L, "000270", "기아", Country.KR, "1.00", 349);
        given(quoteReader.readWithoutTracking(any()))
                .willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when
        refresher.refreshNext();

        // then 국내는 직전 값을 남기고, 매핑이 없는 해외만 0으로 저장한다
        then(industryFlowWriter).should(org.mockito.Mockito.times(1))
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedCountry.getValue()).isEqualTo(Country.US);
        assertThat(savedSnapshot.getValue().stockCount()).isZero();
    }

    @Test
    void 매핑된_종목이_없는_국가는_0으로_저장한다() {
        // given 산업에 종목이 하나도 매핑돼 있지 않다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of());

        // when
        refresher.refreshNext();

        // then 평균 0 · 종목 0 이 사실이므로 저장한다. calculatedAt 이 찍혀야 계산이 돌고 있음이 드러난다
        then(industryFlowWriter).should(org.mockito.Mockito.times(2))
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedSnapshot.getAllValues()).allSatisfy(flow -> {
            assertThat(flow.stockCount()).isZero();
            assertThat(flow.avgChangeRate()).isEqualByComparingTo("0.00");
            assertThat(flow.stocks()).isEmpty();
            assertThat(flow).isNotNull();
        });
    }

    @Test
    void 국내와_해외를_각각_저장한다() {
        // given 자동차에 국내 종목 하나와 해외 종목 하나가 있다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L, 20L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        stub(20L, "TSLA", "Tesla", Country.US, "-1.00", 1000);

        // when
        refresher.refreshNext();

        // then 두 행이 저장되고 국가별로 평균이 나뉜다
        then(industryFlowWriter).should(org.mockito.Mockito.times(2))
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedOf(Country.KR).avgChangeRate()).isEqualByComparingTo("3.00");
        assertThat(savedOf(Country.KR).stockCount()).isEqualTo(1);
        assertThat(savedOf(Country.US).avgChangeRate()).isEqualByComparingTo("-1.00");
        assertThat(savedOf(Country.US).stockCount()).isEqualTo(1);
    }

    @Test
    void 계산_시각을_시장_현지_시각으로_남긴다() {
        // given 고정 시각은 UTC 2026-09-28T01:00:07 = 서울 10:00:07 = 뉴욕 전날 21:00:07
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);

        // when
        refresher.refreshNext();

        // then 같은 순간이지만 행마다 그 시장의 현지 시각으로 남는다
        then(industryFlowWriter).should(org.mockito.Mockito.times(2))
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(calculatedAtOf(Country.KR)).isEqualTo("2026-09-28T10:00:07");
        assertThat(calculatedAtOf(Country.US)).isEqualTo("2026-09-27T21:00:07");
    }

    @Test
    void 산업이_없으면_아무것도_하지_않는다() {
        // given 시드가 들어가지 않은 상태
        given(industryReader.readAll()).willReturn(List.of());

        // when
        refresher.refreshNext();

        // then
        then(industryReader).should(never()).readStockIds(any());
        then(industryFlowWriter).should(never()).save(any(), any(), any(), any());
    }

    /** 저장된 것 중 그 국가의 스냅샷. 국내·해외가 각각 한 번씩 저장되므로 첫 건이 곧 그 국가의 것이다. */
    private LocalDateTime calculatedAtOf(Country country) {
        List<Country> countries = savedCountry.getAllValues();
        List<LocalDateTime> times = savedCalculatedAt.getAllValues();
        return java.util.stream.IntStream.range(0, countries.size())
                .filter(index -> countries.get(index) == country)
                .mapToObj(times::get)
                .findFirst()
                .orElseThrow(() -> new AssertionError(country + " 가 저장되지 않았다"));
    }

    private IndustryFlowSnapshot savedOf(Country country) {
        List<Country> countries = savedCountry.getAllValues();
        List<IndustryFlowSnapshot> snapshots = savedSnapshot.getAllValues();
        return java.util.stream.IntStream.range(0, countries.size())
                .filter(index -> countries.get(index) == country)
                .mapToObj(snapshots::get)
                .findFirst()
                .orElseThrow(() -> new AssertionError(country + " 가 저장되지 않았다"));
    }

    @Test
    void 저장된_일봉으로_거래대금_변화율을_계산해_저장한다() {
        // given 오늘 150, 20거래일 평균 100
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        given(quoteReader.readWithoutTracking(eq(10L))).willReturn(quote("3.00", 866, 150L));
        given(dailyPriceReader.readStoredLatest(eq(10L), anyInt())).willReturn(storedPrices(20, 100L));

        // when
        refresher.refreshNext();

        // then
        then(industryFlowWriter).should(org.mockito.Mockito.atLeastOnce())
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedOf(Country.KR).tradingValue().ratio())
                .isEqualByComparingTo("1.5");
    }

    @Test
    void 저장된_일봉이_스무개보다_적으면_거래대금이_없다() {
        // given 신규 상장이라 일봉이 10개뿐이다
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        given(dailyPriceReader.readStoredLatest(eq(10L), anyInt())).willReturn(storedPrices(10, 100L));

        // when
        refresher.refreshNext();

        // then 등락률은 그대로 저장한다. 둘은 독립된 값이다
        then(industryFlowWriter).should(org.mockito.Mockito.atLeastOnce())
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedOf(Country.KR).tradingValue()).isNull();
        assertThat(savedOf(Country.KR).avgChangeRate()).isEqualByComparingTo("3.00");
    }

    @Test
    void 일봉_조회가_실패해도_평균_등락률은_저장한다() {
        // given
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        given(dailyPriceReader.readStoredLatest(eq(10L), anyInt()))
                .willThrow(new IllegalStateException("DB 장애"));

        // when
        refresher.refreshNext();

        // then 거래대금만 잃고 종목은 평균에 남는다
        then(industryFlowWriter).should(org.mockito.Mockito.atLeastOnce())
                .save(any(), savedCountry.capture(), savedSnapshot.capture(), savedCalculatedAt.capture());
        assertThat(savedOf(Country.KR).tradingValue()).isNull();
        assertThat(savedOf(Country.KR).stockCount()).isEqualTo(1);
        assertThat(savedOf(Country.KR).avgChangeRate()).isEqualByComparingTo("3.00");
    }

    @Test
    void 일봉을_읽어도_시세_호출_횟수는_늘지_않는다() {
        // given 종목 2개
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L, 20L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);
        stub(20L, "000270", "기아", Country.KR, "1.00", 349);

        // when
        refresher.refreshNext();

        // then 종목당 정확히 한 번. 일봉은 DB 라 KIS 예산을 쓰지 않는다
        then(quoteReader).should().readWithoutTracking(10L);
        then(quoteReader).should().readWithoutTracking(20L);
        then(dailyPriceReader).should().readStoredLatest(10L, 20);
        then(dailyPriceReader).should().readStoredLatest(20L, 20);
    }

    private void stub(Long stockId, String ticker, String name, Country country, String changeRate,
            long marketCap) {
        Markets market = country == Country.KR
                ? new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW)
                : new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD);
        Stocks stock = new Stocks(1L, ticker, name, null, StockStatus.ACTIVE,
                country == Country.KR ? Exchange.KRX : Exchange.NASDAQ,
                1L, "대표", LocalDate.of(2000, 1, 1));
        given(stockReader.read(eq(stockId))).willReturn(new StockWithMarket(stock, market));
        given(quoteReader.readWithoutTracking(eq(stockId))).willReturn(quote(changeRate, marketCap));
    }

    private static Quote quote(String changeRate, long marketCap) {
        return quote(changeRate, marketCap, 1L);
    }

    /** 저장된 일봉 {@code days}개. 하루 거래대금이 {@code dailyValue}가 되도록 종가×거래량을 맞춘다. */
    private static DailyPrices storedPrices(int days, long dailyValue) {
        return DailyPrices.of(IntStream.rangeClosed(1, days)
                .mapToObj(day -> new DailyPrice(LocalDate.of(2026, 1, 1).plusDays(day),
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.valueOf(dailyValue), 1L))
                .toList());
    }

    private static Quote quote(String changeRate, long marketCap, long tradingValue) {
        return new Quote(
                PREVIOUS_CLOSE.add(new BigDecimal(changeRate)),
                PREVIOUS_CLOSE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L,
                BigDecimal.valueOf(tradingValue),
                BigDecimal.valueOf(marketCap),
                Currency.KRW,
                OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                PriceTiming.REALTIME);
    }

}
