# 명세: KIS Open API 클라이언트 (kis-client)

한국투자증권(KIS) Open API에 접근하는 공통 인프라의 명세다. 토큰 발급·캐시, 공통 헤더, HTTP 호출, KIS 오류의 변환을 맡는다. 어떤 시세를 어떻게 해석할지는 이 모듈의 관심사가 아니다 — 응답 필드 파싱은 `stock-daily-price`, `stock-quote`의 어댑터가 담당한다.

기능 맵: `CAPABILITY-MAP.md`. 의존 대상 없음.

## 내가 세운 전제

1. 실전 서버(`https://openapi.koreainvestment.com:9443`)와 실전 앱키를 쓴다. 모의투자 서버는 쓰지 않는다.
2. 앱키·시크릿은 환경변수 `KIS_APP_KEY`, `KIS_APP_SECRET`로 주입한다 (기존 `DB_*` 패턴).
3. 애플리케이션 인스턴스는 1대다. 토큰 캐시는 메모리에 둔다.
4. HTTP 클라이언트는 Spring `RestClient`를 쓴다. `spring-boot-starter-restclient`가 클래스패스에 없어 자동 구성이 없으므로 `RestClient.Builder`를 설정 클래스에서 직접 만든다. 의존성은 추가하지 않는다.
5. 호출 한도(실전 초당 20건)를 넘지 않도록 하는 것은 호출자(캐시·저장)의 책임이다. 이 모듈은 제한기를 두지 않는다.

## 목표

도메인 모듈이 KIS의 인증·헤더·오류 형식을 몰라도 `tr_id`와 파라미터만으로 API를 호출하게 한다. 토큰 발급 제한(1분 1회)에 걸리지 않도록 토큰을 재사용하고, KIS 실패는 공통 오류 코드 하나로 바꿔 상위로 던진다.

## 사용하는 KIS API

공식 예제 저장소(`koreainvestment/open-trading-api`) 기준이다.

| 용도 | 메서드·경로 | tr_id | 비고 |
| --- | --- | --- | --- |
| 접근토큰 발급 | `POST /oauth2/tokenP` | — | 본문 `grant_type=client_credentials`, `appkey`, `appsecret`. 응답 `access_token`, `expires_in`(86400). 발급 1분 1회 제한 |
| 국내주식 현재가 | `GET /uapi/domestic-stock/v1/quotations/inquire-price` | `FHKST01010100` | `FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD=종목코드` |
| 국내주식 기간별 시세(일봉) | `GET /uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice` | `FHKST03010100` | `FID_INPUT_DATE_1`·`_2`(YYYYMMDD), `FID_PERIOD_DIV_CODE=D`, `FID_ORG_ADJ_PRC=0`(수정주가). 1회 최대 100건 |
| 해외주식 현재가상세 | `GET /uapi/overseas-price/v1/quotations/price-detail` | `HHDFS76200200` | `AUTH=""`, `EXCD`(NAS/NYS), `SYMB` |
| 해외주식 기간별 시세(일봉) | `GET /uapi/overseas-price/v1/quotations/dailyprice` | `HHDFS76240000` | `EXCD`, `SYMB`, `GUBN=0`(일), `BYMD`(기준일 YYYYMMDD, 이전 역순), `MODP=1`(수정주가). 1회 최대 100건 |

## 규칙

### 설정

| 프로퍼티 | 환경변수 | 설명 |
| --- | --- | --- |
| `ploutos.kis.app-key` | `KIS_APP_KEY` | 앱키 |
| `ploutos.kis.app-secret` | `KIS_APP_SECRET` | 앱시크릿 |

base-url은 실전 주소로 고정한다. 값이 비어 있으면 애플리케이션 기동에 실패한다(잘못된 배포를 빨리 드러낸다).

### 토큰

- 토큰은 메모리에 캐시하고, 만료 5분 전까지는 재발급하지 않는다.
- 만료 오류(`msg_cd = EGW00123`)를 받으면 토큰을 버리고 1회 재발급한 뒤 같은 요청을 한 번 재시도한다. 재시도도 실패하면 오류로 던진다.
- 발급 요청 자체가 실패하면 `BusinessException(MARKET_DATA_UNAVAILABLE)`.
- 동시에 여러 스레드가 만료를 감지해도 발급은 1회만 한다(잠금).

### 요청

모든 API 요청에 다음 헤더를 붙인다.

| 헤더 | 값 |
| --- | --- |
| `content-type` | `application/json; charset=utf-8` |
| `authorization` | `Bearer {access_token}` |
| `appkey` | 앱키 |
| `appsecret` | 앱시크릿 |
| `tr_id` | 호출자가 지정 |
| `custtype` | `P` |

### 응답과 오류

- KIS 응답 본문의 `rt_cd`가 `"0"`이 아니면 실패다. `msg_cd`, `msg1`은 서버 로그에만 남기고 `BusinessException(MARKET_DATA_UNAVAILABLE)`을 던진다.
- HTTP 4xx·5xx, 연결·읽기 타임아웃, 본문 파싱 실패도 같은 예외로 던진다.
- 성공 시 호출자가 지정한 타입으로 역직렬화한 본문을 돌려준다. `output`, `output1`, `output2`의 의미는 호출자가 안다.
- 타임아웃: 연결 3초, 읽기 5초.

### 노출 인터페이스

필요한 부분만 노출한다.

```java
public interface KisAccessTokenProvider {
    String accessToken();
    void invalidate();
}

public interface KisResponse {
    String rtCd();
    String msgCd();
    String msg1();
    default boolean isSuccess() { return "0".equals(rtCd()); }
    default boolean isTokenExpired() { return "EGW00123".equals(msgCd()); }
}

public interface KisApiClient {
    <T extends KisResponse> T get(String path, String trId, Map<String, String> queryParams, Class<T> responseType);
}
```

`KisApiClient`가 토큰 만료 재시도까지 감싸므로 도메인 모듈은 토큰의 존재를 모른다. `KisAccessTokenProvider`는 `KisApiClient` 구현과 테스트만 쓴다. 응답 record가 `KisResponse`를 구현하면 본문을 한 번만 역직렬화하고도 `rt_cd`를 판정할 수 있다.

### 신규 오류 코드

`SPEC-api-response.md`의 오류 코드 표에 추가한다.

| HTTP 상태 | 코드 | 오류 이름 | 메시지 | 발생 조건 |
| --- | --- | --- | --- | --- |
| 502 | `P007` | `MarketDataUnavailableException` | 시세 정보를 불러올 수 없습니다. | `BusinessException(MARKET_DATA_UNAVAILABLE)` — KIS 토큰 발급 실패, `rt_cd != "0"`, HTTP 오류, 타임아웃 |

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*KisApiClientTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/external/kis/        → KisApiClient, KisResponse, RestClientKisApiClient, KisProperties, KisClientConfig(RestClient 빈)
src/main/java/com/swyp/ploutos/external/kis/auth/   → KisAccessTokenProvider, CachedKisAccessTokenProvider, KisTokenResponse(package-private)
src/main/java/com/swyp/ploutos/common/config/       → ClockConfig(Clock 빈 — KIS의 관심사가 아니므로 공통에 둔다)
src/main/java/com/swyp/ploutos/common/exception/    → ErrorCode에 MARKET_DATA_UNAVAILABLE(P007) 추가
src/main/resources/application.properties           → ploutos.kis.* 프로퍼티
src/test/java/com/swyp/ploutos/external/kis/        → 클라이언트·설정 테스트
src/test/java/com/swyp/ploutos/external/kis/auth/   → 토큰 테스트
```

패키지는 가시성 경계다. `external.kis`는 기능 모듈이 아니라 인프라이므로 계층 레이아웃(`repository`/`service`/…)을 따르지 않고 책임별로 나눈다. `public`은 계약뿐이다 — `KisApiClient`, `KisResponse`, `KisProperties`, `KisAccessTokenProvider`. 구현(`RestClientKisApiClient`, `CachedKisAccessTokenProvider`, `KisClientConfig`, `KisTokenResponse`)은 package-private다. `auth` 밖으로는 `KisAccessTokenProvider` 인터페이스만 나간다. 설정은 인증과 호출 양쪽이 쓰므로 루트에 둔다.

방향 규칙은 `ArchitectureTest`가 강제한다: `external`은 `external`·`common` 밖의 프로젝트 클래스에 의존하지 않고, `external.kis.auth`는 `external.kis` 안에서만 쓴다.

`external`은 외부 시스템 연동의 공통 인프라만 담는다. `external` 아래 코드는 도메인 패키지(`stock`, `market` 등)를 import하지 않는다. KIS 응답을 도메인 값으로 바꾸는 어댑터(`KisDailyPriceProvider`, `KisQuoteProvider`)와 그 응답 DTO는 포트와 같은 도메인 패키지에 둔다.

## 코드 스타일

- 설정은 `@ConfigurationProperties(prefix = "ploutos.kis")` record로 바인딩한다. `@Getter`/`@Setter` 금지.
- `else` 없이 guard clause를 쓴다.
- 토큰 만료 판단은 `Clock`을 주입받아 계산한다(테스트에서 시계를 고정).
- KIS 응답 DTO는 `record`로, 필요한 필드만 매핑한다(`@JsonIgnoreProperties(ignoreUnknown = true)`).

```java
// 호출 예시 (도메인 어댑터에서)
DomesticPriceResponse body = kisApiClient.get(
    "/uapi/domestic-stock/v1/quotations/inquire-price",
    "FHKST01010100",
    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", ticker),
    DomesticPriceResponse.class
);
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- 단위: 토큰 캐시 규칙(만료 전 재사용, 만료 5분 전 재발급, 동시 요청 시 1회 발급)은 `Clock`과 가짜 발급기로 스프링 없이 검증한다.
- 통합: `MockRestServiceServer`를 `RestClient.Builder`에 바인딩해 헤더·파라미터·오류 변환을 검증한다. 실제 KIS는 호출하지 않는다.
- E2E 없음. 이 모듈은 HTTP 엔드포인트를 노출하지 않는다.

## 경계

- **항상:** 앱키·시크릿은 환경변수로만 주입한다. KIS 실패는 `MARKET_DATA_UNAVAILABLE`로만 던진다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** 의존성 추가(`spring-boot-starter-restclient`, 재시도·서킷브레이커 라이브러리), 호출 제한기 도입, 모의투자 서버 전환, 타임아웃 값 변경.
- **절대 안 함:** 앱키·시크릿·토큰을 로그·응답·저장소에 남기기, 테스트에서 실제 KIS 호출, 토큰을 매 요청마다 발급.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 토큰이 유효하면 재발급하지 않고 재사용한다. | `KisAccessTokenProviderTest.토큰이_유효하면_재발급하지_않는다` |
| 2 | 만료 5분 전이 되면 재발급한다. | `만료_5분_전이면_재발급한다` |
| 3 | 여러 스레드가 동시에 만료를 감지해도 발급은 1회다. | `동시에_만료를_감지해도_발급은_한_번이다` |
| 4 | 모든 요청에 `authorization`, `appkey`, `appsecret`, `tr_id`, `custtype` 헤더가 붙는다. | `KisApiClientTest.요청에_공통_헤더를_붙인다` |
| 5 | `rt_cd`가 `"0"`이 아니면 `MARKET_DATA_UNAVAILABLE`을 던진다. | `응답코드가_0이_아니면_시세조회_실패_예외를_던진다` |
| 6 | HTTP 오류·타임아웃이면 `MARKET_DATA_UNAVAILABLE`을 던진다. | `HTTP_오류이면_시세조회_실패_예외를_던진다` |
| 7 | 토큰 만료 오류(`EGW00123`)를 받으면 재발급 후 1회 재시도한다. | `토큰_만료_오류를_받으면_재발급_후_한_번_재시도한다` |
| 8 | 재시도도 실패하면 예외를 던지고 더 재시도하지 않는다. | `재시도도_실패하면_예외를_던진다` |
| 9 | `P007`은 502와 `MarketDataUnavailableException`, 고정 메시지로 응답된다. | `GlobalExceptionHandlerIntegrationTest.시세를_불러올_수_없으면_502와_P007을_반환한다` |
| 10 | 앱키·시크릿이 비어 있으면 기동에 실패한다. | `KisPropertiesTest.앱키가_없으면_기동에_실패한다` |

## 미해결 질문

- 국내 시장 분류 코드를 `J`(KRX)로 고정할지 `UN`(KRX+NXT 통합)으로 할지. "직전 정규장 종가" 문구에 따라 `J`로 가정한다.
- 호출 한도(초당 20건) 초과 시 KIS가 주는 오류 코드 — 구현 중 실측해 재시도 대상에 넣을지 결정한다.
- 인스턴스가 2대 이상이 되면 토큰 캐시를 Redis로 옮길지. 시세 캐시(`stock-quote`)는 Redis를 쓰기로 했지만 토큰은 이번 범위에서 메모리에 둔다. 인스턴스마다 따로 발급하면 1분 1회 제한에 걸릴 수 있다.
