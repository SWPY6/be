package com.swyp.ploutos.disclosure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 공급자 목록 한 건을 화면에 보여 줄 수 있게 정제한 공시.
 * DART는 접수 날짜만 주므로 {@code acceptedAt}이 null이다. 자정이나 장 마감 시각으로 채우지 않는다.
 *
 * @param documentId 공급자 원문 ID(DART 접수번호, SEC accession number). 같은 값이면 같은 공시다.
 * @param title      보고서명. {@code [기재정정]} 같은 표기를 그대로 둔다.
 * @param formType   SEC Form 원문(/A 포함). DART는 null
 * @param formLabel  Form 한글 라벨. 사전에 없거나 DART면 null
 * @param issuerName 공시 대상 법인명
 * @param filerName  제출인. 법인 자신이 아닐 수 있다. SEC는 null
 * @param remark     DART 비고(rm) 원문. 없으면 null
 * @param filedDate  접수일
 * @param acceptedAt 확인된 접수 시각. 없으면 null
 */
public record Disclosure(
        DisclosureSource source,
        String documentId,
        String title,
        String formType,
        String formLabel,
        String issuerName,
        String filerName,
        String remark,
        LocalDate filedDate,
        Instant acceptedAt,
        String url,
        LinkKind linkKind
) {

    private static final Pattern DART_RECEIPT_NO = Pattern.compile("\\d{14}");
    private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

    public enum LinkKind {
        /** DART 공시 뷰어 */
        DART_VIEWER
    }

    /** 공시 시각의 정밀도. */
    public enum DatePrecision {
        /** 접수 시각(초)까지 안다. */
        SECOND,
        /** 접수일만 안다. */
        DATE
    }

    /** 공시 시각의 기준. 최초 공개 시각이라고 단정하지 않는다. */
    public enum TimeBasis {
        /** SEC 접수 시각 */
        ACCEPTANCE_TIME,
        /** 접수일 */
        RECEIPT_DATE
    }

    /**
     * DART 공시검색 원본으로 공시를 만든다. 접수번호가 14자리 숫자가 아니거나, 제목이 비었거나,
     * 접수일(yyyyMMdd)을 읽을 수 없으면 보여 줄 수 없는 공시이므로 비어 있다.
     */
    public static Optional<Disclosure> dart(
            String receiptNo, String title, String issuerName, String filerName, String remark, String filedDate
    ) {
        if (receiptNo == null || !DART_RECEIPT_NO.matcher(receiptNo.strip()).matches()) {
            return Optional.empty();
        }
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String id = receiptNo.strip();
        return parseDate(filedDate, DateTimeFormatter.BASIC_ISO_DATE).map(date -> new Disclosure(
                DisclosureSource.DART, id, title.strip(), null, null,
                blankToNull(issuerName), blankToNull(filerName), blankToNull(remark),
                date, null, DART_VIEWER_URL + id, LinkKind.DART_VIEWER
        ));
    }

    /**
     * 기간에 드는지. 시각이 있으면 {@code from < acceptedAt <= to}로, 없으면 접수일이
     * 기간 양 끝 날짜(해당 시장 기준) 안에 있는지로 본다.
     */
    public boolean filedIn(DisclosureWindow window, ZoneId zone) {
        if (acceptedAt != null) {
            return window.contains(acceptedAt);
        }
        return window.filedDatesIn(zone).contains(filedDate);
    }

    /** 접수 시각을 시장 현지 오프셋으로. 시각을 모르면 null이다. */
    public OffsetDateTime acceptedAtIn(ZoneId zone) {
        if (acceptedAt == null) {
            return null;
        }
        return acceptedAt.atZone(zone).toOffsetDateTime();
    }

    public DatePrecision datePrecision() {
        if (acceptedAt == null) {
            return DatePrecision.DATE;
        }
        return DatePrecision.SECOND;
    }

    public TimeBasis timeBasis() {
        if (acceptedAt == null) {
            return TimeBasis.RECEIPT_DATE;
        }
        return TimeBasis.ACCEPTANCE_TIME;
    }

    /** 공급자 조회 범위를 고르는 날짜. 시각이 있으면 시장 현지 날짜, 없으면 접수일이다. */
    public LocalDate localDateIn(ZoneId zone) {
        if (acceptedAt == null) {
            return filedDate;
        }
        return acceptedAt.atZone(zone).toLocalDate();
    }

    private static Optional<LocalDate> parseDate(String value, DateTimeFormatter format) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.strip(), format));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
