package com.swyp.ploutos.market;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.swyp.ploutos.common.enums.Country;

/**
 * 시장 요약 화면에 보여 줄 시세 지표. 종목이 속한 시장을 뜻하는 {@code MarketCode}와는 별개다.
 * 탭 안의 표시 순서는 선언 순서를 따른다.
 */
public enum MarketIndicator {

    KOSPI("코스피", IndicatorKind.DOMESTIC_INDEX, "0001", IndicatorUnit.POINT, Country.KR,
            EnumSet.of(MarketRegion.DOMESTIC)),
    KOSDAQ("코스닥", IndicatorKind.DOMESTIC_INDEX, "1001", IndicatorUnit.POINT, Country.KR,
            EnumSet.of(MarketRegion.DOMESTIC)),
    NASDAQ("나스닥", IndicatorKind.OVERSEAS_INDEX, "COMP", IndicatorUnit.POINT, Country.US,
            EnumSet.of(MarketRegion.OVERSEAS)),
    SP500("S&P 500", IndicatorKind.OVERSEAS_INDEX, "SPX", IndicatorUnit.POINT, Country.US,
            EnumSet.of(MarketRegion.OVERSEAS)),
    USD_KRW("원/달러 환율", IndicatorKind.EXCHANGE_RATE, "FX@KRW", IndicatorUnit.KRW, Country.KR,
            EnumSet.of(MarketRegion.DOMESTIC, MarketRegion.OVERSEAS));

    private final String displayName;
    private final IndicatorKind kind;
    private final String symbol;
    private final IndicatorUnit unit;
    private final Country country;
    private final Set<MarketRegion> regions;

    MarketIndicator(String displayName, IndicatorKind kind, String symbol, IndicatorUnit unit, Country country,
            EnumSet<MarketRegion> regions) {
        this.displayName = displayName;
        this.kind = kind;
        this.symbol = symbol;
        this.unit = unit;
        this.country = country;
        this.regions = Set.copyOf(regions);
    }

    /** 이 탭에 속한 지표를 표시 순서(선언 순서)대로 돌려준다. */
    public static List<MarketIndicator> in(MarketRegion region) {
        return Arrays.stream(values())
                .filter(indicator -> indicator.belongsTo(region))
                .toList();
    }

    public boolean belongsTo(MarketRegion region) {
        return regions.contains(region);
    }

    public String displayName() {
        return displayName;
    }

    public IndicatorKind kind() {
        return kind;
    }

    public String symbol() {
        return symbol;
    }

    public IndicatorUnit unit() {
        return unit;
    }

    /** "오늘"과 거래일 계산에 쓰는 타임존. */
    public ZoneId zoneId() {
        return country.zoneId();
    }

}
