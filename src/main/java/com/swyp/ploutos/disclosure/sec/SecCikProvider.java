package com.swyp.ploutos.disclosure.sec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
import com.swyp.ploutos.disclosure.service.IssuerCodes;
import com.swyp.ploutos.external.sec.SecApiProperties;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * SEC 티커 파일을 받아 {@code 거래소:티커} → CIK(10자리 문자열)로 만든다.
 * 서비스가 다루는 거래소(Nasdaq·NYSE)만 쓰고, 한 키에 CIK가 둘 이상이면 어느 쪽도 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
class SecCikProvider implements IssuerCodeProvider {

    private static final Logger log = LoggerFactory.getLogger(SecCikProvider.class);
    private static final String API = "티커 파일";

    static final String PATH = "/files/company_tickers_exchange.json";

    private final SecFetcher secFetcher;
    private final SecApiProperties secApiProperties;
    private final JsonMapper jsonMapper;

    @Override
    public DisclosureSource source() {
        return DisclosureSource.SEC;
    }

    @Override
    public Map<String, String> fetchAll() {
        SecTickerFile file = parse(secFetcher.get(secApiProperties.wwwBaseUrl() + PATH, API));
        if (file.fields() == null || file.data() == null) {
            throw SecFetcher.unavailable(API, "fields·data가 없음", null);
        }
        int cikAt = file.fields().indexOf("cik");
        int tickerAt = file.fields().indexOf("ticker");
        int exchangeAt = file.fields().indexOf("exchange");
        if (cikAt < 0 || tickerAt < 0 || exchangeAt < 0) {
            throw SecFetcher.unavailable(API, "필요한 열이 없음 fields=" + file.fields(), null);
        }
        Map<String, String> codes = new HashMap<>();
        Set<String> conflicted = new HashSet<>();
        for (List<Object> row : file.data()) {
            put(codes, conflicted, row, cikAt, tickerAt, exchangeAt);
        }
        if (!conflicted.isEmpty()) {
            log.warn("SEC 티커 파일에서 CIK가 둘 이상인 거래소·티커 {}개를 매핑에서 뺐다.", conflicted.size());
        }
        if (codes.isEmpty()) {
            throw SecFetcher.unavailable(API, "쓸 수 있는 행이 없음", null);
        }
        return codes;
    }

    private static void put(
            Map<String, String> codes, Set<String> conflicted, List<Object> row, int cikAt, int tickerAt, int exchangeAt
    ) {
        if (row == null || row.size() <= Math.max(cikAt, Math.max(tickerAt, exchangeAt))) {
            return;
        }
        Exchange exchange = exchangeOf(row.get(exchangeAt));
        String cik = cikOf(row.get(cikAt));
        if (exchange == null || cik == null || !(row.get(tickerAt) instanceof String ticker) || ticker.isBlank()) {
            return;
        }
        String key = IssuerCodes.key(exchange, ticker);
        if (conflicted.contains(key)) {
            return;
        }
        String existing = codes.putIfAbsent(key, cik);
        if (existing != null && !existing.equals(cik)) {
            codes.remove(key);
            conflicted.add(key);
        }
    }

    private static Exchange exchangeOf(Object value) {
        if ("Nasdaq".equals(value)) {
            return Exchange.NASDAQ;
        }
        if ("NYSE".equals(value)) {
            return Exchange.NYSE;
        }
        return null;
    }

    /** 숫자 CIK를 10자리 문자열로 맞춘다. 범위를 벗어나면 null이다. */
    private static String cikOf(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        long cik = number.longValue();
        if (cik <= 0 || cik > 9_999_999_999L) {
            return null;
        }
        return String.format("%010d", cik);
    }

    private SecTickerFile parse(String body) {
        try {
            return jsonMapper.readValue(body, SecTickerFile.class);
        } catch (JacksonException e) {
            throw SecFetcher.unavailable(API, "응답을 읽지 못함", e);
        }
    }
}
