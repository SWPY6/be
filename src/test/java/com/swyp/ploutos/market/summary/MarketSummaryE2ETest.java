package com.swyp.ploutos.market.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

/**
 * 시장 지표 카드를 HTTP 요청부터 응답까지 검증한다.
 * KIS만 스텁으로 바꾸고 Redis 캐시는 실제로 동작시킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MarketSummaryE2ETest extends IntegrationTestContainers {

    private static final String PATH = "/api/v1/markets/summary";
    private static final String DOMESTIC_INDEX_PATH = "/uapi/domestic-stock/v1/quotations/inquire-index-price";
    private static final String OVERSEAS_CHART_PATH = "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice";

    /**
     * 스텁 본문을 실측 응답 그대로 두려면 선언하지 않은 필드를 무시해야 한다.
     * 운영에서 응답을 읽는 매퍼도 같게 동작한다.
     */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @MockitoBean
    private KisApiClient kisApiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private AtomicInteger indicatorCalls;

    @BeforeEach
    void setUp() {
        clearCaches();
        indicatorCalls = new AtomicInteger();

        given(kisApiClient.get(any(), any(), any(), any())).willAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Map<String, String> params = invocation.getArgument(2);
            Class<?> responseType = invocation.getArgument(3);
            return OBJECT_MAPPER.readValue(bodyFor(path, params.get("FID_INPUT_ISCD")), responseType);
        });
    }

    @Test
    void 국내_탭_요청은_지표_카드_3개를_돌려준다() throws Exception {
        // given 위에서 KIS 스텁을 준비했고 캐시는 비어 있다

        // when & then 스텁 응답에서 계산된 값이 표시 순서대로 나온다
        mockMvc.perform(get(PATH).param("region", "DOMESTIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value("DOMESTIC"))
                .andExpect(jsonPath("$.data.indicators.length()").value(3))
                .andExpect(jsonPath("$.data.indicators[0].indicator").value("KOSPI"))
                .andExpect(jsonPath("$.data.indicators[0].name").value("코스피"))
                .andExpect(jsonPath("$.data.indicators[0].unit").value("POINT"))
                .andExpect(jsonPath("$.data.indicators[0].value").value(6870.81))
                .andExpect(jsonPath("$.data.indicators[0].change").value(-18.93))
                .andExpect(jsonPath("$.data.indicators[0].changeRate").value(-0.27))
                .andExpect(jsonPath("$.data.indicators[0].valueAt").isString())
                .andExpect(jsonPath("$.data.indicators[1].indicator").value("KOSDAQ"))
                .andExpect(jsonPath("$.data.indicators[1].name").value("코스닥"))
                .andExpect(jsonPath("$.data.indicators[1].value").value(849.80))
                .andExpect(jsonPath("$.data.indicators[1].change").value(3.22))
                .andExpect(jsonPath("$.data.indicators[1].changeRate").value(0.38))
                .andExpect(jsonPath("$.data.indicators[2].indicator").value("USD_KRW"))
                .andExpect(jsonPath("$.data.indicators[2].name").value("원/달러 환율"))
                .andExpect(jsonPath("$.data.indicators[2].unit").value("KRW"))
                .andExpect(jsonPath("$.data.indicators[2].value").value(1354.00))
                .andExpect(jsonPath("$.data.indicators[2].change").value(-5.90))
                .andExpect(jsonPath("$.data.indicators[2].changeRate").value(-0.43));

        // 지표마다 한 번씩 부른다
        assertThat(indicatorCalls.get()).isEqualTo(3);
    }

    @Test
    void 같은_탭을_다시_요청하면_캐시로_답해_외부를_호출하지_않는다() throws Exception {
        // given 첫 요청으로 캐시를 채운다
        mockMvc.perform(get(PATH).param("region", "DOMESTIC")).andExpect(status().isOk());
        int callsAfterFirst = indicatorCalls.get();
        assertThat(callsAfterFirst).isEqualTo(3);

        // when 같은 탭을 다시 요청한다
        mockMvc.perform(get(PATH).param("region", "DOMESTIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicators.length()").value(3));

        // then 캐시된 값으로 답해 외부를 다시 부르지 않는다
        assertThat(indicatorCalls.get()).isEqualTo(callsAfterFirst);
    }

    /**
     * 컨테이너를 테스트 클래스 사이에 공유하므로 남은 값을 지운다.
     * 지표 캐시가 남아 있으면 첫 요청이 KIS를 부르지 않고, 활성 종목이 남아 있으면
     * 현재가 갱신 스케줄러가 끼어들어 호출 수가 흔들린다.
     */
    private void clearCaches() {
        List<String> keys = new ArrayList<>(Arrays.stream(MarketIndicator.values())
                .map(indicator -> "market-quote:" + indicator.name())
                .toList());
        keys.add("quote:active");
        redisTemplate.delete(keys);
    }

    /** 시장 지표 경로만 센다. 스케줄러가 주식 시세 경로로 끼어들어도 호출 수가 흔들리지 않는다. */
    private String bodyFor(String path, String symbol) {
        if (DOMESTIC_INDEX_PATH.equals(path)) {
            indicatorCalls.incrementAndGet();
            return "1001".equals(symbol) ? kosdaqJson() : kospiJson();
        }
        if (OVERSEAS_CHART_PATH.equals(path)) {
            indicatorCalls.incrementAndGet();
            return exchangeRateJson();
        }
        return stockPriceJson();
    }

    private static String kospiJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "bstp_nmix_prpr":"6870.81","bstp_nmix_prdy_vrss":"-18.93","prdy_vrss_sign":"5",
                  "bstp_nmix_oprc":"6844.41","bstp_nmix_hgpr":"6898.36","bstp_nmix_lwpr":"6782.99"}}
                """;
    }

    private static String kosdaqJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "bstp_nmix_prpr":"849.80","bstp_nmix_prdy_vrss":"3.22","prdy_vrss_sign":"2",
                  "bstp_nmix_oprc":"843.87","bstp_nmix_hgpr":"849.82","bstp_nmix_lwpr":"833.62"}}
                """;
    }

    private static String exchangeRateJson() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output1":{
                  "ovrs_nmix_prpr":"1354.0000","ovrs_nmix_prdy_clpr":"1359.9000",
                  "ovrs_prod_oprc":"1357.0000","ovrs_prod_hgpr":"1361.7000",
                  "ovrs_prod_lwpr":"1353.1000"},"output2":[]}
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
