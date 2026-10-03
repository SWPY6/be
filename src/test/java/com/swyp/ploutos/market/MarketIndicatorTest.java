package com.swyp.ploutos.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MarketIndicatorTest {

    @Test
    void 국내_탭은_코스피_코스닥_환율_순서로_구성된다() {
        // given
        MarketRegion region = MarketRegion.DOMESTIC;

        // when
        List<MarketIndicator> indicators = MarketIndicator.in(region);

        // then
        assertThat(indicators).containsExactly(MarketIndicator.KOSPI, MarketIndicator.KOSDAQ, MarketIndicator.USD_KRW);
    }

    @Test
    void 해외_탭은_나스닥_SP500_환율_순서로_구성된다() {
        // given
        MarketRegion region = MarketRegion.OVERSEAS;

        // when
        List<MarketIndicator> indicators = MarketIndicator.in(region);

        // then
        assertThat(indicators).containsExactly(MarketIndicator.NASDAQ, MarketIndicator.SP500, MarketIndicator.USD_KRW);
    }

    @Test
    void 환율은_국내와_해외_탭에_모두_속한다() {
        // given
        MarketIndicator exchangeRate = MarketIndicator.USD_KRW;

        // when
        boolean domestic = exchangeRate.belongsTo(MarketRegion.DOMESTIC);
        boolean overseas = exchangeRate.belongsTo(MarketRegion.OVERSEAS);

        // then
        assertThat(domestic).isTrue();
        assertThat(overseas).isTrue();
    }

    @Test
    void 모든_지표는_최소_한_탭에_속한다() {
        // given
        MarketIndicator[] indicators = MarketIndicator.values();

        // when
        List<MarketIndicator> orphans = Arrays.stream(indicators)
                .filter(indicator -> Arrays.stream(MarketRegion.values()).noneMatch(indicator::belongsTo))
                .toList();

        // then
        assertThat(orphans).isEmpty();
    }

    @Test
    void 표시명과_심볼은_비어있지_않고_서로_다르다() {
        // given
        MarketIndicator[] indicators = MarketIndicator.values();

        // when
        List<String> displayNames = Arrays.stream(indicators).map(MarketIndicator::displayName).toList();
        List<String> symbols = Arrays.stream(indicators).map(MarketIndicator::symbol).toList();

        // then
        assertThat(displayNames).doesNotContainNull().noneMatch(String::isBlank).doesNotHaveDuplicates();
        assertThat(symbols).doesNotContainNull().noneMatch(String::isBlank).doesNotHaveDuplicates();
    }

    @Test
    void 지표마다_명세의_심볼과_표시명을_가진다() {
        // given 2026-09-30 KIS 실측으로 확인한 값이다
        Map<MarketIndicator, String> expectedSymbols = Map.of(
                MarketIndicator.KOSPI, "0001",
                MarketIndicator.KOSDAQ, "1001",
                MarketIndicator.NASDAQ, "COMP",
                MarketIndicator.SP500, "SPX",
                MarketIndicator.USD_KRW, "FX@KRW");
        Map<MarketIndicator, String> expectedNames = Map.of(
                MarketIndicator.KOSPI, "코스피",
                MarketIndicator.KOSDAQ, "코스닥",
                MarketIndicator.NASDAQ, "나스닥",
                MarketIndicator.SP500, "S&P 500",
                MarketIndicator.USD_KRW, "원/달러 환율");

        // when
        Map<MarketIndicator, String> symbols = Arrays.stream(MarketIndicator.values())
                .collect(Collectors.toMap(indicator -> indicator, MarketIndicator::symbol));
        Map<MarketIndicator, String> names = Arrays.stream(MarketIndicator.values())
                .collect(Collectors.toMap(indicator -> indicator, MarketIndicator::displayName));

        // then
        assertThat(symbols).isEqualTo(expectedSymbols);
        assertThat(names).isEqualTo(expectedNames);
    }

    @Test
    void 기준_국가에_맞는_타임존을_돌려준다() {
        // given
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        ZoneId newYork = ZoneId.of("America/New_York");
        Map<MarketIndicator, ZoneId> expected = Map.of(
                MarketIndicator.KOSPI, seoul,
                MarketIndicator.KOSDAQ, seoul,
                MarketIndicator.NASDAQ, newYork,
                MarketIndicator.SP500, newYork,
                MarketIndicator.USD_KRW, seoul);

        // when
        Map<MarketIndicator, ZoneId> actual = Arrays.stream(MarketIndicator.values())
                .collect(Collectors.toMap(indicator -> indicator, MarketIndicator::zoneId));

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void 지표_종류와_단위가_명세와_같다() {
        // given
        Map<MarketIndicator, IndicatorKind> expectedKinds = Map.of(
                MarketIndicator.KOSPI, IndicatorKind.DOMESTIC_INDEX,
                MarketIndicator.KOSDAQ, IndicatorKind.DOMESTIC_INDEX,
                MarketIndicator.NASDAQ, IndicatorKind.OVERSEAS_INDEX,
                MarketIndicator.SP500, IndicatorKind.OVERSEAS_INDEX,
                MarketIndicator.USD_KRW, IndicatorKind.EXCHANGE_RATE);
        Map<MarketIndicator, IndicatorUnit> expectedUnits = Map.of(
                MarketIndicator.KOSPI, IndicatorUnit.POINT,
                MarketIndicator.KOSDAQ, IndicatorUnit.POINT,
                MarketIndicator.NASDAQ, IndicatorUnit.POINT,
                MarketIndicator.SP500, IndicatorUnit.POINT,
                MarketIndicator.USD_KRW, IndicatorUnit.KRW);

        // when
        Map<MarketIndicator, IndicatorKind> kinds = Arrays.stream(MarketIndicator.values())
                .collect(Collectors.toMap(indicator -> indicator, MarketIndicator::kind));
        Map<MarketIndicator, IndicatorUnit> units = Arrays.stream(MarketIndicator.values())
                .collect(Collectors.toMap(indicator -> indicator, MarketIndicator::unit));

        // then
        assertThat(kinds).isEqualTo(expectedKinds);
        assertThat(units).isEqualTo(expectedUnits);
    }

}
