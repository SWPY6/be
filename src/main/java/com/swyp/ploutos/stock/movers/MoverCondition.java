package com.swyp.ploutos.stock.movers;

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

    /** 외부 순위 API로 답할 수 있는 조건인지. {@code ALL}은 순위가 아니라 목록이라 아니다. */
    public boolean isRanking() {
        return this != ALL;
    }
}
