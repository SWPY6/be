package com.swyp.ploutos.disclosure.dart;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;

/**
 * DART 요청 공통 처리. 매 시도 전에 호출 예산을 소비하고, 서버 오류·타임아웃만 한 번 재시도한다.
 * 429는 호출 제한, 그 밖의 4xx는 요청·인증 오류다. 공시검색과 고유번호 파일은 상한이 다른 예산을 쓰므로
 * 빈으로 두지 않고 공급자마다 자기 예산으로 만든다.
 * 요청 URL에 인증키가 붙으므로 예외 메시지를 로그에 남기지 않는다.
 */
final class DartFetcher {

    private static final Logger log = LoggerFactory.getLogger(DartFetcher.class);

    private final RestClient dartRestClient;
    private final DisclosureCallBudget budget;

    DartFetcher(RestClient dartRestClient, DisclosureCallBudget budget) {
        this.dartRestClient = dartRestClient;
        this.budget = budget;
    }

    /** 본문을 문자열로 돌려준다. 실패하면 {@code DISCLOSURE_UNAVAILABLE}·{@code DISCLOSURE_QUOTA_EXCEEDED}다. */
    String get(Function<UriBuilder, URI> uri, String api) {
        String body = withRetry(api, () -> request(
                api, spec -> spec.uri(uri), in -> new String(in.readAllBytes(), StandardCharsets.UTF_8)
        ));
        if (body.isBlank()) {
            throw unavailable(api, "빈 응답", null);
        }
        return body;
    }

    /** 본문을 바이트로 돌려준다. {@code maxBytes}를 넘으면 끝까지 읽지 않고 멈춘다. */
    byte[] getBytes(String path, String api, long maxBytes) {
        return withRetry(api, () -> request(api, spec -> spec.uri(path), in -> readAtMost(in, maxBytes, api)));
    }

    private <T> T withRetry(String api, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("DART {} 호출이 일시적으로 실패해 한 번 재시도한다. cause={}", api, e.getClass().getSimpleName());
        }
        try {
            return request.get();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unavailable(api, "재시도 후에도 실패", e);
        }
    }

    private <T> T request(
            String api,
            Function<RestClient.RequestHeadersUriSpec<?>, RestClient.RequestHeadersSpec<?>> target,
            BodyReader<T> reader
    ) {
        budget.consume();
        try {
            return target.apply(dartRestClient.get()).exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.is5xxServerError()) {
                    throw new HttpServerErrorException(status);
                }
                if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    log.error("DART {} 호출 한도에 걸렸다. status={}", api, status);
                    throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
                }
                if (!status.is2xxSuccessful()) {
                    throw unavailable(api, "요청·인증 오류 status=" + status, null);
                }
                return reader.read(response.getBody());
            });
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 재시도 여부는 withRetry가 정한다.
            throw e;
        } catch (RestClientException e) {
            throw unavailable(api, "응답을 읽지 못함", e);
        }
    }

    private static byte[] readAtMost(InputStream in, long limit, String api) throws IOException {
        byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 9, limit) + 1);
        if (bytes.length > limit) {
            throw unavailable(api, "파일이 " + limit + "바이트를 넘음", null);
        }
        return bytes;
    }

    // 예외 메시지에는 인증키가 든 요청 URL이 들어 있을 수 있어 클래스명만 남긴다.
    static BusinessException unavailable(String api, String reason, Exception cause) {
        log.error("DART {} 실패: {}{}", api, reason, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @FunctionalInterface
    private interface BodyReader<T> {

        T read(InputStream body) throws IOException;
    }
}
