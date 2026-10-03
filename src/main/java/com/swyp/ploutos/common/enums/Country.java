package com.swyp.ploutos.common.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import lombok.Getter;

@Getter
public enum Country {

	KR(ZoneId.of("Asia/Seoul"), LocalTime.of(15, 30), Currency.KRW),
	US(ZoneId.of("America/New_York"), LocalTime.of(16, 0), Currency.USD);

	/** 이 나라 시장의 현지 타임존. "오늘"과 거래일 계산에 쓴다. */
	private final ZoneId zoneId;

	/**
	 * 정규장 마감 시각(현지 시각). 종가가 확정되는 시점이다 —
	 * 프리마켓·애프터마켓·주간거래는 종가를 만들지 않는다.
	 *
	 * <p>조기 폐장일(연말, 미국 공휴일 전날 13:00 등)에는 실제 마감과 어긋난다. 연 수회이고
	 * 어긋나면 시간 창이 넓어질 뿐이므로 공휴일 달력을 들이지 않는다.
	 */
	private final LocalTime regularCloseTime;

	/**
	 * 이 나라 시장의 표시 통화. 금액을 화면에 쓸 때 기호를 고르는 데 쓴다.
	 *
	 * <p>값의 출처는 {@code markets.currency}이고 여기 둔 것은 그 사본이다 — 한 나라의 시장이
	 * 모두 같은 통화를 쓰기 때문에 성립한다. 나라당 시장은 이미 둘 이상이지만(KOSPI·KOSDAQ,
	 * NASDAQ·SP500) 통화는 각각 KRW·USD로 같다. 한 나라 안에 통화가 다른 시장이 생기면
	 * 이 사본을 버리고 {@code markets.currency}에서 읽어야 한다.
	 */
	private final Currency currency;

	Country(ZoneId zoneId, LocalTime regularCloseTime, Currency currency) {
		this.zoneId = zoneId;
		this.regularCloseTime = regularCloseTime;
		this.currency = currency;
	}

	/** 그 거래일의 종가가 확정된 시각. */
	public LocalDateTime closedAt(LocalDate tradeAt) {
		return tradeAt.atTime(regularCloseTime);
	}
}
