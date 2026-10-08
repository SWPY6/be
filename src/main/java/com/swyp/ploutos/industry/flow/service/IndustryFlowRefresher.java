package com.swyp.ploutos.industry.flow.service;

import java.math.BigDecimal;
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
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.QuotedStock;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.service.QuoteReader;
import com.swyp.ploutos.stock.service.StockReader;
import com.swyp.ploutos.stock.snapshot.service.StockSnapshotWriter;

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
    private final DailyPriceReader dailyPriceReader;
    private final IndustryFlowCalculator calculator;
    private final IndustryFlowWriter industryFlowWriter;
    private final StockSnapshotWriter stockSnapshotWriter;
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

    /**
     * 조회 대상 종목. {@code QuoteReader}가 식별자를 요구하므로 종목 정보와 함께 들고 다닌다.
     * 엔티티에서 꺼내지 않는 이유는 식별자를 DB가 정하기 때문이다 — 그러면 DB를 거치지 않은
     * 객체로는 이 코드를 검증할 수 없다.
     */
    private record QuoteTarget(Long stockId, StockWithMarket stock) {

        Country country() {
            return stock.country();
        }
    }

    private void refresh(Industries industry) {
        // 종목 정보를 먼저 모은다. 시세 조회가 실패해도 그 종목이 어느 국가 소속인지는 알아야
        // "이 국가에 매핑이 없다"와 "매핑은 있는데 시세를 다 못 구했다"를 구분할 수 있다.
        List<QuoteTarget> targets = readStocks(industryReader.readStockIds(industry.industryId()));
        List<QuotedStock> quotedStocks = quoteAll(targets);
        for (Country country : Country.values()) {
            refreshCountry(industry, country, ofCountry(quotedStocks, country),
                    hasStockOf(targets, country));
        }
        saveSnapshots(quotedStocks);
    }

    /**
     * 종목별 시세를 남긴다. 주요 변동 종목 화면이 외부 호출 없이 정렬·필터하려면 소속 종목
     * 전체의 값이 있어야 하는데, 산업 평균을 내느라 이미 받아 둔 것이 그것이다 —
     * <b>여기서 외부 호출이 늘지 않는다.</b>
     *
     * <p>산업 흐름을 저장한 <b>뒤에</b> 부른다. 이쪽이 실패해도 한 바퀴치 평균은 이미 남는다.
     */
    private void saveSnapshots(List<QuotedStock> quotedStocks) {
        stockSnapshotWriter.save(quotedStocks.stream()
                .map(quoted -> quoted.toSnapshot(nowIn(quoted.country())))
                .toList());
    }

    private void refreshCountry(Industries industry, Country country, List<QuotedStock> quotedStocks,
            boolean hasMappedStock) {
        IndustryFlowSnapshot snapshot = calculator.calculate(quotedStocks);

        // 종목은 매핑돼 있는데 시세를 하나도 구하지 못했다. 0으로 덮어쓰면 실패가 "평균 0"으로
        // 위장되므로 저장하지 않는다 — 직전 값과 낡은 calculatedAt 이 남아야 문제가 드러난다.
        // 매핑 자체가 없는 국가는 평균 0 · 종목 0 이 사실이므로 이 분기를 타지 않고 저장된다.
        if (hasMappedStock && snapshot.hasNoStock()) {
            return;
        }
        industryFlowWriter.save(industry.industryId(), country, snapshot, nowIn(country));
    }

    /**
     * 종목 정보를 모은다. DB 조회라 호출 속도를 제한하지 않는다.
     * 찾지 못한 종목은 건너뛴다 — 매핑이 낡아 없는 종목을 가리킬 수 있다.
     */
    private List<QuoteTarget> readStocks(List<Long> stockIds) {
        List<QuoteTarget> targets = new ArrayList<>();
        for (Long stockId : stockIds) {
            try {
                targets.add(new QuoteTarget(stockId, stockReader.read(stockId)));
            } catch (RuntimeException e) {
                log.warn("종목을 찾지 못해 산업 평균에서 제외한다. stockId={}", stockId, e);
            }
        }
        return targets;
    }

    /**
     * 종목 시세를 하나씩 받는다. 호출 사이에 쉬어 초당 호출 수의 상한을 만든다 —
     * KIS 한도를 {@code QuoteRefresher}·사용자 요청과 나눠 쓰기 위해서다.
     */
    private List<QuotedStock> quoteAll(List<QuoteTarget> targets) {
        List<QuotedStock> quotedStocks = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            if (index > 0) {
                pause();
            }
            quote(targets.get(index)).ifPresent(quotedStocks::add);
        }
        return quotedStocks;
    }

    /**
     * 한 종목의 실패가 나머지 종목을 막지 않게 한다. 실패한 종목은 평균에서 빠진다.
     *
     * <p>여기서 로그를 남기지 않는 것은 의도다. 실패 사유는 KIS 클라이언트가 이미 기록하고,
     * 이 자리에서 또 남기면 장애 때 대상 종목 수만큼 같은 내용이 쏟아진다. 몇 종목이 빠졌는지는
     * 저장되는 {@code stockCount}로 드러난다.
     */
    private Optional<QuotedStock> quote(QuoteTarget target) {
        try {
            return Optional.of(new QuotedStock(target.stockId(), target.stock(),
                    quoteReader.readWithoutTracking(target.stockId()),
                    averageTradingValue(target.stockId())));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 20거래일 평균 거래대금. 저장된 일봉만 읽는다 — 종목 수만큼 반복되므로 부족분을 외부에서
     * 채우는 경로({@code findBetween}, {@code averageVolume20d})를 쓰면 KIS 호출이 종목 수만큼 는다.
     *
     * <p>실패해도 시세는 살린다. 거래대금과 평균 등락률은 독립된 값이라, 여기서 예외를 올리면
     * 구할 수 있었던 등락률까지 잃는다.
     */
    private BigDecimal averageTradingValue(Long stockId) {
        try {
            return IndustryTradingValue.approximateAverage(
                    dailyPriceReader.readStoredLatest(stockId, DailyPrices.AVERAGE_DAYS),
                    DailyPrices.AVERAGE_DAYS);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<QuotedStock> ofCountry(List<QuotedStock> quotedStocks, Country country) {
        return quotedStocks.stream()
                .filter(quoted -> quoted.country() == country)
                .toList();
    }

    /** 시세를 구했는지와 무관하게, 이 국가에 매핑된 종목이 하나라도 있었는지. */
    private static boolean hasStockOf(List<QuoteTarget> targets, Country country) {
        return targets.stream().anyMatch(target -> target.country() == country);
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
