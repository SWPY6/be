package com.swyp.ploutos.market.quote.kis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

import tools.jackson.databind.json.JsonMapper;

class KisIndicatorQuoteProviderTest {

    // 서울 2026-09-30 10:15:03, 뉴욕 2026-09-29 21:15:03(서머타임)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T01:15:03Z"), ZoneOffset.UTC);

    private FakeKisApiClient kisApiClient;
    private KisIndicatorQuoteProvider provider;

    @BeforeEach
    void setUp() {
        kisApiClient = new FakeKisApiClient();
        provider = new KisIndicatorQuoteProvider(kisApiClient, CLOCK);
    }

    @Test
    void 국내_지수_응답을_매핑하고_전일종가를_역산한다() {
        // given 전일 종가 필드가 없고 전일 대비가 부호와 함께 온다
        kisApiClient.enqueue(kospiBody());

        // when
        IndicatorQuote quote = provider.fetch(MarketIndicator.KOSPI);

        // then
        assertThat(quote.indicator()).isEqualTo(MarketIndicator.KOSPI);
        assertThat(quote.value()).isEqualByComparingTo("6870.81");
        assertThat(quote.previousClose()).isEqualByComparingTo("6889.74");
        assertThat(quote.open()).isEqualByComparingTo("6844.41");
        assertThat(quote.high()).isEqualByComparingTo("6898.36");
        assertThat(quote.low()).isEqualByComparingTo("6782.99");
        assertThat(quote.change()).isEqualByComparingTo("-18.93");
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisIndicatorQuoteProvider.DOMESTIC_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisIndicatorQuoteProvider.DOMESTIC_TR_ID);
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "U")
                .containsEntry("FID_INPUT_ISCD", "0001");
    }

    @Test
    void 해외_지수는_N으로_조회해_매핑한다() {
        // given
        kisApiClient.enqueue(sp500Body());

        // when
        IndicatorQuote quote = provider.fetch(MarketIndicator.SP500);

        // then
        assertThat(quote.value()).isEqualByComparingTo("7675.05");
        assertThat(quote.previousClose()).isEqualByComparingTo("7683.69");
        assertThat(quote.open()).isEqualByComparingTo("7699.60");
        assertThat(quote.high()).isEqualByComparingTo("7699.60");
        assertThat(quote.low()).isEqualByComparingTo("7653.55");
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisIndicatorQuoteProvider.OVERSEAS_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisIndicatorQuoteProvider.OVERSEAS_TR_ID);
        // 기간은 지표 타임존(뉴욕)의 오늘 하루다
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "N")
                .containsEntry("FID_INPUT_ISCD", "SPX")
                .containsEntry("FID_INPUT_DATE_1", "20260929")
                .containsEntry("FID_INPUT_DATE_2", "20260929")
                .containsEntry("FID_PERIOD_DIV_CODE", "D");
    }

    @Test
    void 환율은_X로_조회해_매핑한다() {
        // given
        kisApiClient.enqueue(usdKrwBody());

        // when
        IndicatorQuote quote = provider.fetch(MarketIndicator.USD_KRW);

        // then 소수 넷째 자리가 그대로 보존된다
        assertThat(quote.value()).isEqualTo(new BigDecimal("1354.0000"));
        assertThat(quote.previousClose()).isEqualTo(new BigDecimal("1359.9000"));
        assertThat(quote.open()).isEqualByComparingTo("1357.0000");
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "X")
                .containsEntry("FID_INPUT_ISCD", "FX@KRW")
                .containsEntry("FID_INPUT_DATE_1", "20260930")
                .containsEntry("FID_INPUT_DATE_2", "20260930");
    }

    @Test
    void 기준_시각은_받은_시각이고_지표_타임존을_따른다() {
        // given
        kisApiClient.enqueue(kospiBody());
        kisApiClient.enqueue(sp500Body());

        // when
        IndicatorQuote domestic = provider.fetch(MarketIndicator.KOSPI);
        IndicatorQuote overseas = provider.fetch(MarketIndicator.SP500);

        // then
        assertThat(domestic.valueAt()).isEqualTo(OffsetDateTime.parse("2026-09-30T10:15:03+09:00"));
        assertThat(overseas.valueAt()).isEqualTo(OffsetDateTime.parse("2026-09-29T21:15:03-04:00"));
    }

    private static String kospiBody() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                  "bstp_nmix_prpr":"6870.81","bstp_nmix_prdy_vrss":"-18.93","prdy_vrss_sign":"5",
                  "bstp_nmix_prdy_ctrt":"-0.27","acml_vol":"221683","bstp_nmix_oprc":"6844.41",
                  "bstp_nmix_hgpr":"6898.36","bstp_nmix_lwpr":"6782.99"}}
                """;
    }

    private static String sp500Body() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output1":{
                  "ovrs_nmix_prdy_vrss":"-8.64","prdy_vrss_sign":"5","prdy_ctrt":"-0.11",
                  "ovrs_nmix_prdy_clpr":"7683.69","acml_vol":"0","hts_kor_isnm":"S&P500",
                  "ovrs_nmix_prpr":"7675.05","stck_shrn_iscd":"SPX","ovrs_prod_oprc":"7699.60",
                  "ovrs_prod_hgpr":"7699.60","ovrs_prod_lwpr":"7653.55"},"output2":[]}
                """;
    }

    private static String usdKrwBody() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output1":{
                  "ovrs_nmix_prdy_vrss":"-5.9000","prdy_vrss_sign":"5","prdy_ctrt":"-0.43",
                  "ovrs_nmix_prdy_clpr":"1359.9000","acml_vol":"0","hts_kor_isnm":"원/달러(KMB)",
                  "ovrs_nmix_prpr":"1354.0000","stck_shrn_iscd":"FX@KRW","ovrs_prod_oprc":"1357.0000",
                  "ovrs_prod_hgpr":"1361.7000","ovrs_prod_lwpr":"1353.1000"},"output2":[]}
                """;
    }

    // 큐에 넣은 JSON을 순서대로 응답 타입으로 역직렬화해 돌려주고, 호출 내용을 기록하는 가짜 클라이언트
    private static final class FakeKisApiClient implements KisApiClient {

        record Call(String path, String trId, Map<String, String> params) {
        }

        private final JsonMapper mapper = JsonMapper.builder().build();
        private final Deque<String> responses = new ArrayDeque<>();
        private final List<Call> calls = new ArrayList<>();

        void enqueue(String json) {
            responses.add(json);
        }

        @Override
        public <T extends KisResponse> T get(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
            calls.add(new Call(path, trId, queryParams));
            String json = responses.poll();
            if (json == null) {
                throw new IllegalStateException("준비된 응답이 없습니다: " + path);
            }
            return mapper.readValue(json, responseType);
        }
    }
}
