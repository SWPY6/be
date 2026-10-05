package com.swyp.ploutos.disclosure.sec;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.IssuerCodeCollector;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
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
        if (!file.hasExpectedFields() || file.data() == null) {
            throw SecFetcher.unavailable(API, "열 구성이 다름 fields=" + file.fields(), null);
        }
        IssuerCodeCollector collector = new IssuerCodeCollector();
        for (SecTickerRow row : file.data()) {
            if (row != null) {
                row.putInto(collector);
            }
        }
        if (collector.conflictedCount() > 0) {
            log.warn("SEC 티커 파일에서 CIK가 둘 이상인 거래소·티커 {}개를 매핑에서 뺐다.", collector.conflictedCount());
        }
        Map<String, String> codes = collector.codes();
        if (codes.isEmpty()) {
            throw SecFetcher.unavailable(API, "쓸 수 있는 행이 없음", null);
        }
        return codes;
    }

    private SecTickerFile parse(String body) {
        try {
            return jsonMapper.readValue(body, SecTickerFile.class);
        } catch (JacksonException e) {
            throw SecFetcher.unavailable(API, "응답을 읽지 못함", e);
        }
    }
}
