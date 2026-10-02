package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.flow.MajorStock;
import com.swyp.ploutos.industry.flow.QuotedStock;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

class IndustryFlowCalculatorTest {

    private static final BigDecimal PREVIOUS_CLOSE = new BigDecimal("100");

    private final IndustryFlowCalculator calculator = new IndustryFlowCalculator();

    @Test
    void 소속_종목의_등락률을_단순평균한다() {
        // given 등락률 3.00 · 1.00 · -1.00 → 평균 1.00
        List<QuotedStock> stocks = List.of(
                quoted("005380", "현대차", "3.00", 300),
                quoted("000270", "기아", "1.00", 200),
                quoted("012330", "현대모비스", "-1.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.avgChangeRate()).isEqualByComparingTo("1.00");
    }

    @Test
    void 평균은_소수_둘째_자리로_반올림한다() {
        // given 등락률 1.00 · 1.00 · 2.00 → 4.00 / 3 = 1.333...
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "1.00", 300),
                quoted("B", "나", "1.00", 200),
                quoted("C", "다", "2.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.avgChangeRate()).isEqualByComparingTo("1.33");
        assertThat(snapshot.avgChangeRate().scale()).isEqualTo(2);
    }

    @Test
    void 소속_종목_수는_평균에_반영된_개수다() {
        // given
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "1.00", 300),
                quoted("B", "나", "2.00", 200));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.stockCount()).isEqualTo(2);
    }

    @Test
    void 오른_종목과_내린_종목을_각각_센다() {
        // given 상승 2 · 하락 1
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "3.00", 300),
                quoted("B", "나", "1.00", 200),
                quoted("C", "다", "-1.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.risingCount()).isEqualTo(2);
        assertThat(snapshot.fallingCount()).isEqualTo(1);
    }

    @Test
    void 보합인_종목은_상승에도_하락에도_세지_않는다() {
        // given 상승 2 · 보합 1 · 하락 1
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "3.00", 400),
                quoted("B", "나", "1.00", 300),
                quoted("C", "다", "0.00", 200),
                quoted("D", "라", "-1.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 합이 종목 수보다 작다. stockCount − risingCount 로 하락 수를 역산할 수 없다
        assertThat(snapshot.stockCount()).isEqualTo(4);
        assertThat(snapshot.risingCount()).isEqualTo(2);
        assertThat(snapshot.fallingCount()).isEqualTo(1);
    }

    @Test
    void 거래대금_변화율을_금액_합계로_계산한다() {
        // given 대형주 600/400(1.5배) · 소형주 3/1(3배)
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "1.00", 300, 60_000_000_000L, 40_000_000_000L),
                quoted("B", "나", "1.00", 100, 300_000_000L, 100_000_000L));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 종목별 비율을 단순 평균하면 +125.00 이 된다
        assertThat(snapshot.tradingValueChangeRate()).isEqualByComparingTo("50.37");
    }

    @Test
    void 이십일_평균이_없는_종목은_거래대금에서_빠진다() {
        // given 두 번째 종목은 일봉이 모자라 20일 평균이 없다
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "1.00", 300, 1_000L, 500L),
                quoted("B", "나", "1.00", 100, 9_000L, 0L));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 9000을 분자에만 넣으면 +900% 가 나온다. 등락률에는 두 종목 모두 반영된다
        assertThat(snapshot.tradingValueChangeRate()).isEqualByComparingTo("100.00");
        assertThat(snapshot.stockCount()).isEqualTo(2);
    }

    @Test
    void 견줄_수_있는_종목이_없으면_거래대금_변화율이_없다() {
        // given 전부 20일 평균이 없다
        List<QuotedStock> stocks = List.of(
                quoted("A", "가", "1.00", 300, 1_000L, 0L),
                quoted("B", "나", "2.00", 100, 2_000L, 0L));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 0.00 으로 내리면 "계산 실패"가 "변화 없음"으로 위장한다
        assertThat(snapshot.tradingValueChangeRate()).isNull();
        assertThat(snapshot.avgChangeRate()).isEqualByComparingTo("1.50");
    }

    @Test
    void 중복_제거된_뒤의_종목만_센다() {
        // given TSLA 가 두 시장에 있어 목록에 두 번 들어왔다
        List<QuotedStock> stocks = List.of(
                quoted("TSLA", "Tesla", "10.00", 300),
                quoted("TSLA", "Tesla", "10.00", 300),
                quoted("F", "Ford", "-1.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 두 번 세면 상승이 2가 된다
        assertThat(snapshot.risingCount()).isEqualTo(1);
        assertThat(snapshot.fallingCount()).isEqualTo(1);
    }

    @Test
    void 같은_종목이_여러_시장에_있으면_한_번만_반영한다() {
        // given TSLA 가 NASDAQ·S&P500 에 각각 있어 목록에 두 번 들어왔다
        List<QuotedStock> stocks = List.of(
                quoted("TSLA", "Tesla", "10.00", 300),
                quoted("TSLA", "Tesla", "10.00", 300),
                quoted("F", "Ford", "0.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 두 번 세면 평균이 6.67 이 된다
        assertThat(snapshot.stockCount()).isEqualTo(2);
        assertThat(snapshot.avgChangeRate()).isEqualByComparingTo("5.00");
    }

    @Test
    void 반영된_종목이_없으면_평균은_0이고_대표_종목이_비어_있다() {
        // given
        List<QuotedStock> stocks = List.of();

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.avgChangeRate()).isEqualByComparingTo("0.00");
        assertThat(snapshot.stockCount()).isZero();
        assertThat(snapshot.risingCount()).isZero();
        assertThat(snapshot.fallingCount()).isZero();
        assertThat(snapshot.majorStocks()).isEmpty();
        assertThat(snapshot.hasNoStock()).isTrue();
    }

    @Test
    void 시가총액_상위_2개를_대표_종목으로_고른다() {
        // given 시가총액이 낮은 순으로 넣어 정렬이 실제로 일어나는지 본다
        List<QuotedStock> stocks = List.of(
                quoted("012330", "현대모비스", "-0.78", 100),
                quoted("005380", "현대차", "3.24", 866),
                quoted("018880", "한온시스템", "2.13", 15),
                quoted("000270", "기아", "1.85", 349));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then 시가총액 866 · 349 순
        assertThat(snapshot.majorStocks()).extracting(MajorStock::ticker)
                .containsExactly("005380", "000270");
    }

    @Test
    void 시가총액이_같으면_ticker_순으로_고른다() {
        // given 셋 다 시가총액 100
        List<QuotedStock> stocks = List.of(
                quoted("C", "다", "1.00", 100),
                quoted("A", "가", "2.00", 100),
                quoted("B", "나", "3.00", 100));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.majorStocks()).extracting(MajorStock::ticker)
                .containsExactly("A", "B");
    }

    @Test
    void 반영된_종목이_하나면_대표_종목도_하나다() {
        // given
        List<QuotedStock> stocks = List.of(quoted("005380", "현대차", "3.24", 866));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.majorStocks()).hasSize(1);
        assertThat(snapshot.majorStocks().get(0).ticker()).isEqualTo("005380");
    }

    @Test
    void 대표_종목은_계산_시점의_종목명과_등락률을_담는다() {
        // given
        List<QuotedStock> stocks = List.of(
                quoted("005380", "현대차", "3.24", 866),
                quoted("000270", "기아", "1.85", 349));

        // when
        IndustryFlowSnapshot snapshot = calculator.calculate(stocks);

        // then
        assertThat(snapshot.majorStocks()).containsExactly(
                new MajorStock(stockIdOf("005380"), "005380", "현대차", new BigDecimal("3.24")),
                new MajorStock(stockIdOf("000270"), "000270", "기아", new BigDecimal("1.85")));
    }

    /** 전일 종가를 100 으로 고정해, 넘긴 등락률이 그대로 나오게 한다. 거래대금은 보지 않는 테스트용. */
    private static QuotedStock quoted(String ticker, String name, String changeRate, long marketCap) {
        return quoted(ticker, name, changeRate, marketCap, 1L, 1L);
    }

    /** 거래대금을 함께 지정한다. {@code average20d}가 0이면 20일 평균이 없는 종목으로 만든다. */
    private static QuotedStock quoted(String ticker, String name, String changeRate, long marketCap,
            long tradingValue, long average20d) {
        QuotedStock base = build(ticker, name, changeRate, marketCap, tradingValue);
        return new QuotedStock(base.stockId(), base.stock(), base.quote(),
                average20d == 0 ? null : BigDecimal.valueOf(average20d));
    }

    private static QuotedStock build(String ticker, String name, String changeRate, long marketCap,
            long tradingValue) {
        Stocks stock = new Stocks(1L, ticker, name, null, StockStatus.ACTIVE, Exchange.KRX,
                1L, "대표", LocalDate.of(2000, 1, 1));
        Markets market = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
        Quote quote = new Quote(
                PREVIOUS_CLOSE.add(new BigDecimal(changeRate)),
                PREVIOUS_CLOSE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1L,
                BigDecimal.valueOf(tradingValue),
                BigDecimal.valueOf(marketCap),
                Currency.KRW,
                OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                PriceTiming.REALTIME);
        return new QuotedStock(stockIdOf(ticker), new StockWithMarket(stock, market), quote,
                BigDecimal.ONE);
    }

    /**
     * ticker 로 식별자를 만든다. {@code Stocks.stockId}는 DB가 정하므로 직접 만든 엔티티에는
     * 없다 — 그래서 {@code QuotedStock}이 식별자를 따로 받는다.
     */
    private static Long stockIdOf(String ticker) {
        return (long) Math.abs(ticker.hashCode() % 100_000);
    }

}
