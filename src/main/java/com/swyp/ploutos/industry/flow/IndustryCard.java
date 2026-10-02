package com.swyp.ploutos.industry.flow;

/**
 * 오늘의 핵심 뉴스 카드 한 장. 고른 산업과 "왜 골랐는지"를 함께 들고 다닌다.
 *
 * <p>관련 뉴스는 여기 없다. 선정이 끝난 뒤에 붙이므로 서비스 계층에서 합친다.
 */
public record IndustryCard(
        RankedIndustryFlow flow,
        Direction direction,
        SelectedBy selectedBy
) {

    /** 상승 카드인지 하락 카드인지. 등락률의 부호가 아니라 카드의 자리를 뜻한다. */
    public enum Direction {
        RISING,
        FALLING
    }

    public enum SelectedBy {
        /** 거래대금이 그 산업의 20거래일 평균 이상이어서 골랐다. */
        MATCHED,
        /** 조건을 만족하는 산업이 없어 등락률만으로 골랐다. */
        CHANGE_RATE_ONLY
    }
}
