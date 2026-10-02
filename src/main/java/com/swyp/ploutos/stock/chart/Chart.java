package com.swyp.ploutos.stock.chart;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

/**
 * 한 구간의 차트 데이터. 확정 일봉을 요청한 봉 단위로 묶고, 진행 중인 봉을 마지막 버킷에 합친다.
 */
public final class Chart {

    /** 평균 거래량 기준선이 쓰는 최대 봉 수. */
    private static final int AVERAGE_CANDLES = 20;

    private final List<ChartCandle> candles;
    private final OffsetDateTime asOf;

    private Chart(List<ChartCandle> candles, OffsetDateTime asOf) {
        this.candles = candles;
        this.asOf = asOf;
    }

    /**
     * 진행 중인 봉을 받을지는 호출자가 판단한다. 그 거래일이 이미 확정 봉으로 있으면 붙이지 않는다 —
     * 장 마감 후 동기화가 끝나면 같은 날이 두 번 들어온다.
     */
    public static Chart of(DailyPrices closed, Optional<LiveCandle> live, ChartInterval interval) {
        Optional<LiveCandle> inProgress = live.filter(candle -> !closed.hasTradeOn(candle.tradeAt()));
        return new Chart(
                aggregate(withLive(closed.values(), inProgress), interval, inProgress.map(LiveCandle::tradeAt)),
                inProgress.map(LiveCandle::asOf).orElse(null)
        );
    }

    /**
     * 진행 중인 봉을 목록 끝에 붙인다.
     * 집계 경로를 하나로 유지하려는 것이다 — 이렇게 해 두면 "마지막 버킷에 합치기"가 특수 분기가 되지 않는다.
     */
    private static List<DailyPrice> withLive(List<DailyPrice> closed, Optional<LiveCandle> inProgress) {
        if (inProgress.isEmpty()) {
            return closed;
        }
        return Stream.concat(closed.stream(), Stream.of(inProgress.get().price())).toList();
    }

    private static List<ChartCandle> aggregate(List<DailyPrice> prices, ChartInterval interval,
            Optional<LocalDate> inProgressDay) {
        Map<LocalDate, List<DailyPrice>> buckets = prices.stream()
                .collect(Collectors.groupingBy(
                        price -> interval.bucketStart(price.tradeAt()),
                        TreeMap::new,
                        Collectors.toList()));
        return buckets.values().stream()
                .map(bucket -> ChartCandle.of(bucket, !holdsInProgress(bucket, inProgressDay)))
                .toList();
    }

    /** 진행 중 봉은 언제나 목록의 마지막이므로 버킷의 마지막 봉만 보면 된다. */
    private static boolean holdsInProgress(List<DailyPrice> bucket, Optional<LocalDate> inProgressDay) {
        return inProgressDay.filter(day -> bucket.getLast().tradedOn(day)).isPresent();
    }

    public List<ChartCandle> candles() {
        return candles;
    }

    /** 실제 포함된 첫 봉의 거래일. 봉이 없으면 비어 있다. */
    public Optional<LocalDate> from() {
        return candles.stream().findFirst().map(ChartCandle::tradeAt);
    }

    /** 실제 포함된 마지막 봉의 거래일. 봉이 없으면 비어 있다. */
    public Optional<LocalDate> to() {
        if (candles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candles.getLast().tradeAt());
    }

    /** 진행 중 봉의 기준 시각. 진행 중 봉이 없으면 비어 있다. */
    public Optional<OffsetDateTime> asOf() {
        return Optional.ofNullable(asOf);
    }

    /**
     * 확정 봉 중 마지막 최대 20개의 평균 거래량. 봉 단위를 따라가므로 기준선과 막대의 스케일이 늘 맞는다.
     * 확정 봉이 없으면 비어 있다.
     */
    public Optional<Long> averageVolume() {
        List<ChartCandle> confirmed = candles.stream().filter(ChartCandle::closed).toList();
        if (confirmed.isEmpty()) {
            return Optional.empty();
        }
        List<ChartCandle> recent = confirmed.subList(Math.max(0, confirmed.size() - AVERAGE_CANDLES), confirmed.size());
        return Optional.of(recent.stream().mapToLong(ChartCandle::volume).sum() / recent.size());
    }
}
