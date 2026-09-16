# SPEC-api-base — API 공통 틀

## 목표

모든 API가 같은 모양으로 성공하고 같은 모양으로 실패하게 한다. 컨트롤러는 응답 포맷과 예외 변환을 신경 쓰지 않는다.

이 명세는 **공통 틀만** 다룬다. 개별 도메인 API(종목, 뉴스 등)는 이 틀 위에 각자의 명세를 쓴다.

## 응답 포맷

### 성공

바디는 항상 `data` 하나로 감싼다. 컨트롤러가 `ApiResponse.of(...)`로 명시적으로 감싼다.

```json
{ "data": { "stockId": 1, "name": "삼성전자" } }
```

바디가 없는 응답(삭제 등)은 `204 No Content`로 보내고 감싸지 않는다.

### 실패

HTTP 상태코드로 실패를 구분하고, 바디는 항상 아래 세 필드다.

```json
{
  "code": "NOT_FOUND_STOCK",
  "message": "종목을 찾을 수 없습니다.",
  "errors": []
}
```

요청 검증(`@Valid`)에 실패하면 `errors`에 필드 단위 원인이 들어간다.

```json
{
  "code": "INVALID_INPUT_VALUE",
  "message": "잘못된 입력값입니다.",
  "errors": [{ "field": "name", "reason": "must not be blank" }]
}
```

| 원인 | 상태 | `code` |
|---|---|---|
| `BusinessException` | `ErrorCode.status()` | `ErrorCode` 이름 |
| `@Valid` 실패 | 400 | `INVALID_INPUT_VALUE` + `errors[]` |
| 스프링 프레임워크 예외 (없는 경로, 잘못된 JSON, 지원하지 않는 메서드 등) | 예외가 정한 상태 | 상태 이름 (`NOT_FOUND`, `BAD_REQUEST`, `METHOD_NOT_ALLOWED`) |
| 그 외 모든 예외 | 500 | `INTERNAL_SERVER_ERROR` (스택은 서버 로그에만) |

## 에러 코드

`ErrorCode`는 인터페이스다. 각 구현체는 `HttpStatus`와 한국어 메시지를 가진다.

- `common/exception/CommonErrorCode` — 도메인에 속하지 않는 코드(`INVALID_INPUT_VALUE`, `INTERNAL_SERVER_ERROR`)
- `{도메인}/XxxErrorCode` — 해당 도메인의 코드. 도메인 패키지가 소유한다 (`stock/StockErrorCode`의 `NOT_FOUND_STOCK`)

`common`은 도메인 코드를 알지 않는다. 새 도메인은 자기 enum을 추가하고 `common`을 건드리지 않는다.

이름은 접두사로 종류를 드러낸다.

| 접두사 | 용도 | 예시 |
|---|---|---|
| `NOT_FOUND_` | 리소스 미존재 | `NOT_FOUND_STOCK` |
| `NOT_ALLOWED_` | 권한·상태 불일치 | `NOT_ALLOWED_DUPLICATE_REQUEST` |
| `ALREADY_EXIST_` | 중복 | `ALREADY_EXIST_EMAIL` |
| `INVALID_` | 유효하지 않은 값 | `INVALID_INPUT_VALUE` |

서비스에서 조건 검사는 `Precondition.validate(condition, errorCode)`로 한다. 거짓이면 `BusinessException`이 난다.

## 향후 API 규약

| 항목 | 규칙 |
|---|---|
| 경로 | `/api/v1/{resources}` — 리소스는 복수형 |
| 컨트롤러 | `XxxController`, `@RestController`. `XxxService`만 주입받는다 |
| 메서드명 | `search`(목록) · `detail`(단건) · `create` · `update` · `delete` |
| 상태코드 | 목록·단건·수정 200, 생성 201, 삭제 204 |
| DTO 위치 | `{도메인}/dto/XxxRequest`, `{도메인}/dto/XxxResponse` |
| DTO 형태 | `sealed interface` + 중첩 `record`. 요청: `Filter`(모두 nullable, 검증 없음) · `Create` · `Update`(검증 적용). 응답: `Search`(요약) · `Detail`(전체) |
| 문서 | 컨트롤러 메서드에 `@Operation`, DTO에 `@Schema` |

## 인수 기준

1. 컨트롤러가 `BusinessException(errorCode)`를 던지면 `errorCode.status()`와 `{code: 이름, message, errors: []}`가 온다.
2. `@Valid` 검증에 실패하면 400과 `errors[]`에 필드명·사유가 온다.
3. 본문이 잘못된 JSON이면 400이 온다.
4. 없는 경로를 요청하면 404와 같은 모양의 JSON 바디가 온다.
5. 처리되지 않은 예외는 500 `INTERNAL_SERVER_ERROR`로 온다.
