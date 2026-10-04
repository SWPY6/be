package com.swyp.ploutos.stock.summary.service;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.stock.service.StockReader;
import com.swyp.ploutos.stock.summary.StockSummary;

import lombok.RequiredArgsConstructor;

/**
 * 종목 기본정보를 조회한다. 시세·뉴스·공시를 호출하지 않으므로 그쪽 장애와 무관하게 응답한다.
 */
@Service
@RequiredArgsConstructor
public class StockSummaryService {

    private final StockReader stockReader;
    private final IndustryReader industryReader;

    public StockSummary read(Long stockId) {
        return StockSummary.from(stockReader.read(stockId), industryReader.readByStockId(stockId));
    }
}
