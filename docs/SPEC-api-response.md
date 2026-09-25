# 명세: 공통 API 응답 (api-response)

이미 구현된 공통 성공·실패 응답 형식을 명세로 정리한 문서다. 개별 API의 요청·응답은 Swagger(`/swagger-ui.html`)를 참고한다.

## 내가 세운 전제

1. 모든 API는 JSON을 반환하는 REST API다.
2. 응답 결과는 HTTP 상태 코드(상태줄)로 전달한다. JSON 본문에는 `status`를 넣지 않는다.
3. 성공과 실패는 최상위 키로 구분한다. 성공은 `data`, 실패는 `error`다.
4. 클라이언트는 `error.code`로 오류를 구분한다. `error.message`는 사용자에게 보여줄 수 있는 공통 안내 문구다.
5. 오류 정의(HTTP 상태·코드·이름·메시지)는 `ErrorCode` 한 곳에서만 관리한다.
6. Spring Security 필터에서 처리되는 인증·인가 실패(401/403)는 이 명세의 범위 밖이다.

## 목표

프론트엔드가 모든 API의 성공·실패 응답을 같은 방식으로 파싱하게 한다. 서버 코드는 비즈니스 규칙을 어기면 `ErrorCode`로 예외만 던지고, 응답 변환은 `GlobalExceptionHandler`가 맡는다.

성공 기준은 "어떤 API든 `data` 또는 `error` 중 정확히 하나만 최상위에 존재하고, 실패 시 `error.code`가 항상 있다"이다.

## 응답 규칙

### 성공

- 반환할 데이터를 `ApiResponse<T>`의 `data`에 담는다. 목록도 `data`에 담고, 빈 목록은 `{"data": []}`다.
- 성공 응답에는 `error`가 없다.
- `ApiResponse`는 HTTP 상태를 설정하지 않는다. 200이 아닌 상태가 필요하면 컨트롤러가 `ResponseEntity`로 지정한다.

```json
{ "data": { "id": 10, "name": "삼성전자" } }
```

### 본문 데이터가 없는 성공 응답

- 리소스 생성 성공은 `201 Created`로 응답하고 생성된 데이터를 `data`에 담는다.
- 반환할 데이터가 없는 수정·삭제 성공은 `204 No Content`로 응답하며 본문을 포함하지 않는다.
- `{"data": null}` 형식은 사용하지 않는다.

상태 코드는 컨트롤러가 `ResponseEntity`로 지정하는 규약이다. 공통 코드(`ApiResponse`)가 강제하지 않으므로 각 API의 테스트에서 검증한다.

### 실패

- 실패 응답에는 `data`가 없다.
- `error.name`, `error.code`, `error.message`는 `ErrorCode`의 값을 그대로 쓴다.
- `error.errors`는 필드 검증 오류가 있을 때만 포함하고, 비어 있으면 생략한다.
- 예상하지 못한 예외의 상세 원인(메시지, 스택)은 응답에 노출하지 않고 서버 로그에만 남긴다.
- 필드 검증 오류는 실패한 필드명(`field`)만 알려주고, 검증 메시지(`reason`)는 응답에 넣지 않는다. 검증 애너테이션의 메시지는 서버 내부 규칙을 드러낼 수 있고, 안내 문구는 `error.message`로 충분하다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `error.name` | O | `ErrorCode`에 정의된 오류 이름. 실제 Java 예외 클래스명과 다를 수 있다. |
| `error.code` | O | 오류 구분 코드. HTTP 상태와 무관하게 `P001`부터 순번으로 채번한다. |
| `error.message` | O | 공통 안내 메시지 |
| `error.errors` | 조건부 | 필드 검증 오류 목록. 없으면 생략 |
| `error.errors[].field` | O | 검증에 실패한 필드명 |

필드 검증에 실패한 경우(HTTP 400)의 응답이다.

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다.",
    "errors": [{ "field": "name" }]
  }
}
```

주식을 찾을 수 없는 경우(HTTP 404)의 응답이다. `errors`는 생략된다.

```json
{
  "error": {
    "name": "StockNotFoundException",
    "code": "P002",
    "message": "주식을 찾을 수 없습니다."
  }
}
```

### 오류 코드

| HTTP 상태 | 코드 | 오류 이름 | 메시지 | 발생 조건 |
| --- | --- | --- | --- | --- |
| 400 | `P001` | `InvalidInputValueException` | 잘못된 입력값입니다. | `@Valid` 검증 실패, 잘못된 JSON, 필수 파라미터 누락, 파라미터 타입 불일치, 또는 `BusinessException(INVALID_INPUT_VALUE)` |
| 404 | `P002` | `StockNotFoundException` | 주식을 찾을 수 없습니다. | `BusinessException(STOCK_NOT_FOUND)` |
| 404 | `P003` | `NoResourceFoundException` | 요청한 리소스를 찾을 수 없습니다. | 매핑되지 않은 경로 요청 |
| 405 | `P004` | `MethodNotAllowedException` | 지원하지 않는 HTTP 메서드입니다. | 지원하지 않는 HTTP 메서드 요청 |
| 415 | `P005` | `UnsupportedMediaTypeException` | 지원하지 않는 미디어 타입입니다. | 지원하지 않는 `Content-Type` 요청 |
| 500 | `P006` | `InternalServerErrorException` | 서버 내부 오류가 발생했습니다. | 핸들러에 전달된 그 밖의 모든 `Exception` |
| 502 | `P007` | `MarketDataUnavailableException` | 시세 정보를 불러올 수 없습니다. | `BusinessException(MARKET_DATA_UNAVAILABLE)` — 외부 시세 제공자(KIS) 호출 실패. `SPEC-kis-client.md` 참고 |

### 예외 → 응답 매핑

| 예외 | HTTP | 코드 | `errors` |
| --- | --- | --- | --- |
| `BusinessException` | 예외의 `ErrorCode.status()` | 예외의 `ErrorCode` | 생략 |
| `MethodArgumentNotValidException` | 400 | `P001` | `BindingResult`의 필드 오류 (`field`만) |
| `HttpMessageNotReadableException` (잘못된 JSON) | 400 | `P001` | 생략 |
| `MissingRequestValueException` (필수 파라미터 누락) | 400 | `P001` | 생략 |
| `MethodArgumentTypeMismatchException` (파라미터 타입 불일치) | 400 | `P001` | 생략 |
| `NoResourceFoundException` | 404 | `P003` | 생략 |
| `HttpRequestMethodNotSupportedException` | 405 | `P004` | 생략 |
| `HttpMediaTypeNotSupportedException` | 415 | `P005` | 생략 |
| 그 외 `Exception` | 500 | `P006` | 생략 (원인은 로그) |

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*GlobalExceptionHandlerIntegrationTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/common/response/    → ApiResponse, ErrorResponse
src/main/java/com/swyp/ploutos/common/exception/   → ErrorCode, BusinessException, Precondition, GlobalExceptionHandler
src/test/java/com/swyp/ploutos/common/response/    → ApiResponseTest
src/test/java/com/swyp/ploutos/common/exception/   → 예외·핸들러 테스트
```

## 코드 스타일

- 응답 객체는 `record`, 접근자는 `errorCode.status()`처럼 `get` 없이 쓴다 (`@Getter`/`@Data` 금지).
- `else` 없이 guard clause를 쓴다.
- 새 오류는 `ErrorCode`에 항목을 추가하고, 서버 코드는 아래처럼 사용한다.

```java
// 성공
return ApiResponse.of(result);

// 실패
throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
Precondition.require(stock != null, ErrorCode.STOCK_NOT_FOUND);
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- 단위 테스트: `ApiResponseTest`, `BusinessExceptionTest`, `PreconditionTest`, `GlobalExceptionHandlerTest` — 스프링 컨텍스트 없이 검증한다.
- 통합 테스트: `GlobalExceptionHandlerIntegrationTest` — `@WebMvcTest` + MockMvc로 실제 JSON 본문과 상태 코드를 검증한다. 보안 필터는 비활성화(`addFilters = false`)하므로 인증·인가 응답은 검증하지 않는다.
- 새 `ErrorCode`를 추가하면 그 코드의 HTTP 상태·`error.code`·`error.message`를 검증하는 테스트를 함께 추가한다.

## 경계

- **항상:** 실패 응답은 `ErrorCode`를 거쳐 만든다. 커밋 전 `./gradlew test`를 실행한다. 응답 형식이 바뀌면 이 명세를 먼저 고친다.
- **먼저 묻기:** 기존 `ErrorCode`의 코드·이름·메시지 변경(프론트엔드 계약이 깨진다), 응답 최상위 필드 추가, 핸들러가 처리하는 예외 종류 추가.
- **절대 안 함:** 응답 본문에 `status`나 예외 스택·내부 메시지·검증 메시지(`reason`) 노출, 컨트롤러에서 임의의 오류 JSON 직접 생성, 승인 없이 실패하는 테스트 삭제.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | `ApiResponse.of(data)`는 `data`를 그대로 담는다. | `ApiResponseTest.데이터를_전달하면_응답에_담긴다` |
| 2 | 성공 응답은 HTTP 200, `{"data": ...}`이며 `status`와 `error`가 없다. | `성공하면_종목정보를_data에_담아_반환한다` |
| 3 | `BusinessException(INVALID_INPUT_VALUE)`는 400 / `P001`이며 `errors`가 없다. | `비즈니스예외가_발생하면_상태코드와_JSON을_반환한다` |
| 4 | `BusinessException(STOCK_NOT_FOUND)`는 404 / `P002`이며 `name`·`code`·`message`가 채워진다. | `주식이_없으면_필수_오류코드와_이름을_반환한다` |
| 5 | 없는 경로는 404 / `P003`이다. | `없는_경로를_요청하면_404와_JSON을_반환한다` |
| 6 | 필드 검증 실패는 400 / `P001`이며 `errors[].field`만 포함하고 `reason`은 없다. | `필드검증에_실패하면_400과_필드오류를_반환한다` |
| 7 | 예상하지 못한 예외는 500 / `P006`이며 `data`가 없고 상세 원인을 노출하지 않는다. | `예상하지_못한_예외가_발생하면_500과_P006를_반환한다` |
| 8 | `Precondition.require`는 조건이 거짓일 때만 `BusinessException`을 던진다. | `PreconditionTest` |
| 9 | 잘못된 JSON 요청은 400 / `P001`이며 `errors`가 없다. | `잘못된_JSON을_요청하면_400과_P001을_반환한다` |
| 10 | 필수 파라미터를 누락하면 400 / `P001`이다. | `필수_파라미터를_누락하면_400과_P001을_반환한다` |
| 11 | 파라미터 타입이 일치하지 않으면 400 / `P001`이다. | `파라미터_타입이_일치하지_않으면_400과_P001을_반환한다` |
| 12 | 지원하지 않는 HTTP 메서드는 405 / `P004`이다. | `지원하지_않는_HTTP_메서드를_요청하면_405와_P004를_반환한다` |
| 13 | 지원하지 않는 미디어 타입은 415 / `P005`이다. | `지원하지_않는_미디어_타입을_요청하면_415와_P005를_반환한다` |

## 추후 구현

1. **인증·인가 실패(401/403) 응답을 공통 `error` 형식으로 맞춘다.** 현재는 Spring Security 기본 응답이다. 로그인 기능을 붙일 때 구현한다.
