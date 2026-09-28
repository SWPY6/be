package com.swyp.ploutos.industry.flow.service;

import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

public interface IndustryFlowReader {

    /**
     * 국가의 산업 흐름을 순위 오름차순으로 읽는다. 계산된 적 없는 산업도 평균 0 · 종목 0 으로
     * 포함한다 — 화면 카드가 항상 9장이어야 한다.
     */
    List<RankedIndustryFlow> read(Country country);
}
