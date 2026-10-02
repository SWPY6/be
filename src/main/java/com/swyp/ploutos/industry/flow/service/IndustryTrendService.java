package com.swyp.ploutos.industry.flow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.IndustryTrendFilter;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

import lombok.RequiredArgsConstructor;

/**
 * 산업별 동향 화면의 산업 카드 목록. 탭에 맞게 거르고 정렬해서 돌려준다.
 *
 * <p>평균 등락률과 순위를 다시 계산하지 않는다 — {@link IndustryFlowService}가 낸 값을 그대로
 * 쓴다. 계산을 두 곳에 두면 시장 요약과 산업별 동향의 순위가 어긋난다.
 *
 * <p>외부 시세를 호출하지 않는다. 값은 {@link IndustryFlowRefresher}가 미리 계산해 저장해 둔다.
 */
@Service
@RequiredArgsConstructor
public class IndustryTrendService {

    private final IndustryFlowService industryFlowService;

    /**
     * {@code rank}는 9개 전체를 놓고 매긴 값이라 {@code filter}에 영향받지 않는다 —
     * {@code FALLING}으로 3건을 받아도 각 항목은 7·8·9 같은 원래 순위를 들고 나간다.
     */
    public List<RankedIndustryFlow> read(Country country, IndustryTrendFilter filter) {
        return filter.apply(industryFlowService.read(country));
    }
}
