package com.swyp.ploutos.stock.quote.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 최근 조회된 종목의 현재가를 주기적으로 받아 캐시에 채운다. 종목을 한 스레드에서 순차로 호출하고
 * 주기가 끝난 뒤부터 간격을 세므로, 초당 KIS 호출 수가 응답 시간으로 제한된다.
 */
@Service
@RequiredArgsConstructor
class QuoteRefresher {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefresher.class);

    private final StockReader stockReader;
    private final QuoteCache cache;
    private final QuoteProvider provider;

    @Scheduled(fixedDelayString = "${ploutos.quote.refresh-interval-seconds}", timeUnit = TimeUnit.SECONDS)
    void refresh() {
        List<Long> stockIds;
        try {
            if (!cache.tryRefreshLeadership()) {
                return;
            }
            stockIds = cache.activeStockIds();
        } catch (BusinessException e) {
            // 캐시 저장소에 접근할 수 없다. 원인은 캐시 어댑터가 남겼으니 이번 주기만 건너뛴다.
            return;
        }
        stockIds.forEach(this::refreshOne);
    }

    private void refreshOne(Long stockId) {
        try {
            StockWithMarket stock = stockReader.read(stockId);
            cache.put(stockId, provider.fetch(stock));
        } catch (RuntimeException e) {
            // 한 종목의 실패(KIS 오류, 삭제된 종목, 예상 밖 응답)가 나머지 종목의 갱신을 막지 않게 한다.
            log.warn("시세를 갱신하지 못했다. 다음 종목으로 넘어간다. stockId={}", stockId, e);
        }
    }
}
