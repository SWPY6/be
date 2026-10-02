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
import com.swyp.ploutos.industry.flow.MajorStock;
import com.swyp.ploutos.industry.flow.QuotedStock;

/**
 * 한 산업·국가의 평균 등락률과 대표 종목을 구한다. 시세를 구하지 못한 종목은 애초에 목록에
 * 들어오지 않으므로, 받은 목록이 곧 "평균에 반영되는 종목"이다.
 */
@Component
public class IndustryFlowCalculator {

    private static final int SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    public IndustryFlowSnapshot calculate(List<QuotedStock> quotedStocks) {
        List<QuotedStock> distinct = distinctByTicker(quotedStocks);
        if (distinct.isEmpty()) {
            return IndustryFlowSnapshot.empty();
        }
        return new IndustryFlowSnapshot(average(distinct), distinct.size(),
                count(distinct, QuotedStock::rose), count(distinct, QuotedStock::fell),
                tradingValueChangeRate(distinct), majorStocks(distinct));
    }

    /**
     * 금액을 먼저 합치고 나중에 나눈다. 종목별 비율을 평균하면 소형주 하나가 산업을 흔든다.
     * 견줄 수 있는 종목이 하나도 없으면 {@code null}이다 — 0.00 으로 내리면 실패가 숨는다.
     */
    private static BigDecimal tradingValueChangeRate(List<QuotedStock> stocks) {
        return IndustryTradingValue.of(stocks.stream().map(QuotedStock::toTradingValue).toList())
                .map(IndustryTradingValue::changeRatePercent)
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

    private static BigDecimal average(List<QuotedStock> stocks) {
        BigDecimal sum = stocks.stream()
                .map(QuotedStock::changeRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(stocks.size()), DIVISION_SCALE, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 시가총액 상위 2개. 등락률 최고·최저가 아닌 이유는 대상이 전 종목이어서, 87개 중 최고·최저는
     * 상한가·급락한 소형주가 되어 산업 평균과 동떨어진 극단값이 뜨기 때문이다.
     * 시가총액이 같으면 ticker 순으로 정해 갱신마다 순서가 흔들리지 않게 한다.
     */
    private static List<MajorStock> majorStocks(List<QuotedStock> stocks) {
        return stocks.stream()
                .sorted(Comparator.comparing(QuotedStock::marketCap, Comparator.reverseOrder())
                        .thenComparing(QuotedStock::ticker))
                .limit(IndustryFlowSnapshot.MAJOR_STOCK_LIMIT)
                .map(QuotedStock::toMajorStock)
                .toList();
    }
}
