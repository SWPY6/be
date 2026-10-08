package com.swyp.ploutos.industry.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.StockIndustries;

public interface StockIndustryRepository extends JpaRepository<StockIndustries, Long> {

    @Query("select si.stockId from StockIndustries si where si.industryId = :industryId")
    List<Long> findStockIdsByIndustryId(@Param("industryId") Long industryId);

    /** 종목 → 산업 역방향. 목록 화면이 종목마다 산업을 보여줘야 해서 묶어 읽는다. */
    List<StockIndustries> findByStockIdIn(List<Long> stockIds);

    /**
     * 국가로 거른 소속 종목. 매핑에는 국가가 없고 {@code Stocks → Markets}에만 있으므로 조인한다.
     *
     * <p>산업 하나에 국내·해외 종목이 함께 매핑된다 — 시드가 산업마다 국내 4 + 해외 4를 넣는다.
     * 그래서 한 국가의 결과만 필요한 호출자는 전부를 받아 거르면 안 되고 이 메서드를 써야 한다.
     */
    @Query("""
            select si.stockId
            from StockIndustries si, Stocks s, Markets m
            where si.stockId = s.stockId
              and s.marketId = m.marketId
              and si.industryId = :industryId
              and m.country = :country
            """)
    List<Long> findStockIdsByIndustryIdAndCountry(@Param("industryId") Long industryId,
            @Param("country") Country country);
}
