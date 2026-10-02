package com.swyp.ploutos.market.price.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrice;

import tools.jackson.databind.json.JsonMapper;

class KisIndicatorDailyPriceProviderTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private FakeKisApiClient kisApiClient;
    private KisIndicatorDailyPriceProvider provider;

    @BeforeEach
    void setUp() {
        kisApiClient = new FakeKisApiClient();
        provider = new KisIndicatorDailyPriceProvider(kisApiClient);
    }

    @Test
    void 국내_지수_응답을_일봉으로_매핑한다() {
        // given 2026-09-30 실측 응답이다. 확정 행에서는 현재값이 그날 종가다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output1":{"prdy_nmix":"6889.74"},
                 "output2":[
                   {"stck_bsop_date":"20260929","bstp_nmix_prpr":"6870.81","bstp_nmix_oprc":"6844.41",
                    "bstp_nmix_hgpr":"6898.36","bstp_nmix_lwpr":"6782.99","acml_vol":"221683","mod_yn":"N"},
                   {"stck_bsop_date":"20260928","bstp_nmix_prpr":"6889.74","bstp_nmix_oprc":"7057.86",
                    "bstp_nmix_hgpr":"7065.90","bstp_nmix_lwpr":"6889.68","acml_vol":"230005","mod_yn":"N"}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.KOSPI, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29));

        // then 거래량은 0이다
        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst()).isEqualTo(new DailyPrice(
                LocalDate.of(2026, 9, 29), new BigDecimal("6844.41"), new BigDecimal("6898.36"),
                new BigDecimal("6782.99"), new BigDecimal("6870.81"), 0L));
        Map<String, String> params = kisApiClient.calls.getFirst().params();
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisIndicatorDailyPriceProvider.DOMESTIC_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisIndicatorDailyPriceProvider.DOMESTIC_TR_ID);
        assertThat(params)
                .containsEntry("FID_COND_MRKT_DIV_CODE", "U")
                .containsEntry("FID_INPUT_ISCD", "0001")
                .containsEntry("FID_INPUT_DATE_1", "20260920")
                .containsEntry("FID_INPUT_DATE_2", "20260929")
                .containsEntry("FID_PERIOD_DIV_CODE", "D");
    }

    @Test
    void 해외_지수_응답을_일봉으로_매핑한다() {
        // given 2026-09-30 실측 응답이다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output1":{"ovrs_nmix_prpr":"26817.30"},
                 "output2":[
                   {"stck_bsop_date":"20260929","ovrs_nmix_prpr":"26817.30","ovrs_nmix_oprc":"26908.76",
                    "ovrs_nmix_hgpr":"26919.01","ovrs_nmix_lwpr":"26717.95","acml_vol":"0","mod_yn":"N"}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.NASDAQ, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29));

        // then
        assertThat(prices).containsExactly(new DailyPrice(
                LocalDate.of(2026, 9, 29), new BigDecimal("26908.76"), new BigDecimal("26919.01"),
                new BigDecimal("26717.95"), new BigDecimal("26817.30"), 0L));
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisIndicatorDailyPriceProvider.OVERSEAS_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisIndicatorDailyPriceProvider.OVERSEAS_TR_ID);
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "N")
                .containsEntry("FID_INPUT_ISCD", "COMP");
    }

    @Test
    void 환율_응답을_일봉으로_매핑한다() {
        // given 환율은 소수 넷째 자리로 온다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[
                   {"stck_bsop_date":"20260928","ovrs_nmix_prpr":"1359.9000","ovrs_nmix_oprc":"1357.0000",
                    "ovrs_nmix_hgpr":"1365.5000","ovrs_nmix_lwpr":"1355.5000","acml_vol":"0","mod_yn":"N"}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.USD_KRW, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 28));

        // then 자릿수가 깎이지 않는다
        assertThat(prices.getFirst().close()).isEqualTo(new BigDecimal("1359.9000"));
        assertThat(prices.getFirst().volume()).isZero();
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "X")
                .containsEntry("FID_INPUT_ISCD", "FX@KRW");
    }

    @Test
    void 국내_지수는_50건_단위로_페이지를_반복한다() {
        // given 2026-01-01 ~ 2026-05-30 = 150일. 50건씩 세 번이면 다 받는다
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 30);
        kisApiClient.enqueue(domesticPage(to, 50));
        kisApiClient.enqueue(domesticPage(to.minusDays(50), 50));
        kisApiClient.enqueue(domesticPage(to.minusDays(100), 50));

        // when
        List<DailyPrice> prices = provider.fetch(MarketIndicator.KOSPI, from, to);

        // then
        assertThat(prices).hasSize(150);
        assertThat(kisApiClient.calls).hasSize(3);
        assertThat(kisApiClient.calls.get(1).params())
                .containsEntry("FID_INPUT_DATE_1", "20260101")
                .containsEntry("FID_INPUT_DATE_2", to.minusDays(50).format(DATE));
    }

    @Test
    void 해외_지수는_100건_단위로_페이지를_반복한다() {
        // given 같은 150일 구간을 100건씩 두 번에 받는다
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 30);
        kisApiClient.enqueue(overseasPage(to, 100));
        kisApiClient.enqueue(overseasPage(to.minusDays(100), 50));

        // when
        List<DailyPrice> prices = provider.fetch(MarketIndicator.NASDAQ, from, to);

        // then 둘째 페이지가 50건이라 최대 건수보다 작으므로 거기서 멈춘다
        assertThat(prices).hasSize(150);
        assertThat(kisApiClient.calls).hasSize(2);
        assertThat(kisApiClient.calls.get(1).params())
                .containsEntry("FID_INPUT_DATE_2", to.minusDays(100).format(DATE));
    }

    @Test
    void 날짜가_빈_행은_버린다() {
        // given KIS는 건수가 모자라면 빈 문자열로 채운 행을 돌려주기도 한다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[
                   {"stck_bsop_date":"20260929","bstp_nmix_prpr":"6870.81","bstp_nmix_oprc":"6844.41",
                    "bstp_nmix_hgpr":"6898.36","bstp_nmix_lwpr":"6782.99"},
                   {"stck_bsop_date":"","bstp_nmix_prpr":"","bstp_nmix_oprc":"",
                    "bstp_nmix_hgpr":"","bstp_nmix_lwpr":""}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.KOSPI, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29));

        // then
        assertThat(prices).extracting(DailyPrice::tradeAt).containsExactly(LocalDate.of(2026, 9, 29));
    }

    @Test
    void 구간_밖의_봉은_버린다() {
        // given 페이지에 요청 구간보다 오래된 행이 섞여 온다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[
                   {"stck_bsop_date":"20260929","bstp_nmix_prpr":"1","bstp_nmix_oprc":"1",
                    "bstp_nmix_hgpr":"1","bstp_nmix_lwpr":"1"},
                   {"stck_bsop_date":"20260919","bstp_nmix_prpr":"1","bstp_nmix_oprc":"1",
                    "bstp_nmix_hgpr":"1","bstp_nmix_lwpr":"1"}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.KOSPI, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29));

        // then
        assertThat(prices).extracting(DailyPrice::tradeAt).containsExactly(LocalDate.of(2026, 9, 29));
    }

    @Test
    void 응답이_비면_외부를_더_부르지_않는다() {
        // given 휴장 구간이면 빈 목록이 온다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(
                MarketIndicator.KOSPI, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 30));

        // then 한 번만 부르고 멈춘다
        assertThat(prices).isEmpty();
        assertThat(kisApiClient.calls).hasSize(1);
    }

    @Test
    void 확정_봉의_종가가_비어_있으면_시세조회_실패로_알린다() {
        // given 날짜는 있는데 값이 비어 있다. 0으로 읽으면 값이 0인 봉이 저장된다
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output2":[
                   {"stck_bsop_date":"20260929","bstp_nmix_prpr":"","bstp_nmix_oprc":"6844.41",
                    "bstp_nmix_hgpr":"6898.36","bstp_nmix_lwpr":"6782.99"}
                 ]}
                """);

        // when & then
        assertThatThrownBy(() -> provider.fetch(
                MarketIndicator.KOSPI, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    /** newest부터 하루씩 거슬러 count건 (KIS와 같이 최신순) */
    private static String domesticPage(LocalDate newest, int count) {
        String rows = IntStream.range(0, count)
                .mapToObj(i -> newest.minusDays(i).format(DATE))
                .map(date -> "{\"stck_bsop_date\":\"" + date + "\",\"bstp_nmix_prpr\":\"1\","
                        + "\"bstp_nmix_oprc\":\"1\",\"bstp_nmix_hgpr\":\"1\",\"bstp_nmix_lwpr\":\"1\"}")
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"정상\",\"output2\":[" + rows + "]}";
    }

    private static String overseasPage(LocalDate newest, int count) {
        String rows = IntStream.range(0, count)
                .mapToObj(i -> newest.minusDays(i).format(DATE))
                .map(date -> "{\"stck_bsop_date\":\"" + date + "\",\"ovrs_nmix_prpr\":\"1\","
                        + "\"ovrs_nmix_oprc\":\"1\",\"ovrs_nmix_hgpr\":\"1\",\"ovrs_nmix_lwpr\":\"1\"}")
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"정상\",\"output2\":[" + rows + "]}";
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
