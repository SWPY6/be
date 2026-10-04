package com.swyp.ploutos.disclosure;

import static com.swyp.ploutos.common.exception.Precondition.require;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * 공시 조회 기간. 시장 휴장 캘린더가 없어 기본 기간은 달력 기준 최근 30일이다.
 * 공급자는 {@link #searchRangeIn}의 날짜 범위로 조회하고, 결과는 기간 양 끝 날짜({@link #filedDatesIn})로,
 * 시각이 있는 공시는 {@link #contains}로 다시 거른다.
 */
public record DisclosureWindow(
        OffsetDateTime from,
        OffsetDateTime to
) {

    private static final Duration DEFAULT_LENGTH = Duration.ofDays(30);
    private static final Duration MAX_LENGTH = Duration.ofDays(90);

    /**
     * 요청 기간을 검증한다. 둘 다 없으면 {@code now} 기준 최근 30일이다.
     * 한쪽만 있거나, 순서가 뒤집혔거나, {@code to}가 미래이거나, 90일을 넘으면 잘못된 입력이다.
     */
    public static DisclosureWindow of(OffsetDateTime from, OffsetDateTime to, OffsetDateTime now) {
        if (from == null && to == null) {
            return new DisclosureWindow(now.minus(DEFAULT_LENGTH), now);
        }
        require(from != null && to != null, ErrorCode.INVALID_INPUT_VALUE);
        require(from.isBefore(to), ErrorCode.INVALID_INPUT_VALUE);
        require(!to.isAfter(now), ErrorCode.INVALID_INPUT_VALUE);
        require(Duration.between(from, to).compareTo(MAX_LENGTH) <= 0, ErrorCode.INVALID_INPUT_VALUE);
        return new DisclosureWindow(from, to);
    }

    /** 경계는 {@code from < time <= to}다. */
    public boolean contains(Instant time) {
        return time.isAfter(from.toInstant()) && !time.isAfter(to.toInstant());
    }

    /**
     * 기간 양 끝이 걸친 날짜(해당 시장 기준). 접수 시각을 모르므로 경계 날짜를 통째로 포함한다 —
     * 시작 날짜의 {@code from} 이전 공시도 들어올 수 있다.
     */
    public FiledDateRange filedDatesIn(ZoneId zone) {
        return new FiledDateRange(
                from.atZoneSameInstant(zone).toLocalDate(),
                to.atZoneSameInstant(zone).toLocalDate()
        );
    }

    /**
     * 공급자에 요청할 접수일 범위. 끝 날짜(해당 시장 날짜) 기준 최근 90일로 고정해, 끝 날짜가 같으면
     * 기간을 어떻게 고르든 같은 범위(같은 캐시)를 쓴다 — 시작만 바꿔 캐시를 피할 수 없다.
     * 기간은 90일 이하라 이 범위 안에 든다.
     */
    public FiledDateRange searchRangeIn(ZoneId zone) {
        LocalDate end = to.atZoneSameInstant(zone).toLocalDate();
        return new FiledDateRange(end.minusDays(MAX_LENGTH.toDays()), end);
    }

    /** 접수일 조회 범위. 양 끝을 포함한다. */
    public record FiledDateRange(LocalDate from, LocalDate to) {

        public boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }
}
