# 명세: 시장 지표 마스터 (market-indicator)

시장 지표 5종을 정본으로 관리하는 모듈이다. 다른 모듈은 여기서 지표가 무엇인지, 어느 탭에 속하는지, 어떤 종류라 어떻게 조회해야 하는지를 얻는다.
HTTP API와 DB 테이블이 없다. `market-quote`, `market-daily-price`, `market-summary`, `market-chart`가 모두 이 모듈에 의존한다.

기능 맵: `CAPABILITY-MAP-market.md`. 의존 대상: 없음 (`common.enums.Country`만 쓴다).

## 내가 세운 전제

1. 지표 5종은 고정이다. 추가·삭제는 코드 변경(enum 상수 추가)으로 한다. 런타임에 지표를 만들거나 고치지 않는다.
2. 지표 정보는 **DB에 두지 않는다.** 5종이 코드로 고정이고, 다른 테이블이 FK로 참조할 일이 없다. 일봉 테이블은 enum 이름을 문자열로 저장한다. `industries`처럼 마스터 테이블과 시드 SQL을 두면 enum과 DB를 함께 맞춰야 하는 비용만 생긴다.
3. 탭(`MarketRegion`)은 화면 구성 개념이다. `Markets`/`MarketCode`(종목이 속한 지수·시장)와는 별개다.
4. 표시명은 서버가 한글로 내려준다.

## 목표

다른 모듈이 "이 지표가 무엇이고 어디에 쓰이는지"를 한 곳에서 얻게 한다. 구체적으로:
- 카드 API는 탭별 지표 목록과 표시 순서를 얻는다.
- KIS 어댑터는 지표 종류와 심볼로 어떤 TR을 부를지 정한다.
- 차트는 "오늘"을 계산할 타임존을 얻는다.
- 응답은 단위를 얻는다.

성공 기준은 **"`MarketIndicator` 하나로 표시명·종류·심볼·단위·타임존·소속 탭이 모두 나오고, 탭을 주면 표시 순서대로 지표 목록이 나온다"**다.

## 규칙

### 지표 5종

| `MarketIndicator` | 표시명 | 종류 (`IndicatorKind`) | 심볼 | 단위 (`IndicatorUnit`) | 기준 국가 | 소속 탭 |
| --- | --- | --- | --- | --- | --- | --- |
| `KOSPI` | 코스피 | `DOMESTIC_INDEX` | `0001` | `POINT` | `KR` | `DOMESTIC` |
| `KOSDAQ` | 코스닥 | `DOMESTIC_INDEX` | `1001` | `POINT` | `KR` | `DOMESTIC` |
| `NASDAQ` | 나스닥 | `OVERSEAS_INDEX` | `COMP` | `POINT` | `US` | `OVERSEAS` |
| `SP500` | S&P 500 | `OVERSEAS_INDEX` | `SPX` | `POINT` | `US` | `OVERSEAS` |
| `USD_KRW` | 원/달러 환율 | `EXCHANGE_RATE` | `FX@KRW` | `KRW` | `KR` | `DOMESTIC`, `OVERSEAS` |

심볼은 모두 2026-09-30 KIS 실측으로 확인한 값이다 (`CAPABILITY-MAP-market.md` "KIS 실측 결과").

### 탭별 구성과 순서

| `MarketRegion` | 지표 (표시 순서) |
| --- | --- |
| `DOMESTIC` | KOSPI, KOSDAQ, USD_KRW |
| `OVERSEAS` | NASDAQ, SP500, USD_KRW |

- 순서는 **enum 선언 순서**를 따른다. 지수를 먼저, 환율을 마지막에 둔다.
- `MarketIndicator.in(region)`은 그 탭에 속한 지표를 선언 순서대로 돌려준다.

### 지표 종류 (`IndicatorKind`)

지표가 어떤 성격의 시세인지를 나타낸다. KIS 어댑터는 이 값으로 호출할 TR을 정한다. `Stocks`의 `exchange.isDomestic()`으로 국내·해외 TR을 고르는 것과 같은 구조다.

| 종류 | 뜻 | KIS 어댑터가 쓸 TR (참고, 각 모듈 명세에서 확정) |
| --- | --- | --- |
| `DOMESTIC_INDEX` | 국내 지수 | 현재값 `FHPUP02100000`, 일봉 `FHKUP03500100` (시장구분 `U`) |
| `OVERSEAS_INDEX` | 해외 지수 | `FHKST03030100` (시장구분 `N`) |
| `EXCHANGE_RATE` | 환율 | `FHKST03030100` (시장구분 `X`) |

- **TR ID와 시장구분 코드는 이 모듈에 두지 않는다.** 둘 다 KIS 연동 세부 사항이라 각 모듈의 `kis` 어댑터가 갖는다.
- 이 모듈은 도메인 의미(종류)와 심볼만 가진다. `Stocks`가 `ticker`와 `exchange`만 갖고 TR을 모르는 것과 같다.
- 그래서 기능 맵이 초안에서 쓴 `OVERSEAS_CHART`(TR 이름에서 온 값)를 `OVERSEAS_INDEX`와 `EXCHANGE_RATE`로 나눴다. 두 종류는 같은 TR을 쓰지만 시장구분 코드가 다르다.

### 단위 (`IndicatorUnit`)

- `POINT`: 지수 포인트
- `KRW`: 원화 금액 (원/달러 환율은 1달러당 원)

응답에 그대로 실려 프론트가 단위 표기(`pt`, `원`)를 고르는 데 쓴다. 기존 `Currency` enum을 쓰지 않는 이유는 `POINT`가 통화가 아니기 때문이다.

- 표시 소수 자릿수는 **모든 지표가 둘째 자리로 같다.** 그래서 지표별 자릿수 필드는 두지 않는다.
- 기능 맵 초안의 "반올림 자릿수도 지표가 정한다"는 이 명세로 대체한다. 자릿수가 다른 지표가 생기면 그때 추가한다.

### 기준 국가 (`Country`)

- "오늘"과 거래일을 계산할 타임존이다. 기존 `common.enums.Country`(`KR`→`Asia/Seoul`, `US`→`America/New_York`)를 재사용한다.
- 원/달러 환율은 `KR`로 둔다.
  - 2026-09-30 실측에서 KIS 환율 일봉은 한국 추석(9/24·25)과 미국 노동절(9/7)을 모두 행으로 가진 **휴일 없는 평일 시계열**이었다.
  - 어느 나라 휴장일도 따르지 않으므로 기준 국가는 "오늘" 계산에만 쓰인다. 서울 외환시장(`KMB`) 시세라 `KR`로 둔다.

### `MarketCode`와의 관계

`KOSPI`, `KOSDAQ`, `NASDAQ`, `SP500`은 이름이 `MarketCode`와 같지만 **연결하지 않는다.**
- `MarketCode`는 "종목이 속한 시장"이고 `Markets` 행과 짝을 이룬다. `MarketIndicator`는 "화면에 보여 줄 시세 지표"다.
- 환율은 `MarketCode`가 없다. 둘을 연결하면 환율만 예외 처리가 필요해진다.

### 노출 인터페이스

리더 인터페이스를 두지 않는다. enum이 곧 계약이다. 조회할 저장소가 없으므로 `IndustryReader` 같은 추상화는 얻는 게 없다.

```java
public enum MarketIndicator {

    KOSPI("코스피", IndicatorKind.DOMESTIC_INDEX, "0001", IndicatorUnit.POINT, Country.KR, EnumSet.of(MarketRegion.DOMESTIC)),
    KOSDAQ("코스닥", IndicatorKind.DOMESTIC_INDEX, "1001", IndicatorUnit.POINT, Country.KR, EnumSet.of(MarketRegion.DOMESTIC)),
    NASDAQ("나스닥", IndicatorKind.OVERSEAS_INDEX, "COMP", IndicatorUnit.POINT, Country.US, EnumSet.of(MarketRegion.OVERSEAS)),
    SP500("S&P 500", IndicatorKind.OVERSEAS_INDEX, "SPX", IndicatorUnit.POINT, Country.US, EnumSet.of(MarketRegion.OVERSEAS)),
    USD_KRW("원/달러 환율", IndicatorKind.EXCHANGE_RATE, "FX@KRW", IndicatorUnit.KRW, Country.KR,
            EnumSet.of(MarketRegion.DOMESTIC, MarketRegion.OVERSEAS));

    // 필드와 생성자 생략

    /** 이 탭에 속한 지표를 표시 순서(선언 순서)대로 돌려준다. */
    public static List<MarketIndicator> in(MarketRegion region) { ... }

    public boolean belongsTo(MarketRegion region) { ... }

    public String displayName() { ... }
    public IndicatorKind kind() { ... }
    public String symbol() { ... }
    public IndicatorUnit unit() { ... }
    /** "오늘" 계산용. Country를 꺼내 한 단계 더 타지 않게 타임존을 직접 준다. */
    public ZoneId zoneId() { ... }
}
```

- `regions` 집합 자체는 노출하지 않는다. 호출자는 `belongsTo`와 `in`만 쓴다.
- `Country`도 노출하지 않고 `zoneId()`로 답한다 (디미터 법칙).

## 유가 확장 대비

유가를 넣을 때 이 모듈에서 할 일은 **상수 1개와 종류 1개 추가**뿐이다.

```java
WTI("WTI 유가", IndicatorKind.OVERSEAS_FUTURES, "<실측으로 확정>", IndicatorUnit.USD, Country.US,
        EnumSet.of(MarketRegion.DOMESTIC, MarketRegion.OVERSEAS));
```

- 지금은 `WTI`, `OVERSEAS_FUTURES`, `USD`를 **만들지 않는다.** 추측성 코드다.
- 선물은 월물마다 심볼이 바뀐다. 고정 문자열 `symbol` 하나로 표현할 수 없을 수 있다. 월물 규칙은 시세 신청 후 실측으로 정하고, 그때 이 명세를 고친다.
- 일봉 테이블이 `indicator` 컬럼을 MySQL 네이티브 `ENUM`으로 만들면 상수를 추가할 때 `ALTER TABLE`이 필요하다. Hibernate 6.2+는 `@Enumerated(STRING)`을 네이티브 enum으로 매핑한다 (`SPEC-industry.md` 참고). `SPEC-market-daily-price.md`에서 이 컬럼을 `VARCHAR`로 매핑해 이 비용을 없앤다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*MarketIndicatorTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/market/MarketIndicator.java   → 지표 5종 (신규)
src/main/java/com/swyp/ploutos/market/MarketRegion.java      → 탭 DOMESTIC, OVERSEAS (신규)
src/main/java/com/swyp/ploutos/market/IndicatorKind.java     → 지표 종류 (신규)
src/main/java/com/swyp/ploutos/market/IndicatorUnit.java     → 단위 (신규)
src/test/java/com/swyp/ploutos/market/MarketIndicatorTest.java
```

- 모두 `market` 모듈 루트에 둔다. 다른 모듈은 다른 모듈의 루트에 의존할 수 있다 (CLAUDE.md 패키지 구조).
- 이후 모듈은 `stock/*`의 선례대로 `market/quote`, `market/price`, `market/summary`, `market/chart`에 둔다.
- `common/enums`에 두지 않는 이유: `MarketCode`·`Country`처럼 여러 도메인이 공유하는 값이 아니라 시장 지표 기능에만 쓰는 도메인 개념이다.
- 네 enum 모두 다른 모듈이 써야 하므로 `public`이다.

## 코드 스타일

- enum 접근자는 손으로 쓴다: `displayName()`, `kind()` 등. `lombok.accessors.fluent`는 enum에 적용되지 않고, `@Getter`를 붙이면 `getDisplayName()`이 된다 (`IndustryCode` 선례).
- `in(region)`은 `Arrays.stream(values()).filter(i -> i.belongsTo(region)).toList()`처럼 선언 순서를 유지한다.
- `regions`는 `EnumSet`으로 받아 불변으로 보관한다 (`Collections.unmodifiableSet` 또는 `Set.copyOf`).
- `else` 금지, 파일 끝 개행.

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **전부 단위 테스트다.** 스프링 컨텍스트와 DB가 필요 없다.
  - `MarketIndicatorTest`: 탭별 구성과 순서, 환율의 두 탭 소속, 모든 지표가 최소 한 탭에 속함, 표시명과 심볼이 비어 있지 않고 서로 다름, 기준 국가의 타임존
- 통합·E2E 없음. HTTP API와 DB가 없는 모듈이다.
- `ArchitectureTest`가 통과해야 한다. 새 규칙은 추가하지 않는다.

## 경계

- **항상:** 지표를 추가·삭제하면 이 명세의 표, `CAPABILITY-MAP-market.md`의 탭 구성, 프론트에 전달한 API 예시를 함께 고친다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** 탭 구성이나 표시 순서, 표시명 변경(프론트 표시가 바뀐다). 지표를 DB 테이블로 옮기기. `MarketCode`와 연결하기.
- **절대 안 함:** TR ID나 KIS 시장구분 코드를 이 모듈에 두기. 쓰이지 않는 지표·종류·단위 상수를 미리 추가하기(유가 포함). 런타임에 지표를 바꾸는 API 추가.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 국내 탭은 KOSPI, KOSDAQ, USD_KRW를 이 순서로 돌려준다. | `MarketIndicatorTest.국내_탭은_코스피_코스닥_환율_순서로_구성된다` |
| 2 | 해외 탭은 NASDAQ, SP500, USD_KRW를 이 순서로 돌려준다. | `해외_탭은_나스닥_SP500_환율_순서로_구성된다` |
| 3 | 환율은 두 탭에 모두 속한다. | `환율은_국내와_해외_탭에_모두_속한다` |
| 4 | 모든 지표가 최소 한 탭에 속한다. | `모든_지표는_최소_한_탭에_속한다` |
| 5 | 표시명과 심볼이 비어 있지 않고 지표끼리 겹치지 않는다. | `표시명과_심볼은_비어있지_않고_서로_다르다` |
| 6 | 국내 지표와 환율은 서울, 해외 지수는 뉴욕 타임존을 돌려준다. | `기준_국가에_맞는_타임존을_돌려준다` |
| 7 | 지표 종류와 단위가 표와 같다. | `지표_종류와_단위가_명세와_같다` |

## 미해결 질문

- 없음. 환율 날짜 기준과 KOSDAQ 일봉은 2026-09-30 실측으로 확인했다 (`SPEC-market-daily-price.md`).
  - 환율 최신 행이 지수보다 하루 늦은 문제는 `SPEC-market-daily-price.md`와 `SPEC-market-chart.md`에서 다룬다.
