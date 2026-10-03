package com.swyp.ploutos.industry.flow.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.industry.flow.IndustryFlowStocks;

public interface IndustryFlowStockRepository extends JpaRepository<IndustryFlowStocks, Long> {

    /**
     * 여러 산업의 대표 종목을 한 번에 읽는다. 산업마다 따로 조회하면 9개 산업에 조회가 9번 된다.
     * 돌려주는 순서가 섞여 있으므로 호출자가 {@code displayOrder}로 정렬한다.
     */
    List<IndustryFlowStocks> findByIndustryFlowIdIn(Collection<Long> industryFlowIds);

    /**
     * 한 산업의 대표 종목을 모두 지운다. 갱신은 지우고 다시 넣는다 —
     * 종목 구성이 바뀌는 경우까지 다루려면 비교 로직이 필요한데 행이 산업당 4개라 이득이 없다.
     */
    void deleteByIndustryFlowId(Long industryFlowId);
}
