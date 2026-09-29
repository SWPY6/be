package com.swyp.ploutos.news.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsArticle.LinkKind;
import com.swyp.ploutos.news.service.NewsCallBudget;
import com.swyp.ploutos.news.service.NewsProvider.SearchResult;

import tools.jackson.databind.json.JsonMapper;

class NaverNewsProviderTest {

    private static final String BASE_URL = "https://naver.test";
    private static final String QUERY = "삼성전자";
    private static final String SEARCH_URL = BASE_URL + NaverNewsProvider.PATH;

    private static final String ONE_ITEM = """
            {
              "lastBuildDate": "Wed, 30 Sep 2026 14:00:00 +0900",
              "total": 1, "start": 1, "display": 1,
              "items": [{
                "title": "<b>삼성전자</b>, &quot;HBM&quot; 증설",
                "originallink": "https://news.mt.co.kr/mtview.php?no=1",
                "link": "https://n.news.naver.com/mnews/article/008/1",
                "description": "<b>삼성전자</b>가 증설한다",
                "pubDate": "Wed, 30 Sep 2026 09:12:00 +0900"
              }]
            }
            """;

    private MockRestServiceServer server;
    private CountingBudget budget;
    private NaverNewsProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        budget = new CountingBudget(Integer.MAX_VALUE);
        provider = new NaverNewsProvider(builder.build(), budget, JsonMapper.builder().build());
    }

    @Test
    void 검색어를_UTF8로_인코딩하고_최신순_JSON_1페이지를_요청한다() {
        // given
        String encodedQuery = URLEncoder.encode(QUERY, StandardCharsets.UTF_8);
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(requestTo(Matchers.containsString("query=" + encodedQuery)))
                .andExpect(queryParam("display", "100"))
                .andExpect(queryParam("start", "1"))
                .andExpect(queryParam("sort", "date"))
                .andExpect(queryParam("format", "json"))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        provider.search(QUERY);

        // then
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 응답_기사를_정제하고_pubDate를_오프셋_시각으로_읽는다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        NewsArticle article = result.articles().getFirst();
        assertThat(article.title()).isEqualTo("삼성전자, \"HBM\" 증설");
        assertThat(article.summary()).isEqualTo("삼성전자가 증설한다");
        assertThat(article.linkKind()).isEqualTo(LinkKind.ORIGINAL);
        assertThat(article.publisherName()).isEqualTo("머니투데이");
        assertThat(article.publishedAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 30, 9, 12, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void 네이버가_JSON을_text_plain으로_보내도_읽는다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(ONE_ITEM, MediaType.parseMediaType("text/plain;charset=UTF-8")));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.articles()).hasSize(1);
    }

    @Test
    void 전체_결과가_1페이지에_다_들어오면_검색_결과를_끝까지_받은_것이다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.exhausted()).isTrue();
    }

    @Test
    void 전체_결과가_받은_건수보다_많으면_결과가_더_남은_것이다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(ONE_ITEM.replace("\"total\": 1", "\"total\": 5000"), MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.exhausted()).isFalse();
    }

    @Test
    void 검색_결과가_없으면_빈_후보를_돌려준다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess("{\"total\": 0, \"items\": []}", MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.articles()).isEmpty();
        assertThat(result.exhausted()).isTrue();
    }

    @Test
    void 쓸_수_없는_레코드는_빼고_나머지를_돌려준다() {
        // given
        String body = ONE_ITEM.replace("\"items\": [{", """
                "items": [{"title": "", "originallink": "https://a.com/1", "link": "", "description": "", "pubDate": "Wed, 30 Sep 2026 09:00:00 +0900"}, {""");
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.articles()).hasSize(1);
    }

    @Test
    void 레코드가_있는데_모두_쓸_수_없으면_공급_실패다() {
        // given
        String body = ONE_ITEM.replace("Wed, 30 Sep 2026 09:12:00 +0900", "not a date");
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when & then
        assertError(ErrorCode.NEWS_UNAVAILABLE);
    }

    @Test
    void 서버_오류_후_재시도에_성공하면_결과를_돌려주고_예산은_두_번_쓴다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL))).andRespond(withServerError());
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        SearchResult result = provider.search(QUERY);

        // then
        assertThat(result.articles()).hasSize(1);
        assertThat(budget.consumed).isEqualTo(2);
        server.verify();
    }

    @Test
    void 서버_오류가_두_번_나면_공급_실패다() {
        // given
        server.expect(times(2), requestTo(Matchers.startsWith(SEARCH_URL))).andRespond(withServerError());

        // when & then
        assertError(ErrorCode.NEWS_UNAVAILABLE);
        server.verify();
    }

    @Test
    void 타임아웃이_두_번_나면_공급_실패다() {
        // given
        server.expect(times(2), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when & then
        assertError(ErrorCode.NEWS_UNAVAILABLE);
        server.verify();
    }

    @Test
    void 인증_오류는_재시도하지_않고_공급_실패다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertError(ErrorCode.NEWS_UNAVAILABLE);
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 공급자_호출_한도에_걸리면_한도_초과다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertError(ErrorCode.NEWS_QUOTA_EXCEEDED);
        server.verify();
    }

    @Test
    void JSON이_아닌_응답이면_공급_실패다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL)))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.APPLICATION_JSON));

        // when & then
        assertError(ErrorCode.NEWS_UNAVAILABLE);
    }

    @Test
    void 예산이_없으면_네이버를_호출하지_않는다() {
        // given
        budget = new CountingBudget(0);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new NaverNewsProvider(builder.build(), budget, JsonMapper.builder().build());

        // when & then
        assertError(ErrorCode.NEWS_QUOTA_EXCEEDED);
        server.verify();
    }

    @Test
    void 재시도할_예산이_없으면_재시도하지_않고_한도_초과다() {
        // given
        budget = new CountingBudget(1);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new NaverNewsProvider(builder.build(), budget, JsonMapper.builder().build());
        server.expect(once(), requestTo(Matchers.startsWith(SEARCH_URL))).andRespond(withServerError());

        // when & then
        assertError(ErrorCode.NEWS_QUOTA_EXCEEDED);
        server.verify();
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> provider.search(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    private static final class CountingBudget implements NewsCallBudget {

        private final int limit;
        private int consumed;

        CountingBudget(int limit) {
            this.limit = limit;
        }

        @Override
        public void consume() {
            if (consumed >= limit) {
                throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
            }
            consumed++;
        }
    }
}
