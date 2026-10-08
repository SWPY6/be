package com.swyp.ploutos.stock.movers.service;

import java.util.List;

import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;

/** 한 번의 조회 결과. 목록은 조건이 정한 순서대로이며, 그 순서가 곧 순위다. */
public record StockMoverDetail(
        MoverCondition condition,
        List<StockMover> stocks
) {

}
