package com.swyp.ploutos.stock.summary;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.quote.service.QuoteProvider;
import com.swyp.ploutos.stock.repository.StockRepository;

/**
 * 실제 DB(Testcontainers MySQL)의 종목·시장으로 HTTP 요청부터 응답까지 검증한다.
 * 시세 제공자는 목으로 바꿔 기본정보 조회가 시세를 부르지 않는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class StockSummaryIntegrationTest {

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
    private QuoteProvider quoteProvider;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 저장된_국내_종목의_기본정보를_시세_호출_없이_반환한다() throws Exception {
        // given
        Markets kospi = marketRepository.save(new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        Stocks samsung = stockRepository.save(stock(kospi, "005930", "삼성전자", "https://logo/005930.png", Exchange.KRX));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", samsung.stockId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(samsung.stockId()))
                .andExpect(jsonPath("$.data.profile.name").value("삼성전자"))
                .andExpect(jsonPath("$.data.profile.ticker").value("005930"))
                .andExpect(jsonPath("$.data.profile.logoUrl").value("https://logo/005930.png"))
                .andExpect(jsonPath("$.data.country").value("KR"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
        then(quoteProvider).shouldHaveNoInteractions();
    }

    @Test
    void 저장된_미국_종목은_뉴욕_시간대로_반환한다() throws Exception {
        // given
        Markets nasdaq = marketRepository.save(new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD));
        Stocks apple = stockRepository.save(stock(nasdaq, "AAPL", "애플", null, Exchange.NASDAQ));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", apple.stockId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.ticker").value("AAPL"))
                .andExpect(jsonPath("$.data.profile.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.country").value("US"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"));
    }

    @Test
    void DB에_없는_종목이면_404와_P002를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void Swagger_문서에_기본정보_API와_오류_응답이_있다() throws Exception {
        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}'].get.summary").value("종목 기본정보 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}'].get.responses['404']").exists());
    }

    private static Stocks stock(Markets market, String ticker, String name, String imgUrl, Exchange exchange) {
        return new Stocks(market.marketId(), ticker, name, imgUrl, StockStatus.ACTIVE, exchange, 1L, "대표",
                LocalDate.of(2000, 1, 1));
    }
}
