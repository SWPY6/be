package com.swyp.ploutos.disclosure.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureCache.CachedSearch;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.service.StockReader;

/**
 * 종목 공시를 조회한다. 종목과 기간을 먼저 검증해 잘못된 요청이면 공급자를 부르지 않는다.
 * 종목 시장으로 공급자 하나(KR=DART)를 고르며 다른 시장 공급자로 대체하지 않는다.
 * 공급자가 없는 시장(US)은 외부를 호출하지 않고 미지원으로 돌려준다.
 * 목록은 공급자·법인·접수일 범위별로 캐시하고, 공급자 실패는 캐시하지 않는다. 공급자 범위를
 * 기간 끝 날짜 기준 최근 90일로 고정하므로 끝 날짜가 같은 종목은 기간과 관계없이 캐시 하나를 쓴다.
 * 공급자마다 법인 매핑과 공급자가 하나씩 있어야 하며, 빠지면 기동에 실패한다.
 */
@Service
public class StockDisclosureService {

    private final StockReader stockReader;
    private final Map<DisclosureSource, IssuerCodes> issuerCodes;
    private final DisclosureCache disclosureCache;
    private final Map<DisclosureSource, DisclosureProvider> disclosureProviders;
    private final Clock clock;

    public StockDisclosureService(
            StockReader stockReader,
            List<IssuerCodes> issuerCodes,
            DisclosureCache disclosureCache,
            List<DisclosureProvider> disclosureProviders,
            Clock clock
    ) {
        this.stockReader = stockReader;
        this.issuerCodes = bySource(issuerCodes, IssuerCodes::source, "법인 매핑");
        this.disclosureCache = disclosureCache;
        this.disclosureProviders = bySource(disclosureProviders, DisclosureProvider::source, "공시 공급자");
        this.clock = clock;
    }

    /** {@code from}·{@code to}를 둘 다 생략하면 종목 시장 현지 시각 기준 최근 30일이다. */
    public StockDisclosures read(Long stockId, OffsetDateTime from, OffsetDateTime to) {
        StockWithMarket stock = stockReader.read(stockId);
        // 응답 시각에 나노초가 찍히지 않게 초 단위로 자른다. 기본 기간과 수집 시각이 모두 이 값을 쓴다.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        DisclosureWindow window = DisclosureWindow.of(from, to, stock.localTimeAt(now));
        Optional<DisclosureSource> source = DisclosureSource.of(stock.country());
        if (source.isEmpty()) {
            return new StockDisclosures.Unsupported(stockId, stock.country(), window);
        }
        return read(stockId, stock, source.get(), window, now);
    }

    private StockDisclosures read(
            Long stockId, StockWithMarket stock, DisclosureSource source, DisclosureWindow window, Instant now
    ) {
        Optional<String> issuerId = issuerCodes.get(source).issuerIdOf(stock.exchange(), stock.ticker());
        if (issuerId.isEmpty()) {
            return new StockDisclosures.Unmapped(stockId, stock.country(), source, window);
        }
        ZoneId zone = stock.country().zoneId();
        FiledDateRange range = window.searchRangeIn(zone);
        CachedSearch search = disclosureCache.find(source, issuerId.get(), range)
                .orElseGet(() -> fetch(source, issuerId.get(), range, now));
        return new StockDisclosures.Fetched(
                stockId, stock.country(), source, window, range, stock.localTimeAt(search.fetchedAt()),
                search.feedIn(window, zone)
        );
    }

    private CachedSearch fetch(DisclosureSource source, String issuerId, FiledDateRange range, Instant now) {
        CachedSearch search = new CachedSearch(disclosureProviders.get(source).search(issuerId, range), now);
        disclosureCache.put(source, issuerId, range, search);
        return search;
    }

    private static <T> Map<DisclosureSource, T> bySource(
            List<T> items, Function<T, DisclosureSource> sourceOf, String name
    ) {
        Map<DisclosureSource, T> bySource = new EnumMap<>(DisclosureSource.class);
        for (T item : items) {
            if (bySource.putIfAbsent(sourceOf.apply(item), item) != null) {
                throw new IllegalStateException(sourceOf.apply(item) + " " + name + "이(가) 둘 이상 등록됐다.");
            }
        }
        for (DisclosureSource source : DisclosureSource.values()) {
            if (!bySource.containsKey(source)) {
                throw new IllegalStateException(source + " " + name + "이(가) 등록되지 않았다.");
            }
        }
        return bySource;
    }
}
