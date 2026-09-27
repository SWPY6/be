package com.swyp.ploutos.common.enums;

public enum IndustryCode {

    AUTOMOBILE("자동차"),
    CONSTRUCTION("건설"),
    TRANSPORT("운송"),
    RETAIL("유통"),
    FOOD_BEVERAGE("음식료"),
    TELECOM("통신"),
    STEEL("철강"),
    ENERGY("에너지"),
    CHEMICAL("화학");

    private final String displayName;

    IndustryCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

}
