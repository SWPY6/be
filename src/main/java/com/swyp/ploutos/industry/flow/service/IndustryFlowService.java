package com.swyp.ploutos.industry.flow.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.IndustryFlowStocks;
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowStockRepository;
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
    private final IndustryFlowStockRepository industryFlowStockRepository;

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

        List<Industries> byChangeRate = industryReader.readAll().stream()
                .sorted(byRank(stored))
                .toList();

        // 산업마다 따로 읽으면 조회가 9번 된다. 한 번에 읽어 산업별로 나눈다
        Map<Long, List<IndustryFlowStock>> stocksByFlowId = readStocks(stored.values());

        // 순위는 이 순서에서 나오지만 응답에 값으로 실려 나간다. 뒤에서 순서를 바꿔도(관심 산업 고정,
        // 동향 탭의 가나다순 필터) 각 원소가 자기 순위를 들고 다니므로 다시 매길 필요가 없다.
        return IntStream.range(0, byChangeRate.size())
                .mapToObj(index -> toRanked(byChangeRate.get(index), index + 1, stored,
                        stocksByFlowId, country))
                .toList();
    }

    /**
     * 저장된 종목 행을 산업별로 모은다. {@code displayOrder}로 정렬해 시가총액 순서를 되살린다 —
     * 조회 결과의 순서에 기대지 않는다.
     */
    private Map<Long, List<IndustryFlowStock>> readStocks(Collection<IndustryFlows> flows) {
        List<Long> flowIds = flows.stream().map(IndustryFlows::industryFlowId).toList();
        if (flowIds.isEmpty()) {
            return Map.of();
        }
        return industryFlowStockRepository.findByIndustryFlowIdIn(flowIds).stream()
                .sorted(Comparator.comparingInt(IndustryFlowStocks::displayOrder))
                .collect(Collectors.groupingBy(IndustryFlowStocks::industryFlowId,
                        Collectors.mapping(IndustryFlowService::toFlowStock, Collectors.toList())));
    }

    private static IndustryFlowStock toFlowStock(IndustryFlowStocks stock) {
        return new IndustryFlowStock(stock.stockId(), stock.ticker(), stock.name(),
                stock.price(), stock.changeRate());
    }

    private static RankedIndustryFlow toRanked(Industries industry, int rank,
            Map<Long, IndustryFlows> stored, Map<Long, List<IndustryFlowStock>> stocksByFlowId,
            Country country) {
        IndustryFlows flow = stored.get(industry.industryId());
        if (flow == null) {
            return new RankedIndustryFlow(industry.name(), rank, NOT_CALCULATED, 0, 0, 0, null,
                    List.of(), null);
        }
        return new RankedIndustryFlow(industry.name(), rank, flow.avgChangeRate(), flow.stockCount(),
                flow.risingCount(), flow.fallingCount(), flow.tradingValue().orElse(null),
                stocksByFlowId.getOrDefault(flow.industryFlowId(), List.of()),
                localTime(flow, country));
    }

    /**
     * 순위를 매기는 비교자. 세 단계로 가른다 (RQ-0603).
     * 1) 반올림 전 평균 등락률  내림차순
     * 2) 거래대금 비율          내림차순   ← 측정하지 못한 산업은 뒤로
     * 3) 산업 표시명            가나다순
     *
     *
     * <p>1번이 <b>반올림 전</b> 값이어야 하는 이유는, 응답에 나가는 두 자리로는
     * {@code 1.333333}과 {@code 1.330000}이 둘 다 {@code 1.33}이라 구분할 수 없기 때문이다.
     *
     * <p>3번까지 두는 것은 순위가 매 요청 흔들리지 않게 하기 위해서다
     */
    private static Comparator<Industries> byRank(Map<Long, IndustryFlows> stored) {
        return Comparator
                .comparing((Industries industry) -> avgChangeRateOf(stored, industry),
                        Comparator.reverseOrder())
                .thenComparing(industry -> tradingRatioOf(stored, industry),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Industries::displayName);
    }

    /**
     * 오늘 거래대금이 그 산업의 20거래일 평균의 몇 배인지. 측정하지 못했으면 {@code null}이고
     * 비교자가 뒤로 보낸다 — 모르는 산업을 "거래가 활발했다"고 볼 수 없다.
     *
     * <p>시장 전체 대비 상대비율을 쓰지 않는 이유는 모든 산업을 같은 값으로 나누는 것이라
     * <b>순서가 바뀌지 않기</b> 때문이다. 더 단순한 쪽을 쓴다.
     */
    private static BigDecimal tradingRatioOf(Map<Long, IndustryFlows> stored, Industries industry) {
        IndustryFlows flow = stored.get(industry.industryId());
        if (flow == null) {
            return null;
        }
        return flow.tradingValue().map(IndustryTradingValue::ratio).orElse(null);
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
