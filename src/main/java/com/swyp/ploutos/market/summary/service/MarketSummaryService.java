package com.swyp.ploutos.market.summary.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.quote.service.IndicatorQuoteReader;

import lombok.RequiredArgsConstructor;

/**
 * 시장 탭의 지표 시세를 표시 순서대로 모은다. 하나라도 구하지 못하면 예외를 그대로 올려 전체가 실패한다 —
 * 일부 카드만 빈 채로 내려가면 사용자가 그 값을 시세로 읽는다.
 */
@Service
@RequiredArgsConstructor
public class MarketSummaryService {

    private final IndicatorQuoteReader indicatorQuoteReader;

    /**
     * 지표를 하나씩 순서대로 읽는다. 캐시가 차 있으면 Redis 조회 몇 번이라 빠르고,
     * 캐시가 비었을 때 KIS를 동시에 부르지 않아 초당 호출 한도에 걸리지 않는다.
     */
    public MarketSummary read(MarketRegion region) {
        List<IndicatorQuote> quotes = MarketIndicator.in(region).stream()
                .map(indicatorQuoteReader::read)
                .toList();
        return new MarketSummary(region, quotes);
    }
}
