package com.swyp.ploutos.news;

import java.util.Map;

/**
 * 원문 도메인 → 언론사명 사전. 네이버 검색 응답에 언론사명이 없어 원문 호스트로 추정한다.
 * 호스트가 등록 도메인이거나 그 하위 도메인이면 매핑한다. 사전에 없으면 언론사명을 비워 둔다.
 */
final class Publishers {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("yna.co.kr", "연합뉴스"),
            Map.entry("newsis.com", "뉴시스"),
            Map.entry("news1.kr", "뉴스1"),
            Map.entry("mk.co.kr", "매일경제"),
            Map.entry("hankyung.com", "한국경제"),
            Map.entry("mt.co.kr", "머니투데이"),
            Map.entry("edaily.co.kr", "이데일리"),
            Map.entry("sedaily.com", "서울경제"),
            Map.entry("fnnews.com", "파이낸셜뉴스"),
            Map.entry("asiae.co.kr", "아시아경제"),
            Map.entry("heraldcorp.com", "헤럴드경제"),
            Map.entry("etnews.com", "전자신문"),
            Map.entry("chosun.com", "조선일보"),
            Map.entry("joongang.co.kr", "중앙일보"),
            Map.entry("donga.com", "동아일보"),
            Map.entry("hani.co.kr", "한겨레"),
            Map.entry("khan.co.kr", "경향신문")
    );

    private Publishers() {
    }

    /** 사전에 없는 호스트면 null이다. */
    static String nameOf(String host) {
        String domain = host;
        while (domain.contains(".")) {
            String name = NAMES.get(domain);
            if (name != null) {
                return name;
            }
            domain = domain.substring(domain.indexOf('.') + 1);
        }
        return null;
    }
}
