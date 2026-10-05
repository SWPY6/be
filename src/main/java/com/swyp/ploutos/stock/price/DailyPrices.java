package com.swyp.ploutos.stock.price;

import java.math.BigDecimal;
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

    /** 그 거래일의 봉을 이미 가지고 있는가. */
    public boolean hasTradeOn(LocalDate tradeAt) {
        return prices.stream().anyMatch(price -> price.tradedOn(tradeAt));
    }

    /**
     * 마지막 확정 봉의 종가가 이 값과 같은가. 봉이 없으면 비교할 수 없으므로 거짓이다.
     *
     * <p>시세의 전일 종가를 넘겨 그 시세가 확정 봉에서 이어지는지 본다. 이어지지 않으면
     * 이미 확정된 거래일의 시세다. 비교를 {@code compareTo}로 하는 이유는 저장 정밀도가
     * 소수 넷째 자리라서다 — {@code equals}는 {@code 240217.0000}과 {@code 240217}을 다르게 본다.
     * 그 사정이 이 컬렉션 안쪽 지식이므로 종가를 꺼내 주지 않고 여기서 비교한다.
     */
    public boolean lastCloseIs(BigDecimal close) {
        if (prices.isEmpty()) {
            return false;
        }
        return prices.getLast().close().compareTo(close) == 0;
    }

    /** 해당 거래일의 봉을 뺀다. 당일 진행 중 봉을 저장·집계에서 제외할 때 쓴다. */
    public DailyPrices without(LocalDate tradeAt) {
        return new DailyPrices(prices.stream()
                .filter(price -> !price.tradedOn(tradeAt))
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
