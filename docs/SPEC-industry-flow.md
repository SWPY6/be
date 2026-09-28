# 명세: 오늘의 산업 흐름 (industry-flow)

시장 요약 화면의 "오늘의 산업 흐름"(RQ-0201)을 제공하는 API의 명세다.
9개 산업의 평균 등락률·순위·소속 종목 수를 한 번의 호출로 준다.

기능 맵: `CAPABILITY-MAP-industry.md`. 의존 대상: `industry`, `stock-quote`.

## 내가 세운 전제

1. 경로 접두어는 `/api/v1`, 응답 봉투는 `SPEC-api-response.md`의 `data`/`error` 규칙을 따른다.
2. 인증은 없다. 비로그인 사용자도 이 화면을 본다.
3. 프론트는 이 API를 주기적으로 폴링한다. 서버는 푸시하지 않는다.
4. 9개 산업은 항상 전부 응답한다. 소속 종목이 0개인 산업도 목록에 포함한다(카드가 9장이므로).
5. 금액·퍼센트 표기(`+1.61%`)는 프론트가 한다. 서버는 소수 둘째 자리로 반올림한 JSON number까지 책임진다.
6. 트랜잭션 경계는 서비스 계층.

## 목표

RQ-0201의 수용 기준은 **"산업명, 평균 등락률 순위, 평균 등락률, 소속 종목 수가 한 카드에 표시된다"**다.
9개를 한 번에 내려주므로 RQ-0202(10초 자동 순환)와 RQ-0203(이전·다음 버튼)은 **재요청 없이** 동작한다.

성공 기준은 **"종목이 수백 개로 늘어도 응답 시간이 일정하고, 값이 언제 기준인지 알 수 있다"**다.

## 규칙

### 계산

```
종목 등락률   = (현재가 − 직전 거래일 종가) ÷ 직전 거래일 종가 × 100
산업 평균     = 소속 종목 등락률의 단순평균 (시가총액 가중 아님)
순위          = 평균 등락률 내림차순. 1위가 가장 높다
```

종목 등락률은 **직접 계산하지 않고** `stock-quote`의 `Quote.changeRate()` 값을 쓴다. 계산식을 두 곳에
두면 종목 상세 화면과 산업 카드의 값이 어긋난다.

단순평균은 목업이 명시한 방식이다 — *"평균 등락률은 표시된 산업별 예시 종목의 단순평균이며 실제
업종지수가 아닙니다."* 시가총액 가중으로 바꾸려면 시가총액을 저장해야 하는데 지금은 DB에 없다.

### 대상 종목

`stock_industries`에 매핑된 **전 종목**이다. 상한을 두지 않는다(정책 C).
KOSPI·KOSDAQ·NASDAQ·S&P500 중 9개 산업에 해당하는 종목으로, **700~900개로 추정**한다.
9개 산업(자동차·건설·운송·유통·음식료·통신·철강·에너지·화학)은 반도체·바이오·금융·IT를 포함하지
않으므로 전체 상장 종목(약 6,000개)의 일부다.

**같은 종목이 여러 시장에 있으면 한 번만 반영한다.** `stocks`의 유일 제약이 `(ticker, market_id)`라
Tesla가 NASDAQ과 S&P500에 각각 행을 가질 수 있고, 그대로 두면 평균에 두 번 들어간다. `ticker`로
중복을 제거한다.

### 대표 종목 2개

카드에 산업별 대표 종목 **2개**를 함께 표시한다. **시가총액 상위 2개**를 고른다.

`Quote`에 `marketCap`이 들어 있고 스케줄러가 이미 대상 종목 전부의 `Quote`를 받으므로,
**추가 KIS 호출 없이** 그 안에서 고를 수 있다. 조회 시점에 고르려 하면 시가총액을 알기 위해
다시 시세를 받아야 해 순환이 되지만, 계산 시점에는 이미 손에 있다.

**등락률 최고·최저가 아닌 이유.** 목업은 `현대차 +3.24%`(최고)와 `현대모비스 -0.78%`(최저)를
보여주는데, 그것은 산업당 종목이 4개라 자연스러운 규칙이었다. 대상이 전 종목(자동차 약 87개)이
되면 최고·최저는 상한가·급락한 소형주가 되어 **평균 +1.61%와 동떨어진 극단값**이 뜬다. 87개 중
2개를 뽑을 때는 "이 산업의 대표 기업"이 표본으로서 의미가 있다.

**통화 단위는 국가 안에서 통일된다.** 국내 시가총액은 `hts_avls`(억원)를 원으로 환산한 값이고
해외는 `tomv`(달러)다. 국내는 전부 KRW(KOSPI·KOSDAQ), 해외는 전부 USD(NASDAQ·S&P500)이며
`country`별로 나눠 계산하므로 단위가 섞이지 않는다. 시가총액이 같으면 `ticker` 순으로 정해
순서가 갱신마다 흔들리지 않게 한다.

**계산 시점의 값을 저장한다.** 종목명·등락률을 조회 시점에 다시 구하면 KIS를 호출하게 되어
설계가 무너진다. 스냅샷이므로 나중에 종목명이 바뀌어도 "그 시각에는 이랬다"가 보존된다.

### 국내·해외 구분

`Country`(`KR`/`US`)로 구분한다. `marketId`가 아니다.

```
국내시장  =  Country.KR   (KOSPI + KOSDAQ 합산)
해외시장  =  Country.US   (NASDAQ + S&P500 합산)
```

화면 토글이 "국내시장 / 해외시장" 두 개이고 `Country`가 시장 여럿을 묶는 단위다. `Country`는
`ZoneId`도 갖고 있어 "오늘"과 거래일 계산에 그대로 쓸 수 있다.

**종목의 국가는 `StockReader.read(stockId)`가 돌려주는 `StockWithMarket.market().country()`로 안다.**
`stocks`·`markets` 테이블을 직접 조회(JOIN)하지 않는다 — 그 테이블은 `stock`·`market` 모듈이 소유하고,
직접 읽으면 그쪽 스키마가 바뀔 때 이 모듈이 조용히 깨진다. 이 모듈의 리포지토리는 자기 테이블
(`stock_industries`, `industry_flows`)만 읽는다.

### 연속 순회로 미리 계산해 저장한다

**요청이 올 때 계산하지 않는다.** 스케줄러가 산업을 하나씩 계속 순회하며 계산해 저장하고,
API는 저장된 값을 읽는다.

```
[스케줄러] 산업 하나씩, 쉬지 않고 순회 (cursor 로 다음 산업을 가리킨다)

    10:00:00  자동차
                stock_industries 에서 자동차 종목 전부 (국내 87 + 해외 15 = 102)
                종목마다  StockReader.read  +  QuoteReader.readWithoutTracking
                country 로 나눠  →  KR 평균 · US 평균  →  자동차 2행 저장
    10:00:34  건설  →  같은 방식으로 2행 저장
       ...
    10:05:00  화학 끝  →  다시 자동차로

[API] GET /api/v1/industries/flows?country=KR
    industry_flows 에서 9행 SELECT  →  정렬해 순위 부여  →  응답
    KIS 를 호출하지 않는다. 응답 시간이 종목 수와 무관하다
```

**한 산업을 처리할 때 국내·해외를 함께 계산한다.** 국가별로 따로 순회하면 국내를 계산할 때
해외 종목까지 읽고 버리게 된다. 한 번 읽어 `country`로 나누면 낭비가 없고, 한 바퀴에 18행이 모두 갱신된다.

요청 시 계산하면 한 요청이 N개 종목의 시세를 기다린다. 순차 호출 지연을 200ms로 보면
900종목에 **180초**다. 사용자가 기다릴 수 없고, 캐시가 빈 종목이 있으면
`MARKET_DATA_UNAVAILABLE`(502)로 화면 전체가 죽는다.

**"N분 주기"가 아니라 "초당 몇 건"을 정한다.** 주기를 늘려도 한 바퀴에 걸리는 시간은 그대로이고,
도는 동안의 순간 부하도 그대로다. KIS 한도는 평균이 아니라 **순간 부하**로 판정하므로, 주기를
늘리는 것은 충돌이 일어나는 시간 비율만 줄이고 충돌 강도는 낮추지 못한다. 실제 손잡이는 호출 속도다.

```
ploutos.industry-flow.calls-per-second = 3     ← 유일한 조절 장치
한 바퀴(= 갱신 주기) = 900 ÷ 3 = 300초 = 5분
KIS 부하 = 항상 초당 3건 (한도 20건의 15%). 버스트가 없다
```

초당 3건은 한도의 15%만 쓴다. 남는 17건을 `QuoteRefresher`와 사용자 요청이 쓴다.
`5건으로 3분 일하고 2분 쉬는` 방식보다 **순간 최대 부하가 낮다.**

> ⚠️ 200ms는 추정치다. 실제 KIS 응답 시간이 334ms를 넘으면 호출 간격을 둘 필요 없이
> 자연히 초당 3건 아래가 되므로, 측정 후 간격을 줄이거나 없앤다.

**산업 하나를 다 돌면 그 산업만 저장한다.** 세 가지 이점이 있다.

| | |
| --- | --- |
| 버스트가 작다 | 한 번에 900개가 아니라 한 산업(약 87개)씩, 약 30초 구간 |
| 부분 진행이 보존된다 | 앱이 중간에 재시작해도 이미 저장한 산업은 남는다. 몰아서 하면 한 바퀴치가 날아간다 |
| `calculatedAt`이 정확하다 | 조회 직후에 저장하므로 산업 간 편차가 5분이 아니라 **약 30초**다 |

**`@Scheduled(fixedDelay)`를 쓴다.** 한 산업 처리가 끝난 뒤부터 간격을 세므로, 처리가 길어지면
자연히 속도가 느려진다. `QuoteRefresher`가 쓰는 것과 같은 자기 조절 방식이다.
상태는 다음 산업을 가리키는 정수 `cursor` 하나뿐이다 — 앱이 재시작하면 0부터 다시 돌지만
한 바퀴가 5분이라 문제가 없다.

### 스케줄러 스레드를 2개로 늘려야 한다

```properties
spring.task.scheduling.pool.size=2
```

**이 설정이 없으면 `QuoteRefresher`가 멈춘다.** `SchedulingConfig`는 `@EnableScheduling`만 있고
`spring.task.scheduling.pool.size`의 기본값은 **1**이다. 스케줄러 스레드가 하나면 이 모듈의
순회 작업이 스레드를 붙잡는 동안 `QuoteRefresher`가 실행되지 못하고, 10초 주기가 수십 초로 밀린다.

그러면 사용자가 보고 있는 종목의 캐시가 TTL(30초) 뒤 비고, 사용자 요청이 캐시 미스가 되어
직접 KIS를 호출하다 느려지거나 502가 난다. **로그에 오류가 찍히지 않고 "갱신이 늦어지는" 형태로만
나타나므로 발견하기 어렵다.**

스레드를 2개로 늘려도 같은 `@Scheduled` 메서드가 자기 자신과 겹치지는 않는다
(`ScheduledThreadPoolExecutor`가 보장한다). 서로 다른 작업 두 개가 병행될 뿐이다.
스레드 하나당 스택 1MB이므로 비용은 사실상 없다.

### 저장 위치는 DB다

Redis가 아니라 `industry_flows` 테이블에 둔다. **배포·재시작 직후의 공백을 없애기 위해서다.**
Redis에 두면 앱이 뜰 때마다 비고, 첫 계산이 끝나는 3분 동안 화면에 아무것도 못 보여준다.
배포가 잦으므로 사용자에게 드러난다. DB에 두면 직전 값이라도 `calculatedAt`과 함께 보여줄 수 있다.

9개 산업 × 2개 국가 = **최대 18행**이다. 이력을 쌓지 않고 UPSERT로 덮어쓴다 —
"전일 대비 순위 변동" 같은 요구사항이 없으므로 `trade_at` 키를 두지 않는다.

### 순위는 저장하지 않는다

평균 등락률만 저장하고, 순위는 **응답을 만들 때 정렬해 부여한다.**
순위는 나머지 8개와의 관계에서 나오는 파생값이라, 저장하면 한 산업만 갱신됐을 때 1위가 둘인
상태가 만들어진다. 9개 정렬은 비용이 없다.

동점이면 산업 표시명 가나다순으로 앞선 산업이 높은 순위를 갖는다(순위가 매 요청 흔들리지 않게).

### 부분 실패

**시세를 구하지 못한 종목은 평균에서 제외하고 나머지로 계산한다.** 900개 중 몇 개가 실패해도
화면이 죽지 않아야 한다.

| 상황 | 처리 |
| --- | --- |
| 일부 종목 실패 | 제외하고 평균. `stockCount`는 **실제로 반영된 개수** |
| 한 산업의 모든 종목 실패 | 그 산업만 UPSERT를 건너뛴다 → 직전 값이 남고 `calculatedAt`이 낡아 드러난다 |
| 매핑이 0건인 산업 | `avgChangeRate = 0`, `stockCount = 0`으로 저장. 카드는 9장이어야 한다 |
| 스케줄러 전체 실패 | 아무것도 갱신하지 않는다. API는 직전 값을 계속 응답한다 |

`stockCount`가 "매핑된 수"가 아니라 "반영된 수"인 것이 중요하다. 목업의 *"소속 종목 4개 단순평균"*과
같은 의미이며, 값이 얼마나 믿을 만한지를 프론트가 판단할 근거가 된다.

### 감시 — `calculatedAt`

**산업별로 `calculatedAt`을 응답에 넣는다.** 스케줄러가 죽어도 API는 200을 반환하고 사용자는
멈춘 값을 정상으로 본다 — 2026-09-26의 13시간 502와 같은 종류의 조용한 고장이다.

`calculatedAt`이 있으면 프론트가 *"10분 전 기준"*을 표시하거나 경고할 수 있고, 개발자는 응답만
보고 스케줄러가 멈춘 것을 안다. 산업별로 주는 이유는 **특정 산업만 갱신이 멈춘 경우**(위 부분 실패)도
드러나야 하기 때문이다. 프론트는 9개 중 가장 오래된 값을 화면 기준 시각으로 쓸 수 있다.

### 설계

- `IndustryFlows`(엔티티): `industryId`, `country`, `avgChangeRate`, `stockCount`, `calculatedAt`,
  그리고 대표 종목 2개의 스냅샷 `firstTicker`·`firstName`·`firstChangeRate`,
  `secondTicker`·`secondName`·`secondChangeRate`(전부 nullable — 반영된 종목이 0·1개일 수 있다).
  유일 제약 `(industry_id, country)`.

  대표 종목을 별도 테이블로 정규화하지 않는다. 개수가 2개로 고정이고 18행짜리 스냅샷이라
  조회를 두 번 하거나 조인할 값이 없다. 4개로 늘려야 하면 그때 테이블을 분리한다.
- `IndustryFlowReader`(public 계약): `List<IndustryFlow> read(Country country)`.
  `industry-trend`와 `industry-news`가 같은 값을 재사용한다.
- `JpaIndustryFlowReader`(package-private): 9행을 읽어 정렬·순위 부여.
- `IndustryFlowCalculator`(`@Component`): 산업별 평균을 계산한다. 스프링 없이 단위 테스트한다.
- `IndustryFlowRefresher`(`@Component`): `@Scheduled(fixedDelay)`로 깨어나 `cursor`가 가리키는
  산업 하나만 처리하고 국가별로 2행을 저장한다. 종목 호출 사이에 `1000 / calls-per-second`
  밀리초를 쉰다. `@EnableScheduling`은 `SchedulingConfig`에 이미 있다(`stock-quote`가 추가).
  **종목마다 `try-catch`로 감싼다** — 한 종목의 실패가 나머지를 막지 않게 한다
  (`QuoteRefresher.refreshOne`과 같은 이유다).

  종목 정보와 시세는 **다른 모듈의 공개 계약으로만** 얻는다.

  ```java
  List<Long> stockIds = industryStockRepository.findStockIdsByIndustryId(industryId);
  for (Long id : stockIds) {
      StockWithMarket stock = stockReader.read(id);          // ticker · name · country
      Quote quote = quoteReader.readWithoutTracking(id);     // 등락률 · 시가총액
  }
  ```

- `IndustryStockRepository`: `stock_industries`에서 산업별 `stockId` 목록만 읽는다.
  `stocks`·`markets`와 JOIN하지 않는다.
- `IndustryFlowController`: `GET /api/v1/industries/flows`.

계산(순수 로직)과 스케줄링(시간)을 나눠 계산을 스프링 없이 검증한다.

## API 계약

프론트엔드에 전달하는 계약이다. 봉투 규칙은 `SPEC-api-response.md`를 따른다.

### `GET /api/v1/industries/flows`

| 파라미터 | 필수 | 값 | 설명 |
| --- | --- | --- | --- |
| `country` | X | `KR` \| `US` | 기본 `KR`. 화면의 국내·해외 토글 |

**200 성공**

```json
{
  "data": [
    {
      "code": "AUTOMOBILE",
      "displayName": "자동차",
      "rank": 1,
      "avgChangeRate": 1.61,
      "stockCount": 87,
      "majorStocks": [
        { "ticker": "005380", "name": "현대차", "changeRate": 3.24 },
        { "ticker": "000270", "name": "기아", "changeRate": 1.85 }
      ],
      "calculatedAt": "2026-09-28T10:00:07+09:00"
    },
    {
      "code": "CONSTRUCTION",
      "displayName": "건설",
      "rank": 2,
      "avgChangeRate": 0.45,
      "stockCount": 94,
      "majorStocks": [
        { "ticker": "000720", "name": "현대건설", "changeRate": 1.68 },
        { "ticker": "047040", "name": "대우건설", "changeRate": 0.49 }
      ],
      "calculatedAt": "2026-09-28T10:00:07+09:00"
    }
  ]
}
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `code` | string | X | 산업 코드. `"AUTOMOBILE"` 등 9종. **산업 식별자로 이 값을 쓴다** |
| `displayName` | string | X | 한글 산업명. `"자동차"` |
| `rank` | integer | X | 평균 등락률 순위. 1이 가장 높다 (RQ-0201) |
| `avgChangeRate` | number | X | 평균 등락률 %, 소수 둘째 자리. 음수 가능 |
| `stockCount` | integer | X | **평균에 실제로 반영된** 종목 수. 0일 수 있다 |
| `majorStocks` | array | X | 시가총액 상위 대표 종목. **0~2개.** 빈 배열일 수 있다 |
| `majorStocks[].ticker` | string | X | 종목 코드 (`005380`, `TSLA`) |
| `majorStocks[].name` | string | X | 종목명. 계산 시점의 값 |
| `majorStocks[].changeRate` | number | X | 그 종목의 등락률 %, 소수 둘째 자리 |
| `calculatedAt` | string(ISO-8601, 오프셋 포함) | X | 이 산업의 값이 계산된 시각. 최대 5분 과거 |

`majorStocks`를 배열로 두면 종목이 0·1개인 산업도 같은 형태로 표현되고, 나중에 4개로 늘릴 때
프론트 계약이 깨지지 않는다.

배열은 항상 **9개**이며 `rank` 오름차순으로 정렬돼 있다. `industryId`는 내려주지 않는다 —
`auto_increment` 값이라 환경마다 다를 수 있다.

**400 `country`가 `KR`·`US`가 아님**

```json
{ "error": { "name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다." } }
```

**계산이 한 번도 실행되지 않은 경우** 빈 배열(`{"data": []}`)이 아니라 9개 산업을
`avgChangeRate: 0`, `stockCount: 0`, `majorStocks: []`로 응답한다. 프론트가 카드 9장을 그대로
그릴 수 있게 한다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*IndustryFlowCalculatorTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/industry/flow/                 → IndustryFlows(엔티티), IndustryFlow·MajorStock(값 객체)
src/main/java/com/swyp/ploutos/industry/flow/repository/      → IndustryFlowRepository, IndustryStockRepository
src/main/java/com/swyp/ploutos/industry/flow/service/         → IndustryFlowReader, JpaIndustryFlowReader,
                                                                IndustryFlowCalculator, IndustryFlowRefresher
src/main/java/com/swyp/ploutos/industry/flow/controller/      → IndustryFlowController, IndustryFlowResponse
src/main/resources/application.properties                     → ploutos.industry-flow.calls-per-second,
                                                                spring.task.scheduling.pool.size=2
db/                                                           → industry_flows 테이블 DDL
src/test/java/com/swyp/ploutos/industry/flow/                  → 단위·슬라이스 테스트
```

`industry/flow`를 하위 모듈로 두는 것은 `stock/price`·`stock/quote`의 선례를 따른다.
`IndustryFlowReader`만 `public`이고 구현체는 package-private다.

## 코드 스타일

- 엔티티명은 복수형(`IndustryFlows`), 식별자는 `industryFlowId`. `@Column`의 `name`은 쓰지 않는다.
- `LocalDateTime` 필드는 `~At`으로 끝낸다(`calculatedAt`).
- 응답 DTO는 `record`. `@Setter`·`@Data` 금지, `@Getter`는 클래스 단위.
- `else` 없이 guard clause.
- 시각은 `Clock`으로 얻는다(`ClockConfig`가 이미 있다).
- 평균은 `BigDecimal`로 계산하고 마지막에 소수 둘째 자리 `HALF_UP`으로 반올림한다.

```java
// IndustryFlowCalculator — 반영된 종목이 없으면 0을 돌려준다
if (changeRates.isEmpty()) {
    return BigDecimal.ZERO.setScale(2);
}
return changeRates.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(changeRates.size()), 6, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP);
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위(60%)**
  - `IndustryFlowCalculatorTest` — 단순평균, 반영 종목 0개, 일부 종목 실패 시 제외,
    같은 ticker 중복 제거, 소수 둘째 자리 반올림, **시가총액 상위 2개 선정**(동점 시 ticker 순,
    종목이 1개뿐인 경우). 가짜 `QuoteReader`를 쓴다
  - `JpaIndustryFlowReaderTest` — 순위 부여(내림차순), 동점 시 가나다순, 9개 보장.
    `@Mock` 리포지토리
  - `IndustryFlowRefresherTest` — 한 산업이 전부 실패하면 그 산업만 저장을 건너뛴다,
    **한 번 깨어날 때 산업 하나만 처리한다**, **`cursor`가 9를 넘으면 처음 산업으로 돌아간다**,
    한 종목이 예외를 던져도 나머지 종목을 계속 조회한다. 고정 `Clock`, 가짜 `QuoteReader`
- **통합(30%)** — `@WebMvcTest(IndustryFlowController)` + MockMvc로 JSON 본문·상태 코드,
  `country` 잘못된 값이 400인지
- **E2E(10%)** — `@SpringBootTest`에서 `QuoteReader`를 스텁하고 스케줄러 1회 실행 → 조회까지 1건
- `ArchitectureTest` 통과. 리포지토리는 `industry/flow` 안에서만 쓴다

## 경계

- **항상:** 종목 등락률은 `Quote.changeRate()` 값을 쓴다. 응답에 `calculatedAt`을 포함한다.
  배열은 항상 9개다. **`spring.task.scheduling.pool.size=2`를 유지한다.** 시세는
  `readWithoutTracking`으로 읽는다(활성 목록을 오염시키지 않는다). 커밋 전 `./gradlew test`.
- **먼저 묻기:** `calls-per-second` 변경(KIS 예산을 `QuoteRefresher`와 나눠 쓴다),
  단순평균 → 시가총액 가중 변경, 응답 필드 추가·이름 변경(프론트 계약), 대상 종목 범위 변경,
  `industry_flows`에 이력(`trade_at`) 도입, 대표 종목 선정 기준·개수 변경, 우선순위 제어 도입.
- **절대 안 함:** `Industries` 엔티티에 평균 등락률·순위 저장, 순위를 DB에 저장, 응답에 `industryId` 노출,
  요청 처리 중에 KIS 호출, 등락률을 직접 계산(KIS 원본 필드나 자체 수식 사용),
  `calculatedAt` 없이 응답, 캐시·Redis를 직접 만지기(`QuoteReader`만 쓴다),
  **`stocks`·`markets` 테이블을 직접 조회**(`StockReader`만 쓴다).

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 소속 종목 등락률의 단순평균을 소수 둘째 자리로 계산한다. | `IndustryFlowCalculatorTest.소속_종목의_등락률을_단순평균한다` |
| 2 | 시세를 못 구한 종목은 평균에서 제외하고 나머지로 계산한다. | `시세를_못_구한_종목은_제외하고_평균한다` |
| 3 | `stockCount`는 평균에 반영된 종목 수다. | `소속_종목_수는_평균에_반영된_개수다` |
| 4 | 같은 ticker가 여러 시장에 있으면 한 번만 반영한다. | `같은_종목이_여러_시장에_있으면_한_번만_반영한다` |
| 5 | 반영된 종목이 없으면 평균은 0이다. | `반영된_종목이_없으면_평균은_0이다` |
| 6 | 시가총액 상위 2개를 대표 종목으로 고른다. | `시가총액_상위_2개를_대표_종목으로_고른다` |
| 7 | 시가총액이 같으면 ticker 순으로 정한다. | `시가총액이_같으면_ticker_순으로_고른다` |
| 8 | 반영된 종목이 1개면 대표 종목도 1개다. | `반영된_종목이_하나면_대표_종목도_하나다` |
| 9 | 대표 종목은 계산 시점의 종목명·등락률을 담는다. | `대표_종목은_계산_시점의_값을_담는다` |
| 10 | 평균 등락률 내림차순으로 1위부터 순위를 매긴다. | `JpaIndustryFlowReaderTest.평균_등락률이_높은_순으로_순위를_매긴다` |
| 11 | 동점이면 산업명 가나다순이 높은 순위다. | `동점이면_산업명_가나다순으로_순위를_매긴다` |
| 12 | 저장된 값이 없어도 9개 산업을 모두 응답한다. | `저장된_값이_없어도_9개_산업을_모두_응답한다` |
| 13 | 한 산업의 모든 종목이 실패하면 그 산업만 저장을 건너뛴다. | `IndustryFlowRefresherTest.산업의_모든_종목이_실패하면_저장하지_않는다` |
| 14 | 한 번 깨어날 때 산업 하나만 처리한다. | `한_번_실행하면_산업_하나만_처리한다` |
| 15 | 마지막 산업 다음에는 처음 산업으로 돌아간다. | `마지막_산업_다음에는_처음_산업으로_돌아간다` |
| 16 | 한 종목이 예외를 던져도 나머지 종목을 계속 조회한다. | `한_종목이_실패해도_나머지를_계속_조회한다` |
| 17 | 응답 배열은 9개이며 `rank` 오름차순이다. | `IndustryFlowControllerTest.9개_산업을_순위_오름차순으로_응답한다` |
| 18 | 산업마다 `calculatedAt`을 내려준다. | `산업마다_계산_시각을_내려준다` |
| 19 | 대표 종목이 없는 산업은 `majorStocks`가 빈 배열이다. | `대표_종목이_없으면_빈_배열을_응답한다` |
| 20 | `country`가 `KR`·`US`가 아니면 400 / `P001`. | `국가가_잘못되면_400과_P001을_반환한다` |
| 21 | 요청 처리 중 KIS를 호출하지 않는다. | `조회는_외부_시세를_호출하지_않는다` |

## 미해결 질문

구현 전에 답이 필요한 것 셋이다. **모두 이 모듈 밖에서 결정된다.**

1. **`stock_industries` 매핑 700~900행을 누가 만드는가.** 지금 `markets`·`stocks`·`stock_industries`가
   모두 비어 있다. `Stocks`는 `floatShares`·`ceo`·`listedAt`이 `nullable = false`라 종목마다 그
   값들도 필요하며, 900종목을 손으로 채울 수 없으므로 KIS 종목정보 API로 받아와야 한다.
   **종목 담당 영역이고 이 모듈의 선행 조건이다.** 매핑이 비면 모든 산업의 `stockCount`가 0이 된다.
2. **`QuoteReader.readWithoutTracking`이 필요하다.** `StockQuoteService.read()`는 `cache.markActive`로
   조회한 종목을 `quote:active` ZSET에 등록하고, `QuoteRefresher`가 그 목록을 10초마다 순차 갱신한다.
   이 배치가 종목을 그 목록에 넣으면 refresher 한 바퀴가 길어져 **사용자가 보고 있는 종목의 시세
   갱신이 밀린다.** 배치는 활성 등록이 필요하지 않으므로 등록만 건너뛰는 경로를 요청한다.
   **팀원과 협의 중.** 변경은 파일 2개, 약 4줄이다.

   ```java
   // QuoteReader.java — 선언 추가
   Quote readWithoutTracking(Long stockId);

   // StockQuoteService.java — 본문을 새 메서드로 옮기고 read()가 감싼다
   public Quote read(Long stockId) {
       cache.markActive(stockId);
       return readWithoutTracking(stockId);
   }
   ```

   캐시 확인·락·KIS 호출은 그대로 재사용되고 기존 `read()`의 동작도 바뀌지 않는다.
   다만 `markActive`가 `stockReader.read()`(종목 존재 확인)보다 먼저 실행되므로, 없는 `stockId`로
   요청하면 그 종목이 60초 동안 활성 목록에 남아 refresher가 WARN 로그를 남긴다 — 기능은 정상이다.
3. **대표 종목 선정 기준이 목업과 다르다.** 목업 카드는 `현대차 +3.24%`, `현대모비스 -0.78%`로
   **등락률 최고·최저**를 보여주지만, 이 명세는 **시가총액 상위 2개**로 정했다(위 "대표 종목 2개").
   산업당 종목이 4개일 때는 최고·최저가 자연스럽지만 87개에서는 극단값이 뜬다.
   **프론트 담당자에게 이 차이를 알려야 한다.** 화면 문구도 `소속 종목 4개 단순평균`에서
   `소속 종목 87개 단순평균`으로 바뀐다.

## 알려진 한계

**대상 종목이 늘어날 때의 경로**를 미리 적어둔다. 지금 구조는 초당 3건 기준이다.
호출 속도를 고정했으므로 갱신 주기가 종목 수에 정비례해 늘어난다.

| 대상 종목 | 한 바퀴(= 갱신 주기) | KIS 부하 | 판단 |
| --- | --- | --- | --- |
| ~180 | 1분 | 3건/초 (15%) | 문제없음 |
| ~900 | **5분** | 3건/초 (15%) | **현재 설계** |
| ~1,800 | 10분 | 3건/초 (15%) | 감당 가능. 신선도만 떨어진다 |
| ~3,600 | 20분 | 3건/초 (15%) | 산업 순위가 20분 전 값이다. 아래 전환을 검토 |
| ~6,000 | 33분 | 3건/초 (15%) | 아래 전환 |

**호출 속도를 고정했으므로 KIS 부하는 종목 수와 무관하게 초당 3건이다.** 늘어나는 것은 갱신
주기뿐이다. 그래서 한계는 "KIS 한도"가 아니라 **"몇 분 전 값까지 허용하는가"**가 된다.
속도를 올려 주기를 줄일 수는 있지만 그만큼 `QuoteRefresher`의 예산을 가져간다.

**우선순위 제어는 도입하지 않았다.** "사용자 요청을 최우선으로 하고 배치는 빈 시간에만 호출"하는
방식(`external/kis`에 공용 토큰 버킷 + 여유가 있을 때만 배치에 허용)을 검토했으나 지금은 하지 않는다.

- 사용자 요청은 대부분 캐시 히트다. KIS를 부르는 것은 `QuoteRefresher`이므로 실제 경쟁은
  **refresher vs 이 배치**이고 둘 다 백그라운드다. "사용자 우선"을 구현해도 실효가 작다
- 배치가 순차 호출이라 이미 호출 사이가 대부분 빈 시간이다. 사용자가 끼어들 때 기다리는 시간은
  최대 한 건(약 200ms)이다
- `external/kis`는 팀 공용 모듈이라 `SPEC-kis-client.md`와 모든 호출 경로에 영향이 간다.
  그리고 임계치를 정할 근거(실제 호출량)가 아직 없다

**활성 종목이 150개를 넘으면 재검토한다.** `refresher` 부하가 `활성 종목 수 ÷ 10초`이므로
150개에서 초당 15건이 되고, 이 배치의 3건을 더하면 한도(20건)에 닿는다.

**6,000종목 규모가 되면 일봉(종가) 계산으로 전환한다.** `stock_daily_prices`에 종가가 있으므로
`GROUP BY` 한 번으로 종목 수와 무관하게 계산되고 KIS 호출이 0이 된다. 대가는 실시간을 포기하는
것(장중에는 전일 종가 대비)이다. 산업 평균은 `Quote`의 8개 지표 중 등락률 하나만 쓰므로 교체 지점이
한 곳이다. 지금 미리 추상화를 넣지 않는다.

**"캐시 값으로 평균을 내 저장한다"는 중간안은 기각했다.** 스케줄러가 여전히 종목마다
`QuoteReader`를 부르므로 근본 부하가 줄지 않고, 9개 산업 전 종목을 항상 활성 상태로 유지해
`active-window`가 만든 절약(아무도 안 보면 호출 0)을 무력화한다. 지금 설계도 같은 성질을 갖지만
5분 주기 + 60초 활성 윈도우 덕에 **5분 중 1분만** 점유한다 — 그래서 주기가 중요하다.

## 추후 구현

1. **시가총액 가중 평균** — 지금은 단순평균이다. `Quote`에 시가총액이 있으므로 계산 자체는
   가능하지만, 목업이 단순평균을 명시했고 가중 평균은 대형주 몇 개가 산업 전체를 대표하게 된다.
   요구사항이 바뀌면 검토한다.
2. **`industry-trend`가 이 모듈을 재사용** — 산업별 동향 화면도 `평균 등락률 N위`를 표시한다.
   `IndustryFlowReader`를 그대로 쓴다. 계산을 두 곳에 두면 같은 화면의 두 영역이 다른 순위를 보인다.
   그 화면은 산업당 종목을 4개 이상 보여주므로, 그때 대표 종목을 별도 테이블로 분리할지 결정한다.
3. **갱신 실패 알림** — 지금은 `calculatedAt`으로 드러내기만 한다. 일정 시간 이상 갱신이 없으면
   경고하는 장치는 운영하며 필요해지면 붙인다.
