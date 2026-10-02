package com.swyp.ploutos.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed.Coverage;
import com.swyp.ploutos.disclosure.service.DisclosureCache.CachedSearch;
import com.swyp.ploutos.disclosure.service.DisclosureProvider.SearchResult;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.service.StockReader;

class StockDisclosureServiceTest {

    private static final Long KR_STOCK_ID = 1L;
    private static final Long US_STOCK_ID = 2L;
    private static final Long ETF_STOCK_ID = 3L;
    private static final Long MISSING_STOCK_ID = 99L;
    private static final String CORP_CODE = "00126380";
    private static final String CIK = "0000320193";
    // 2026-10-02 14:00:00.5 KST = 2026-10-02 01:00:00.5 EDT
    private static final Instant NOW = Instant.parse("2026-10-02T05:00:00.500Z");
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final OffsetDateTime NOW_KST = OffsetDateTime.of(2026, 10, 2, 14, 0, 0, 0, KST);
    private static final FiledDateRange KR_DEFAULT_RANGE =
            new FiledDateRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));
    private static final FiledDateRange US_DEFAULT_RANGE =
            new FiledDateRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));

    private FakeIssuerCodes dartCodes;
    private FakeIssuerCodes secCodes;
    private FakeDisclosureCache cache;
    private FakeDisclosureProvider dart;
    private FakeDisclosureProvider sec;
    private StockDisclosureService service;

    @BeforeEach
    void setUp() {
        StockWithMarket samsung = stock("005930", "삼성전자", Exchange.KRX, MarketCode.KOSPI, Country.KR, Currency.KRW);
        StockWithMarket apple = stock("AAPL", "Apple", Exchange.NASDAQ, MarketCode.NASDAQ, Country.US, Currency.USD);
        StockWithMarket etf = stock("069500", "KODEX 200", Exchange.KRX, MarketCode.KOSPI, Country.KR, Currency.KRW);
        StockReader stockReader = id -> {
            if (id.equals(KR_STOCK_ID)) {
                return samsung;
            }
            if (id.equals(US_STOCK_ID)) {
                return apple;
            }
            if (id.equals(ETF_STOCK_ID)) {
                return etf;
            }
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        };
        dartCodes = new FakeIssuerCodes(DisclosureSource.DART, Map.of("KRX:005930", CORP_CODE));
        secCodes = new FakeIssuerCodes(DisclosureSource.SEC, Map.of("NASDAQ:AAPL", CIK));
        cache = new FakeDisclosureCache();
        dart = new FakeDisclosureProvider(DisclosureSource.DART);
        sec = new FakeDisclosureProvider(DisclosureSource.SEC);
        service = new StockDisclosureService(
                stockReader, List.of(dartCodes, secCodes), cache, List.of(dart, sec), Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 국내_종목은_DART에서_법인_코드와_기간의_한국_날짜로_조회하고_결과를_캐시한다() {
        // given
        dart.result = new SearchResult(List.of(dartDisclosure("20260930000123", "20260930")), true);

        // when
        StockDisclosures disclosures = service.read(KR_STOCK_ID, null, null);

        // then
        assertThat(disclosures.source()).isEqualTo(DisclosureSource.DART);
        assertThat(dart.requests).containsExactly(CORP_CODE + "/" + KR_DEFAULT_RANGE);
        assertThat(sec.requests).isEmpty();
        assertThat(cache.store).containsKey("DART/" + CORP_CODE + "/" + KR_DEFAULT_RANGE);
        assertThat(disclosures.feed().items()).hasSize(1);
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.COMPLETE);
        assertThat(disclosures.filedDateRange()).isEqualTo(KR_DEFAULT_RANGE);
        assertThat(disclosures.fetchedAt()).isEqualTo(NOW_KST);
    }

    @Test
    void 미국_종목은_SEC에서_CIK와_기간의_뉴욕_날짜로_조회하고_DART는_부르지_않는다() {
        // given
        sec.result = new SearchResult(List.of(secDisclosure("2026-09-30T14:00:00.000Z")), true);

        // when
        StockDisclosures disclosures = service.read(US_STOCK_ID, null, null);

        // then
        assertThat(disclosures.source()).isEqualTo(DisclosureSource.SEC);
        assertThat(disclosures.market()).isEqualTo(Country.US);
        assertThat(sec.requests).containsExactly(CIK + "/" + US_DEFAULT_RANGE);
        assertThat(dart.requests).isEmpty();
        assertThat(dartCodes.lookups).isEmpty();
        assertThat(disclosures.feed().items()).hasSize(1);
        assertThat(disclosures.fetchedAt()).isEqualTo(OffsetDateTime.parse("2026-10-02T01:00:00-04:00"));
    }

    @Test
    void 접수_시각이_기간_밖인_SEC_공시는_뺀다() {
        // given 기간 시작 = 2026-09-02 01:00 EDT = 05:00 UTC
        sec.result = new SearchResult(List.of(secDisclosure("2026-09-02T04:59:59.000Z")), true);

        // when
        StockDisclosures disclosures = service.read(US_STOCK_ID, null, null);

        // then
        assertThat(disclosures.feed().items()).isEmpty();
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.COMPLETE);
    }

    @Test
    void 캐시에_있으면_공급자를_부르지_않고_캐시의_수집_시각을_돌려준다() {
        // given
        Instant fetchedAt = Instant.parse("2026-10-02T04:55:00Z");
        cache.store.put("DART/" + CORP_CODE + "/" + KR_DEFAULT_RANGE, new CachedSearch(
                new SearchResult(List.of(dartDisclosure("20260930000123", "20260930")), false), fetchedAt
        ));

        // when
        StockDisclosures disclosures = service.read(KR_STOCK_ID, null, null);

        // then
        assertThat(dart.requests).isEmpty();
        assertThat(disclosures.fetchedAt()).isEqualTo(fetchedAt.atOffset(KST));
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.PARTIAL);
    }

    @Test
    void 기간을_주면_양_끝이_걸친_시장_날짜_전체를_조회한다() {
        // given
        dart.result = new SearchResult(List.of(), true);
        OffsetDateTime from = OffsetDateTime.of(2026, 10, 1, 15, 30, 0, 0, KST);

        // when
        StockDisclosures disclosures = service.read(KR_STOCK_ID, from, NOW_KST);

        // then
        assertThat(disclosures.filedDateRange())
                .isEqualTo(new FiledDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)));
        assertThat(disclosures.window().from()).isEqualTo(from);
    }

    @Test
    void 법인을_찾지_못한_종목은_공급자를_부르지_않고_미매핑이다() {
        // when
        StockDisclosures disclosures = service.read(ETF_STOCK_ID, null, null);

        // then
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.UNMAPPED);
        assertThat(disclosures.source()).isEqualTo(DisclosureSource.DART);
        assertThat(disclosures.filedDateRange()).isNull();
        assertThat(disclosures.fetchedAt()).isNull();
        assertThat(dartCodes.lookups).containsExactly("KRX:069500");
        assertThat(dart.requests).isEmpty();
    }

    @Test
    void 없는_종목이면_공급자를_부르지_않고_종목_없음이다() {
        // when & then
        assertError(() -> service.read(MISSING_STOCK_ID, null, null), ErrorCode.STOCK_NOT_FOUND);
        assertThat(dartCodes.lookups).isEmpty();
        assertThat(secCodes.lookups).isEmpty();
    }

    @Test
    void 잘못된_기간이면_매핑과_공급자를_부르지_않는다() {
        // when & then
        assertError(() -> service.read(KR_STOCK_ID, NOW_KST.minusDays(1), null), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.read(US_STOCK_ID, NOW_KST.minusDays(91), NOW_KST), ErrorCode.INVALID_INPUT_VALUE);
        assertThat(dartCodes.lookups).isEmpty();
        assertThat(secCodes.lookups).isEmpty();
    }

    @Test
    void 공급자가_실패하면_캐시하지_않고_다른_시장_공급자로_대체하지_않는다() {
        // given
        sec.error = ErrorCode.DISCLOSURE_UNAVAILABLE;

        // when & then
        assertError(() -> service.read(US_STOCK_ID, null, null), ErrorCode.DISCLOSURE_UNAVAILABLE);
        assertThat(cache.store).isEmpty();
        assertThat(dart.requests).isEmpty();
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    private static Disclosure dartDisclosure(String receiptNo, String filedDate) {
        return Disclosure.dart(receiptNo, "분기보고서", "삼성전자", "삼성전자", null, filedDate).orElseThrow();
    }

    private static Disclosure secDisclosure(String acceptance) {
        return Disclosure.sec(CIK, "0000320193-26-000001", "8-K", "Current report", "Apple Inc.",
                acceptance.substring(0, 10), acceptance, "a.htm").orElseThrow();
    }

    private static StockWithMarket stock(
            String ticker, String name, Exchange exchange, MarketCode marketCode, Country country, Currency currency
    ) {
        return new StockWithMarket(
                new Stocks(1L, ticker, name, null, StockStatus.ACTIVE, exchange, 1L, "대표", LocalDate.of(1975, 6, 11)),
                new Markets(marketCode, country, TradingSession.REGULAR, currency)
        );
    }

    private static final class FakeIssuerCodes implements IssuerCodes {

        private final DisclosureSource source;
        private final Map<String, String> codes;
        final List<String> lookups = new ArrayList<>();

        FakeIssuerCodes(DisclosureSource source, Map<String, String> codes) {
            this.source = source;
            this.codes = codes;
        }

        @Override
        public DisclosureSource source() {
            return source;
        }

        @Override
        public Optional<String> issuerIdOf(Exchange exchange, String ticker) {
            String key = IssuerCodes.key(exchange, ticker);
            lookups.add(key);
            return Optional.ofNullable(codes.get(key));
        }
    }

    private static final class FakeDisclosureCache implements DisclosureCache {

        final Map<String, CachedSearch> store = new HashMap<>();

        @Override
        public Optional<CachedSearch> find(DisclosureSource source, String issuerId, FiledDateRange range) {
            return Optional.ofNullable(store.get(source + "/" + issuerId + "/" + range));
        }

        @Override
        public void put(DisclosureSource source, String issuerId, FiledDateRange range, CachedSearch search) {
            store.put(source + "/" + issuerId + "/" + range, search);
        }
    }

    private static final class FakeDisclosureProvider implements DisclosureProvider {

        private final DisclosureSource source;
        SearchResult result;
        ErrorCode error;
        final List<String> requests = new ArrayList<>();

        FakeDisclosureProvider(DisclosureSource source) {
            this.source = source;
        }

        @Override
        public DisclosureSource source() {
            return source;
        }

        @Override
        public SearchResult search(String issuerId, FiledDateRange range) {
            requests.add(issuerId + "/" + range);
            if (error != null) {
                throw new BusinessException(error);
            }
            return result;
        }
    }
}
