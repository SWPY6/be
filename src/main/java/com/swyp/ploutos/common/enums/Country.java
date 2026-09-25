package com.swyp.ploutos.common.enums;

import java.time.ZoneId;

import lombok.Getter;

@Getter
public enum Country {
	
	KR(ZoneId.of("Asia/Seoul")),
	US(ZoneId.of("America/New_York"));

	/** 이 나라 시장의 현지 타임존. "오늘"과 거래일 계산에 쓴다. */
	private final ZoneId zoneId;

	Country(ZoneId zoneId) {
		this.zoneId = zoneId;
	}
}
