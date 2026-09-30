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

    /**
     * 저장된 최근 일봉 {@code days}개. 거래일 오름차순이며, 저장된 것이 부족하면 부족한 대로 준다.
     *
     * <p>이 메서드만 read-through를 하지 않는다. 위의 둘은 저장된 봉이 부족하면 외부를 호출하는데,
     * 종목 수만큼 반복해 부르는 호출자(산업 평균 계산)에게는 그 편의가 외부 호출을 종목 수만큼
     * 늘리는 부작용이 된다. 그런 호출자는 "있는 것만 달라"가 필요하다.
     */
    DailyPrices readStoredLatest(Long stockId, int days);
}
