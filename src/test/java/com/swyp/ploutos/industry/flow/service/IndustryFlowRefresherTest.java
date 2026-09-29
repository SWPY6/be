package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
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
    private IndustryFlowRepository industryFlowRepository;

    @Captor
    private ArgumentCaptor<IndustryFlows> saved;

    private IndustryFlowRefresher refresher;

    @BeforeEach
    void setUp() {
        // 초당 1000건 = 호출 사이 1ms. 테스트가 기다리지 않게 한다.
        refresher = new IndustryFlowRefresher(industryReader, stockReader, quoteReader,
                new IndustryFlowCalculator(), industryFlowRepository, new IndustryFlowProperties(1000),
                Clock.fixed(Instant.parse("2026-09-28T01:00:07Z"), ZoneOffset.UTC));
        given(industryFlowRepository.findByIndustryIdAndCountry(any(), any())).willReturn(Optional.empty());
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

        // then 30번도 조회했고, 평균은 성공한 둘의 것이다
        then(stockReader).should().read(30L);
        then(industryFlowRepository).should().save(saved.capture());
        assertThat(saved.getValue().stockCount()).isEqualTo(2);
        assertThat(saved.getValue().avgChangeRate()).isEqualByComparingTo("2.00");
    }

    @Test
    void 산업의_모든_종목이_실패하면_저장하지_않는다() {
        // given
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L, 20L));
        given(stockReader.read(any())).willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when
        refresher.refreshNext();

        // then 직전 값을 남긴다
        then(industryFlowRepository).should(never()).save(any());
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
        then(industryFlowRepository).should(org.mockito.Mockito.times(2)).save(saved.capture());
        Map<Country, IndustryFlows> byCountry = saved.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(IndustryFlows::country, flow -> flow));
        assertThat(byCountry.get(Country.KR).avgChangeRate()).isEqualByComparingTo("3.00");
        assertThat(byCountry.get(Country.KR).stockCount()).isEqualTo(1);
        assertThat(byCountry.get(Country.US).avgChangeRate()).isEqualByComparingTo("-1.00");
        assertThat(byCountry.get(Country.US).stockCount()).isEqualTo(1);
    }

    @Test
    void 계산_시각을_시장_현지_시각으로_남긴다() {
        // given 고정 시각은 UTC 2026-09-28T01:00:07 = 서울 10:00:07
        given(industryReader.readAll()).willReturn(List.of(AUTOMOBILE));
        given(industryReader.readStockIds(1L)).willReturn(List.of(10L));
        stub(10L, "005380", "현대차", Country.KR, "3.00", 866);

        // when
        refresher.refreshNext();

        // then
        then(industryFlowRepository).should().save(saved.capture());
        assertThat(saved.getValue().calculatedAt()).isEqualTo("2026-09-28T10:00:07");
    }

    @Test
    void 산업이_없으면_아무것도_하지_않는다() {
        // given 시드가 들어가지 않은 상태
        given(industryReader.readAll()).willReturn(List.of());

        // when
        refresher.refreshNext();

        // then
        then(industryReader).should(never()).readStockIds(any());
        then(industryFlowRepository).should(never()).save(any());
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
        return new Quote(
                PREVIOUS_CLOSE.add(new BigDecimal(changeRate)),
                PREVIOUS_CLOSE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L, BigDecimal.ONE,
                BigDecimal.valueOf(marketCap),
                Currency.KRW,
                OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                PriceTiming.REALTIME);
    }

}
