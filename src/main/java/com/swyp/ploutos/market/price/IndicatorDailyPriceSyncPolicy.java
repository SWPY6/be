package com.swyp.ploutos.market.price;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.StoredRange;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 일봉이 요청 구간을 덮는지 판정하고, 같은 지표의 외부 동기화를 하루 1회로 제한한다.
 * 단 그날 시도한 것보다 더 이른 시작일을 요청하면 그 구간을 위해 한 번 더 허용한다.
 *
 * <p>종목과 달리 상장일 조건이 없다. 지표는 상장일이 없고 차트의 최대 구간(5년)보다 오래됐다.
 */
@Component
@RequiredArgsConstructor
public class IndicatorDailyPriceSyncPolicy {

    private final Clock clock;
    private final Map<MarketIndicator, Attempt> lastAttempts = new ConcurrentHashMap<>();

    /** 지표 타임존의 오늘. 해외 지수는 뉴욕 날짜다. */
    public LocalDate today(MarketIndicator indicator) {
        return LocalDate.ofInstant(clock.instant(), indicator.zoneId());
    }

    /**
     * 저장된 봉이 [from, 마지막 거래일] 구간을 덮는가.
     * (a) 가장 이른 저장일이 from 이전이고,
     * (b) 가장 늦은 저장일이 마지막 거래일 추정치 이후여야 한다.
     */
    public boolean covers(StoredRange stored, LocalDate from, LocalDate today) {
        return stored.startsOnOrBefore(from) && stored.endsOnOrAfter(lastTradingDayBefore(today));
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
     * 오늘 이 지표를 이 시작일까지 아직 받아 오려 한 적이 없으면 시도를 기록하고 true. 이미 했으면 false.
     *
     * <p>기록을 원자적으로 남긴다. 지표는 5개로 고정이라 첫 요청이 한꺼번에 몰리기 쉽고,
     * 두 요청이 동시에 동기화하면 같은 행을 저장하다 유니크 제약에 걸린다.
     *
     * <p>거절할 때는 기록을 덮지 않는다. 그날 넓은 구간으로 받아 뒀는데 좁은 요청이 기록을 덮으면,
     * 그다음 중간 구간 요청이 헛동기화를 일으킨다.
     */
    public boolean tryStartSync(MarketIndicator indicator, LocalDate from, LocalDate today) {
        AtomicBoolean granted = new AtomicBoolean(false);
        lastAttempts.compute(indicator, (key, previous) -> {
            if (previous != null && previous.covers(from, today)) {
                return previous;
            }
            granted.set(true);
            return new Attempt(today, from);
        });
        return granted.get();
    }

    /**
     * 실패한 시도를 지워 같은 날 다시 시도할 수 있게 한다. 지우지 않으면 다음 요청이 외부를 부르지 않고
     * 저장된 행만 돌려준다. 그사이 다른 요청이 기록을 바꿨으면 그 기록은 남긴다.
     */
    public void cancelSync(MarketIndicator indicator, LocalDate from, LocalDate today) {
        lastAttempts.remove(indicator, new Attempt(today, from));
    }

    /** 그날 이 지표에 대해 시도한 가장 이른 시작일. */
    private record Attempt(LocalDate today, LocalDate from) {

        boolean covers(LocalDate from, LocalDate today) {
            return this.today.equals(today) && !from.isBefore(this.from);
        }
    }
}
