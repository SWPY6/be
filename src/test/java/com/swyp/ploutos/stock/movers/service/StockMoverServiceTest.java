package com.swyp.ploutos.stock.movers.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.service.StockListReader;
import com.swyp.ploutos.stock.snapshot.StockSnapshot;
import com.swyp.ploutos.stock.snapshot.service.StockSnapshotReader;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMoverServiceTest {

    private static final Industries AUTOMOBILE = new Industries(1L, IndustryCode.AUTOMOBILE);

    @Mock
    private MoverRankingProvider rankingProvider;

    @Mock
    private StockSnapshotReader snapshotReader;

    @Mock
    private StockListReader stockListReader;

    @Mock
    private IndustryReader industryReader;

    @InjectMocks
    private StockMoverService service;

    @Test
    void 산업_필터가_없으면_외부_순위를_쓴다() {
        // given
        given(rankingProvider.rank(Country.KR, MoverCondition.RISING))
                .willReturn(List.of(ranked("005380", "현대차", "3.00")));
        given(stockListReader.readAllByTickers(any())).willReturn(List.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.RISING, null, null);

        // then
        assertThat(detail.stocks()).extracting(StockMover::ticker).containsExactly("005380");
        then(snapshotReader).should(never()).readAll();
    }

    @Test
    void 산업_필터가_있으면_외부_순위를_부르지_않는다() {
        // given 자동차에 종목 둘이 있다
        given(industryReader.read(IndustryCode.AUTOMOBILE)).willReturn(AUTOMOBILE);
        given(industryReader.readStockIds(1L, Country.KR)).willReturn(List.of(10L, 20L));
        given(snapshotReader.read(List.of(10L, 20L)))
                .willReturn(List.of(snapshot(10L, "3.00"), snapshot(20L, "1.00")));
        given(stockListReader.readAll(any()))
                .willReturn(List.of(stock(10L, "005380", "현대차"), stock(20L, "000270", "기아")));
        given(industryReader.readCodesByStockIds(any()))
                .willReturn(Map.of(10L, IndustryCode.AUTOMOBILE, 20L, IndustryCode.AUTOMOBILE));

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.RISING,
                IndustryCode.AUTOMOBILE, null);

        // then 순위를 받아 거르면 목록이 비므로, 모집단부터 다시 센다
        then(rankingProvider).should(never()).rank(any(), any());
        assertThat(detail.stocks()).extracting(StockMover::ticker)
                .containsExactly("005380", "000270");
    }

    @Test
    void 우리_종목_마스터에_없는_종목도_목록에_남는다() {
        // given 외부 순위에만 있는 종목
        given(rankingProvider.rank(Country.KR, MoverCondition.RISING))
                .willReturn(List.of(ranked("069500", "KODEX 200", "3.00")));
        given(stockListReader.readAllByTickers(any())).willReturn(List.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.RISING, null, null);

        // then 빼지 않는다 — ETF·우선주·신규 상장은 항상 있다
        assertThat(detail.stocks()).hasSize(1);
        assertThat(detail.stocks().getFirst().stockId()).isNull();
        assertThat(detail.stocks().getFirst().industry()).isNull();
    }

    @Test
    void 같은_종목코드가_둘이면_식별자를_붙이지_않는다() {
        // given 한 종목이 두 시장에 속해 있다
        given(rankingProvider.rank(Country.US, MoverCondition.RISING))
                .willReturn(List.of(ranked("AAPL", "애플", "3.00")));
        given(stockListReader.readAllByTickers(any())).willReturn(List.of(
                usStock(40L, "AAPL", "애플"), usStock(41L, "AAPL", "애플")));

        // when
        StockMoverDetail detail = service.read(Country.US, MoverCondition.RISING, null, null);

        // then 틀린 종목 상세로 보내는 것보다 비워 두는 쪽이 낫다
        assertThat(detail.stocks().getFirst().stockId()).isNull();
    }

    @Test
    void 스냅샷_경로의_급증은_배수_큰_순으로_온다() {
        // given 3배 종목과 1.5배 종목
        given(industryReader.read(IndustryCode.AUTOMOBILE)).willReturn(AUTOMOBILE);
        given(industryReader.readStockIds(1L, Country.KR)).willReturn(List.of(10L, 20L));
        given(snapshotReader.read(any())).willReturn(List.of(
                snapshotWithVolume(10L, 30_000L, 10_000L),
                snapshotWithVolume(20L, 15_000L, 10_000L)));
        given(stockListReader.readAll(any()))
                .willReturn(List.of(stock(10L, "005380", "현대차"), stock(20L, "000270", "기아")));
        given(industryReader.readCodesByStockIds(any())).willReturn(Map.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.VOLUME_SURGE,
                IndustryCode.AUTOMOBILE, null);

        // then 1.5배 종목도 남는다 — 장중에는 배수가 작은 것이 정상이다
        assertThat(detail.stocks()).extracting(StockMover::ticker)
                .containsExactly("005380", "000270");
    }

    @Test
    void 평균_거래량이_없는_종목은_급증에서_빠진다() {
        // given 저장된 일봉이 20거래일에 못 미친다
        given(industryReader.read(IndustryCode.AUTOMOBILE)).willReturn(AUTOMOBILE);
        given(industryReader.readStockIds(1L, Country.KR)).willReturn(List.of(10L));
        given(snapshotReader.read(any())).willReturn(List.of(snapshotWithVolume(10L, 30_000L, null)));
        given(stockListReader.readAll(any())).willReturn(List.of(stock(10L, "005380", "현대차")));
        given(industryReader.readCodesByStockIds(any())).willReturn(Map.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.VOLUME_SURGE,
                IndustryCode.AUTOMOBILE, null);

        // then 배수를 재지 못하면 급증이 아니다
        assertThat(detail.stocks()).isEmpty();
    }

    @Test
    void 전체_종목은_저장된_스냅샷을_시가총액_순으로_준다() {
        // given
        given(snapshotReader.readAll()).willReturn(List.of(
                snapshotWithMarketCap(10L, 100L), snapshotWithMarketCap(20L, 900L)));
        given(stockListReader.readAll(any()))
                .willReturn(List.of(stock(10L, "005380", "현대차"), stock(20L, "000270", "기아")));
        given(industryReader.readCodesByStockIds(any())).willReturn(Map.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.ALL, null, null);

        // then 큰 종목부터 보여 준다
        assertThat(detail.stocks()).extracting(StockMover::ticker).containsExactly("000270", "005380");
        then(rankingProvider).should(never()).rank(any(), any());
    }

    @Test
    void 검색어는_종목명과_종목코드_모두에_걸린다() {
        // given
        given(rankingProvider.rank(any(), any())).willReturn(List.of(
                ranked("005380", "현대차", "3.00"),
                ranked("000270", "기아", "2.00")));
        given(stockListReader.readAllByTickers(any())).willReturn(List.of());

        // when 종목명으로 찾는다
        StockMoverDetail byName = service.read(Country.KR, MoverCondition.RISING, null, "현대");

        // then
        assertThat(byName.stocks()).extracting(StockMover::ticker).containsExactly("005380");

        // when 종목코드로 찾는다
        StockMoverDetail byTicker = service.read(Country.KR, MoverCondition.RISING, null, "000270");

        // then
        assertThat(byTicker.stocks()).extracting(StockMover::name).containsExactly("기아");
    }

    @Test
    void 다른_국가의_종목은_목록에_섞이지_않는다() {
        // given 스냅샷에는 국가 정보가 없어 종목으로 걸러야 한다
        given(snapshotReader.readAll()).willReturn(List.of(
                snapshotWithMarketCap(10L, 100L), snapshotWithMarketCap(40L, 900L)));
        given(stockListReader.readAll(any()))
                .willReturn(List.of(stock(10L, "005380", "현대차"), usStock(40L, "AAPL", "애플")));
        given(industryReader.readCodesByStockIds(any())).willReturn(Map.of());

        // when
        StockMoverDetail detail = service.read(Country.KR, MoverCondition.ALL, null, null);

        // then
        assertThat(detail.stocks()).extracting(StockMover::ticker).containsExactly("005380");
    }

    private static StockMover ranked(String ticker, String name, String changeRate) {
        return new StockMover(null, ticker, name, null, new BigDecimal("1000"),
                new BigDecimal(changeRate), 100L, null, null, null);
    }

    private static StockSnapshot snapshot(Long stockId, String changeRate) {
        return new StockSnapshot(stockId, new BigDecimal("1000"), new BigDecimal(changeRate),
                100L, null, null, null, LocalDateTime.of(2026, 10, 8, 10, 0));
    }

    private static StockSnapshot snapshotWithVolume(Long stockId, long volume, Long average) {
        return new StockSnapshot(stockId, new BigDecimal("1000"), new BigDecimal("1.00"),
                volume, null, null, average, LocalDateTime.of(2026, 10, 8, 10, 0));
    }

    private static StockSnapshot snapshotWithMarketCap(Long stockId, long marketCap) {
        return new StockSnapshot(stockId, new BigDecimal("1000"), new BigDecimal("1.00"),
                100L, null, BigDecimal.valueOf(marketCap), null,
                LocalDateTime.of(2026, 10, 8, 10, 0));
    }

    private static StockWithMarket stock(Long stockId, String ticker, String name) {
        return new StockWithMarket(
                new Stocks(1L, ticker, name, null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표",
                        LocalDate.of(2000, 1, 1)) {
                    @Override
                    public Long stockId() {
                        return stockId;
                    }
                },
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
    }

    private static StockWithMarket usStock(Long stockId, String ticker, String name) {
        return new StockWithMarket(
                new Stocks(2L, ticker, name, null, StockStatus.ACTIVE, Exchange.NASDAQ, 1L, "대표",
                        LocalDate.of(2000, 1, 1)) {
                    @Override
                    public Long stockId() {
                        return stockId;
                    }
                },
                new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD));
    }
}
