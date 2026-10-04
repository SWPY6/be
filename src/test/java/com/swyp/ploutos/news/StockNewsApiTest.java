package com.swyp.ploutos.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.news.NewsArticle.LinkKind;
import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;
import com.swyp.ploutos.news.service.NewsProvider;
import com.swyp.ploutos.news.service.NewsSearchResult;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.repository.StockRepository;

/**
 * 실제 DB(Testcontainers MySQL)의 종목과 실제 Redis 캐시로 HTTP 요청부터 응답까지 검증한다.
 * 네이버 공급자만 목으로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class StockNewsApiTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    @MockitoBean
    private NewsProvider newsProvider;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 저장된_종목의_관련_뉴스를_반환하고_두_번째_요청은_캐시로_응답한다() throws Exception {
        // given
        Stocks samsung = saveSamsung();
        OffsetDateTime publishedAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusHours(1).withNano(0);
        NewsArticle related = NewsArticle.of(
                "삼성전자 HBM 증설", "요약", URI.create("https://news.mt.co.kr/mtview.php?no=1"), LinkKind.ORIGINAL, publishedAt
        ).orElseThrow();
        NewsArticle unrelated = NewsArticle.of(
                "SK하이닉스 실적", "요약", URI.create("https://www.hankyung.com/article/1"), LinkKind.ORIGINAL, publishedAt
        ).orElseThrow();
        given(newsProvider.search("삼성전자")).willReturn(new NewsSearchResult(List.of(related, unrelated), true));

        // when & then
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/stocks/{stockId}/news", samsung.stockId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stockId").value(samsung.stockId()))
                    .andExpect(jsonPath("$.data.country").value("KR"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].title").value("삼성전자 HBM 증설"))
                    .andExpect(jsonPath("$.data.items[0].publisherName").value("머니투데이"))
                    .andExpect(jsonPath("$.data.items[0].publishedAt").value(publishedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        }
        then(newsProvider).should(times(1)).search("삼성전자");
        assertThat(redisTemplate.hasKey("news:v1:" + samsung.stockId())).isTrue();
    }

    @Test
    void 공급자가_실패하면_502를_반환하고_다음_요청에서_다시_조회한다() throws Exception {
        // given
        Stocks samsung = saveSamsung();
        given(newsProvider.search(anyString())).willThrow(new BusinessException(ErrorCode.NEWS_UNAVAILABLE));

        // when & then
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/stocks/{stockId}/news", samsung.stockId()))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("P008"));
        }
        then(newsProvider).should(times(2)).search("삼성전자");
    }

    @Test
    void DB에_없는_종목이면_404와_P002를_반환하고_공급자를_부르지_않는다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/news", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
        then(newsProvider).shouldHaveNoInteractions();
    }

    @Test
    void Swagger_문서에_뉴스_API와_오류_응답이_있다() throws Exception {
        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/news'].get.summary").value("종목 관련 뉴스 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/news'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/news'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/news'].get.responses['502']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/news'].get.responses['503']").exists());
    }

    private Stocks saveSamsung() {
        Markets kospi = marketRepository.save(new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        return stockRepository.save(new Stocks(kospi.marketId(), "005930", "삼성전자", null, StockStatus.ACTIVE,
                Exchange.KRX, 1L, "대표", LocalDate.of(1975, 6, 11)));
    }
}
