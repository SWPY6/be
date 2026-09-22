package com.swyp.ploutos.external.kis.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

record KisTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn
) {
}
