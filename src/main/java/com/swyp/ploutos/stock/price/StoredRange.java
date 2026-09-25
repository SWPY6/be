package com.swyp.ploutos.stock.price;

import java.time.LocalDate;
import java.util.Optional;

/** 한 종목에 대해 DB에 저장된 일봉의 가장 이른·늦은 거래일. 저장된 것이 없으면 둘 다 비어 있다. */
public record StoredRange(
        Optional<LocalDate> earliest,
        Optional<LocalDate> latest
) {

    public static StoredRange empty() {
        return new StoredRange(Optional.empty(), Optional.empty());
    }

    /** 저장된 가장 이른 거래일이 기준일 이전인가. 저장된 것이 없으면 false. */
    public boolean startsOnOrBefore(LocalDate date) {
        return earliest.map(day -> !day.isAfter(date)).orElse(false);
    }

    /** 저장된 가장 늦은 거래일이 기준일 이후인가. 저장된 것이 없으면 false. */
    public boolean endsOnOrAfter(LocalDate date) {
        return latest.map(day -> !day.isBefore(date)).orElse(false);
    }
}
