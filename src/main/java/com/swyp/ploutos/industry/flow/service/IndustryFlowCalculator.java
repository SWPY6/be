package com.swyp.ploutos.industry.flow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.QuotedStock;

/**
 * 한 산업·국가의 평균 등락률과 대표 종목을 구한다. 시세를 구하지 못한 종목은 애초에 목록에
 * 들어오지 않으므로, 받은 목록이 곧 "평균에 반영되는 종목"이다.
 */
@Component
public class IndustryFlowCalculator {

    /**
     * 저장할 종목 수. 산업별 동향 카드가 4개를 보여준다(RQ-0601).
     *
     * <p>저장 자리 수가 아니라 <b>몇 개를 담을까</b>이다 — 자식 테이블이 개수를 제한하지 않으므로
     * 이 값만 바꾸면 늘어난다. 시장 요약 카드는 이 중 앞 2개만 쓴다
     * ({@code RankedIndustryFlow.majorStocks()}).
     */
    private static final int STORED_STOCK_COUNT = 4;

    private static final int DIVISION_SCALE = 6;

    public IndustryFlowSnapshot calculate(List<QuotedStock> quotedStocks) {
        List<QuotedStock> distinct = distinctByTicker(quotedStocks);
        if (distinct.isEmpty()) {
            return IndustryFlowSnapshot.empty();
        }
        return new IndustryFlowSnapshot(average(distinct), distinct.size(),
                count(distinct, QuotedStock::rose), count(distinct, QuotedStock::fell),
                tradingValue(distinct), stocks(distinct));
    }

    /**
     * 금액을 합치기만 하고 나누지 않는다. 비교는 조회 시점에 시장 전체와 함께 한다 —
     * 여기서는 다른 산업의 숫자를 알 수 없다. 종목별 비율을 평균하지 않는 이유는 소형주 하나가
     * 산업을 흔들기 때문이다.
     *
     * <p>견줄 수 있는 종목이 하나도 없으면 {@code null}이다 — 0 으로 채우면 실패가 숨는다.
     */
    private static IndustryTradingValue tradingValue(List<QuotedStock> stocks) {
        return IndustryTradingValue.of(stocks.stream().map(QuotedStock::toTradingValue).toList())
                .orElse(null);
    }

    /** 보합인 종목은 어느 쪽에도 세지 않는다. 그래서 두 수의 합이 종목 수보다 작을 수 있다. */
    private static int count(List<QuotedStock> stocks, Predicate<QuotedStock> moved) {
        return (int) stocks.stream().filter(moved).count();
    }

    /**
     * 같은 종목이 여러 시장에 상장돼 있으면(예: NASDAQ 과 S&P500) stocks 에 행이 둘 생겨
     * 평균에 두 번 들어간다. 같은 회사이므로 ticker 로 한 번만 반영한다.
     */
    private static List<QuotedStock> distinctByTicker(List<QuotedStock> quotedStocks) {
        Map<String, QuotedStock> byTicker = new LinkedHashMap<>();
        quotedStocks.forEach(quoted -> byTicker.putIfAbsent(quoted.ticker(), quoted));
        return List.copyOf(byTicker.values());
    }

    /**
     * 반올림하지 않는다. 표기용으로 자르는 것은 응답을 만들 때
     * {@link RankedIndustryFlow#displayAvgChangeRate()}가 한다.
     *
     * <p>여기서 두 자리로 자르면 {@code +1.6149}와 {@code +1.6151}이 같은 값이 되어
     * 순위 동률을 풀 수 없다(RQ-0603).
     */
    private static BigDecimal average(List<QuotedStock> stocks) {
        BigDecimal sum = stocks.stream()
                .map(QuotedStock::changeRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(stocks.size()), DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 시가총액 상위 4개. 등락률 최고·최저가 아닌 이유는 대상이 전 종목이어서, 87개 중 최고·최저는
     * 상한가·급락한 소형주가 되어 산업 평균과 동떨어진 극단값이 뜨기 때문이다.
     * 시가총액이 같으면 ticker 순으로 정해 갱신마다 순서가 흔들리지 않게 한다.
     */
    private static List<IndustryFlowStock> stocks(List<QuotedStock> stocks) {
        return stocks.stream()
                .sorted(Comparator.comparing(QuotedStock::marketCap, Comparator.reverseOrder())
                        .thenComparing(QuotedStock::ticker))
                .limit(STORED_STOCK_COUNT)
                .map(QuotedStock::toFlowStock)
                .toList();
    }
}
