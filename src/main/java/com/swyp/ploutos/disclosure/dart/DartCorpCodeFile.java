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

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.disclosure.service.IssuerCodes;

/**
 * DART 고유번호 파일 본문을 {@code KRX:종목코드} → 법인 코드(corp_code)로 읽는다.
 * 이름과 달리 ZIP 안에 XML이 들어 있고, 오류일 때만 ZIP이 아닌 XML이 온다.
 * 압축 해제 크기에 상한을 두고 DTD가 들어 있으면 읽지 않는다(외부 엔티티 차단).
 */
final class DartCorpCodeFile {

    private static final Logger log = LoggerFactory.getLogger(DartCorpCodeFile.class);
    private static final String API = DartCorpCodeProvider.API;

    private static final Pattern CORP_CODE = Pattern.compile("\\d{8}");
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    private final long maxXmlBytes;

    DartCorpCodeFile(long maxXmlBytes) {
        this.maxXmlBytes = maxXmlBytes;
    }

    /** 종목코드가 있는 법인이 하나도 없거나 읽지 못하면 {@code DISCLOSURE_UNAVAILABLE}이다. */
    Map<String, String> codesOf(byte[] body) {
        if (!isZip(body)) {
            DartStatus.requireOk(errorStatusOf(body), API);
            throw DartFetcher.unavailable(API, "ZIP이 아닌 정상 응답", null);
        }
        Map<String, String> codes = parseZip(body);
        if (codes.isEmpty()) {
            throw DartFetcher.unavailable(API, "종목코드가 있는 법인이 없음", null);
        }
        return codes;
    }

    private Map<String, String> parseZip(byte[] body) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null && !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                entry = zip.getNextEntry();
            }
            if (entry == null) {
                throw DartFetcher.unavailable(API, "ZIP 안에 XML이 없음", null);
            }
            return parseXml(new LimitedInputStream(zip, maxXmlBytes));
        } catch (IOException | XMLStreamException e) {
            throw DartFetcher.unavailable(API, "파일을 읽지 못함", e);
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
                    throw DartFetcher.unavailable(API, "DTD가 들어 있음", null);
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
