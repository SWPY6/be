package com.swyp.ploutos.disclosure.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class DartCorpCodeProviderTest {

    private static final String BASE_URL = "https://dart.test";
    private static final String FILE_URL = BASE_URL + DartCorpCodeProvider.PATH;

    private static final String CORP_CODES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <result>
              <list>
                <corp_code>00126380</corp_code>
                <corp_name>삼성전자</corp_name>
                <corp_eng_name>SAMSUNG ELECTRONICS CO,.LTD</corp_eng_name>
                <stock_code>005930</stock_code>
                <modify_date>20250101</modify_date>
              </list>
              <list>
                <corp_code>00434003</corp_code>
                <corp_name>비상장회사</corp_name>
                <corp_eng_name></corp_eng_name>
                <stock_code> </stock_code>
                <modify_date>20250101</modify_date>
              </list>
              <list>
                <corp_code>01234567</corp_code>
                <corp_name>선행영</corp_name>
                <stock_code>000020</stock_code>
              </list>
            </result>
            """;

    private MockRestServiceServer server;
    private CountingBudget budget = new CountingBudget(Integer.MAX_VALUE);

    @Test
    void ZIP_안의_XML에서_종목코드가_있는_법인만_KRX_키와_문자열_코드로_읽는다() throws IOException {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(FILE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(zip(CORP_CODES));

        // when
        Map<String, String> codes = provider.fetchAll();

        // then
        assertThat(codes).containsExactlyInAnyOrderEntriesOf(Map.of("KRX:005930", "00126380", "KRX:000020", "01234567"));
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 한_종목코드에_법인이_둘_이상이면_매핑하지_않는다() throws IOException {
        // given
        DartCorpCodeProvider provider = provider();
        String duplicated = CORP_CODES.replace("</result>", """
                  <list><corp_code>09999999</corp_code><stock_code>005930</stock_code></list>
                </result>
                """);
        server.expect(once(), requestTo(FILE_URL)).andRespond(zip(duplicated));

        // when
        Map<String, String> codes = provider.fetchAll();

        // then
        assertThat(codes).doesNotContainKey("KRX:005930").containsEntry("KRX:000020", "01234567");
    }

    @Test
    void ZIP이_아닌_오류_XML이면_status로_판정한다() {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess("""
                <?xml version="1.0" encoding="UTF-8"?>
                <result><status>010</status><message>등록되지 않은 키입니다.</message></result>
                """, MediaType.APPLICATION_XML));
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess("""
                <?xml version="1.0" encoding="UTF-8"?>
                <result><status>020</status><message>요청 제한을 초과하였습니다.</message></result>
                """, MediaType.APPLICATION_XML));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
        assertError(provider, ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    @Test
    void 손상된_ZIP이면_공급_실패다() {
        // given
        DartCorpCodeProvider provider = provider();
        byte[] broken = {'P', 'K', 3, 4, 1, 2, 3};
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess(broken, MediaType.APPLICATION_OCTET_STREAM));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 호출_한도_429면_재시도하지_않고_한도_초과다() {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(FILE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void DTD가_들어_있으면_외부_엔티티를_읽지_않고_공급_실패다() throws IOException {
        // given
        DartCorpCodeProvider provider = provider();
        String withEntity = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE result [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
                <result><list><corp_code>00126380</corp_code><stock_code>&xxe;</stock_code></list></result>
                """;
        server.expect(once(), requestTo(FILE_URL)).andRespond(zip(withEntity));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 종목코드가_있는_법인이_하나도_없으면_공급_실패다() throws IOException {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(FILE_URL)).andRespond(zip("<result></result>"));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 서버_오류는_한_번_재시도하고_예산을_두_번_쓴다() throws IOException {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(FILE_URL)).andRespond(withServerError());
        server.expect(once(), requestTo(FILE_URL)).andRespond(zip(CORP_CODES));

        // when
        Map<String, String> codes = provider.fetchAll();

        // then
        assertThat(codes).hasSize(2);
        assertThat(budget.consumed).isEqualTo(2);
        server.verify();
    }

    @Test
    void 서버_오류가_두_번_나면_공급_실패다() {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(times(2), requestTo(FILE_URL)).andRespond(withServerError());

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void HTTP_4xx는_재시도하지_않는다() {
        // given
        DartCorpCodeProvider provider = provider();
        server.expect(once(), requestTo(Matchers.startsWith(FILE_URL))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        // when & then
        assertError(provider, ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    private DartCorpCodeProvider provider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new DartCorpCodeProvider(builder.build(), budget);
    }

    private static void assertError(DartCorpCodeProvider provider, ErrorCode expected) {
        assertThatThrownBy(provider::fetchAll)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    private static ResponseCreator zip(String xml) throws IOException {
        return withSuccess(zipBytes(xml), MediaType.APPLICATION_OCTET_STREAM);
    }

    static byte[] zipBytes(String xml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
