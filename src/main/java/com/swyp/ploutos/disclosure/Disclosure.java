package com.swyp.ploutos.disclosure;

import java.time.Instant;
import java.time.LocalDate;
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
    private static final Pattern SEC_ACCESSION_NO = Pattern.compile("\\d{10}-\\d{2}-\\d{6}");
    private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";
    private static final String SEC_ARCHIVES_URL = "https://www.sec.gov/Archives/edgar/data/";
    private static final String SEC_PLACEHOLDER_DESCRIPTION = "PRIMARY DOCUMENT";

    public enum LinkKind {
        /** DART 공시 뷰어 */
        DART_VIEWER,
        /** SEC 제출 문서 본문 */
        SEC_DOCUMENT,
        /** 본문 경로가 없어 대신 준 SEC 제출 문서 목록 */
        SEC_FILING_INDEX
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
     * SEC submissions 원본으로 공시를 만든다. accession number 형식이 틀리거나, Form이 비었거나,
     * 접수일을 읽을 수 없으면 비어 있다. 접수 시각을 읽을 수 없으면 시각 없이 만든다.
     * 원문 경로의 CIK는 accession 앞자리(제출 대행사일 수 있음)가 아니라 조회한 법인의 CIK다.
     */
    public static Optional<Disclosure> sec(
            String cik, String accessionNumber, String form, String description, String issuerName,
            String filingDate, String acceptanceDateTime, String primaryDocument
    ) {
        if (accessionNumber == null || !SEC_ACCESSION_NO.matcher(accessionNumber.strip()).matches()) {
            return Optional.empty();
        }
        if (form == null || form.isBlank()) {
            return Optional.empty();
        }
        String id = accessionNumber.strip();
        String formType = form.strip();
        String folder = SEC_ARCHIVES_URL + Long.parseLong(cik) + "/" + id.replace("-", "") + "/";
        boolean hasDocument = primaryDocument != null && !primaryDocument.isBlank();
        return parseDate(filingDate, DateTimeFormatter.ISO_LOCAL_DATE).map(date -> new Disclosure(
                DisclosureSource.SEC, id, secTitle(formType, description, issuerName), formType,
                SecFormLabels.labelOf(formType), blankToNull(issuerName), null, null,
                date, parseInstant(acceptanceDateTime),
                hasDocument ? folder + primaryDocument.strip() : folder + id + "-index.htm",
                hasDocument ? LinkKind.SEC_DOCUMENT : LinkKind.SEC_FILING_INDEX
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
        DisclosureWindow.FiledDateRange range = window.filedDatesIn(zone);
        return !filedDate.isBefore(range.from()) && !filedDate.isAfter(range.to());
    }

    /** 공급자 조회 범위를 고르는 날짜. 시각이 있으면 시장 현지 날짜, 없으면 접수일이다. */
    public LocalDate localDateIn(ZoneId zone) {
        if (acceptedAt == null) {
            return filedDate;
        }
        return acceptedAt.atZone(zone).toLocalDate();
    }

    /**
     * 설명이 비었거나, Form 코드와 같거나, 제출자가 적지 않아 SEC가 넣은 기본값({@code PRIMARY DOCUMENT})이면
     * 무슨 공시인지 알 수 없으므로 법인명과 Form으로 제목을 만든다.
     */
    private static String secTitle(String form, String description, String issuerName) {
        String text = description == null ? "" : description.strip();
        if (!text.isEmpty() && !text.equalsIgnoreCase(form) && !text.equalsIgnoreCase("FORM " + form)
                && !text.equalsIgnoreCase(SEC_PLACEHOLDER_DESCRIPTION)) {
            return text;
        }
        if (issuerName == null || issuerName.isBlank()) {
            return form;
        }
        return issuerName.strip() + " " + form;
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

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
