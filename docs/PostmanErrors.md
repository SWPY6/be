# Postman 오류 응답 확인

- `postman` 프로필에서만 오류 확인용 컨트롤러를 등록한다.
- 기존 Spring Security 인증을 유지한다. 요청은 Basic Auth로 인증한다.
- GET `/local-test/errors/success`: HTTP 200과 `{"data":{"id":10,"name":"삼성전자"}}` 샘플 응답.
- GET `/local-test/errors/p001`: BusinessException으로 HTTP 400 / P001.
- GET `/local-test/errors/p002`: BusinessException으로 HTTP 404 / P002.
- GET `/local-test/errors/p003`: 매핑을 만들지 않고 실제 없는 경로로 HTTP 404 / P003.
- GET `/local-test/errors/p004`: IllegalStateException으로 HTTP 500 / P004.
- DB 데이터를 읽거나 변경하지 않는다.

## 인수 기준

- 인증한 요청이 각 HTTP 상태와 error.code를 반환한다.
- 인증하지 않은 요청은 401을 반환한다.
- postman 프로필이 없으면 테스트 컨트롤러가 등록되지 않는다.
