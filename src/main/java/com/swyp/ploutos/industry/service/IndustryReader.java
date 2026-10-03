package com.swyp.ploutos.industry.service;

import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;

public interface IndustryReader {

    /** 산업 9종을 한글 표시명 가나다순으로 읽는다. */
    List<Industries> readAll();

    /** 산업 코드에 대응하는 산업. 시드가 없으면 예외를 던진다. */
    Industries read(IndustryCode code);

    /** 산업에 매핑된 종목 식별자. 매핑이 없으면 빈 목록. 국가 구분은 하지 않는다. */
    List<Long> readStockIds(Long industryId);

    /**
     * 산업에 매핑된 종목 중 그 국가의 것만. 매핑이 없으면 빈 목록.
     *
     * <p>산업 하나에 국내·해외 종목이 함께 매핑되므로, 한 국가의 결과만 다루는 호출자는
     * {@link #readStockIds(Long)}가 아니라 이것을 쓴다. 전부를 받아 거르려면 종목마다
     * 시장을 다시 읽어야 하지만 이 메서드는 조회 한 번으로 끝난다.
     */
    List<Long> readStockIds(Long industryId, Country country);
}
