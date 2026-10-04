package com.swyp.ploutos.disclosure.sec;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;

/** 소비 횟수를 세고, 한도를 넘으면 실제 예산처럼 한도 초과를 던진다. */
final class CountingBudget implements DisclosureCallBudget {

    private final int limit;
    int consumed;

    CountingBudget(int limit) {
        this.limit = limit;
    }

    @Override
    public void consume() {
        if (consumed >= limit) {
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
        consumed++;
    }
}
