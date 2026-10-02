package com.swyp.ploutos.stock.price;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.stock.StockWithMarket;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 일봉이 요청 구간을 덮는지 판정하고, 같은 종목의 외부 동기화를 하루 1회로 제한한다.
 * 단 그날 시도한 것보다 더 이른 시작일을 요청하면 그 구간을 위해 한 번 더 허용한다.
 */
@Component
@RequiredArgsConstructor
public class StockDailyPriceSyncPolicy {

    private final Clock clock;
    private final Map<Long, Attempt> lastAttempts = new ConcurrentHashMap<>();

    public LocalDate today(Country country) {
        return LocalDate.ofInstant(clock.instant(), country.zoneId());
    }

    /**
     * 저장된 봉이 [from, 마지막 거래일] 구간을 덮는가.
     * (a) 가장 이른 저장일이 from 이전이거나 상장일이 from 이후이고,
     * (b) 가장 늦은 저장일이 마지막 거래일 추정치 이후여야 한다.
     */
    public boolean covers(StoredRange stored, LocalDate from, StockWithMarket stock, LocalDate today) {
        boolean startCovered = stored.startsOnOrBefore(from) || stock.listedAfter(from);
        return startCovered && stored.endsOnOrAfter(lastTradingDayBefore(today));
    }

    /** 오늘 이전의 마지막 거래일 추정치. 토·일이면 직전 금요일. 공휴일은 고려하지 않는다. */
    public LocalDate lastTradingDayBefore(LocalDate today) {
        LocalDate day = today.minusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day;
    }

    /**
     * 오늘 이 종목을 이 시작일까지 아직 받아 오려 한 적이 없으면 시도를 기록하고 true. 이미 했으면 false.
     * 시작일을 함께 보는 이유: 조회 구간은 요청마다 다르다. 같은 날 짧은 구간으로 먼저 시도했다고 해서
     * 더 이른 시작일의 요청까지 막으면, 저장된 봉이 덮지 못하는 구간을 다음 날까지 채우지 못한다.
     *
     * <p>기록을 원자적으로 남긴다. 조회 후 기록 사이가 벌어지면 여러 요청이 함께 시도권을 얻고,
     * 같이 동기화하다 {@code stock_daily_prices}의 (stock_id, trade_at) 유니크 제약에 걸린다.
     *
     * <p>거절할 때는 기록을 덮지 않는다. 그날 넓은 구간으로 받아 뒀는데 좁은 요청이 기록을 덮으면,
     * 그다음 중간 구간 요청이 헛동기화를 일으킨다.
     */
    public boolean tryStartSync(Long stockId, LocalDate from, LocalDate today) {
        AtomicBoolean granted = new AtomicBoolean(false);
        lastAttempts.compute(stockId, (key, previous) -> {
            if (previous != null && previous.covers(from, today)) {
                return previous;
            }
            granted.set(true);
            return new Attempt(today, from);
        });
        return granted.get();
    }

    /** 그날 이 종목에 대해 시도한 가장 이른 시작일. */
    private record Attempt(LocalDate today, LocalDate from) {

        boolean covers(LocalDate from, LocalDate today) {
            return this.today.equals(today) && !from.isBefore(this.from);
        }
    }
}
