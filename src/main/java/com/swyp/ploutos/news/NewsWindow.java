package com.swyp.ploutos.news;

import static com.swyp.ploutos.common.exception.Precondition.require;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * 뉴스 조회 기간. 경계는 {@code from < publishedAt <= to}다.
 * 시장 휴장 캘린더가 없어 기본 기간은 달력 기준 최근 7일이다.
 */
public record NewsWindow(
        OffsetDateTime from,
        OffsetDateTime to
) {

    private static final Duration MAX_LENGTH = Duration.ofDays(7);

    /**
     * 요청 기간을 검증한다. 둘 다 없으면 {@code now} 기준 최근 7일이다.
     * 한쪽만 있거나, 순서가 뒤집혔거나, {@code to}가 미래이거나, 7일을 넘으면 잘못된 입력이다.
     */
    public static NewsWindow of(OffsetDateTime from, OffsetDateTime to, OffsetDateTime now) {
        if (from == null && to == null) {
            return new NewsWindow(now.minus(MAX_LENGTH), now);
        }
        require(from != null && to != null, ErrorCode.INVALID_INPUT_VALUE);
        require(from.isBefore(to), ErrorCode.INVALID_INPUT_VALUE);
        require(!to.isAfter(now), ErrorCode.INVALID_INPUT_VALUE);
        require(Duration.between(from, to).compareTo(MAX_LENGTH) <= 0, ErrorCode.INVALID_INPUT_VALUE);
        return new NewsWindow(from, to);
    }

    public boolean contains(OffsetDateTime time) {
        return time.isAfter(from) && !time.isAfter(to);
    }

    /** 이 시각까지 거슬러 올라갔다면 기간의 시작에 닿은 것이다. */
    public boolean reachedStartBy(OffsetDateTime oldest) {
        return !oldest.isAfter(from);
    }
}
