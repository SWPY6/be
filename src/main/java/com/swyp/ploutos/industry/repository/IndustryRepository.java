package com.swyp.ploutos.industry.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;

public interface IndustryRepository extends JpaRepository<Industries, Long> {

    Optional<Industries> findByName(IndustryCode name);

    // stock_industries 에 (stock_id, industry_id) 유니크 제약이 없어 같은 연결이 중복될 수 있다.
    @Query("""
            select distinct i
            from Industries i
            join StockIndustries si on si.industryId = i.industryId
            where si.stockId = :stockId
            """)
    List<Industries> findByStockId(@Param("stockId") Long stockId);
}
