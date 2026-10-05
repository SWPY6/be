package com.swyp.ploutos.disclosure.service;

/**
 * 공시 공급자의 일일 호출 예산. 공급자를 한 번 호출할 때마다(재시도·법인 코드 파일 포함) 먼저 소비한다.
 */
public interface DisclosureCallBudget {

    /**
     * 호출 한 번을 소비한다. 오늘 한도를 넘었거나 예산을 확인할 수 없으면
     * {@code DISCLOSURE_QUOTA_EXCEEDED}를 던지며, 호출자는 공급자를 부르지 않아야 한다.
     */
    void consume();
}
