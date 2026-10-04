package com.swyp.ploutos.disclosure.controller;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.common.exception.Precondition;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.disclosure.service.StockDisclosureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "종목 공시", description = "종목 상세 화면의 관련 공시")
@RestController
@RequiredArgsConstructor
class StockDisclosureController {

    private final StockDisclosureService stockDisclosureService;

    @Operation(
            summary = "종목 공시 조회",
            description = "종목 법인의 공시를 최신순으로 최대 100건 준다. 국내 종목은 DART다. "
                    + "미국 종목은 아직 공급자가 없어 외부를 호출하지 않고 coverage=UNSUPPORTED_MARKET을 준다. "
                    + "DART는 접수 날짜만 주므로 기간 양 끝 날짜 전체를 조회하고(windowPrecision=DATE_EXPANDED) "
                    + "publishedAt은 null이다. 화면에는 '접수일 기준'으로 표기한다. "
                    + "요약은 제공하지 않는다(summaryStatus=UNAVAILABLE). 목록은 최대 10분 캐시한다. "
                    + "법인을 찾지 못한 종목은 coverage=UNMAPPED이며 공시 0건(coverage=COMPLETE, total=0)과 구분한다. "
                    + "가격 변동의 원인으로 단정하지 않는다는 안내를 함께 표시한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공. 공시가 없으면 items=[], total=0, coverage=COMPLETE"),
            @ApiResponse(responseCode = "400",
                    description = "P001 stockId가 양의 정수가 아님, from·to 중 하나만 있음, 오프셋 없는 시각, "
                            + "from ≥ to, 미래 to, 90일 초과 기간",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다."}}
                                    """))),
            @ApiResponse(responseCode = "404", description = "P002 없는 종목",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "StockNotFoundException", "code": "P002", "message": "주식을 찾을 수 없습니다."}}
                                    """))),
            @ApiResponse(responseCode = "502",
                    description = "P010 공시 공급자 오류(DART 실패, 인증 오류, 쓸 수 없는 응답, 법인 매핑 파일 수신 실패)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "DisclosureUnavailableException", "code": "P010", "message": "공시를 불러올 수 없습니다."}}
                                    """))),
            @ApiResponse(responseCode = "503",
                    description = "P011 공시 조회 제한(DART 일일 호출 한도, 공급자 호출 제한·점검, 캐시 저장소 접근 불가, "
                            + "법인 매핑 최초 수신 중). 즉시 재시도하지 않는다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "DisclosureQuotaExceededException", "code": "P011", "message": "공시 조회가 일시적으로 제한되었습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/stocks/{stockId}/disclosures")
    ApiResult<StockDisclosureResponse> disclosures(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId,
            // 예시 값을 두면 Swagger "Try it out"이 칸을 미리 채워 기본 기간(최근 30일)으로 조회할 수 없다.
            @Parameter(description = "기간 시작(제외). 예: 2026-09-02T14:00:00+09:00. 오프셋 필수, URL에서 +는 %2B로 인코딩. "
                    + "to와 함께 주거나 둘 다 생략(최근 30일)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "기간 끝(포함). 예: 2026-10-02T14:00:00+09:00. 오프셋 필수, 미래 불가, from과 최대 90일")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        Precondition.require(stockId > 0, ErrorCode.INVALID_INPUT_VALUE);
        return ApiResult.of(StockDisclosureResponse.from(stockDisclosureService.read(stockId, from, to)));
    }
}
