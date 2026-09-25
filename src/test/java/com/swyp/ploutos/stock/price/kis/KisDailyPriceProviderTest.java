package com.swyp.ploutos.stock.price.kis;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.Stocks;

import tools.jackson.databind.json.JsonMapper;

class KisDailyPriceProviderTest {

    private static final LocalDate LISTED_AT = LocalDate.of(2000, 1, 1);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private FakeKisApiClient kisApiClient;
    private KisDailyPriceProvider provider;

    @BeforeEach
    void setUp() {
        kisApiClient = new FakeKisApiClient();
        provider = new KisDailyPriceProvider(kisApiClient);
    }

    @Test
    void 국내_응답을_일봉으로_매핑한다() {
        // given
        StockWithMarket samsung = domestic("005930");
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output1":{"stck_prpr":"72000"},
                 "output2":[
                   {"stck_bsop_date":"20260812","stck_oprc":"71600","stck_hgpr":"72300","stck_lwpr":"71500","stck_clpr":"72000","acml_vol":"3521000"},
                   {"stck_bsop_date":"20260811","stck_oprc":"71200","stck_hgpr":"71800","stck_lwpr":"71000","stck_clpr":"71600","acml_vol":"2980000"},
                   {"stck_bsop_date":"","stck_oprc":"","stck_hgpr":"","stck_lwpr":"","stck_clpr":"","acml_vol":""}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(samsung, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 12));

        // then
        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst()).isEqualTo(new DailyPrice(
                LocalDate.of(2026, 8, 12), new BigDecimal("71600"), new BigDecimal("72300"),
                new BigDecimal("71500"), new BigDecimal("72000"), 3_521_000L));
        Map<String, String> params = kisApiClient.calls.getFirst().params();
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisDailyPriceProvider.DOMESTIC_TR_ID);
        assertThat(params)
                .containsEntry("FID_COND_MRKT_DIV_CODE", "J")
                .containsEntry("FID_INPUT_ISCD", "005930")
                .containsEntry("FID_INPUT_DATE_1", "20260801")
                .containsEntry("FID_INPUT_DATE_2", "20260812")
                .containsEntry("FID_PERIOD_DIV_CODE", "D")
                .containsEntry("FID_ORG_ADJ_PRC", "0");
    }

    @Test
    void 해외_응답을_일봉으로_매핑한다() {
        // given
        StockWithMarket apple = overseas("AAPL", Exchange.NASDAQ);
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output1":{"rsym":"DNASAAPL"},
                 "output2":[
                   {"xymd":"20260811","open":"184.2000","high":"186.0000","low":"183.9000","clos":"185.7000","tvol":"41230000","tamt":"7650000000"}
                 ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(apple, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11));

        // then
        assertThat(prices).containsExactly(new DailyPrice(
                LocalDate.of(2026, 8, 11), new BigDecimal("184.2000"), new BigDecimal("186.0000"),
                new BigDecimal("183.9000"), new BigDecimal("185.7000"), 41_230_000L));
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisDailyPriceProvider.OVERSEAS_TR_ID);
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("EXCD", "NAS")
                .containsEntry("SYMB", "AAPL")
                .containsEntry("GUBN", "0")
                .containsEntry("BYMD", "20260811")
                .containsEntry("MODP", "1");
    }

    @Test
    void NYSE_종목은_거래소_코드_NYS로_호출한다() {
        // given
        StockWithMarket jpm = overseas("JPM", Exchange.NYSE);
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output2":[]}
                """);

        // when
        provider.fetch(jpm, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11));

        // then
        assertThat(kisApiClient.calls.getFirst().params()).containsEntry("EXCD", "NYS");
    }

    @Test
    void 백건을_넘으면_페이지를_반복해_모두_받는다() {
        // given
        // 2026-01-01 ~ 2026-05-30 = 150일. 첫 페이지 100건(05-30 ~ 02-20), 둘째 페이지 50건(02-19 ~ 01-01)
        StockWithMarket samsung = domestic("005930");
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 30);
        kisApiClient.enqueue(domesticPage(to, 100));
        kisApiClient.enqueue(domesticPage(to.minusDays(100), 50));

        // when
        List<DailyPrice> prices = provider.fetch(samsung, from, to);

        // then
        assertThat(prices).hasSize(150);
        assertThat(kisApiClient.calls).hasSize(2);
        assertThat(kisApiClient.calls.get(1).params())
                .containsEntry("FID_INPUT_DATE_1", "20260101")
                .containsEntry("FID_INPUT_DATE_2", "20260219");
    }

    @Test
    void 해외는_기준일을_옮겨_페이지를_반복한다() {
        // given
        StockWithMarket apple = overseas("AAPL", Exchange.NASDAQ);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 30);
        kisApiClient.enqueue(overseasPage(to, 100));
        kisApiClient.enqueue(overseasPage(to.minusDays(100), 50));

        // when
        List<DailyPrice> prices = provider.fetch(apple, from, to);

        // then
        assertThat(prices).hasSize(150);
        assertThat(kisApiClient.calls.get(1).params()).containsEntry("BYMD", "20260219");
    }

    @Test
    void 구간_밖의_봉은_버린다() {
        // given
        StockWithMarket samsung = domestic("005930");
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output2":[
                   {"stck_bsop_date":"20260812","stck_oprc":"1","stck_hgpr":"1","stck_lwpr":"1","stck_clpr":"1","acml_vol":"1"},
                   {"stck_bsop_date":"20260731","stck_oprc":"1","stck_hgpr":"1","stck_lwpr":"1","stck_clpr":"1","acml_vol":"1"}
                ]}
                """);

        // when
        List<DailyPrice> prices = provider.fetch(samsung, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 12));

        // then
        assertThat(prices).extracting(DailyPrice::tradeAt).containsExactly(LocalDate.of(2026, 8, 12));
    }

    private static StockWithMarket domestic(String ticker) {
        Stocks stock = new Stocks(1L, ticker, "종목", null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표", LISTED_AT);
        Markets market = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
        return new StockWithMarket(stock, market);
    }

    private static StockWithMarket overseas(String ticker, Exchange exchange) {
        Stocks stock = new Stocks(2L, ticker, "종목", null, StockStatus.ACTIVE, exchange, 1L, "대표", LISTED_AT);
        Markets market = new Markets(MarketCode.SP500, Country.US, TradingSession.REGULAR, Currency.USD);
        return new StockWithMarket(stock, market);
    }

    /** newest부터 하루씩 거슬러 count건 (최신순) */
    private static String domesticPage(LocalDate newest, int count) {
        String rows = IntStream.range(0, count)
                .mapToObj(i -> newest.minusDays(i).format(DATE))
                .map(d -> "{\"stck_bsop_date\":\"" + d + "\",\"stck_oprc\":\"1\",\"stck_hgpr\":\"1\",\"stck_lwpr\":\"1\",\"stck_clpr\":\"1\",\"acml_vol\":\"1\"}")
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"정상\",\"output2\":[" + rows + "]}";
    }

    private static String overseasPage(LocalDate newest, int count) {
        String rows = IntStream.range(0, count)
                .mapToObj(i -> newest.minusDays(i).format(DATE))
                .map(d -> "{\"xymd\":\"" + d + "\",\"open\":\"1\",\"high\":\"1\",\"low\":\"1\",\"clos\":\"1\",\"tvol\":\"1\"}")
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
