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
    // 기본 기간은 오늘에서 끝나므로 공급자 범위는 오늘(서울·뉴욕 모두 10-02) 기준 최근 90일이다.
    private static final FiledDateRange KR_DEFAULT_RANGE =
            new FiledDateRange(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 10, 2));
    private static final FiledDateRange US_DEFAULT_RANGE =
            new FiledDateRange(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 10, 2));

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
        dart.result = new DisclosureSearchResult(List.of(dartDisclosure("20260930000123", "20260930")), true);

        // when
        StockDisclosures.Fetched disclosures = fetched(service.read(KR_STOCK_ID, null, null));

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
        sec.result = new DisclosureSearchResult(List.of(secDisclosure("2026-09-30T14:00:00.000Z")), true);

        // when
        StockDisclosures.Fetched disclosures = fetched(service.read(US_STOCK_ID, null, null));

        // then
        assertThat(disclosures.source()).isEqualTo(DisclosureSource.SEC);
        assertThat(disclosures.country()).isEqualTo(Country.US);
        assertThat(sec.requests).containsExactly(CIK + "/" + US_DEFAULT_RANGE);
        assertThat(dart.requests).isEmpty();
        assertThat(dartCodes.lookups).isEmpty();
        assertThat(disclosures.feed().items()).hasSize(1);
        assertThat(disclosures.fetchedAt()).isEqualTo(OffsetDateTime.parse("2026-10-02T01:00:00-04:00"));
    }

    @Test
    void 접수_시각이_기간_밖인_SEC_공시는_뺀다() {
        // given 기간 시작 = 2026-09-02 01:00 EDT = 05:00 UTC
        sec.result = new DisclosureSearchResult(List.of(secDisclosure("2026-09-02T04:59:59.000Z")), true);

        // when
        StockDisclosures.Fetched disclosures = fetched(service.read(US_STOCK_ID, null, null));

        // then
        assertThat(disclosures.feed().items()).isEmpty();
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.COMPLETE);
    }

    @Test
    void 캐시에_있으면_공급자를_부르지_않고_캐시의_수집_시각을_돌려준다() {
        // given
        Instant fetchedAt = Instant.parse("2026-10-02T04:55:00Z");
        cache.store.put("DART/" + CORP_CODE + "/" + KR_DEFAULT_RANGE, new CachedSearch(
                new DisclosureSearchResult(List.of(dartDisclosure("20260930000123", "20260930")), false), fetchedAt
        ));

        // when
        StockDisclosures.Fetched disclosures = fetched(service.read(KR_STOCK_ID, null, null));

        // then
        assertThat(dart.requests).isEmpty();
        assertThat(disclosures.fetchedAt()).isEqualTo(fetchedAt.atOffset(KST));
        assertThat(disclosures.feed().coverage()).isEqualTo(Coverage.PARTIAL);
    }

    @Test
    void 오늘에서_끝나는_기간은_날짜를_바꿔도_최근_90일로_한_번만_조회하고_캐시를_함께_쓴다() {
        // given
        dart.result = new DisclosureSearchResult(List.of(dartDisclosure("20260925000123", "20260925")), true);
        OffsetDateTime from = OffsetDateTime.of(2026, 10, 1, 15, 30, 0, 0, KST);

        // when
        StockDisclosures.Fetched first = fetched(service.read(KR_STOCK_ID, from, NOW_KST));
        StockDisclosures.Fetched second =
                fetched(service.read(KR_STOCK_ID, from.minusDays(10), NOW_KST.minusHours(1)));
        StockDisclosures.Fetched third = fetched(service.read(KR_STOCK_ID, null, null));

        // then
        assertThat(dart.requests).containsExactly(CORP_CODE + "/" + KR_DEFAULT_RANGE);
        assertThat(first.filedDateRange()).isEqualTo(KR_DEFAULT_RANGE);
        assertThat(second.filedDateRange()).isEqualTo(KR_DEFAULT_RANGE);
        assertThat(third.filedDateRange()).isEqualTo(KR_DEFAULT_RANGE);
        assertThat(first.window().from()).isEqualTo(from);
        assertThat(first.feed().items()).isEmpty();
        assertThat(second.feed().items()).hasSize(1);
    }

    @Test
    void 과거에서_끝나는_기간은_시작을_바꿔도_끝_날짜_기준_최근_90일로_한_번만_조회한다() {
        // given
        dart.result = new DisclosureSearchResult(List.of(), true);
        OffsetDateTime to = OffsetDateTime.of(2026, 8, 31, 9, 0, 0, 0, KST);

        // when
        StockDisclosures.Fetched first = fetched(service.read(KR_STOCK_ID, to.minusDays(30), to));
        StockDisclosures.Fetched second = fetched(service.read(KR_STOCK_ID, to.minusDays(3), to.plusHours(5)));

        // then
        FiledDateRange expected = new FiledDateRange(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 8, 31));
        assertThat(first.filedDateRange()).isEqualTo(expected);
        assertThat(second.filedDateRange()).isEqualTo(expected);
        assertThat(dart.requests).containsExactly(CORP_CODE + "/" + expected);
    }

    @Test
    void 법인을_찾지_못한_종목은_공급자를_부르지_않고_미매핑이다() {
        // when
        StockDisclosures disclosures = service.read(ETF_STOCK_ID, null, null);

        // then
        assertThat(disclosures).isInstanceOf(StockDisclosures.Unmapped.class);
        assertThat(disclosures.source()).isEqualTo(DisclosureSource.DART);
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

    @Test
    void 공급자의_법인_매핑이나_공급자가_빠지면_기동에_실패한다() {
        // given
        StockReader stockReader = id -> {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        };
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        // when & then
        assertThatThrownBy(() -> new StockDisclosureService(
                stockReader, List.of(dartCodes), cache, List.of(dart, sec), clock
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("SEC 법인 매핑");
        assertThatThrownBy(() -> new StockDisclosureService(
                stockReader, List.of(dartCodes, secCodes), cache, List.of(dart), clock
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("SEC 공시 공급자");
    }

    private static StockDisclosures.Fetched fetched(StockDisclosures disclosures) {
        assertThat(disclosures).isInstanceOf(StockDisclosures.Fetched.class);
        return (StockDisclosures.Fetched) disclosures;
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
        DisclosureSearchResult result;
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
        public DisclosureSearchResult search(String issuerId, FiledDateRange range) {
            requests.add(issuerId + "/" + range);
            if (error != null) {
                throw new BusinessException(error);
            }
            return result;
        }
    }
}
