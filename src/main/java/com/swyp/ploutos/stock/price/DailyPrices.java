package com.swyp.ploutos.stock.price;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 한 종목의 확정 일봉 목록. 항상 거래일 오름차순이다.
 */
public final class DailyPrices {

    /** 거래량 평균의 기준이 되는 거래일 수. 화면의 "평소 거래량 대비"와 거래량 차트 기준선이 이 값을 쓴다. */
    public static final int AVERAGE_DAYS = 20;

    private final List<DailyPrice> prices;

    private DailyPrices(List<DailyPrice> sorted) {
        this.prices = sorted;
    }

    public static DailyPrices of(List<DailyPrice> prices) {
        return new DailyPrices(prices.stream()
                .sorted(Comparator.comparing(DailyPrice::tradeAt))
                .toList());
    }

    public List<DailyPrice> values() {
        return prices;
    }

    public boolean isEmpty() {
        return prices.isEmpty();
    }

    public Optional<LocalDate> firstTradeAt() {
        return prices.stream().findFirst().map(DailyPrice::tradeAt);
    }

    public Optional<LocalDate> lastTradeAt() {
        if (prices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(prices.getLast().tradeAt());
    }

    /** 해당 거래일의 봉을 뺀다. 당일 진행 중 봉을 저장·집계에서 제외할 때 쓴다. */
    public DailyPrices without(LocalDate tradeAt) {
        return new DailyPrices(prices.stream()
                .filter(price -> !price.tradeAt().equals(tradeAt))
                .toList());
    }

    /** 주어진 거래일의 봉을 뺀다. 이미 저장된 거래일을 걸러낼 때 쓴다. */
    public DailyPrices excluding(Set<LocalDate> tradeAts) {
        return new DailyPrices(prices.stream()
                .filter(price -> !tradeAts.contains(price.tradeAt()))
                .toList());
    }

    /** 최근 20거래일 거래량의 단순 평균. 거래일이 부족하면 비어 있다. */
    public Optional<Long> averageVolume20d() {
        return averageVolumeOfLast(AVERAGE_DAYS);
    }

    /** 최근 {@code days}거래일 거래량의 단순 평균(버림). 거래일이 부족하면 비어 있다. */
    public Optional<Long> averageVolumeOfLast(int days) {
        if (prices.size() < days) {
            return Optional.empty();
        }
        long sum = prices.subList(prices.size() - days, prices.size()).stream()
                .mapToLong(DailyPrice::volume)
                .sum();
        return Optional.of(sum / days);
    }
}
