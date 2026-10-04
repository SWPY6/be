package com.swyp.ploutos.disclosure.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class DartCorpCodeFileTest {

    private static final String CORP_CODES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <result>
              <list>
                <corp_code>00126380</corp_code>
                <stock_code>005930</stock_code>
              </list>
            </result>
            """;

    @Test
    void 압축을_푼_XML이_상한_이하면_읽는다() throws IOException {
        // given
        byte[] body = DartCorpCodeProviderTest.zipBytes(CORP_CODES);
        DartCorpCodeFile file = new DartCorpCodeFile(Long.MAX_VALUE);

        // when
        Map<String, String> codes = file.codesOf(body);

        // then
        assertThat(codes).containsExactlyEntriesOf(Map.of("KRX:005930", "00126380"));
    }

    @Test
    void 압축을_푼_XML이_상한을_넘으면_공급_실패다() throws IOException {
        // given
        byte[] body = DartCorpCodeProviderTest.zipBytes(CORP_CODES);
        DartCorpCodeFile file = new DartCorpCodeFile(100);

        // when & then
        assertThatThrownBy(() -> file.codesOf(body))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }
}
