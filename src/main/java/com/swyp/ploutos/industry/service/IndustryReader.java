package com.swyp.ploutos.industry.service;

import java.util.List;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;

public interface IndustryReader {

    /** 산업 9종을 한글 표시명 가나다순으로 읽는다. */
    List<Industries> readAll();

    /** 산업 코드에 대응하는 산업. 시드가 없으면 예외를 던진다. */
    Industries read(IndustryCode code);

    /** 산업에 매핑된 종목 식별자. 매핑이 없으면 빈 목록. 국가 구분은 하지 않는다. */
    List<Long> readStockIds(Long industryId);
}
