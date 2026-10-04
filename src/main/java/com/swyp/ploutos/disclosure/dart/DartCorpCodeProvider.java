package com.swyp.ploutos.disclosure.dart;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;

/**
 * DART 고유번호 파일(corpCode.xml)을 받아 {@code KRX:종목코드} → 법인 코드(corp_code)로 만든다.
 * 받은 파일 크기에 상한을 두고, 해석은 {@link DartCorpCodeFile}이 한다.
 * 공시검색이 상한에 걸려도 매핑을 갱신할 수 있게 DART 전체 상한 예산을 쓴다.
 */
@Component
class DartCorpCodeProvider implements IssuerCodeProvider {

    static final String API = "고유번호";
    static final String PATH = "/api/corpCode.xml";
    static final long MAX_ZIP_BYTES = 20L * 1024 * 1024;
    static final long MAX_XML_BYTES = 100L * 1024 * 1024;

    private final DartFetcher dartFetcher;
    private final DartCorpCodeFile corpCodeFile = new DartCorpCodeFile(MAX_XML_BYTES);

    DartCorpCodeProvider(RestClient dartRestClient, DisclosureCallBudget dartCallBudget) {
        this.dartFetcher = new DartFetcher(dartRestClient, dartCallBudget);
    }

    @Override
    public DisclosureSource source() {
        return DisclosureSource.DART;
    }

    /** 키는 {@code KRX:종목코드}다. */
    @Override
    public Map<String, String> fetchAll() {
        return corpCodeFile.codesOf(dartFetcher.getBytes(PATH, API, MAX_ZIP_BYTES));
    }
}
