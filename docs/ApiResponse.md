# 공통 API 응답 가이드

현재 구현된 공통 응답 형식과 서버 코드 작성 방법을 정리한다. 응답 규칙의 명세는 [SPEC-api-response.md](docs/SPEC-api-response.md)를 참고하고, 개별 API의 요청·응답 정의는 Swagger(`/swagger-ui.html`)에서 확인한다.

## 기본 규칙

- 성공 응답은 `data`, 실패 응답은 `error`로 감싼다.
- HTTP 상태 코드는 응답 상태줄로 전달하며, JSON 본문에 `status`를 넣지 않는다.
- 실패 응답의 `name`, `code`, `message`는 `ErrorCode`에 정의된 값을 사용한다.
- 필드 검증 오류가 있을 때만 `error.errors`를 포함한다. 빈 목록은 생략한다.
- 예상하지 못한 예외의 상세 원인은 서버 로그에 기록하고 응답에는 노출하지 않는다.

## 성공 응답

`ApiResponse<T>`의 `data`에 반환할 데이터를 담는다. 다음은 HTTP 200 응답 예시이며, 데이터 필드는 API마다 다르다.

```json
{
  "data": {
    "id": 10,
    "name": "삼성전자"
  }
}
```

목록도 동일하게 `data`에 담는다. 빈 목록은 `{"data": []}`로 표현할 수 있다.

```java
return ApiResponse.of(result);
```

`ApiResponse` 자체는 HTTP 상태를 설정하지 않는다. 별도 상태가 필요한 컨트롤러에서는 `ResponseEntity` 등으로 지정한다.

## 실패 응답

다음은 주식을 찾을 수 없을 때의 HTTP 404 응답이다.

```json
{
  "error": {
    "name": "StockNotFoundException",
    "code": "P002",
    "message": "주식을 찾을 수 없습니다."
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `error.name` | `ErrorCode`에 정의된 오류 이름. 실제로 발생한 Java 예외 클래스명과는 다를 수 있다. |
| `error.code` | 오류를 구분하는 필수 코드. 클라이언트에서 오류별로 처리할 때 사용한다. |
| `error.message` | 오류에 대한 공통 안내 메시지 |
| `error.errors` | 필드 검증 오류 목록. 필드 오류가 없으면 생략한다. |
| `error.errors[].field` | 검증에 실패한 필드명 |
| `error.errors[].reason` | 해당 필드의 검증 메시지 |

### 오류 코드

| HTTP 상태 | 코드 | 오류 이름 | 메시지 |
| --- | --- | --- | --- |
| 400 Bad Request | `P001` | `InvalidInputValueException` | 잘못된 입력값입니다. |
| 404 Not Found | `P002` | `StockNotFoundException` | 주식을 찾을 수 없습니다. |
| 404 Not Found | `P003` | `NoResourceFoundException` | 요청한 리소스를 찾을 수 없습니다. |
| 500 Internal Server Error | `P004` | `InternalServerErrorException` | 서버 내부 오류가 발생했습니다. |

### 필드 검증 실패

`MethodArgumentNotValidException`이 발생하면 HTTP 400과 `P001`을 반환한다. `BindingResult`의 필드 오류를 `error.errors`에 담는다.

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다.",
    "errors": [
      {
        "field": "name",
        "reason": "이름은 필수입니다."
      }
    ]
  }
}
```

위 `reason`은 예시다. 실제 값은 검증 애너테이션의 메시지와 검증 설정에 따라 달라진다. `BusinessException`으로 `P001`을 반환하는 경우에는 필드 오류 목록이 포함되지 않는다.

## 서버에서 사용하는 방법

비즈니스 규칙을 만족하지 못하면 해당 `ErrorCode`로 예외를 발생시킨다.

```java
throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
```

조건을 검증하는 경우에는 `Precondition.require`를 사용할 수 있다. 조건이 `false`이면 `BusinessException`이 발생한다.

```java
Precondition.require(stock != null, ErrorCode.STOCK_NOT_FOUND);
```

`GlobalExceptionHandler`가 예외를 받아 HTTP 상태와 `ErrorResponse`를 생성한다.

| 처리 대상 | 응답 결정 방식 |
| --- | --- |
| `BusinessException` | 예외에 담긴 `ErrorCode`의 상태·이름·코드·메시지 사용 |
| `MethodArgumentNotValidException` | HTTP 400, `P001`, 필드 오류 목록 |
| `NoResourceFoundException` | HTTP 404, `P003` |
| 그 외 핸들러에 전달된 `Exception` | HTTP 500, `P004`, 상세 원인은 서버 로그에 기록 |

현재 구현은 모든 잘못된 요청을 `P001`로 변환하지 않는다. 위 표에 없는 예외가 공통 핸들러에 전달되면 기본 처리인 `P004`가 적용된다. 또한 Spring Security 필터에서 처리되는 인증·인가 실패는 이 핸들러의 공통 JSON 형식이 보장되는 범위에 포함되지 않는다.

## 관련 코드와 검증

- [ApiResponse.java](src/main/java/com/swyp/ploutos/common/response/ApiResponse.java): 성공 응답 객체
- [ErrorResponse.java](src/main/java/com/swyp/ploutos/common/response/ErrorResponse.java): 실패 응답 객체와 필드 오류 변환
- [ErrorCode.java](src/main/java/com/swyp/ploutos/common/exception/ErrorCode.java): HTTP 상태와 오류 정보
- [GlobalExceptionHandler.java](src/main/java/com/swyp/ploutos/common/exception/GlobalExceptionHandler.java): 예외별 응답 처리
- [GlobalExceptionHandlerIntegrationTest.java](src/test/java/com/swyp/ploutos/common/exception/GlobalExceptionHandlerIntegrationTest.java): 성공·실패 JSON, 필드 검증, 미존재 경로, 예상하지 못한 예외 검증

통합 테스트는 보안 필터를 비활성화해 공통 응답 동작을 검증한다. 인증·인가 응답을 검증하는 테스트는 아니다.
