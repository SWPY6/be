package com.swyp.ploutos.common.enums;

/**
 * 종목을 담는 시장. 코스피·코스닥·나스닥·뉴욕 네 시장을 쓴다.
 *
 * <p>{@code SP500}은 <b>쓰지 않는다.</b> 지수라서 구성 종목이 나스닥과 뉴욕에 걸쳐 있다 —
 * 시장으로 쓰면 한 종목이 두 시장에 속해 종목코드만으로 특정할 수 없고, 그러면 외부 순위를
 * 우리 종목에 잇지 못한다. 지우지 않는 것은 기존 데이터가 아직 이 값을 쓰기 때문이다.
 */
public enum MarketCode {

    KOSPI,
    KOSDAQ,
    NASDAQ,
    NYSE,
    SP500

}
