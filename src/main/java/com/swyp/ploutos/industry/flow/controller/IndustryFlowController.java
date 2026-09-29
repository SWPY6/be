package com.swyp.ploutos.industry.flow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.industry.flow.service.IndustryFlowReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/industries")
@RequiredArgsConstructor
class IndustryFlowController {

    private final IndustryFlowReader industryFlowReader;

    @Operation(
            summary = "오늘의 산업 흐름",
            description = """
                    9개 산업의 평균 등락률과 순위를 한 번에 돌려준다. 자동 순환·이전·다음 버튼은
                    이 배열 안에서 처리하면 되므로 재요청이 필요하지 않다.

                    배열은 평균 등락률이 높은 순이고 `rank`는 1부터 올라간다. 카드의
                    "평균 등락률 N위"에는 `rank`를 그대로 쓴다 — 관심 산업 고정이 들어오면 고정한
                    산업이 앞으로 오면서 배열 위치와 `rank`가 어긋나므로 배열 인덱스로 세면 안 된다.

                    값은 서버가 주기적으로 미리 계산해 저장한 것이며, 이 요청은 외부 시세를
                    호출하지 않는다. 언제 계산된 값인지는 산업마다 `calculatedAt`으로 알 수 있다.
                    폴링 주기는 1분을 권장한다.
                    """)
    @GetMapping("/flows")
    ApiResult<List<IndustryFlowResponse>> readFlows(
            @Parameter(description = "국내(KR) 또는 해외(US). 화면의 시장 토글", example = "KR")
            @RequestParam(defaultValue = "KR") Country country) {

        List<IndustryFlowResponse> flows = industryFlowReader.read(country).stream()
                .map(IndustryFlowResponse::from)
                .toList();
        return ApiResult.of(flows);
    }
}
