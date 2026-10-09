package com.swyp.ploutos.market.service;

import java.util.List;

import com.swyp.ploutos.market.Markets;

public interface MarketReader {

    Markets read(Long marketId);

    /**
     * 시장 전부. 행이 국가당 두어 개뿐이라 식별자로 좁히지 않는다 — 종목 목록을 만들 때
     * 종목마다 시장을 읽으면 조회가 종목 수만큼 나간다.
     */
    List<Markets> readAll();
}
