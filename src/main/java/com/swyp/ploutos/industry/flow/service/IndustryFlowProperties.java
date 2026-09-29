package com.swyp.ploutos.industry.flow.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 산업 흐름 갱신 설정. 조절 장치는 초당 호출 수 하나뿐이고, 한 바퀴(= 갱신 주기)는
 * {@code 대상 종목 수 / callsPerSecond}로 정해진다.
 *
 * <p>"몇 분 주기"로 정하지 않는 이유는 KIS 한도가 평균이 아니라 순간 부하로 판정되기 때문이다.
 * 주기를 늘려도 도는 동안의 초당 호출 수는 그대로여서, 주기만 늘리면 충돌이 일어나는 시간
 * 비율만 줄고 강도는 낮아지지 않는다.
 */
@ConfigurationProperties(prefix = "ploutos.industry-flow")
record IndustryFlowProperties(int callsPerSecond) {

    private static final int MILLIS_PER_SECOND = 1000;

    IndustryFlowProperties {
        if (callsPerSecond <= 0) {
            throw new IllegalArgumentException("ploutos.industry-flow.calls-per-second 는 1 이상이어야 합니다.");
        }
    }

    /** 종목 시세 조회 사이에 쉬는 시간. 외부 호출 속도의 상한을 만든다. */
    Duration callInterval() {
        return Duration.ofMillis(MILLIS_PER_SECOND / callsPerSecond);
    }
}
