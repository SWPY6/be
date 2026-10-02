package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.repository.StockRepository;

/**
 * 실제 DB(MySQL)·Redis 컨테이너와 로컬 DART·SEC 스텁 서버로 HTTP 요청부터 응답까지 검증한다.
 * 스텁은 실제 형식대로 DART 고유번호는 ZIP, 공시검색·SEC 티커·submissions는 JSON으로 준다. 실제 키·운영 호출은 쓰지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class StockDisclosureIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String TEST_API_KEY = "test-dart-api-key";

    private static final String CORP_CODES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <result>
              <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code></list>
              <list><corp_code>00434003</corp_code><corp_name>비상장</corp_name><stock_code> </stock_code></list>
            </result>
            """;

    // 기본 기간(최근 30일) 안에 들도록 실행 시점의 한국 날짜 기준으로 만든다.
    private static final String RECENT_KST = LocalDate.now(Country.KR.zoneId()).minusDays(1)
            .format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String OLDER_KST = LocalDate.now(Country.KR.zoneId()).minusDays(10)
            .format(DateTimeFormatter.BASIC_ISO_DATE);

    private static final String LIST = """
            {
              "status": "000", "message": "정상", "page_no": 1, "page_count": 100, "total_count": 2, "total_page": 1,
              "list": [
                {"corp_name": "삼성전자", "corp_code": "00126380", "stock_code": "005930",
                 "report_nm": "분기보고서 (2026.06)", "rcept_no": "%s000001", "flr_nm": "삼성전자",
                 "rcept_dt": "%s", "rm": ""},
                {"corp_name": "삼성전자", "corp_code": "00126380", "stock_code": "005930",
                 "report_nm": "[기재정정]주요사항보고서(자기주식취득결정)", "rcept_no": "%s000123",
                 "flr_nm": "삼성전자", "rcept_dt": "%s", "rm": "유"}
              ]
            }
            """.formatted(OLDER_KST, OLDER_KST, RECENT_KST, RECENT_KST);

    private static final String TEST_USER_AGENT = "Ploutos-test test@example.com";

    private static final String TICKERS = """
            {"fields":["cik","name","ticker","exchange"],"data":[[320193,"Apple Inc.","AAPL","Nasdaq"]]}
            """;

    private static final List<String> stubRequests = new CopyOnWriteArrayList<>();
    private static final List<String> secUserAgents = new CopyOnWriteArrayList<>();
    private static final HttpServer stub = startStub();

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
        registry.add("ploutos.dart.base-url", () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("ploutos.dart.api-key", () -> TEST_API_KEY);
        registry.add("ploutos.sec.data-base-url", () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("ploutos.sec.www-base-url", () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("ploutos.sec.user-agent", () -> TEST_USER_AGENT);
    }

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @BeforeEach
    void clearState() {
        redisTemplate.delete(redisTemplate.keys("disclosure:*"));
        stubRequests.clear();
        secUserAgents.clear();
    }

    @Test
    void 국내_종목은_법인_코드를_받아_DART_공시를_접수일_최신순으로_반환하고_두_번째_요청은_캐시를_쓴다() throws Exception {
        // given
        Markets kospi = marketRepository.save(new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        Stocks samsung = stockRepository.save(stock(kospi, "005930", "삼성전자", Exchange.KRX));

        // when & then
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/stocks/{stockId}/disclosures", samsung.stockId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.market").value("KR"))
                    .andExpect(jsonPath("$.data.source").value("DART"))
                    .andExpect(jsonPath("$.data.windowPrecision").value("DATE_EXPANDED"))
                    .andExpect(jsonPath("$.data.coverage").value("COMPLETE"))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.items[0].providerDocumentId").value(RECENT_KST + "000123"))
                    .andExpect(jsonPath("$.data.items[0].filedDate")
                            .value(LocalDate.parse(RECENT_KST, DateTimeFormatter.BASIC_ISO_DATE).toString()))
                    .andExpect(jsonPath("$.data.items[0].remark").value("유"))
                    .andExpect(jsonPath("$.data.items[1].providerDocumentId").value(OLDER_KST + "000001"))
                    .andExpect(jsonPath("$.data.items[1].remark").value(nullValue()));
        }
        assertThat(stubRequests).hasSize(2);
        assertThat(stubRequests.get(0)).startsWith("/api/corpCode.xml?").contains("crtfc_key=" + TEST_API_KEY);
        assertThat(stubRequests.get(1)).startsWith("/api/list.json?")
                .contains("corp_code=00126380")
                .contains("last_reprt_at=N")
                .contains("crtfc_key=" + TEST_API_KEY);
        assertThat(redisTemplate.opsForValue().get("disclosure:dart:calls:" + todayKst())).isEqualTo("2");
    }

    @Test
    void 미국_종목은_CIK를_받아_SEC_공시를_접수_시각과_한글_라벨로_반환하고_DART는_부르지_않는다() throws Exception {
        // given
        Markets nasdaq = marketRepository.save(new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD));
        Stocks apple = stockRepository.save(stock(nasdaq, "AAPL", "애플", Exchange.NASDAQ));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/disclosures", apple.stockId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.market").value("US"))
                .andExpect(jsonPath("$.data.source").value("SEC"))
                .andExpect(jsonPath("$.data.windowPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.coverage").value("COMPLETE"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].provider").value("SEC"))
                .andExpect(jsonPath("$.data.items[0].formType").value("10-Q"))
                .andExpect(jsonPath("$.data.items[0].formLabel").value("분기보고서"))
                .andExpect(jsonPath("$.data.items[0].datePrecision").value("SECOND"))
                .andExpect(jsonPath("$.data.items[0].url").value(Matchers.startsWith(
                        "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/")));
        assertThat(stubRequests).containsExactly(
                "/files/company_tickers_exchange.json", "/submissions/CIK0000320193.json"
        );
        assertThat(secUserAgents).containsOnly(TEST_USER_AGENT);
    }

    @Test
    void 법인_코드가_없는_국내_종목은_공시_목록을_부르지_않고_미매핑이다() throws Exception {
        // given
        Markets kospi = marketRepository.save(new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        Stocks etf = stockRepository.save(stock(kospi, "069500", "KODEX 200", Exchange.KRX));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/disclosures", etf.stockId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage").value("UNMAPPED"))
                .andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(stubRequests).hasSize(1).allMatch(request -> request.startsWith("/api/corpCode.xml"));
    }

    @Test
    void DB에_없는_종목이면_DART를_부르지_않고_404와_P002를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/disclosures", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
        assertThat(stubRequests).isEmpty();
    }

    @Test
    void Swagger_문서에_공시_경로와_오류_응답이_올라간다() throws Exception {
        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/disclosures'].get.summary").value("종목 공시 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/disclosures'].get.responses['502']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/{stockId}/disclosures'].get.responses['503']").exists());
    }

    private static String todayKst() {
        return LocalDate.now(Country.KR.zoneId()).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static Stocks stock(Markets market, String ticker, String name, Exchange exchange) {
        return new Stocks(market.marketId(), ticker, name, null, StockStatus.ACTIVE, exchange, 1_000L, "대표",
                LocalDate.of(2000, 1, 1));
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/corpCode.xml", exchange -> respond(exchange, "application/zip", zip(CORP_CODES)));
            server.createContext("/api/list.json", exchange ->
                    respond(exchange, "application/json", LIST.getBytes(StandardCharsets.UTF_8)));
            server.createContext("/files/company_tickers_exchange.json", exchange ->
                    respondSec(exchange, TICKERS));
            server.createContext("/submissions/", exchange -> respondSec(exchange, submissions()));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("공급자 스텁 서버를 띄우지 못했다.", e);
        }
    }

    private static void respondSec(HttpExchange exchange, String json) throws IOException {
        secUserAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        respond(exchange, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    /** 기본 기간(최근 30일) 안에 접수 시각이 들도록 요청 시점 기준 하루 전 10-Q 한 건과 기간 밖 한 건을 준다. */
    private static String submissions() {
        Instant recent = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        Instant old = Instant.now().minus(Duration.ofDays(400)).truncatedTo(ChronoUnit.SECONDS);
        String recentDate = recent.atZone(Country.US.zoneId()).toLocalDate().toString();
        String oldDate = old.atZone(Country.US.zoneId()).toLocalDate().toString();
        return """
                {"cik":"320193","name":"Apple Inc.","filings":{"recent":{
                  "accessionNumber":["0000320193-26-000001","0000320193-25-000001"],
                  "filingDate":["%s","%s"],
                  "acceptanceDateTime":["%s","%s"],
                  "form":["10-Q","10-K"],
                  "primaryDocument":["aapl-q.htm","aapl-k.htm"],
                  "primaryDocDescription":["10-Q","10-K"]
                },"files":[]}}
                """.formatted(recentDate, oldDate, recent, old);
    }

    private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        stubRequests.add(exchange.getRequestURI().toString());
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] zip(String xml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
