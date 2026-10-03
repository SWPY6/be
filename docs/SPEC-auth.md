# 명세: 로그인·로그아웃 (auth)

RQ-0702(로그인·로그아웃)의 구현이다. **`loginId` 로그인**방식이며,
로그인 상태는 **서버 세션(HttpSession) 쿠키**로 유지한다.

## 내가 세운 전제

1. 비밀번호 검증은 하지 않는다. `loginId`를 아는 사람은 그 사용자로 로그인할 수 있다. 개발·데모용 임시 방식이며,
   비밀번호·소셜 로그인이 들어오면 `LoginService`의 검증만 바뀌고 세션 구조는 유지한다.
2. 회원가입(RQ-0701)은 범위 밖이다. 사용자는 DB에 이미 있다고 본다.
3. 로그인할 때 관심 산업·알림 설정을 불러오는 것(RQ-0702 후반부)은 해당 기능이 아직 없어 범위 밖이다.
4. 세션은 애플리케이션 메모리에 둔다. 서버를 재시작하면 로그아웃된다. 서버를 여러 대로 늘리면
   `spring-session-data-redis`로 옮긴다.
5. 프론트엔드(Cloudflare Pages)와 백엔드는 도메인이 달라 cross-site 요청이다. 세션 쿠키는
   `SameSite=None; Secure; HttpOnly`로 내려주고, 프론트는 요청마다 `credentials: 'include'`를 붙인다.
6. 저장 기능이 아직 없으므로, 로그인이 필요한 경로는 현재 `GET /api/v1/auth/me` 하나다.
   시장·산업·종목 조회는 비회원도 쓸 수 있다(RQ-0703).

## API

### `POST /api/v1/auth/login` — 로그인

요청

```json
{ "loginId": "user" }
```

| 상황 | HTTP | 응답 |
| --- | --- | --- |
| `loginId`의 사용자가 있고 상태가 `ACTIVE` | 200 | `{"data": {"userId": 1, "loginId": "user", "name": "유저"}}` + `Set-Cookie: JSESSIONID=...` |
| `loginId`가 비었거나 없음 | 400 | `P001` |
| 사용자가 없음, 또는 상태가 `ACTIVE`가 아님(`INACTIVE`, `WITHDRAWN`) | 401 | `P008` |

- 사용자가 없는 경우와 비활성 사용자인 경우를 구분하지 않는다(같은 `P008`). 어떤 `loginId`가 가입돼 있는지 드러내지 않기 위해서다.
- 로그인에 성공하면 세션 ID를 새로 발급한다(세션 고정 공격 방지).

### `POST /api/v1/auth/logout` — 로그아웃

| 상황 | HTTP | 응답 |
| --- | --- | --- |
| 로그인 여부와 무관 | 204 | 본문 없음. 세션이 있으면 무효화한다 |

### `GET /api/v1/auth/me` — 현재 로그인 사용자

새로고침 뒤 프론트가 로그인 상태를 확인하는 용도다. 세션 쿠키는 `HttpOnly`라 프론트가 직접 읽을 수 없다.

| 상황 | HTTP | 응답 |
| --- | --- | --- |
| 로그인 상태 | 200 | `{"data": {"userId": 1, "loginId": "user", "name": "유저"}}` |
| 비로그인(세션 없음·만료·로그아웃 후) | 401 | 본문 없음 |

- 응답은 로그인 시점에 세션에 저장한 값이다. DB를 다시 읽지 않는다.
- 비로그인 401은 Spring Security 필터가 내려주므로 `ErrorResponse` 본문이 없다(`SPEC-api-response.md` 전제 6).
  앞으로 로그인이 필요한 경로가 추가되면 같은 방식으로 401을 받는다.

### 오류 코드 (추가)

| HTTP 상태 | 코드 | 오류 이름 | 메시지 | 발생 조건 |
| --- | --- | --- | --- | --- |
| 401 | `P008` | `LoginFailedException` | 로그인에 실패했습니다. | 없는 `loginId`, 또는 `ACTIVE`가 아닌 사용자 |

## 인수 기준

1. `ACTIVE` 사용자의 `loginId`로 로그인하면 200과 사용자 정보를 받고, 받은 세션으로 `/me`를 호출하면 같은 사용자가 나온다.
2. 없는 `loginId`로 로그인하면 401과 `P008`을 받는다.
3. `INACTIVE`·`WITHDRAWN` 사용자로 로그인하면 401과 `P008`을 받는다.
4. `loginId`가 비어 있으면 400과 `P001`을 받는다.
5. 로그인 없이 `/me`를 호출하면 401을 받는다.
6. 로그아웃한 뒤 같은 세션으로 `/me`를 호출하면 401을 받는다.
7. 로그인 없이도 기존 조회 API(시장·산업·종목)는 그대로 호출된다.

## 프로젝트 구조

```
user/Users.java                        → 엔티티. canLogin() 도메인 메서드
user/repository/UserRepository.java    → findByLoginId
user/service/LoginService.java         → loginId로 로그인 가능한 사용자 확인
user/service/LoginUser.java            → 세션에 저장하는 로그인 사용자 정보
user/controller/AuthController.java    → login, logout, me
common/config/SecurityConfig.java      → /me 인증 필요, 401 진입점, 세션 저장소
```
