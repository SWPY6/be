package com.swyp.ploutos.market.summary.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.summary.service.MarketSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "시장 지표", description = "시장 요약 화면의 지표 카드")
@RestController
@RequiredArgsConstructor
class MarketSummaryController {

    private final MarketSummaryService marketSummaryService;

    @Operation(
            summary = "시장 지표 카드 조회",
            description = "탭에 속한 지표의 현재값·등락폭·등락률을 표시 순서대로 준다. "
                    + "지표 구성과 순서는 서버가 정하므로 프론트는 받은 배열을 그대로 그린다. "
                    + "캐시 TTL이 10초이므로 폴링 주기는 10초를 권장한다. "
                    + "지표 하나라도 시세를 얻지 못하면 전체가 502다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "P001 region 누락 또는 허용되지 않은 값(소문자 포함)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다."}}
                                    """))),
            @ApiResponse(responseCode = "502",
                    description = "P007 시세 제공자 오류(KIS 실패, 캐시 저장소 접근 불가, 첫 조회 대기 초과)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "MarketDataUnavailableException", "code": "P007", "message": "시세 정보를 불러올 수 없습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/markets/summary")
    ApiResult<MarketSummaryResponse> summary(
            @Parameter(description = "시장 탭", example = "DOMESTIC") @RequestParam MarketRegion region
    ) {
        return ApiResult.of(MarketSummaryResponse.from(marketSummaryService.read(region)));
    }
}
