package com.swyp.ploutos.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
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
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsArticle.LinkKind;
import com.swyp.ploutos.news.service.NewsCache.CachedSearch;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.service.StockReader;

class StockNewsServiceTest {

    private static final Long KR_STOCK_ID = 1L;
    private static final Long US_STOCK_ID = 2L;
    private static final Long MISSING_STOCK_ID = 99L;
    // 2026-09-30 14:00 KST = 01:00 EDT
    private static final Instant NOW = Instant.parse("2026-09-30T05:00:00Z");
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private FakeNewsCache cache;
    private FakeNewsProvider provider;
    private StockReader stockReader;
    private StockNewsService service;

    @BeforeEach
    void setUp() {
        StockWithMarket samsung = new StockWithMarket(
                new Stocks(1L, "005930", "삼성전자", null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표",
                        LocalDate.of(1975, 6, 11)),
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        StockWithMarket apple = new StockWithMarket(
                new Stocks(2L, "AAPL", "Apple", null, StockStatus.ACTIVE, Exchange.NASDAQ, 1L, "CEO",
                        LocalDate.of(1980, 12, 12)),
                new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD));
        cache = new FakeNewsCache();
        provider = new FakeNewsProvider();
        stockReader = id -> {
            if (id.equals(KR_STOCK_ID)) {
                return samsung;
            }
            if (id.equals(US_STOCK_ID)) {
                return apple;
            }
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        };
        service = new StockNewsService(stockReader, cache, provider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 캐시에_없으면_종목명으로_검색하고_결과를_캐시한다() {
        // given
        provider.result = new NewsSearchResult(List.of(article("삼성전자 실적", 1)), true);

        // when
        StockNewsResult news = service.read(KR_STOCK_ID, null, null);

        // then
        assertThat(provider.queries).containsExactly("삼성전자");
        assertThat(cache.store).containsKey(KR_STOCK_ID);
        assertThat(news.feed().items()).hasSize(1);
        assertThat(news.fetchedAt()).isEqualTo(NOW.atOffset(KST));
    }

    @Test
    void 캐시에_있으면_공급자를_부르지_않고_캐시의_수집_시각을_돌려준다() {
        // given
        Instant fetchedAt = NOW.minusSeconds(300);
        cache.store.put(KR_STOCK_ID,
                new CachedSearch(new NewsSearchResult(List.of(article("삼성전자 실적", 1)), true), fetchedAt));

        // when
        StockNewsResult news = service.read(KR_STOCK_ID, null, null);

        // then
        assertThat(provider.queries).isEmpty();
        assertThat(news.fetchedAt()).isEqualTo(fetchedAt.atOffset(KST));
        assertThat(news.feed().items()).hasSize(1);
    }

    @Test
    void 같은_캐시_결과에_요청마다_기간과_관련성_필터를_적용한다() {
        // given
        cache.store.put(KR_STOCK_ID, new CachedSearch(new NewsSearchResult(List.of(
                article("삼성전자 오늘", 1),
                article("삼성전자 사흘 전", 72),
                article("SK하이닉스 오늘", 2)
        ), true), NOW));
        OffsetDateTime from = NOW.atOffset(KST).minusDays(1);
        OffsetDateTime to = NOW.atOffset(KST);

        // when
        StockNewsResult news = service.read(KR_STOCK_ID, from, to);

        // then
        assertThat(news.feed().items()).extracting(NewsArticle::title).containsExactly("삼성전자 오늘");
        assertThat(news.window().from()).isEqualTo(from);
    }

    @Test
    void 기간을_생략하면_종목_시장_현지_시각_기준_최근_7일이다() {
        // given
        provider.result = new NewsSearchResult(List.of(), true);

        // when
        StockNewsResult news = service.read(US_STOCK_ID, null, null);

        // then
        OffsetDateTime newYorkNow = OffsetDateTime.parse("2026-09-30T01:00:00-04:00");
        assertThat(news.country()).isEqualTo(Country.US);
        assertThat(news.window().to()).isEqualTo(newYorkNow);
        assertThat(news.window().from()).isEqualTo(newYorkNow.minusDays(7));
    }

    @Test
    void 현재_시각의_초_미만은_버려_기본_기간과_수집_시각에_나노초가_없다() {
        // given
        Instant nowWithNanos = NOW.plusNanos(335_206_399);
        StockNewsService serviceWithNanos = new StockNewsService(
                stockReader,
                cache, provider, Clock.fixed(nowWithNanos, ZoneOffset.UTC));

        // when
        StockNewsResult news = serviceWithNanos.read(KR_STOCK_ID, null, null);

        // then
        assertThat(news.window().to()).isEqualTo(NOW.atOffset(KST));
        assertThat(news.window().from()).isEqualTo(NOW.atOffset(KST).minusDays(7));
        assertThat(news.fetchedAt()).isEqualTo(NOW.atOffset(KST));
    }

    @Test
    void 관련_기사가_없으면_빈_목록이고_결과는_캐시한다() {
        // given
        provider.result = new NewsSearchResult(List.of(article("SK하이닉스 실적", 1)), true);

        // when
        StockNewsResult news = service.read(KR_STOCK_ID, null, null);

        // then
        assertThat(news.feed().items()).isEmpty();
        assertThat(news.feed().total()).isZero();
        assertThat(cache.store).containsKey(KR_STOCK_ID);
    }

    @Test
    void 등록되지_않은_종목이면_공급자를_부르지_않는다() {
        // when & then
        assertError(() -> service.read(MISSING_STOCK_ID, null, null), ErrorCode.STOCK_NOT_FOUND);
        assertThat(provider.queries).isEmpty();
    }

    @Test
    void 기간이_잘못되면_공급자를_부르지_않는다() {
        // given
        OffsetDateTime to = NOW.atOffset(KST);

        // when & then
        assertError(() -> service.read(KR_STOCK_ID, to, to), ErrorCode.INVALID_INPUT_VALUE);
        assertThat(provider.queries).isEmpty();
    }

    @Test
    void 공급자가_실패하면_오류를_그대로_전하고_캐시하지_않는다() {
        // given
        provider.failure = ErrorCode.NEWS_UNAVAILABLE;

        // when & then
        assertError(() -> service.read(KR_STOCK_ID, null, null), ErrorCode.NEWS_UNAVAILABLE);
        assertThat(cache.store).isEmpty();
    }

    @Test
    void 호출_한도에_걸리면_오류를_그대로_전하고_캐시하지_않는다() {
        // given
        provider.failure = ErrorCode.NEWS_QUOTA_EXCEEDED;

        // when & then
        assertError(() -> service.read(KR_STOCK_ID, null, null), ErrorCode.NEWS_QUOTA_EXCEEDED);
        assertThat(cache.store).isEmpty();
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    private static NewsArticle article(String title, int hoursAgo) {
        return NewsArticle.of(
                title, "요약", URI.create("https://a.com/" + title.hashCode()), LinkKind.ORIGINAL,
                NOW.atOffset(KST).minusHours(hoursAgo)
        ).orElseThrow();
    }

    private static final class FakeNewsCache implements NewsCache {

        private final Map<Long, CachedSearch> store = new HashMap<>();

        @Override
        public Optional<CachedSearch> find(Long stockId) {
            return Optional.ofNullable(store.get(stockId));
        }

        @Override
        public void put(Long stockId, CachedSearch search) {
            store.put(stockId, search);
        }
    }

    private static final class FakeNewsProvider implements NewsProvider {

        private final List<String> queries = new ArrayList<>();
        private NewsSearchResult result = new NewsSearchResult(List.of(), true);
        private ErrorCode failure;

        @Override
        public NewsSearchResult search(String query) {
            queries.add(query);
            if (failure != null) {
                throw new BusinessException(failure);
            }
            return result;
        }
    }
}
