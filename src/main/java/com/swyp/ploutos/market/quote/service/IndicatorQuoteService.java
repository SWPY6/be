package com.swyp.ploutos.market.quote.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

import lombok.RequiredArgsConstructor;

/**
 * 캐시된 지표 현재값을 돌려준다. 지표가 5개로 고정이라 갱신 스케줄러를 두지 않고,
 * 캐시가 빈 지표의 첫 요청이 값을 채운다. 그 요청이 겹치면 락을 잡은 한 건만 외부를 부르고
 * 나머지는 채워지기를 기다린다. 그래도 비어 있으면 외부를 부르지 않고 실패한다(스탬피드 방지).
 */
@Service
@RequiredArgsConstructor
class IndicatorQuoteService implements IndicatorQuoteReader {

    private static final Logger log = LoggerFactory.getLogger(IndicatorQuoteService.class);
    static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    // 락 TTL(3초) 동안 기다린다. 그 뒤에는 락을 잡은 요청이 죽은 것으로 본다.
    static final int MAX_POLLS = 60;

    private final IndicatorQuoteCache cache;
    private final IndicatorQuoteProvider provider;

    @Override
    public IndicatorQuote read(MarketIndicator indicator) {
        Optional<IndicatorQuote> cached = cache.find(indicator);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (cache.tryLock(indicator)) {
            return fetchAndStore(indicator);
        }
        return waitForFill(indicator);
    }

    private IndicatorQuote fetchAndStore(MarketIndicator indicator) {
        try {
            IndicatorQuote quote = provider.fetch(indicator);
            cache.put(indicator, quote);
            return quote;
        } finally {
            cache.unlock(indicator);
        }
    }

    private IndicatorQuote waitForFill(MarketIndicator indicator) {
        for (int poll = 0; poll < MAX_POLLS; poll++) {
            pause();
            Optional<IndicatorQuote> filled = cache.find(indicator);
            if (filled.isPresent()) {
                return filled.get();
            }
        }
        log.error("지표 시세 캐시가 채워지기를 기다렸지만 비어 있습니다. indicator={}", indicator);
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
