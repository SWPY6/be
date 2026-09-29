package com.swyp.ploutos.industry.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.industry.StockIndustries;

public interface StockIndustryRepository extends JpaRepository<StockIndustries, Long> {

    @Query("select si.stockId from StockIndustries si where si.industryId = :industryId")
    List<Long> findStockIdsByIndustryId(@Param("industryId") Long industryId);
}
