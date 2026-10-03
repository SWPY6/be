package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

/**
 * 한 산업의 거래대금. 오늘 누적 금액과 20거래일 평균 금액을 <b>합계로</b> 담는다.
 *
 * <p>금액을 먼저 합치고 나중에 나눈다. 종목별로 비율을 낸 뒤 평균하지 않는 이유는 소형주 하나가
 * 산업 전체를 흔들기 때문이다 — 거래대금 5배가 된 소형주 하나가 87종목 산업을 3.1배로 만든다.
 * 금액을 합치면 큰 종목이 자연히 크게 기여하므로 별도의 가중치가 필요하지 않다.
 *
 * <p><b>평균이 아니라 합계인 이유</b>는 산업 여럿을 시장 전체로 합치기 때문이다. 산업 하나만
 * 보면 종목 수가 약분되어 어느 쪽이든 비율이 같지만, 평균을 더하면 종목 4개짜리 산업과
 * 87개짜리 산업이 같은 비중을 갖는다. 합계를 더해야 실제 시장 총액이 된다.
 *
 * <p>{@code average20d}가 0이 될 수 없다. 그런 입력은 {@link #of(List)}가 걸러 내므로
 * 이 객체가 존재하면 {@link #ratio()}는 언제나 답을 낸다.
 */
public record IndustryTradingValue(BigDecimal today, BigDecimal average20d) {

    private static final int DIVISION_SCALE = 6;

    /** 상대비율의 자리수. 비율이라 퍼센트가 아니다. */
    private static final int RATIO_SCALE = 3;

    /**
     * 시장 전체를 말하려면 이만큼의 산업이 측정돼야 한다. 한 산업만 있으면 그 산업이 곧 시장이
     * 되어 상대비율이 언제나 {@code 1.000}이다 — 정보가 없는 값이 "통과"로 둔갑한다.
     */
    private static final int MIN_MEASURED_INDUSTRIES = 3;

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
        return Optional.of(new IndustryTradingValue(
                sum(comparable.stream().map(StockTradingValue::today)),
                sum(comparable.stream().map(StockTradingValue::average20d))));
    }

    /**
     * 저장된 일봉으로 한 종목의 평균 거래대금을 근사한다. {@code stock_daily_prices}에 거래대금
     * 컬럼이 없어 {@code 종가 × 거래량}으로 갈음한다 — 비율은 분자·분모에 같은 성질의 오차가
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
     * 같은 시각 시장 전체의 비율. 측정된 산업들의 금액을 모두 합쳐 나눈다.
     *
     * <p>산업 수가 아니라 <b>금액</b>으로 합치므로 큰 산업이 시장을 더 많이 설명한다.
     * 측정된 산업이 {@value #MIN_MEASURED_INDUSTRIES}개 미만이면 비어 있다.
     */
    public static Optional<BigDecimal> marketRatio(List<IndustryTradingValue> measured) {
        if (measured.size() < MIN_MEASURED_INDUSTRIES) {
            return Optional.empty();
        }
        return Optional.of(divide(
                sum(measured.stream().map(IndustryTradingValue::today)),
                sum(measured.stream().map(IndustryTradingValue::average20d))));
    }

    /**
     * 시장 전체 대비 이 산업의 활발함. {@code 1.000}이면 시장과 같은 속도, 그보다 크면 활발하다.
     *
     * <p>당일 누적 금액에는 "하루 중 얼마나 지났는가"가 곱해져 있어 20거래일 <b>하루 전체</b>
     * 평균과 직접 견줄 수 없다 — 장중에는 거의 언제나 작게 나온다. 그 경과율은 모든 산업에
     * 똑같이 걸리므로, 시장 전체의 비율로 한 번 더 나누면 <b>약분되어 사라진다.</b>
     * 경과율이 얼마인지 추정할 필요가 없다.
     */
    public BigDecimal relativeTo(BigDecimal marketRatio) {
        return ratio().divide(marketRatio, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** 자기 20거래일 평균 대비 비율. 평소와 같으면 {@code 1}, 오늘 거래가 없으면 {@code 0}이다. */
    public BigDecimal ratio() {
        return divide(today, average20d);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add);
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
