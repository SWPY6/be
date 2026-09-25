package com.swyp.ploutos.external.kis.auth;

public interface KisAccessTokenProvider {

    String accessToken();

    void invalidate();
}
