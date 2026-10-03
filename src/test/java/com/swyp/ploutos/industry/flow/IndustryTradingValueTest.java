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
    void 종목들의_금액을_합쳐_담는다() {
        // given 오늘 600억, 20일 평균 400억
        List<StockTradingValue> stocks = List.of(stock(600, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then 나눈 결과가 아니라 금액을 그대로 담는다
        assertThat(value.today()).isEqualByComparingTo("600");
        assertThat(value.average20d()).isEqualByComparingTo("400");
    }

    @Test
    void 자기_평균_대비_비율을_낸다() {
        // given
        List<StockTradingValue> stocks = List.of(stock(600, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.ratio()).isEqualByComparingTo("1.5");
    }

    @Test
    void 오늘_거래가_없으면_비율이_0이다() {
        // given 장 시작 전이라 누적 거래대금이 0이다
        List<StockTradingValue> stocks = List.of(stock(0, 400));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then
        assertThat(value.ratio()).isEqualByComparingTo("0");
    }

    @Test
    void 금액이_큰_종목이_산업을_주도한다() {
        // given 대형주는 1.5배(600억/400억), 소형주는 3배(3억/1억)로 늘었다
        List<StockTradingValue> stocks = List.of(
                stock(60_000_000_000L, 40_000_000_000L),
                stock(300_000_000L, 100_000_000L));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then 종목별 비율을 단순 평균하면 2.25배가 되지만, 금액을 합치면 대형주가 지배한다
        assertThat(value.ratio()).isEqualByComparingTo("1.503741");
    }

    @Test
    void 이십일_평균을_구하지_못한_종목은_오늘_금액에서도_뺀다() {
        // given 신규 상장 종목은 일봉이 20개가 안 되어 20일 평균이 없다
        List<StockTradingValue> stocks = List.of(
                stock(1_000, 500),
                new StockTradingValue(BigDecimal.valueOf(9_000), null));

        // when
        IndustryTradingValue value = IndustryTradingValue.of(stocks).orElseThrow();

        // then 9000을 분자에만 넣으면 20배가 나온다
        assertThat(value.today()).isEqualByComparingTo("1000");
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
        assertThat(value.today()).isEqualByComparingTo("600");
        assertThat(value.average20d()).isEqualByComparingTo("400");
    }

    @Test
    void 견줄_수_있는_종목이_없으면_비어_있다() {
        // given 둘 다 20일 평균이 없다
        List<StockTradingValue> stocks = List.of(
                new StockTradingValue(BigDecimal.valueOf(1_000), null),
                new StockTradingValue(BigDecimal.valueOf(2_000), BigDecimal.ZERO));

        // when
        Optional<IndustryTradingValue> value = IndustryTradingValue.of(stocks);

        // then 0 으로 채우면 "계산 실패"가 "거래 없음"으로 위장한다
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
    void 이십일_평균이_0이면_객체를_만들_수_없다() {
        // when & then 팩토리를 거치지 않고 직접 만들어도 잘못된 상태가 생기지 않는다
        assertThatThrownBy(() -> new IndustryTradingValue(BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시장_전체는_금액을_모두_합쳐_구한다() {
        // given 큰 산업은 1.5배, 작은 산업 둘은 그대로다
        List<IndustryTradingValue> industries = List.of(
                industry(600, 400),
                industry(10, 10),
                industry(10, 10));

        // when
        BigDecimal market = IndustryTradingValue.marketRatio(industries).orElseThrow();

        // then 산업 수로 평균하면 1.167 이지만 금액으로 합치면 큰 산업이 시장을 설명한다
        assertThat(market).isEqualByComparingTo("1.476190");
    }

    @Test
    void 측정된_산업이_셋보다_적으면_시장을_말할_수_없다() {
        // given 일봉이 드물어 두 산업만 측정됐다
        List<IndustryTradingValue> industries = List.of(industry(600, 400), industry(10, 10));

        // when
        Optional<BigDecimal> market = IndustryTradingValue.marketRatio(industries);

        // then 한 산업이 곧 시장이 되면 자기 자신과 비교하게 된다
        assertThat(market).isEmpty();
    }

    @Test
    void 시장보다_활발하면_상대비율이_1보다_크다() {
        // given 시장은 1.2배인데 이 산업은 1.5배다
        IndustryTradingValue value = industry(150, 100);

        // when
        BigDecimal relative = value.relativeTo(new BigDecimal("1.2"));

        // then
        assertThat(relative).isEqualByComparingTo("1.250");
    }

    @Test
    void 시장보다_한산하면_상대비율이_1보다_작다() {
        // given 시장은 1.2배인데 이 산업은 0.9배다
        IndustryTradingValue value = industry(90, 100);

        // when
        BigDecimal relative = value.relativeTo(new BigDecimal("1.2"));

        // then
        assertThat(relative).isEqualByComparingTo("0.750");
    }

    @Test
    void 장중_경과율은_상대비율에서_약분된다() {
        // given 하루 전체 기준으로 산업은 1.5배, 시장은 1.2배다.
        // 장이 절반쯤 지난 시각이라 양쪽 모두 관측값에 0.47 이 곱해져 있다
        BigDecimal elapsed = new BigDecimal("0.47");
        IndustryTradingValue observed = new IndustryTradingValue(
                new BigDecimal("150").multiply(elapsed), new BigDecimal("100"));
        BigDecimal observedMarket = new BigDecimal("1.2").multiply(elapsed);

        // when
        BigDecimal relative = observed.relativeTo(observedMarket);

        // then 경과율을 추정하지 않았는데도 하루 전체 기준의 값이 그대로 나온다
        assertThat(relative).isEqualByComparingTo("1.250");
    }

    @Test
    void 상대비율은_소수_셋째_자리로_반올림한다() {
        // given 1 ÷ 3 = 0.333333...
        IndustryTradingValue value = industry(100, 100);

        // when
        BigDecimal relative = value.relativeTo(new BigDecimal("3"));

        // then
        assertThat(relative).isEqualByComparingTo("0.333");
        assertThat(relative.scale()).isEqualTo(3);
    }

    private static StockTradingValue stock(long today, long average20d) {
        return new StockTradingValue(BigDecimal.valueOf(today), BigDecimal.valueOf(average20d));
    }

    private static IndustryTradingValue industry(long today, long average20d) {
        return new IndustryTradingValue(BigDecimal.valueOf(today), BigDecimal.valueOf(average20d));
    }
}
