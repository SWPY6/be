package com.swyp.ploutos.stock.movers;

import com.swyp.ploutos.common.enums.Country;

/**
 * 목록을 고르는 조건. <b>모집단과 정렬을 함께 정한다</b> — "상승 TOP"은 정렬 기준이 아니라
 * "전 종목 중 상승률 상위"라는 한 덩어리다. 그래서 조건을 바꾸면 목록·순위·결과 수가 모두 바뀐다.
 */
public enum MoverCondition {

    /** 전체 종목. 우리가 아는 종목만 셀 수 있어 외부 순위를 쓰지 않는다. */
    ALL,
    RISING,
    FALLING,
    VOLUME_SURGE;

    /**
     * 순위 조건의 국내 상한. KIS 등락률·거래량 순위가 한 번에 30건까지만 주고 이어 받을 수
     * 없어, 코스피와 코스닥을 나눠 불러 만드는 수다.
     */
    private static final int RANKING_DOMESTIC_LIMIT = 60;

    /** 순위 조건의 해외 상한. KIS 가 한 번에 주는 수이고 요구사항의 100개와 같다. */
    private static final int RANKING_OVERSEAS_LIMIT = 100;

    /**
     * 전체 종목의 상한. 화면이 한 페이지에 100개씩 최대 5페이지를 보여주므로(RQ-0708)
     * 그보다 많이 보내도 쓰이지 않는다.
     */
    private static final int ALL_LIMIT = 500;

    /** 외부 순위 API로 답할 수 있는 조건인지. {@code ALL}은 순위가 아니라 목록이라 아니다. */
    public boolean isRanking() {
        return this != ALL;
    }

    /**
     * 응답에 실을 최대 건수. <b>경로와 무관하게 같다</b> — 산업을 고르고 말고에 따라 같은
     * 조건의 건수 규칙이 달라지면 안 된다.
     *
     * <p>순위 조건과 전체 종목의 상한이 다른 것은 출처가 달라서다. 순위의 60·100은 KIS 가
     * 그만큼만 준다는 <b>외부 제약</b>이고, 전체 종목의 500은 화면이 그만큼만 보여준다는
     * <b>내부 제약</b>이다.
     */
    public int limitIn(Country country) {
        if (this == ALL) {
            return ALL_LIMIT;
        }
        return country == Country.KR ? RANKING_DOMESTIC_LIMIT : RANKING_OVERSEAS_LIMIT;
    }
}
