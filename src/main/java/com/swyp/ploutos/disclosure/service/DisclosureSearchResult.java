package com.swyp.ploutos.disclosure.service;

import java.util.List;

import com.swyp.ploutos.disclosure.Disclosure;

/**
 * 공시 공급자의 조회 결과. 캐시도 이 형식으로 저장하므로 공급자 포트의 내부 타입으로 두지 않는다.
 *
 * @param disclosures 정제된 공시. 보여 줄 수 없는 레코드는 이미 빠져 있다.
 * @param exhausted   조회 범위의 공시를 끝까지 받았는지
 */
public record DisclosureSearchResult(List<Disclosure> disclosures, boolean exhausted) {
}
