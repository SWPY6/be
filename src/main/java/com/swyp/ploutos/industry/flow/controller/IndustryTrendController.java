package com.swyp.ploutos.industry.flow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.industry.flow.IndustryTrendFilter;
import com.swyp.ploutos.industry.flow.service.IndustryTrendService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/industries")
@RequiredArgsConstructor
class IndustryTrendController {

    private final IndustryTrendService industryTrendService;

    @Operation(
            summary = "산업별 동향",
            description = """
                    산업 카드 목록을 탭에 맞게 **걸러서 정렬까지 끝낸 상태로** 돌려준다.
                    프론트는 받은 순서대로 그리면 된다 — 다시 정렬하지 않는다.

                    `avgChangeRate`가 소수 둘째 자리로 반올림된 값이라 그것으로 정렬하면 동률이
                    어긋난다. 서버는 반올림 전 값과 거래대금으로 가른다.

                    `filter=ALL`은 9개 전부를 산업명 가나다순으로, `RISING`은 오른 산업만 많이 오른
                    순으로, `FALLING`은 내린 산업만 많이 내린 순으로 준다. 등락률이 정확히 0인 산업은
                    `ALL`에만 나오므로 두 탭의 길이 합이 9보다 작을 수 있다. 전 산업이 한쪽으로 쏠린
                    날에는 반대쪽 탭이 **빈 배열**이다.

                    `rank`는 9개 전체를 놓고 매긴 순위라 `filter`에 영향받지 않는다 — `FALLING`으로
                    3건을 받아도 `7`·`8`·`9`가 온다. 카드의 "평균 등락률 N위"에는 이 값을 그대로
                    쓰고 **배열 인덱스로 세면 안 된다.**

                    `stocks`는 시가총액 상위 **0~4개**로 가변이다. 종목 상세·현재가·차트로 이동할
                    때는 `stockId`를 쓴다 — `ticker`는 화면에 표시하는 종목 코드다. `stockId`는
                    환경마다 달라질 수 있으니 저장하지 말고 그 화면에서만 쓴다. 현재가의 단위는
                    `currency`가 정한다.

                    관심 산업 고정은 받은 목록을 프론트가 앞으로 당기는 것이고, 그때도 `rank`는
                    바뀌지 않는다.

                    값은 서버가 주기적으로 미리 계산해 저장한 것이며 이 요청은 외부 시세를
                    호출하지 않는다. 폴링 주기는 10초를 권장한다.
                    """)
    @GetMapping("/trends")
    ApiResult<List<IndustryTrendResponse>> readTrends(
            @Parameter(description = "국내(KR) 또는 해외(US). 화면의 시장 토글", example = "KR")
            @RequestParam(defaultValue = "KR") Country country,

            @Parameter(description = "화면의 탭. 거르기와 정렬을 서버가 한다", example = "ALL")
            @RequestParam(defaultValue = "ALL") IndustryTrendFilter filter) {

        List<IndustryTrendResponse> trends = industryTrendService.read(country, filter).stream()
                .map(flow -> IndustryTrendResponse.from(flow, country))
                .toList();
        return ApiResult.of(trends);
    }
}
