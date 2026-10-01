package com.swyp.ploutos.industry.flow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.industry.flow.service.IndustryNewsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/industries")
@RequiredArgsConstructor
class IndustryNewsController {

    private final IndustryNewsService industryNewsService;

    @Operation(
            summary = "오늘의 핵심 뉴스",
            description = """
                    상승 1건과 하락 1건, 항상 2건을 `[RISING, FALLING]` 순으로 돌려준다.
                    배열 길이는 어떤 데이터에서도 2이므로 프론트는 개수를 가정해도 된다.

                    선정 규칙은 세 신호를 차례로 거른다 — 등락률 부호로 상승군·하락군을 나누고,
                    그중 거래대금이 그 산업의 20거래일 평균 이상인 첫 산업을 고르고, 같은 조건이면
                    등락률이 큰 쪽을 쓴다. 조건을 만족하는 산업이 없으면 등락률만으로 고르고
                    `selectedBy`를 `CHANGE_RATE_ONLY`로 알린다.

                    `rank`는 9개 산업 중 등락률 순위이고 **선정 순서와 무관하다** — 등락률 1위가
                    거래대금 조건을 통과하지 못하면 3위가 뽑히고 `rank`는 3이 된다. 카드의
                    "평균 등락률 N위"에는 이 값을 그대로 쓴다.

                    `news`는 가격 움직임 전후에 발표된 관련 뉴스다. 없으면 빈 배열이며 그 영역을
                    생략해도 된다. 가격 변동의 원인이 아니라 함께 확인된 맥락이므로, 카드마다
                    그 취지의 안내 문구를 함께 표시한다.

                    값은 서버가 주기적으로 미리 계산해 저장한 것이며 이 요청은 외부 시세를
                    호출하지 않는다. 폴링 주기는 1분을 권장한다.
                    """)
    @GetMapping("/news")
    ApiResult<List<IndustryNewsResponse>> readNews(
            @Parameter(description = "국내(KR) 또는 해외(US). 화면의 시장 토글", example = "KR")
            @RequestParam(defaultValue = "KR") Country country) {

        List<IndustryNewsResponse> cards = industryNewsService.read(country).stream()
                .map(detail -> IndustryNewsResponse.from(detail, country))
                .toList();
        return ApiResult.of(cards);
    }
}
