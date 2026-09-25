package com.swyp.ploutos.stock.price.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.stock.price.StockDailyPrices;

public interface StockDailyPriceRepository extends JpaRepository<StockDailyPrices, Long> {

    List<StockDailyPrices> findByStockIdAndTradeAtBetweenOrderByTradeAtAsc(Long stockId, LocalDate from, LocalDate to);

    List<StockDailyPrices> findByStockIdOrderByTradeAtDesc(Long stockId, Limit limit);

    Optional<StockDailyPrices> findFirstByStockIdOrderByTradeAtAsc(Long stockId);

    Optional<StockDailyPrices> findFirstByStockIdOrderByTradeAtDesc(Long stockId);

    @Query("select p.tradeAt from StockDailyPrices p where p.stockId = :stockId and p.tradeAt between :from and :to")
    List<LocalDate> findTradeAtsBetween(@Param("stockId") Long stockId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
