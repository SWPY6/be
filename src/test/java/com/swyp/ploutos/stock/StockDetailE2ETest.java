package com.swyp.ploutos.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.swyp.ploutos.IntegrationTestContainers;
import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;
import com.swyp.ploutos.stock.price.repository.StockDailyPriceRepository;
import com.swyp.ploutos.stock.repository.StockRepository;

/**
 * 종목 상세의 현재가·차트를 HTTP 요청부터 응답까지 검증한다.
 * KIS만 스텁으로 바꾸고 DB·Redis·캐시는 실제로 동작시킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StockDetailE2ETest extends IntegrationTestContainers {

    private static final String DAILY_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

    /**
     * 스텁이 돌려줄 일봉 개수. 월봉 집계를 보려면 여러 달에 걸쳐야 하고,
     * {@code KisDailyPriceProvider.PAGE_SIZE}(100) 미만이어야 페이징이 한 번으로 끝난다.
     */
    private static final int STUB_DAILY_PRICE_DAYS = 70;

    /** KIS 응답 DTO는 @JsonProperty만 쓰므로 기본 설정으로 충분하다. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @MockitoBean
    private KisApiClient kisApiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDailyPriceRepository stockDailyPriceRepository;

    private Long stockId;
    private AtomicInteger dailyPriceCalls;

    @BeforeEach
    void setUp() {
        Markets market = marketRepository.save(
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        stockId = stockRepository.save(new Stocks(
                market.marketId(), "005380", "현대차", null, StockStatus.ACTIVE, Exchange.KRX,
                200_000_000L, "대표", LocalDate.of(1974, 6, 28))).stockId();
        dailyPriceCalls = new AtomicInteger();

        given(kisApiClient.get(any(), any(), any(), any())).willAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Class<?> responseType = invocation.getArgument(3);
            if (DAILY_PRICE_PATH.equals(path)) {
                dailyPriceCalls.incrementAndGet();
                return OBJECT_MAPPER.readValue(dailyPriceJson(), responseType);
            }
            return OBJECT_MAPPER.readValue(priceJson(), responseType);
        });
    }

    @Test
    void 현재가를_조회하면_지표와_함께_응답한다() throws Exception {
        // given 위에서 종목과 KIS 스텁을 준비했다

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", stockId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId))
                .andExpect(jsonPath("$.data.ticker").value("005380"))
                .andExpect(jsonPath("$.data.name").value("현대차"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.price").value(248000))
                .andExpect(jsonPath("$.data.change").value(7783))
                .andExpect(jsonPath("$.data.changeRate").value(3.24))
                .andExpect(jsonPath("$.data.priceTiming").value("REALTIME"))
                .andExpect(jsonPath("$.data.indicators.previousClose").value(240217))
                .andExpect(jsonPath("$.data.indicators.open").value(244280))
                .andExpect(jsonPath("$.data.indicators.high").value(251224))
                .andExpect(jsonPath("$.data.indicators.low").value(241056))
                .andExpect(jsonPath("$.data.indicators.volume").value(245000))
                .andExpect(jsonPath("$.data.indicators.marketCap").value(86600000000000L))
                .andExpect(jsonPath("$.data.indicators.tradingValue").value(60800000000L));
    }

    @Test
    void 차트를_조회하면_일봉이_저장되고_재조회는_외부를_호출하지_않는다() throws Exception {
        // given 스텁이 덮는 구간(약 3개월) 안쪽으로 요청해야 저장된 봉이 구간을 실제로 덮는다
        assertThat(stockDailyPriceRepository.findLatestTradeAt(stockId)).isEmpty();
        String from = today().minusMonths(2).toString();
        String to = today().toString();

        // when 첫 조회
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", stockId).param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId))
                .andExpect(jsonPath("$.data.interval").value("1D"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.candles[0].closed").value(true))
                .andExpect(jsonPath("$.data.averageVolume").isNumber())
                .andExpect(jsonPath("$.data.to").value(today().toString()))
                .andExpect(jsonPath("$.data.asOf").isNotEmpty());

        // then 일봉이 DB에 남고 외부는 한 번만 불렸다
        assertThat(stockDailyPriceRepository.findLatestTradeAt(stockId)).isPresent();
        int callsAfterFirst = dailyPriceCalls.get();
        assertThat(callsAfterFirst).isEqualTo(1);

        // when 같은 구간을 다시 조회
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", stockId).param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candles").isNotEmpty())
                .andExpect(jsonPath("$.data.to").value(today().toString()))
                .andExpect(jsonPath("$.data.asOf").isNotEmpty());

        // then 저장된 일봉으로 답해 외부를 다시 부르지 않는다
        assertThat(dailyPriceCalls.get()).isEqualTo(callsAfterFirst);
    }

    @Test
    void 월봉으로_조회하면_일봉이_달_단위로_묶인다() throws Exception {
        // given 같은 구간을 일봉으로 먼저 받아 둔다
        String from = today().minusMonths(2).toString();
        String to = today().toString();
        String daily = mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", stockId)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int dailyCandles = JsonPath.read(daily, "$.data.candles.length()");

        // when 같은 구간을 월봉으로 조회한다
        String monthly = mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", stockId)
                        .param("from", from).param("to", to).param("interval", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1M"))
                .andReturn().getResponse().getContentAsString();

        // then 봉 수가 줄고, 한 봉의 거래량은 일봉 하나(100000)의 합이라 그보다 크다
        int monthlyCandles = JsonPath.read(monthly, "$.data.candles.length()");
        int firstVolume = JsonPath.read(monthly, "$.data.candles[0].volume");
        assertThat(monthlyCandles).isLessThan(dailyCandles);
        assertThat(monthlyCandles).isGreaterThan(1);
        assertThat(firstVolume).isGreaterThan(100_000);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private static String priceJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"244280",
                  "stck_hgpr":"251224","stck_lwpr":"241056","acml_vol":"245000",
                  "acml_tr_pbmn":"60800000000","hts_avls":"866000"}}
                """;
    }

    /**
     * 어제부터 거슬러 올라간 거래일(주말 제외) 일봉. KIS와 같이 최신순이다.
     *
     * <p>종가는 현재가 스텁({@link #priceJson()})의 {@code stck_sdpr}과 같아야 한다. 다르면
     * 시세가 이미 확정된 거래일의 것으로 판정되어 진행 중인 봉이 붙지 않고, 이 테스트가
     * 확정 봉 경로만 태우게 된다.
     */
    private static String dailyPriceJson() {
        String rows = tradingDaysBeforeToday().stream()
                .map(date -> """
                        {"stck_bsop_date":"%s","stck_oprc":"238500","stck_hgpr":"242900",\
                        "stck_lwpr":"237100","stck_clpr":"240217","acml_vol":"100000"}\
                        """.formatted(date.toString().replace("-", "")))
                .collect(Collectors.joining(","));
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[%s]}
                """.formatted(rows);
    }

    private static List<LocalDate> tradingDaysBeforeToday() {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        List<LocalDate> days = new ArrayList<>();
        while (days.size() < STUB_DAILY_PRICE_DAYS) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(day);
            }
            day = day.minusDays(1);
        }
        return days;
    }
}
