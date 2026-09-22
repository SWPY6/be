package com.swyp.ploutos.stock.price.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 종목의 확정 일봉을 읽는다. 저장된 봉이 부족하면 외부에서 받아 채운 뒤 돌려준다(read-through).
 * 호출자는 동기화의 존재를 알 필요가 없다.
 */
import com.swyp.ploutos.stock.price.DailyPrices;

public interface DailyPriceReader {

    DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to);

    Optional<Long> averageVolume20d(Long stockId);
}
