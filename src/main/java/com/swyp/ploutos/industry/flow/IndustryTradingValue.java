package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

/**
 * 한 산업의 거래대금. 오늘 금액을 그 산업 자신의 20거래일 평균과 견준다.
 *
 * <p>금액을 먼저 합치고 나중에 나눈다. 종목별로 비율을 낸 뒤 평균하지 않는 이유는 소형주 하나가
 * 산업 전체를 흔들기 때문이다 — 거래대금 5배가 된 소형주 하나가 87종목 산업을 +210%로 만든다.
 * 금액을 합치면 큰 종목이 자연히 크게 기여하므로 별도의 가중치가 필요하지 않다.
 *
 * <p>{@code average20d}가 0이 될 수 없다. 그런 입력은 {@link #of(List)}가 걸러 내므로
 * 이 객체가 존재하면 {@link #changeRatePercent()}는 언제나 답을 낸다.
 */
public record IndustryTradingValue(BigDecimal todayAverage, BigDecimal average20d) {

    private static final int SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    public IndustryTradingValue {
        if (average20d.signum() <= 0) {
            throw new IllegalArgumentException("20거래일 평균 거래대금은 0보다 커야 합니다: " + average20d);
        }
    }

    /**
     * 종목별 거래대금을 산업 하나의 값으로 접는다. 견줄 수 없는 종목은 빠지고,
     * 남는 종목이 없으면 비어 있다 — 그 산업은 거래대금을 판단할 수 없다.
     */
    public static Optional<IndustryTradingValue> of(List<StockTradingValue> stocks) {
        List<StockTradingValue> comparable = stocks.stream()
                .filter(StockTradingValue::comparable)
                .toList();
        if (comparable.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal count = BigDecimal.valueOf(comparable.size());
        return Optional.of(new IndustryTradingValue(
                average(comparable.stream().map(StockTradingValue::today), count),
                average(comparable.stream().map(StockTradingValue::average20d), count)));
    }

    /**
     * 저장된 일봉으로 한 종목의 평균 거래대금을 근사한다. {@code stock_daily_prices}에 거래대금
     * 컬럼이 없어 {@code 종가 × 거래량}으로 갈음한다 — 변화율은 분자·분모에 같은 성질의 오차가
     * 들어가 상당 부분 상쇄되므로 순위를 매기는 데는 충분하다.
     *
     * <p>일봉이 {@code days}개에 못 미치면 견줄 기준이 없으므로 {@code null}이다.
     * 신규 상장 종목이 여기 해당한다.
     */
    public static BigDecimal approximateAverage(DailyPrices stored, int days) {
        List<DailyPrice> prices = stored.values();
        if (prices.size() < days) {
            return null;
        }
        BigDecimal sum = prices.stream()
                .map(price -> price.close().multiply(BigDecimal.valueOf(price.volume())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(days), DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 20거래일 평균 대비 변화율 %. 소수 둘째 자리로 반올림한다.
     * 평소와 같으면 {@code 0.00}, 오늘 거래가 없으면 {@code -100.00}이다.
     */
    public BigDecimal changeRatePercent() {
        return todayAverage.divide(average20d, DIVISION_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .movePointRight(SCALE)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(Stream<BigDecimal> amounts, BigDecimal count) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 한 종목의 거래대금.
     *
     * @param today     오늘 누적 거래대금. 시세를 구한 종목만 들어오므로 항상 있다
     * @param average20d 최근 20거래일 평균. 저장된 일봉이 20개가 안 되면 {@code null}이다
     */
    public record StockTradingValue(BigDecimal today, BigDecimal average20d) {

        /**
         * 과거와 견줄 수 있는 종목인지. 20일 평균이 없거나 0이면 견줄 수 없다.
         *
         * <p>견줄 수 없는 종목은 <b>오늘 금액에서도 빠진다</b>. 분자에만 남기면
         * "오늘은 87종목, 과거는 60종목"을 나누게 되어 비교가 성립하지 않는다.
         */
        boolean comparable() {
            return average20d != null && average20d.signum() > 0;
        }
    }
}
