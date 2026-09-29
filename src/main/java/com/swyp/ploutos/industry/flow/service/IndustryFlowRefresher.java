package com.swyp.ploutos.industry.flow.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.QuotedStock;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.QuoteReader;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 산업을 하나씩 계속 순회하며 평균 등락률을 계산해 저장한다. 한 산업을 다 돌면 그 산업만 저장하므로
 * 앱이 중간에 멈춰도 이미 저장한 산업은 남고, 산업별 계산 시각이 조회 시점과 가깝게 유지된다.
 *
 * <p>종목 시세는 {@code readWithoutTracking}으로 읽는다. {@code read}를 쓰면 수백 종목이 갱신
 * 대상으로 등록되어 사용자가 보고 있는 종목의 시세 갱신이 뒤로 밀린다.
 */
@Component
@RequiredArgsConstructor
class IndustryFlowRefresher {

    private static final Logger log = LoggerFactory.getLogger(IndustryFlowRefresher.class);

    private final IndustryReader industryReader;
    private final StockReader stockReader;
    private final QuoteReader quoteReader;
    private final IndustryFlowCalculator calculator;
    private final IndustryFlowRepository industryFlowRepository;
    private final IndustryFlowProperties properties;
    private final Clock clock;

    /** 다음에 처리할 산업. 앱이 재시작하면 0부터 다시 돌지만 한 바퀴가 짧아 문제되지 않는다. */
    private int cursor;

    /**
     * 한 번 깨어날 때 산업 하나만 처리한다. 900종목을 한 번에 몰아 훑으면 그 구간의 순간
     * 부하가 커지고, 중간에 멈추면 한 바퀴치가 날아간다.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    void refreshNext() {
        List<Industries> industries = industryReader.readAll();
        if (industries.isEmpty()) {
            return;
        }
        Industries target = industries.get(Math.floorMod(cursor, industries.size()));
        cursor = Math.floorMod(cursor + 1, industries.size());
        refresh(target);
    }

    private void refresh(Industries industry) {
        List<QuotedStock> quotedStocks = quoteAll(industryReader.readStockIds(industry.industryId()));
        for (Country country : Country.values()) {
            refreshCountry(industry, country, ofCountry(quotedStocks, country));
        }
    }

    private void refreshCountry(Industries industry, Country country, List<QuotedStock> quotedStocks) {
        IndustryFlowSnapshot snapshot = calculator.calculate(quotedStocks);
        if (snapshot.hasNoStock()) {
            // 시세를 한 건도 구하지 못했다. 직전 값을 남겨 두면 calculatedAt 이 낡아 드러난다.
            return;
        }
        save(industry.industryId(), country, snapshot, nowIn(country));
        log.debug("산업 흐름을 갱신했다. industry={} country={} avg={} stocks={}",
                industry.displayName(), country, snapshot.avgChangeRate(), snapshot.stockCount());
    }

    /**
     * 종목 시세를 하나씩 받는다. 호출 사이에 쉬어 초당 호출 수의 상한을 만든다 —
     * KIS 한도를 {@code QuoteRefresher}·사용자 요청과 나눠 쓰기 위해서다.
     */
    private List<QuotedStock> quoteAll(List<Long> stockIds) {
        List<QuotedStock> quotedStocks = new ArrayList<>();
        for (int index = 0; index < stockIds.size(); index++) {
            if (index > 0) {
                pause();
            }
            quote(stockIds.get(index)).ifPresent(quotedStocks::add);
        }
        return quotedStocks;
    }

    /** 한 종목의 실패가 나머지 종목을 막지 않게 한다. 실패한 종목은 평균에서 빠진다. */
    private Optional<QuotedStock> quote(Long stockId) {
        try {
            StockWithMarket stock = stockReader.read(stockId);
            Quote quote = quoteReader.readWithoutTracking(stockId);
            return Optional.of(new QuotedStock(stock, quote));
        } catch (RuntimeException e) {
            log.warn("종목 시세를 구하지 못해 산업 평균에서 제외한다. stockId={}", stockId, e);
            return Optional.empty();
        }
    }

    private void save(Long industryId, Country country, IndustryFlowSnapshot snapshot,
            LocalDateTime calculatedAt) {
        Optional<IndustryFlows> stored = industryFlowRepository.findByIndustryIdAndCountry(industryId, country);
        if (stored.isEmpty()) {
            industryFlowRepository.save(new IndustryFlows(industryId, country, snapshot, calculatedAt));
            return;
        }
        IndustryFlows flow = stored.get();
        flow.refresh(snapshot, calculatedAt);
        industryFlowRepository.save(flow);
    }

    private static List<QuotedStock> ofCountry(List<QuotedStock> quotedStocks, Country country) {
        return quotedStocks.stream()
                .filter(quoted -> quoted.country() == country)
                .toList();
    }

    /** 행이 국가별이므로 계산 시각도 그 시장의 현지 시각으로 남긴다. */
    private LocalDateTime nowIn(Country country) {
        return LocalDateTime.now(clock.withZone(country.zoneId()));
    }

    private void pause() {
        try {
            Thread.sleep(properties.callInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("산업 흐름 갱신이 중단됐다", e);
        }
    }
}
