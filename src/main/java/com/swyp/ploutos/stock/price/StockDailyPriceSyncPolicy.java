package com.swyp.ploutos.stock.price;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.stock.StockWithMarket;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 일봉이 요청 구간을 덮는지 판정하고, 같은 종목의 외부 동기화를 하루 1회로 제한한다.
 */
@Component
@RequiredArgsConstructor
public class StockDailyPriceSyncPolicy {

    private final Clock clock;
    private final Map<Long, LocalDate> lastAttemptDates = new ConcurrentHashMap<>();

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

    /** 오늘 이 종목의 동기화를 아직 시도하지 않았으면 시도를 기록하고 true. 이미 했으면 false. */
    public boolean tryStartSync(Long stockId, LocalDate today) {
        LocalDate previous = lastAttemptDates.put(stockId, today);
        return previous == null || !previous.equals(today);
    }
}
