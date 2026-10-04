package com.swyp.ploutos.market.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swyp.ploutos.IntegrationTestContainers;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.MarketDailyPrices;
import com.swyp.ploutos.market.price.repository.MarketDailyPriceRepository;
import com.swyp.ploutos.stock.price.DailyPrice;

/**
 * 지표 차트를 HTTP 요청부터 응답까지 검증한다. KIS만 스텁으로 바꾸고 MySQL과 Redis는 실제로 동작시킨다.
 *
 * <p>운영 {@code Clock}이 실제 시각이라 날짜를 고정할 수 없다. 그래서 스텁 일봉을 "오늘" 기준
 * 상대 날짜로 만든다. 거래일 여부는 코드가 따지지 않으므로 주말에 돌려도 결과가 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MarketChartE2ETest extends IntegrationTestContainers {

    private static final String PATH = "/api/v1/markets/indicators/{indicator}/chart";
    private static final String QUOTE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-index-price";
    private static final String DAILY_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice";
    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 스텁 본문을 실측 응답 그대로 두려면 선언하지 않은 필드를 무시해야 한다. */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @MockitoBean
    private KisApiClient kisApiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MarketDailyPriceRepository repository;

    private AtomicInteger dailyCalls;

    @BeforeEach
    void setUp() {
        clearCaches();
        repository.deleteAll();
        dailyCalls = new AtomicInteger();

        given(kisApiClient.get(any(), any(), any(), any())).willAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Map<String, String> params = invocation.getArgument(2);
            Class<?> responseType = invocation.getArgument(3);
            return OBJECT_MAPPER.readValue(bodyFor(path, params.get("FID_INPUT_ISCD")), responseType);
        });
    }

    @Test
    void 코스피_차트는_확정봉과_진행중인_봉을_돌려준다() throws Exception {
        // given 저장된 일봉이 없다. 스텁은 어제까지 확정 봉 3개를 주고,
        // 현재값의 전일 종가는 마지막 확정 봉 종가와 같아 오늘 봉이 이어진다
        LocalDate today = today(MarketIndicator.KOSPI);

        // when
        mockMvc.perform(get(PATH, "KOSPI").param("from", today.minusDays(3).toString()))
                // then 확정 봉 3개 뒤에 진행 중인 봉이 붙는다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicator").value("KOSPI"))
                .andExpect(jsonPath("$.data.name").value("코스피"))
                .andExpect(jsonPath("$.data.unit").value("POINT"))
                .andExpect(jsonPath("$.data.interval").value("1D"))
                .andExpect(jsonPath("$.data.from").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.data.to").value(today.toString()))
                .andExpect(jsonPath("$.data.asOf", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.candles.length()").value(4))
                .andExpect(jsonPath("$.data.candles[0].tradeAt").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.data.candles[0].close").value(6889.74))
                .andExpect(jsonPath("$.data.candles[0].closed").value(true))
                .andExpect(jsonPath("$.data.candles[2].tradeAt").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.candles[2].close").value(6870.81))
                .andExpect(jsonPath("$.data.candles[2].closed").value(true))
                .andExpect(jsonPath("$.data.candles[3].tradeAt").value(today.toString()))
                .andExpect(jsonPath("$.data.candles[3].open").value(6875.20))
                .andExpect(jsonPath("$.data.candles[3].high").value(6910.45))
                .andExpect(jsonPath("$.data.candles[3].low").value(6861.02))
                .andExpect(jsonPath("$.data.candles[3].close").value(6902.33))
                .andExpect(jsonPath("$.data.candles[3].closed").value(false))
                .andExpect(jsonPath("$.data.candles[3].volume").doesNotExist());

        // 확정 봉만 저장된다. 진행 중인 봉은 저장하지 않는다
        List<MarketDailyPrices> stored = repository.findByIndicatorAndTradeAtBetweenOrderByTradeAtAsc(
                MarketIndicator.KOSPI, today.minusDays(10), today);
        assertThat(stored.stream().map(MarketDailyPrices::toDailyPrice).map(DailyPrice::tradeAt))
                .containsExactly(today.minusDays(3), today.minusDays(2), today.minusDays(1));
        assertThat(dailyCalls.get()).isEqualTo(1);
    }

    @Test
    void 같은_지표를_다시_요청하면_일봉을_다시_받지_않는다() throws Exception {
        // given 첫 요청으로 일봉을 저장한다. 코스피와 정책 상태가 섞이지 않도록 코스닥을 쓴다
        LocalDate today = today(MarketIndicator.KOSDAQ);
        String from = today.minusDays(3).toString();
        String first = mockMvc.perform(get(PATH, "KOSDAQ").param("from", from))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dailyCalls.get()).isEqualTo(1);

        // when 같은 구간을 다시 요청한다
        String second = mockMvc.perform(get(PATH, "KOSDAQ").param("from", from))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then 저장된 봉이 구간을 덮으므로 외부 일봉 API를 다시 부르지 않는다
        assertThat(dailyCalls.get()).isEqualTo(1);
        // DB와 캐시를 왕복한 값이 그대로 돌아온다. 자릿수나 오프셋이 깨지면 여기서 드러난다
        assertThat(second).isEqualTo(first);
    }

    private static LocalDate today(MarketIndicator indicator) {
        return LocalDate.now(indicator.zoneId());
    }

    /**
     * 컨테이너를 테스트 클래스 사이에 공유하므로 남은 값을 지운다.
     * 현재값 캐시가 남아 있으면 첫 요청이 KIS를 부르지 않고, 락이 남아 있으면 첫 요청이 기다린 뒤 502가 된다.
     * 활성 종목이 남아 있으면 현재가 갱신 스케줄러가 끼어들어 호출 수가 흔들린다.
     */
    private void clearCaches() {
        List<String> keys = new ArrayList<>(Arrays.stream(MarketIndicator.values())
                .flatMap(indicator -> Stream.of(
                        "market-quote:" + indicator.name(),
                        "market-quote:lock:" + indicator.name()))
                .toList());
        keys.add("quote:active");
        redisTemplate.delete(keys);
    }

    /** 지표 일봉 경로만 센다. 스케줄러가 주식 시세 경로로 끼어들어도 호출 수가 흔들리지 않는다. */
    private String bodyFor(String path, String symbol) {
        if (DAILY_PATH.equals(path)) {
            dailyCalls.incrementAndGet();
            return dailyJson(today(indicatorOf(symbol)));
        }
        if (QUOTE_PATH.equals(path)) {
            return quoteJson();
        }
        return stockPriceJson();
    }

    private static MarketIndicator indicatorOf(String symbol) {
        return "1001".equals(symbol) ? MarketIndicator.KOSDAQ : MarketIndicator.KOSPI;
    }

    /**
     * 어제까지의 확정 봉 3개. 마지막 봉 종가 6870.81이 현재값의 전일 종가와 같아야
     * 오늘 봉이 이어진 것으로 판정된다.
     */
    private static String dailyJson(LocalDate today) {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[
                  {"stck_bsop_date":"%s","bstp_nmix_oprc":"6800.00","bstp_nmix_hgpr":"6898.36",
                   "bstp_nmix_lwpr":"6782.99","bstp_nmix_prpr":"6870.81"},
                  {"stck_bsop_date":"%s","bstp_nmix_oprc":"6889.74","bstp_nmix_hgpr":"6898.36",
                   "bstp_nmix_lwpr":"6782.99","bstp_nmix_prpr":"6800.00"},
                  {"stck_bsop_date":"%s","bstp_nmix_oprc":"7000.00","bstp_nmix_hgpr":"7065.90",
                   "bstp_nmix_lwpr":"6889.68","bstp_nmix_prpr":"6889.74"}]}
                """.formatted(
                today.minusDays(1).format(KIS_DATE),
                today.minusDays(2).format(KIS_DATE),
                today.minusDays(3).format(KIS_DATE));
    }

    /** 장중 현재값. 전일 대비 31.52이므로 전일 종가는 6870.81이다. */
    private static String quoteJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "bstp_nmix_prpr":"6902.33","bstp_nmix_prdy_vrss":"31.52","prdy_vrss_sign":"2",
                  "bstp_nmix_oprc":"6875.20","bstp_nmix_hgpr":"6910.45","bstp_nmix_lwpr":"6861.02"}}
                """;
    }

    /** 현재가 갱신 스케줄러가 남은 활성 종목을 갱신할 때만 쓰인다. */
    private static String stockPriceJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"244280",
                  "stck_hgpr":"251224","stck_lwpr":"241056","acml_vol":"245000",
                  "acml_tr_pbmn":"60800000000","hts_avls":"866000"}}
                """;
    }
}
