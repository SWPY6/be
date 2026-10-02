package com.swyp.ploutos.market.price.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.MarketDailyPrices;

public interface MarketDailyPriceRepository extends JpaRepository<MarketDailyPrices, Long> {

    List<MarketDailyPrices> findByIndicatorAndTradeAtBetweenOrderByTradeAtAsc(
            MarketIndicator indicator, LocalDate from, LocalDate to);

    @Query("select min(p.tradeAt) from MarketDailyPrices p where p.indicator = :indicator")
    Optional<LocalDate> findEarliestTradeAt(@Param("indicator") MarketIndicator indicator);

    @Query("select max(p.tradeAt) from MarketDailyPrices p where p.indicator = :indicator")
    Optional<LocalDate> findLatestTradeAt(@Param("indicator") MarketIndicator indicator);

    @Query("select p.tradeAt from MarketDailyPrices p "
            + "where p.indicator = :indicator and p.tradeAt between :from and :to")
    List<LocalDate> findTradeAtsBetween(
            @Param("indicator") MarketIndicator indicator,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
