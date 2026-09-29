package com.swyp.ploutos.industry.flow.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.service.IndustryReader;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 산업 흐름을 읽어 순위를 매긴다. 외부 시세를 호출하지 않고 DB만 읽는다 —
 * 값은 {@link IndustryFlowRefresher}가 미리 계산해 저장해 둔다.
 */
@Service
@RequiredArgsConstructor
public class IndustryFlowService {

    private static final BigDecimal NOT_CALCULATED = BigDecimal.ZERO.setScale(2);

    private final IndustryReader industryReader;
    private final IndustryFlowRepository industryFlowRepository;

    /**
     * 국가의 산업 흐름을 평균 등락률이 높은 순으로 읽는다. 계산된 적 없는 산업도 평균 0 · 종목 0
     * 으로 포함한다 — 화면 카드가 항상 9장이어야 한다.
     *
     * <p>{@code rank}는 순서에서 나오지만 값으로 실려 나간다. 그래서 호출하는 쪽이 목록을 다시
     * 배열해도 순위는 망가지지 않는다 — 관심 산업 고정(최대 3개)은 고정한 산업을 앞으로 당기고,
     * 산업별 동향 탭의 `전체` 필터는 가나다순으로 놓는다. 둘 다 순서만 바꾸고 순위 값은 건드리지
     * 않는다. 고정했다고 3위가 1위가 되면 안 된다.
     */
    public List<RankedIndustryFlow> read(Country country) {
        Map<Long, IndustryFlows> stored = industryFlowRepository.findByCountry(country).stream()
                .collect(Collectors.toMap(IndustryFlows::industryId, Function.identity()));

        // 평균 등락률 내림차순. 동점이면 표시명 가나다순으로 정해 순위가 매 요청 흔들리지 않게 한다.
        List<Industries> byChangeRate = industryReader.readAll().stream()
                .sorted(Comparator.comparing((Industries industry) -> avgChangeRateOf(stored, industry),
                                Comparator.reverseOrder())
                        .thenComparing(Industries::displayName))
                .toList();

        // 순위는 이 순서에서 나오지만 응답에 값으로 실려 나간다. 뒤에서 순서를 바꿔도(관심 산업 고정,
        // 동향 탭의 가나다순 필터) 각 원소가 자기 순위를 들고 다니므로 다시 매길 필요가 없다.
        return IntStream.range(0, byChangeRate.size())
                .mapToObj(index -> toRanked(byChangeRate.get(index), index + 1, stored, country))
                .toList();
    }

    private static RankedIndustryFlow toRanked(Industries industry, int rank,
            Map<Long, IndustryFlows> stored, Country country) {
        IndustryFlows flow = stored.get(industry.industryId());
        if (flow == null) {
            return new RankedIndustryFlow(industry.name(), rank, NOT_CALCULATED, 0, List.of(), null);
        }
        return new RankedIndustryFlow(industry.name(), rank, flow.avgChangeRate(), flow.stockCount(),
                flow.majorStocks(), localTime(flow, country));
    }

    private static BigDecimal avgChangeRateOf(Map<Long, IndustryFlows> stored, Industries industry) {
        IndustryFlows flow = stored.get(industry.industryId());
        if (flow == null) {
            return NOT_CALCULATED;
        }
        return flow.avgChangeRate();
    }

    /** 저장된 시각은 그 시장의 현지 시각이다. 국가의 오프셋을 붙여 내려준다. */
    private static OffsetDateTime localTime(IndustryFlows flow, Country country) {
        return flow.calculatedAt().atZone(country.zoneId()).toOffsetDateTime();
    }
}
