package com.swyp.ploutos.stock.movers;

/**
 * 거래량 배수의 분모가 무엇인지. 국가마다 다르다 — KIS 국내 API 에는 N일 평균이 없고
 * 전일 거래량만 있다(SPEC-stock-movers.md 「실측 기록」).
 *
 * <p>같은 {@code 3.2}가 국내에서는 "어제보다", 해외에서는 "평소보다"를 뜻한다. 값을 억지로
 * 맞추지 않고 기준을 함께 보내, 읽는 쪽이 라벨을 나누게 한다.
 */
public enum VolumeBaseline {

    /** 전일 거래량. */
    PREVIOUS_DAY,

    /** 직전 20거래일 평균 거래량. */
    AVERAGE_20D
}
