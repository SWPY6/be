package com.swyp.ploutos.stock.movers.service;

import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;

/**
 * 외부 순위 제공자. 모집단이 전 종목이라 우리가 셀 수 없는 조건을 대신 세어 준다.
 *
 * <p>돌려주는 {@code StockMover}에는 {@code stockId}·{@code industry}가 비어 있다 —
 * 공급자는 우리 종목 마스터를 모른다. 채우는 일은 서비스가 한다.
 */
public interface MoverRankingProvider {

    /** 정렬·건수 상한까지 적용해 돌려준다. {@code ALL}은 순위가 아니므로 받지 않는다. */
    List<StockMover> rank(Country country, MoverCondition condition);
}
