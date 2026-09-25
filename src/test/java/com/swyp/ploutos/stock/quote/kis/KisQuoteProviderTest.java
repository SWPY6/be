package com.swyp.ploutos.stock.quote.kis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

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
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

import tools.jackson.databind.json.JsonMapper;

class KisQuoteProviderTest {

    private static final LocalDate LISTED_AT = LocalDate.of(2000, 1, 1);
    // 서울 14:31:05, 뉴욕 01:31:05(서머타임)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T05:31:05Z"), ZoneOffset.UTC);

    private FakeKisApiClient kisApiClient;
    private KisQuoteProvider provider;

    @BeforeEach
    void setUp() {
        kisApiClient = new FakeKisApiClient();
        provider = new KisQuoteProvider(kisApiClient, CLOCK);
    }

    @Test
    void 국내_응답을_시세로_매핑한다() {
        // given
        StockWithMarket hyundai = domestic("005380");
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"244280",
                  "stck_hgpr":"251224","stck_lwpr":"241056","acml_vol":"245000",
                  "acml_tr_pbmn":"60800000000","hts_avls":"866000"}}
                """);

        // when
        Quote quote = provider.fetch(hyundai);

        // then
        assertThat(quote.price()).isEqualByComparingTo("248000");
        assertThat(quote.previousClose()).isEqualByComparingTo("240217");
        assertThat(quote.open()).isEqualByComparingTo("244280");
        assertThat(quote.high()).isEqualByComparingTo("251224");
        assertThat(quote.low()).isEqualByComparingTo("241056");
        assertThat(quote.volume()).isEqualTo(245_000L);
        assertThat(quote.tradingValue()).isEqualByComparingTo("60800000000");
        assertThat(quote.currency()).isEqualTo(Currency.KRW);
        assertThat(quote.priceTiming()).isEqualTo(PriceTiming.REALTIME);
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisQuoteProvider.DOMESTIC_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisQuoteProvider.DOMESTIC_TR_ID);
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("FID_COND_MRKT_DIV_CODE", "J")
                .containsEntry("FID_INPUT_ISCD", "005380");
    }

    @Test
    void 해외_응답을_시세로_매핑한다() {
        // given
        StockWithMarket apple = overseas("AAPL", Exchange.NASDAQ);
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "last":"185.7000","base":"183.2500","open":"184.2000",
                  "high":"186.0000","low":"183.9000","tvol":"41230000",
                  "tamt":"7650000000","tomv":"2890000000000"}}
                """);

        // when
        Quote quote = provider.fetch(apple);

        // then
        assertThat(quote.price()).isEqualByComparingTo("185.7000");
        assertThat(quote.previousClose()).isEqualByComparingTo("183.2500");
        assertThat(quote.open()).isEqualByComparingTo("184.2000");
        assertThat(quote.high()).isEqualByComparingTo("186.0000");
        assertThat(quote.low()).isEqualByComparingTo("183.9000");
        assertThat(quote.volume()).isEqualTo(41_230_000L);
        assertThat(quote.tradingValue()).isEqualByComparingTo("7650000000");
        assertThat(quote.marketCap()).isEqualByComparingTo("2890000000000");
        assertThat(quote.currency()).isEqualTo(Currency.USD);
        assertThat(quote.priceTiming()).isEqualTo(PriceTiming.REALTIME);
        assertThat(kisApiClient.calls.getFirst().path()).isEqualTo(KisQuoteProvider.OVERSEAS_PATH);
        assertThat(kisApiClient.calls.getFirst().trId()).isEqualTo(KisQuoteProvider.OVERSEAS_TR_ID);
        assertThat(kisApiClient.calls.getFirst().params())
                .containsEntry("AUTH", "")
                .containsEntry("EXCD", "NAS")
                .containsEntry("SYMB", "AAPL");
    }

    @Test
    void 국내_시가총액은_억원을_원으로_환산한다() {
        // given
        StockWithMarket hyundai = domestic("005380");
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"244280",
                  "stck_hgpr":"251224","stck_lwpr":"241056","acml_vol":"245000",
                  "acml_tr_pbmn":"60800000000","hts_avls":"866000"}}
                """);

        // when
        Quote quote = provider.fetch(hyundai);

        // then
        assertThat(quote.marketCap()).isEqualByComparingTo("86600000000000");
    }

    @Test
    void 가격_기준_시각은_시세를_받은_시각이다() {
        // given
        StockWithMarket hyundai = domestic("005380");
        StockWithMarket apple = overseas("AAPL", Exchange.NASDAQ);
        kisApiClient.enqueue(domesticBody());
        kisApiClient.enqueue(overseasBody());

        // when
        Quote domesticQuote = provider.fetch(hyundai);
        Quote overseasQuote = provider.fetch(apple);

        // then
        assertThat(domesticQuote.priceAt()).isEqualTo(OffsetDateTime.parse("2026-08-12T14:31:05+09:00"));
        assertThat(overseasQuote.priceAt()).isEqualTo(OffsetDateTime.parse("2026-08-12T01:31:05-04:00"));
    }

    @Test
    void NYSE_종목은_거래소_코드_NYS로_호출한다() {
        // given
        StockWithMarket jpm = overseas("JPM", Exchange.NYSE);
        kisApiClient.enqueue(overseasBody());

        // when
        provider.fetch(jpm);

        // then
        assertThat(kisApiClient.calls.getFirst().params()).containsEntry("EXCD", "NYS");
    }

    @Test
    void 값이_비어_있으면_0으로_읽는다() {
        // given 개장 전에는 시가·고가·저가·거래량이 빈 값으로 온다
        StockWithMarket hyundai = domestic("005380");
        kisApiClient.enqueue("""
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"",
                  "stck_hgpr":"","stck_lwpr":"","acml_vol":"",
                  "acml_tr_pbmn":"","hts_avls":"866000"}}
                """);

        // when
        Quote quote = provider.fetch(hyundai);

        // then
        assertThat(quote.open()).isEqualByComparingTo("0");
        assertThat(quote.high()).isEqualByComparingTo("0");
        assertThat(quote.low()).isEqualByComparingTo("0");
        assertThat(quote.volume()).isZero();
        assertThat(quote.tradingValue()).isEqualByComparingTo("0");
        assertThat(quote.notOpenedToday()).isTrue();
    }

    private static String domesticBody() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "stck_prpr":"248000","stck_sdpr":"240217","stck_oprc":"244280",
                  "stck_hgpr":"251224","stck_lwpr":"241056","acml_vol":"245000",
                  "acml_tr_pbmn":"60800000000","hts_avls":"866000"}}
                """;
    }

    private static String overseasBody() {
        return """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상","output":{
                  "last":"185.7000","base":"183.2500","open":"184.2000",
                  "high":"186.0000","low":"183.9000","tvol":"41230000",
                  "tamt":"7650000000","tomv":"2890000000000"}}
                """;
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
