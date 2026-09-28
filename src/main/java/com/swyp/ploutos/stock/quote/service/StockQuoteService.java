package com.swyp.ploutos.stock.quote.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 캐시된 현재가를 돌려준다. 값은 {@link QuoteRefresher}가 채우므로, 사용자 요청이 KIS를 부르는 것은
 * 캐시가 빈 종목의 첫 조회에서 락을 잡은 한 건뿐이다. 락을 못 잡은 요청은 채워지기를 기다리고,
 * 그래도 비어 있으면 KIS를 부르지 않고 실패한다(스탬피드 방지).
 */
@Service
@RequiredArgsConstructor
class StockQuoteService implements QuoteReader {

    private static final Logger log = LoggerFactory.getLogger(StockQuoteService.class);
    static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    // 락 TTL(3초) 동안 기다린다. 그 뒤에는 락을 잡은 요청이 죽은 것으로 본다.
    static final int MAX_POLLS = 60;

    private final StockReader stockReader;
    private final QuoteCache cache;
    private final QuoteProvider provider;

    @Override
    public Quote read(Long stockId) {
        StockWithMarket stock = stockReader.read(stockId);
        cache.markActive(stockId);
        Optional<Quote> cached = cache.find(stockId);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (cache.tryLock(stockId)) {
            return fetchAndStore(stockId, stock);
        }
        return waitForFill(stockId);
    }

    private Quote fetchAndStore(Long stockId, StockWithMarket stock) {
        try {
            Quote quote = provider.fetch(stock);
            cache.put(stockId, quote);
            return quote;
        } finally {
            cache.unlock(stockId);
        }
    }

    private Quote waitForFill(Long stockId) {
        for (int poll = 0; poll < MAX_POLLS; poll++) {
            pause();
            Optional<Quote> filled = cache.find(stockId);
            if (filled.isPresent()) {
                return filled.get();
            }
        }
        log.error("시세 캐시가 채워지기를 기다렸지만 비어 있습니다. stockId={}", stockId);
        throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
