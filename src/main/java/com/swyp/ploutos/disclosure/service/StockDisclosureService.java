package com.swyp.ploutos.disclosure.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;
import com.swyp.ploutos.disclosure.service.DisclosureCache.CachedSearch;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.service.StockReader;

/**
 * 종목 공시를 조회한다. 종목과 기간을 먼저 검증해 잘못된 요청이면 공급자를 부르지 않는다.
 * 종목 시장으로 공급자 하나(KR=DART, US=SEC)를 고르며 다른 시장 공급자로 대체하지 않는다.
 * 목록은 공급자·법인·접수일 범위별로 캐시하고, 공급자 실패는 캐시하지 않는다.
 */
@Service
public class StockDisclosureService {

    private final StockReader stockReader;
    private final List<IssuerCodes> issuerCodes;
    private final DisclosureCache disclosureCache;
    private final List<DisclosureProvider> disclosureProviders;
    private final Clock clock;

    public StockDisclosureService(
            StockReader stockReader,
            List<IssuerCodes> issuerCodes,
            DisclosureCache disclosureCache,
            List<DisclosureProvider> disclosureProviders,
            Clock clock
    ) {
        this.stockReader = stockReader;
        this.issuerCodes = issuerCodes;
        this.disclosureCache = disclosureCache;
        this.disclosureProviders = disclosureProviders;
        this.clock = clock;
    }

    /** {@code from}·{@code to}를 둘 다 생략하면 종목 시장 현지 시각 기준 최근 30일이다. */
    public StockDisclosures read(Long stockId, OffsetDateTime from, OffsetDateTime to) {
        StockWithMarket stock = stockReader.read(stockId);
        // 응답 시각에 나노초가 찍히지 않게 초 단위로 자른다. 기본 기간과 수집 시각이 모두 이 값을 쓴다.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        DisclosureWindow window = DisclosureWindow.of(from, to, stock.localTimeAt(now));
        DisclosureSource source = DisclosureSource.of(stock.country());
        Optional<String> issuerId = issuerCodesOf(source).issuerIdOf(stock.exchange(), stock.ticker());
        if (issuerId.isEmpty()) {
            return StockDisclosures.unmapped(stockId, stock.country(), source, window);
        }
        ZoneId zone = stock.country().zoneId();
        FiledDateRange range = window.filedDatesIn(zone);
        CachedSearch search = disclosureCache.find(source, issuerId.get(), range)
                .orElseGet(() -> fetch(source, issuerId.get(), range, now));
        StockDisclosureFeed feed = StockDisclosureFeed.of(
                search.result().disclosures(), search.result().exhausted(), window, zone
        );
        return new StockDisclosures(
                stockId, stock.country(), source, window, range, stock.localTimeAt(search.fetchedAt()), feed
        );
    }

    private CachedSearch fetch(DisclosureSource source, String issuerId, FiledDateRange range, Instant now) {
        CachedSearch search = new CachedSearch(providerOf(source).search(issuerId, range), now);
        disclosureCache.put(source, issuerId, range, search);
        return search;
    }

    private IssuerCodes issuerCodesOf(DisclosureSource source) {
        return issuerCodes.stream()
                .filter(codes -> codes.source() == source)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(source + " 법인 매핑이 등록되지 않았다."));
    }

    private DisclosureProvider providerOf(DisclosureSource source) {
        return disclosureProviders.stream()
                .filter(provider -> provider.source() == source)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(source + " 공시 공급자가 등록되지 않았다."));
    }
}
