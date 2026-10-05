package com.swyp.ploutos.disclosure;

import java.util.Map;

/**
 * SEC Form 코드 → 화면용 한글 라벨. 번역이 아니라 고정 매핑이며, 사전에 없는 Form은 라벨이 없다.
 * 정정본(/A)은 기본 Form 라벨 뒤에 "(정정)"을 붙인다.
 */
final class SecFormLabels {

    private static final String AMENDMENT_SUFFIX = "/A";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("10-K", "연간보고서"),
            Map.entry("10-Q", "분기보고서"),
            Map.entry("8-K", "수시공시(주요사항)"),
            Map.entry("20-F", "외국기업 연간보고서"),
            Map.entry("40-F", "캐나다기업 연간보고서"),
            Map.entry("6-K", "외국기업 수시공시"),
            Map.entry("DEF 14A", "주주총회 위임장 설명서"),
            Map.entry("DEFA14A", "주주총회 위임장 추가자료"),
            Map.entry("3", "내부자 최초 지분 보고"),
            Map.entry("4", "내부자 지분 변동"),
            Map.entry("5", "내부자 연간 지분 보고"),
            Map.entry("144", "제한주식 매도 예정 신고"),
            Map.entry("SC 13D", "5% 이상 지분 보고(경영 참여)"),
            Map.entry("SCHEDULE 13D", "5% 이상 지분 보고(경영 참여)"),
            Map.entry("SC 13G", "5% 이상 지분 보고(단순 투자)"),
            Map.entry("SCHEDULE 13G", "5% 이상 지분 보고(단순 투자)"),
            Map.entry("S-1", "증권신고서(신규 공모)"),
            Map.entry("S-3", "증권신고서(간이)"),
            Map.entry("S-3ASR", "증권신고서(자동 효력)"),
            Map.entry("S-8", "임직원 주식보상 등록"),
            Map.entry("424B2", "투자설명서"),
            Map.entry("11-K", "임직원 주식플랜 연간보고서"),
            Map.entry("SD", "분쟁광물 보고서"),
            Map.entry("ARS", "주주용 연간보고서")
    );

    private SecFormLabels() {
    }

    /** 사전에 없거나 Form이 비어 있으면 null이다. */
    static String labelOf(String form) {
        if (form == null) {
            return null;
        }
        String trimmed = form.strip();
        if (!trimmed.endsWith(AMENDMENT_SUFFIX)) {
            return LABELS.get(trimmed);
        }
        String base = LABELS.get(trimmed.substring(0, trimmed.length() - AMENDMENT_SUFFIX.length()));
        if (base == null) {
            return null;
        }
        return base + "(정정)";
    }
}
