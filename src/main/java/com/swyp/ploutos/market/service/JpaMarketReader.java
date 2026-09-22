package com.swyp.ploutos.market.service;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class JpaMarketReader implements MarketReader {

    private final MarketRepository marketRepository;

    @Override
    public Markets read(Long marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new IllegalStateException("시장 " + marketId + " 이 존재하지 않습니다"));
    }
}
