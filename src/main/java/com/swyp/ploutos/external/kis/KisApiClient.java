package com.swyp.ploutos.external.kis;

import java.util.Map;

public interface KisApiClient {

    <T extends KisResponse> T get(String path, String trId, Map<String, String> queryParams, Class<T> responseType);
}
