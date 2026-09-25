package com.swyp.ploutos.market.service;

import com.swyp.ploutos.market.Markets;

public interface MarketReader {

    Markets read(Long marketId);
}
