package com.swyp.ploutos.industry.flow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.IndustryFlowStocks;
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowStockRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계산 결과를 저장한다. 산업 행과 그 산업의 종목 행을 <b>한 트랜잭션</b>에서 바꾼다.
 *
 * <p>{@link IndustryFlowRefresher}에 두지 않는 이유는 그쪽이 종목마다 쉬어 가며 시세를 받아
 * 한 산업에 수십 초를 쓰기 때문이다. 거기에 트랜잭션을 걸면 그동안 DB 커넥션을 붙잡는다.
 * 저장만 떼어 내면 트랜잭션이 짧게 끝난다.
 */
@Service
@Transactional
@RequiredArgsConstructor
class IndustryFlowWriter {

    private final IndustryFlowRepository industryFlowRepository;
    private final IndustryFlowStockRepository industryFlowStockRepository;

    /** 같은 산업·국가의 행을 덮어쓰고, 그 산업의 종목 행을 지운 뒤 다시 넣는다. */
    public void save(Long industryId, Country country, IndustryFlowSnapshot snapshot,
            LocalDateTime calculatedAt) {
        IndustryFlows flow = industryFlowRepository.save(
                merged(industryId, country, snapshot, calculatedAt));
        replaceStocks(flow.industryFlowId(), snapshot.stocks());
    }

    private IndustryFlows merged(Long industryId, Country country, IndustryFlowSnapshot snapshot,
            LocalDateTime calculatedAt) {
        Optional<IndustryFlows> stored =
                industryFlowRepository.findByIndustryIdAndCountry(industryId, country);
        if (stored.isEmpty()) {
            return new IndustryFlows(industryId, country, snapshot, calculatedAt);
        }
        IndustryFlows flow = stored.get();
        flow.refresh(snapshot, calculatedAt);
        return flow;
    }

    /**
     * 지우고 다시 넣는다. 종목 구성이 바뀌는 경우까지 다루려면 비교 로직이 필요한데
     * 행이 산업당 몇 개뿐이라 이득이 없다.
     *
     * <p>{@code displayOrder}는 받은 목록의 순서다 — 계산기가 시가총액 내림차순으로 넘긴다.
     */
    private void replaceStocks(Long industryFlowId, List<IndustryFlowStock> stocks) {
        industryFlowStockRepository.deleteByIndustryFlowId(industryFlowId);
        industryFlowStockRepository.flush();
        industryFlowStockRepository.saveAll(IntStream.range(0, stocks.size())
                .mapToObj(order -> toEntity(industryFlowId, stocks.get(order), order))
                .toList());
    }

    private static IndustryFlowStocks toEntity(Long industryFlowId, IndustryFlowStock stock,
            int displayOrder) {
        return new IndustryFlowStocks(industryFlowId, stock.stockId(), stock.ticker(), stock.name(),
                stock.price(), stock.changeRate(), displayOrder);
    }
}
