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

@Service
@RequiredArgsConstructor
class JpaIndustryFlowReader implements IndustryFlowReader {

    private static final BigDecimal NOT_CALCULATED = BigDecimal.ZERO.setScale(2);

    private final IndustryReader industryReader;
    private final IndustryFlowRepository industryFlowRepository;

    @Override
    public List<RankedIndustryFlow> read(Country country) {
        Map<Long, IndustryFlows> stored = industryFlowRepository.findByCountry(country).stream()
                .collect(Collectors.toMap(IndustryFlows::industryId, Function.identity()));

        // 평균 등락률 내림차순. 동점이면 표시명 가나다순으로 정해 순위가 매 요청 흔들리지 않게 한다.
        List<Industries> ranked = industryReader.readAll().stream()
                .sorted(Comparator.comparing((Industries industry) -> avgChangeRateOf(stored, industry),
                                Comparator.reverseOrder())
                        .thenComparing(Industries::displayName))
                .toList();

        return IntStream.range(0, ranked.size())
                .mapToObj(index -> toRanked(ranked.get(index), index + 1, stored, country))
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
