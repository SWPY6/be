package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.industry.flow.IndustryTradingValue.StockTradingValue;

class IndustryTradingValueTest {

    @Test
    void 오늘_거래대금이_평소보다_많으면_양수_변화율이다() {
        // given 오늘 600억, 20일 평균 400억
        List<StockTradingValue> stocks = List.of(stock(600, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void 오늘_거래대금이_평소보다_적으면_음수_변화율이다() {
        // given
        List<StockTradingValue> stocks = List.of(stock(200, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("-50.00");
    }

    @Test
    void 평소와_같으면_변화율이_0이다() {
        // given
        List<StockTradingValue> stocks = List.of(stock(400, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void 오늘_거래가_없으면_마이너스_100퍼센트다() {
        // given 장 시작 전이라 누적 거래대금이 0이다
        List<StockTradingValue> stocks = List.of(stock(0, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("-100.00");
    }

    @Test
    void 금액이_큰_종목이_산업을_주도한다() {
        // given 대형주는 1.5배(600억/400억), 소형주는 3배(3억/1억)로 늘었다
        List<StockTradingValue> stocks = List.of(
                stock(60_000_000_000L, 40_000_000_000L),
                stock(300_000_000L, 100_000_000L));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then 종목별 비율을 단순 평균하면 +125.00 이 되지만, 금액을 합치면 대형주가 지배한다
        assertThat(value.changeRatePercent()).isEqualByComparingTo("50.37");
    }

    @Test
    void 이십일_평균을_구하지_못한_종목은_오늘_금액에서도_뺀다() {
        // given 신규 상장 종목은 일봉이 20개가 안 되어 20일 평균이 없다
        List<StockTradingValue> stocks = List.of(
                stock(1_000, 500),
                new StockTradingValue(BigDecimal.valueOf(9_000), null));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then 비교 가능한 종목만으로 계산한다. 9000을 분자에만 넣으면 +900% 가 나온다
        assertThat(value.changeRatePercent()).isEqualByComparingTo("100.00");
        assertThat(value.todayAverage()).isEqualByComparingTo("1000");
        assertThat(value.average20d()).isEqualByComparingTo("500");
    }

    @Test
    void 이십일_평균이_0인_종목은_제외한다() {
        // given 20거래일 내내 거래가 없던 종목. 0으로 나눌 수 없다
        List<StockTradingValue> stocks = List.of(
                stock(600, 400),
                stock(100, 0));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void 견줄_수_있는_종목이_없으면_비어_있다() {
        // given 둘 다 20일 평균이 없다
        List<StockTradingValue> stocks = List.of(
                new StockTradingValue(BigDecimal.valueOf(1_000), null),
                new StockTradingValue(BigDecimal.valueOf(2_000), BigDecimal.ZERO));

        // when
        Optional<IndustryTradingValue> value = IndustryTradingValue.of(stocks);

        // then 0.00 으로 내리면 "계산 실패"가 "변화 없음"으로 위장한다
        assertThat(value).isEmpty();
    }

    @Test
    void 종목이_하나도_없으면_비어_있다() {
        // when 매핑된 종목이 없거나 시세를 모두 구하지 못한 산업
        Optional<IndustryTradingValue> value = IndustryTradingValue.of(List.of());

        // then
        assertThat(value).isEmpty();
    }

    @Test
    void 소수_둘째_자리로_반올림한다() {
        // given 3억 / 7억 = 0.428571... → -57.142857...%
        List<StockTradingValue> stocks = List.of(stock(3, 7));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.changeRatePercent()).isEqualByComparingTo("-57.14");
        assertThat(value.changeRatePercent().scale()).isEqualTo(2);
    }

    @Test
    void 이십일_평균이_0이면_객체를_만들_수_없다() {
        // when & then 팩토리를 거치지 않고 직접 만들어도 잘못된 상태가 생기지 않는다
        assertThatThrownBy(() -> new IndustryTradingValue(BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StockTradingValue stock(long today, long average20d) {
        return new StockTradingValue(BigDecimal.valueOf(today), BigDecimal.valueOf(average20d));
    }
}
