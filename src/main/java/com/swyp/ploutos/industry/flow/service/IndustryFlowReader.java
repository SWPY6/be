package com.swyp.ploutos.industry.flow.service;

import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

public interface IndustryFlowReader {

    /**
     * 국가의 산업 흐름을 평균 등락률이 높은 순으로 읽는다. 계산된 적 없는 산업도 평균 0 · 종목 0
     * 으로 포함한다 — 화면 카드가 항상 9장이어야 한다.
     *
     * <p>{@code rank}는 순서에서 나오지만 값으로 실려 나간다. 그래서 호출하는 쪽이 목록을 다시
     * 배열해도 순위는 망가지지 않는다 — 관심 산업 고정(최대 3개)은 고정한 산업을 앞으로 당기고,
     * 산업별 동향 탭의 `전체` 필터는 가나다순으로 놓는다. 둘 다 순서만 바꾸고 순위 값은 건드리지
     * 않는다. 고정했다고 3위가 1위가 되면 안 된다.
     */
    List<RankedIndustryFlow> read(Country country);
}
