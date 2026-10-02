package com.swyp.ploutos.disclosure.dart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
import com.swyp.ploutos.disclosure.service.IssuerCodes;

/**
 * DART 고유번호 파일(corpCode.xml)을 받아 {@code KRX:종목코드} → 법인 코드(corp_code)로 만든다.
 * 이름과 달리 ZIP 안에 XML이 들어 있고, 오류일 때만 ZIP이 아닌 XML을 준다.
 * 압축·해제 크기에 상한을 두고 DTD가 들어 있으면 읽지 않는다(외부 엔티티 차단).
 */
@Component
class DartCorpCodeProvider implements IssuerCodeProvider {

    private static final Logger log = LoggerFactory.getLogger(DartCorpCodeProvider.class);
    private static final String API = "고유번호";

    static final String PATH = "/api/corpCode.xml";
    static final long MAX_ZIP_BYTES = 20L * 1024 * 1024;
    static final long MAX_XML_BYTES = 100L * 1024 * 1024;

    private static final Pattern CORP_CODE = Pattern.compile("\\d{8}");
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    private final RestClient dartRestClient;
    private final DisclosureCallBudget dartCallBudget;
    private final long maxZipBytes;
    private final long maxXmlBytes;

    @Autowired
    DartCorpCodeProvider(RestClient dartRestClient, DisclosureCallBudget dartCallBudget) {
        this(dartRestClient, dartCallBudget, MAX_ZIP_BYTES, MAX_XML_BYTES);
    }

    /** 크기 상한을 바꿔 검증하려는 테스트용 생성자. */
    DartCorpCodeProvider(
            RestClient dartRestClient, DisclosureCallBudget dartCallBudget, long maxZipBytes, long maxXmlBytes
    ) {
        this.dartRestClient = dartRestClient;
        this.dartCallBudget = dartCallBudget;
        this.maxZipBytes = maxZipBytes;
        this.maxXmlBytes = maxXmlBytes;
    }

    @Override
    public DisclosureSource source() {
        return DisclosureSource.DART;
    }

    /** 키는 {@code KRX:종목코드}다. */
    @Override
    public Map<String, String> fetchAll() {
        byte[] body = fetch();
        if (!isZip(body)) {
            DartStatus.requireOk(errorStatusOf(body), API);
            throw unavailable("ZIP이 아닌 정상 응답", null);
        }
        Map<String, String> codes = parseZip(body);
        if (codes.isEmpty()) {
            throw unavailable("종목코드가 있는 법인이 없음", null);
        }
        return codes;
    }

    private byte[] fetch() {
        try {
            return request();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("DART 고유번호 파일 수신이 일시적으로 실패해 한 번 재시도한다. cause={}", e.getClass().getSimpleName());
        }
        try {
            return request();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unavailable("재시도 후에도 실패", e);
        }
    }

    private byte[] request() {
        dartCallBudget.consume();
        try {
            return dartRestClient.get()
                    .uri(PATH)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is5xxServerError()) {
                            throw new HttpServerErrorException(status);
                        }
                        if (!status.is2xxSuccessful()) {
                            throw unavailable("요청·인증 오류 status=" + status, null);
                        }
                        return readAtMost(response.getBody(), maxZipBytes);
                    });
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 재시도 여부는 fetch가 정한다.
            throw e;
        } catch (RestClientException e) {
            throw unavailable("응답을 읽지 못함", e);
        }
    }

    /** 상한을 넘으면 끝까지 읽지 않고 멈춘다. */
    private static byte[] readAtMost(InputStream in, long limit) throws IOException {
        byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 9, limit) + 1);
        if (bytes.length > limit) {
            throw unavailable("파일이 " + limit + "바이트를 넘음", null);
        }
        return bytes;
    }

    private Map<String, String> parseZip(byte[] body) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null && !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                entry = zip.getNextEntry();
            }
            if (entry == null) {
                throw unavailable("ZIP 안에 XML이 없음", null);
            }
            return parseXml(new LimitedInputStream(zip, maxXmlBytes));
        } catch (IOException | XMLStreamException e) {
            throw unavailable("파일을 읽지 못함", e);
        }
    }

    /** {@code <list>}마다 corp_code·stock_code를 읽는다. 한 종목코드에 법인이 둘 이상이면 어느 쪽도 쓰지 않는다. */
    private static Map<String, String> parseXml(InputStream in) throws XMLStreamException {
        XMLStreamReader reader = newReader(in);
        Map<String, String> codes = new HashMap<>();
        Set<String> conflicted = new HashSet<>();
        String corpCode = null;
        String stockCode = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    throw unavailable("DTD가 들어 있음", null);
                }
                if (event == XMLStreamConstants.START_ELEMENT && "list".equals(reader.getLocalName())) {
                    corpCode = null;
                    stockCode = null;
                    continue;
                }
                if (event == XMLStreamConstants.START_ELEMENT && "corp_code".equals(reader.getLocalName())) {
                    corpCode = reader.getElementText().strip();
                    continue;
                }
                if (event == XMLStreamConstants.START_ELEMENT && "stock_code".equals(reader.getLocalName())) {
                    stockCode = reader.getElementText().strip();
                    continue;
                }
                if (event == XMLStreamConstants.END_ELEMENT && "list".equals(reader.getLocalName())) {
                    put(codes, conflicted, stockCode, corpCode);
                }
            }
        } finally {
            reader.close();
        }
        if (!conflicted.isEmpty()) {
            log.warn("DART 고유번호 파일에서 법인이 둘 이상인 종목코드 {}개를 매핑에서 뺐다.", conflicted.size());
        }
        return codes;
    }

    private static void put(Map<String, String> codes, Set<String> conflicted, String stockCode, String corpCode) {
        if (stockCode == null || stockCode.isEmpty() || corpCode == null || !CORP_CODE.matcher(corpCode).matches()) {
            return;
        }
        String key = IssuerCodes.key(Exchange.KRX, stockCode);
        if (conflicted.contains(key)) {
            return;
        }
        String existing = codes.putIfAbsent(key, corpCode);
        if (existing != null && !existing.equals(corpCode)) {
            codes.remove(key);
            conflicted.add(key);
        }
    }

    private static XMLStreamReader newReader(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(in);
    }

    /** 오류 응답(ZIP이 아닌 XML)의 status. 읽지 못하면 null이다. */
    private static String errorStatusOf(byte[] body) {
        try {
            XMLStreamReader reader = newReader(new ByteArrayInputStream(body));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && "status".equals(reader.getLocalName())) {
                        return reader.getElementText().strip();
                    }
                }
                return null;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            return null;
        }
    }

    private static boolean isZip(byte[] body) {
        if (body.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (body[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    // 예외 메시지에는 인증키가 든 요청 URL이 들어 있을 수 있어 클래스명만 남긴다.
    private static BusinessException unavailable(String reason, Exception cause) {
        log.error("DART 고유번호 파일 실패: {}{}", reason, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    /** 압축 해제한 바이트 수가 상한을 넘으면 읽기를 멈춘다(압축 폭탄 방지). */
    private static final class LimitedInputStream extends InputStream {

        private final InputStream delegate;
        private final long limit;
        private long read;

        LimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > limit) {
                throw new IOException("압축 해제 크기가 " + limit + "바이트를 넘음");
            }
        }
    }
}
