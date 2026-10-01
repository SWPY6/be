# 명세: 오늘의 핵심 뉴스 (industry-news)

시장 요약 화면의 "오늘의 핵심 뉴스"(RQ-0401 ~ RQ-0405)를 제공하는 API의 명세다.
상승 산업 1건과 하락 산업 1건을, 평균 등락률·상승·하락 종목 수·관련 뉴스와 함께 준다.

수용 기준과 두 군데 다르다. ① 요구사항은 "뉴스·공시"라고 쓰지만 **이번 구현은 뉴스만 다룬다**
(근거는 「선행 조건 ②」). ② **거래대금 변화율은 응답에 넣지 않는다** — 화면에 표시하지 않는다는
확인을 받았다. 선정 관문으로 쓰고 `industry_flows`에 저장하므로, 표시가 필요해지면 응답에
필드 한 줄을 더하면 된다.

기능 맵: `CAPABILITY-MAP-industry.md`. 의존 대상: `industry`, `industry-flow`, `stock-daily-price`, `news`.

## 내가 세운 전제

1. 경로 접두어는 `/api/v1`, 응답 봉투는 `SPEC-api-response.md`의 `data`/`error` 규칙을 따른다.
2. 인증은 없다. 비로그인 사용자도 이 화면을 본다.
3. **카드는 항상 2건이다** — 상승 1건, 하락 1건. 목업의 `01 상승 관련`, `02 하락 관련`이 근거다.
   배열 길이가 2가 아닌 경우는 없다.
4. **등락률이 1차 결정자, 거래대금이 관문, 뉴스는 표시 항목이다.** 등락률로 후보 순서를 정하고,
   거래대금이 자기 산업의 평소 수준을 넘는 첫 산업을 고르고, 뉴스는 선정된 산업에만 붙인다.
   뉴스는 **선정에 관여하지 않는다** — 요구사항 첨부가 "관련 뉴스가 없다면 뉴스는 화면에 항상
   뜨지 않아도 되며 생략이 가능함"이라고 명시한다.
5. **등락률은 직전 거래일 종가 대비다.** `industry-flow`의 `avgChangeRate`를 그대로 쓴다.
   요구사항 첨부의 "등락률 + 거래대금 … 20거래일" 문구는 **거래대금에만** 적용한다 —
   목업 헤더가 `직전 거래일 종가 대비`이고 카드 값 `자동차 산업 +1.61%`가
   「오늘의 산업 흐름」과 같은 값이기 때문이다. 두 섹션이 같은 화면에 있으므로 어긋나면 안 된다.
6. **"20거래일 평균 대비"는 그 산업 자신의 과거와 비교하는 것이다.** 다른 산업과 비교하지 않는다.
   요구사항 첨부에 "20일 전 가격/거래대금과 비교"라는 문장도 있지만, RQ-0402의 수용 기준과
   목업이 모두 `20일 평균 대비`라고 적었다. 20일 전 단 하루를 기준으로 삼으면 그날이 특이했을 때
   기준이 통째로 망가진다.
7. **거래대금은 산업 단위 금액으로 집계한 뒤 비교한다.** 종목별 비율을 먼저 내어 평균하지 않는다.
   금액을 합치면 큰 종목이 자연히 크게 기여하므로 **시가총액 가중이 따로 필요하지 않다.**
8. **거래대금 변화율은 퍼센트다.** 목업이 `+52.65%`로 표기한다. 배수(1.53배)가 아니다.
9. "함께 확인된 맥락이며 가격 변동의 원인으로 단정하지 않는다"(RQ-0404)는 고정 문구이며
   **프론트가 표시한다.** 서버가 매 응답에 같은 문자열을 실어 보내지 않는다.
10. `news` 모듈의 리포지토리를 직접 조회하지 않는다. 그 모듈이 노출하는 계약만 쓴다
    (CLAUDE.md "리포지토리는 자기 기능 모듈 안에서만 쓴다", `ArchitectureTest`가 강제).
11. 트랜잭션 경계는 서비스 계층.

→ 지금 바로잡아 주지 않으면 이대로 진행한다.

## 목표

RQ-0401의 수용 기준은 **"산업명, 평균 등락률과 순위, 상승·하락 종목 수, 거래대금 변화율,
관련 뉴스·공시가 한 카드에 표시된다"**다. 이 중 **거래대금 변화율과 공시는 이번 범위에서 뺀다**
— 위의 두 가지 차이다.

카드 2장을 한 번의 호출로 내려준다. RQ-0402(도움말 아이콘)와 RQ-0404(맥락 고지)는
프론트의 고정 문구이므로 백엔드 작업이 없고, RQ-0405의 화면 이동은 프론트 라우팅이다.

## 규칙

### 선정 규칙

`industry-flow`가 9개 산업을 **평균 등락률 내림차순으로 정렬**해 `rank`와 함께 돌려준다.
그 목록을 그대로 쓴다. 새로 정렬하지 않는다.

**상승 카드**

```
1) 상승군 = avgChangeRate > 0 인 산업 (목록 순서 유지 = 등락률 높은 순)
2) 상승군을 앞에서부터 훑어, tradingValueChangeRate ≥ 0 인 첫 산업을 고른다
   → selectedBy = MATCHED
3) 끝까지 없으면 상승군의 첫 산업(등락률 1위)
   → selectedBy = CHANGE_RATE_ONLY
4) 상승군 자체가 비면(전 산업 하락) 전체의 첫 산업
   → selectedBy = CHANGE_RATE_ONLY
```

**하락 카드** — 부호와 훑는 방향만 뒤집는다.

```
1) 하락군 = avgChangeRate < 0 인 산업
2) 하락군을 뒤에서부터 훑어(등락률 낮은 순), tradingValueChangeRate ≥ 0 인 첫 산업
3) 없으면 하락군의 마지막 산업(최하위)
4) 하락군 자체가 비면 전체의 마지막 산업
```

`avgChangeRate`가 정확히 `0.00`인 산업은 **어느 군에도 들어가지 않는다.** 오른 것도 내린 것도 아니다.

**하락 카드도 거래대금은 증가(`≥ 0`)를 본다.** 목업의 화학이 `-0.35%`인데 거래대금은 `+96.03%`다 —
가격이 내려도 돈은 몰린다. 거래대금 변화율은 등락률과 독립된 신호이므로 부호를 맞추지 않는다.

상승군과 하락군은 부호로 배타적이라 **같은 산업이 두 카드에 뽑힐 수 없다.**

### 왜 점수 합산이 아닌가

세 신호의 자료형이 다르다 — 등락률은 연속값(%), 거래대금 변화율도 연속값이지만 단위가 다르고,
뉴스는 불리언이다. 한 축에 놓으려면 정규화와 가중치를 임의로 정해야 하고 그 가중치에는
객관적 근거를 댈 수 없다.

**RQ-0402가 사용자에게 선정 기준을 설명해 주는 기능**이라는 점도 걸린다. "등락률이 가장 높고,
거래대금이 평소보다 많은 산업"은 설명할 수 있지만 "가중치 0.3"은 설명할 수 없다.
단계적 통과는 결과를 사람이 손으로 재현할 수 있다.

### 순위는 그대로 옮긴다

선정된 산업의 `rank`를 **다시 매기지 않고 그대로** 카드에 싣는다.
목업의 `평균 등락률 1위`, `평균 등락률 9위`가 이 값이다.

선정 순서와 순위는 별개다 — 등락률 1위가 거래대금 관문을 통과하지 못해 3위가 선정되면
카드에 `rank: 3`이 실린다.

### 거래대금 변화율

**오늘 값은 이미 손에 있다.** `IndustryFlowRefresher`가 등락률을 얻기 위해 종목마다
`quoteReader.readWithoutTracking()`을 이미 부르고, 그 `Quote`가 `tradingValue`를 들고 있다.
`changeRate()`만 꺼내 쓰고 버리던 값이다. **추가 KIS 호출도 DB 조회도 없다.**

새로 읽는 것은 **과거 20일 일봉**뿐이고 그것도 DB다. 그래서 이 지표를 더해도 외부 호출이 늘지 않는다
(성공 기준 14번).

산업의 거래대금을 먼저 금액으로 집계하고, 그 산업 자신의 20거래일 평균과 비교한다.

```
종목ᵢ의 20일 평균 = 최근 20거래일 (종가 × 거래량)의 평균          ← 저장된 일봉
산업의 20일 평균  = Σ(종목ᵢ의 20일 평균) ÷ 대상 종목 수
산업의 오늘 값    = Σ(종목ᵢ의 Quote.tradingValue) ÷ 대상 종목 수   ← KIS 당일 누적
변화율            = (오늘 ÷ 20일 평균 − 1) × 100
```

소수 둘째 자리로 반올림한다.

**분자와 분모의 대상 종목은 반드시 같다.** 20일 평균을 구할 수 없는 종목은 분모에 기여하지
못하므로 분자에서도 빼야 한다. 그러지 않으면 "오늘은 87종목, 과거는 60종목"을 나누게 되어
비교가 성립하지 않는다.

**구현 참고:** 대상 종목이 같으므로 종목 수가 약분된다 — `(Σa/N) ÷ (Σb/N) = Σa ÷ Σb`.
나눗셈 두 번을 생략해도 결과가 같다. 명세를 평균으로 정의해 두는 것은 나중에 절대 금액을
화면에 노출할 때 의미를 일관되게 하기 위해서다.

**금액 자체는 저장하지 않는다.** 변화율만 저장한다. 목업이 `거래대금 20일 평균 대비 +52.65%`만
표시하고 절대 금액은 보여주지 않는다. "오늘 6,000억" 같은 표시가 필요해지면 컬럼을 더한다.

### 갱신 한 바퀴의 흐름

```
산업 1개 선택 (cursor 순회)
  └─ 종목 id 목록                     industryReader.readStockIds()        DB
       └─ 종목마다:
            ├─ 종목 정보               stockReader.read()                   DB
            ├─ 시세                    quoteReader.readWithoutTracking()    캐시/KIS  ← 기존
            │    ├─ changeRate         → 평균 등락률, 상승·하락 종목 수
            │    └─ tradingValue       → 오늘 거래대금          ★ 새로 쓰는 값 (호출 0)
            └─ 일봉 20행               readStoredLatest(stockId, 20)        DB       ★ 새 조회
                 └─ Σ(종가×거래량)÷20  → 그 종목의 20일 평균

  산업 오늘 값    = Σ tradingValueᵢ ÷ 대상 종목 수
  산업 20일 평균  = Σ (종목ᵢ의 20일 평균) ÷ 대상 종목 수
  변화율          = (오늘 ÷ 20일 평균 − 1) × 100   → industry_flows 에 저장
```

### 왜 종목별 비율의 가중 평균이 아닌가

검토했으나 채택하지 않았다. 종목마다 비율을 내고 시가총액으로 가중하는 방식은 같은 데이터에서
다른 답을 낸다.

| 방식 | 계산 | 문제 |
| --- | --- | --- |
| 종목별 비율의 단순 평균 | `avg(오늘ᵢ ÷ 평균ᵢ) − 1` | 소형주 하나가 5배면 산업 전체가 +210%로 표시된다 |
| 종목별 비율의 시총 가중 평균 | `Σ(시총ᵢ × 비율ᵢ) ÷ Σ시총ᵢ − 1` | 가중 로직이 따로 필요하고, 시가총액 결측을 다뤄야 한다 |
| **산업 금액 집계 후 비교** | `Σ오늘ᵢ ÷ Σ평균ᵢ − 1` | — |

금액을 더하면 **가중이 이미 들어가 있다.** 대형주의 600억이 소형주의 20억 옆에서 자연히 크게
기여한다. 별도 가중치가 없으므로 시가총액 결측·0 처리도 필요 없다.
요구사항 문구(`거래대금 변화율`)와도 가장 가깝다 — 거래대금이라는 금액의 변화율이다.

평균 등락률(`IndustryFlowCalculator.average`)은 **종목별 단순 평균**이라 방식이 다르다.
모순이 아니다 — 등락률은 이미 `%`로 정규화된 값이라 금액처럼 더할 수 없고, 목업이
"소속 종목 4개 **단순평균**"이라고 명시한다.

### 20일 평균은 저장하지 않고 갱신 때 계산한다

`IndustryFlowRefresher`가 산업을 갱신할 때 일봉에서 계산하고, **결과인 변화율만**
`industry_flows`에 저장한다.

산업의 일별 거래대금을 별도 테이블에 쌓는 방안을 검토했으나 채택하지 않았다.

| | 새 테이블에 일별 저장 | **갱신 때 일봉에서 계산** |
| --- | --- | --- |
| 새 테이블 | 1개 | 0개 |
| 새 스케줄러 | 필요 (장 마감 후 1일 1회) | 0개 — 기존 Refresher 재사용 |
| 배포 직후 | **20거래일(약 한 달) 동안 작동하지 않는다.** 백필해야 한다 | 즉시 작동한다 |
| 갱신 때 읽는 행 | 20행 | 종목 수 × 20 (87종목이면 1,740행) |

결정적인 것은 세 번째 줄이다. 어느 쪽이든 과거 20일은 일봉에서 계산해야 하므로,
테이블을 만들면 **백필 작업이 덧붙을 뿐 얻는 것이 없다.**
1,740행은 DB 조회이고 KIS 호출이 아니다 — 갱신 주기가 15분이라 부담이 없다.

산업 거래대금의 **일별 추이를 화면에 보여줄 요구사항이 생기면** 그때 테이블을 만든다.
지금 요구사항에는 없다(CLAUDE.md "요청하지 않은 기능은 만들지 않는다").

### 제외 규칙

거래대금 계산에서 빼는 종목이다. 하나라도 어기면 그 종목만 빠지고 나머지로 계산한다.

| 상황 | 이유 |
| --- | --- |
| 저장된 일봉이 20개 미만 | 20일 평균을 낼 수 없다. 신규 상장 등 |
| 20일 평균이 0 | 0으로 나눌 수 없다 |
| 시세를 구하지 못함 | 오늘 값이 없다. `industry-flow`의 부분 실패 규칙과 같다 |

**전 종목이 제외되면 `tradingValueChangeRate`는 `null`이다.** 0.00으로 저장하면
"계산 실패"가 "변화 없음"으로 위장한다. `industry-flow`가 평균 등락률에 쓰는 것과 같은 판단이다.

**`null`은 선정 2단계를 통과하지 못한다.** 거래대금이 평소 이상인지 확인할 수 없으면
`MATCHED`라고 말할 수 없다.

### 상승·하락 종목 수

목업이 카드 성격에 따라 다른 숫자를 보여준다.

```
자동차(상승 카드): "표시된 종목 4개 중 3개가 상승했어요"
화학(하락 카드):   "표시된 종목 4개 중 2개가 하락했어요"
```

**둘 다 내려주고 프론트가 고른다.** 보합(등락률 `0.00`)인 종목이 있을 수 있어
`stockCount − risingCount`로 역산할 수 없다.

```
risingCount  = changeRate > 0 인 종목 수
fallingCount = changeRate < 0 인 종목 수
보합         = 어느 쪽에도 세지 않는다
```

모집단은 평균에 실제로 반영된 종목(`stockCount`)이다. 시세를 구하지 못한 종목은 세지 않는다.

### 관련 뉴스

**이번 범위는 뉴스뿐이다.** 공시(`Announcements`)는 원문 링크와 출처 컬럼이 없어 제외한다
(아래 「선행 조건 ②」 참고).

**선정이 끝난 뒤에 조회한다.** 뉴스는 선정에 관여하지 않으므로 **카드 2개의 산업만** 대상이다.
후보 9개를 미리 훑을 필요가 없다.

**시간 창** (RQ-0403) — `[직전 거래일 종가 산정 시점, 현재 데이터 기준 시각]`

```
시작: 직전 거래일의 시장 마감 시각 (국내 15:30 KST, 미국 16:00 ET)
끝:   그 산업의 calculatedAt
```

직전 거래일은 저장된 마지막 확정 일봉의 거래일(`DailyPrices.lastTradeAt()`)로 안다.
장 마감 후 발표된 자료(예: 전날 18시 실적 공시)가 다음 날 주가를 움직이는 경우가 흔하므로
"오늘 00시부터"가 아니라 이 창이 맞다.

**대상** — 그 산업에 매핑된 **종목 전체**. 대표 종목 2개로 좁히지 않는다.
RQ-0403이 "해당 산업 또는 소속 종목과 연결된"이라고 쓴다.

**중복 제거** — `stock_news`는 N:M 조인 테이블이라 한 뉴스가 한 산업의 여러 종목에 걸린다.
`newsId`로 한 번만 싣는다.

**정렬·상한** — 발표 시각 내림차순, **1건**. 목업이 1건을 보여준다.
상수(`NEWS_LIMIT`)로 두어 늘릴 때 한 줄만 고치면 되게 한다.

**한계: "가장 최신 1건"이고 "가장 중요한 1건"이 아니다.** 중요도를 판단할 신호(조회수, 언론사
등급, 본문 분석)가 데이터에 없어 `publishedAt` 내림차순만 쓸 수 있다. 1건만 내려주므로
그 한 건의 선택이 곧 카드의 설명이 된다 — 신호가 생기면 정렬을 바꾼다.

**0건이면 빈 배열** — `null`이 아니다. `industry-flow`의 `majorStocks`와 같은 정책이라
프론트가 길이만 확인하면 된다.

**조회가 실패해도 카드는 응답한다.** 뉴스는 부가 정보이므로 없어도 카드가 성립한다
(RQ-0403이 생략을 허용한다). 실패는 로그로 남기고 빈 배열을 싣는다.

### 저장하는 것과 조회 시 계산하는 것

| 값 | 위치 | 이유 |
| --- | --- | --- |
| 거래대금 변화율 | `industry_flows`에 **저장** | 종목 수 × 20행을 읽어야 해 요청마다 계산하면 느리다 |
| 상승·하락 종목 수 | `industry_flows`에 **저장** | 시세를 이미 모은 갱신 시점에 함께 센다 |
| 카드 2건 선정 | **조회 시** | 저장된 9개 행을 놓고 고르는 일이라 비용이 없다 |
| 관련 뉴스 | **조회 시** | DB 읽기라 싸고, 최신성이 중요하다 |
| 순위 | 저장하지 않음 | `industry-flow`의 기존 결정을 따른다 |

`IndustryFlowRefresher`가 산업을 한 바퀴 돌며 갱신하는 기존 구조를 그대로 쓴다.
**새 스케줄러도, 새 테이블도 만들지 않는다.**

### 스키마 변경

`industry_flows`에 컬럼 3개를 더한다. DDL은 손으로 쓰지 않고 Hibernate가 생성한 것을 옮긴다.

| 컬럼 | 타입 | null | 비고 |
| --- | --- | --- | --- |
| `trading_value_change_rate` | `decimal(7,2)` | **허용** | 계산 불가 시 `null` |
| `rising_count` | `int` | 불가 | 기본 0 |
| `falling_count` | `int` | 불가 | 기본 0 |

`decimal(7,2)`는 ±99999.99%까지 담는다. 거래대금이 평소의 1000배가 되는 이상치도 들어간다.

### 부분 실패

`industry-flow`의 규칙을 그대로 잇는다.

- 한 종목의 일봉 조회 실패는 그 종목만 제외하고 나머지로 계산한다.
- 매핑된 종목이 있는데 시세를 하나도 구하지 못하면 저장하지 않는다(직전 값을 남긴다).
- 거래대금을 구하지 못해도 평균 등락률은 저장한다 — 둘은 독립된 값이다.

### 설계

- `IndustryTradingValue`(신규 VO): 산업의 오늘 금액과 20일 평균 금액을 받아 변화율을 낸다.
  제외 규칙과 `null` 판단을 여기 둔다.
- `IndustryFlowSnapshot`(기존 VO)에 `tradingValueChangeRate`·`risingCount`·`fallingCount`를 더한다.
- `IndustryFlowCalculator`(기존)가 세 값을 함께 계산한다. 계산기는 DB를 모른다.
- `QuotedStock`(기존)에 `tradingValue()` 접근자와 `averageTradingValue20d` 컴포넌트를 더한다.
  구하지 못했으면 비어 있고, 제외 판단은 `IndustryTradingValue`가 한다.
- `IndustryCardSelector`(신규 도메인 정책, `@Component`): 선정·대체 규칙을 소유한다.
  DB도 시각도 모르고 산업 목록만 받아 2건을 고른다 — 그래서 순수 단위 테스트가 된다.
- `IndustryNewsService`(신규, `industry/flow/service`): `IndustryFlowService.read(country)` →
  `IndustryCardSelector`로 2건 선정 → 선정된 산업의 종목으로 `NewsReader` 호출 → 응답 조립.
- `IndustryNewsController`(신규): `GET /api/v1/industries/news` → `ApiResult<List<IndustryNewsResponse>>`.
- `IndustryFlowRefresher`(기존)에 일봉 조회 의존을 더한다.

## 선행 조건 — 내가 함께 구현한다

두 모듈이 내 담당은 아니지만 **계약을 기다리지 않고 직접 넣는다.** 둘 다 추가량이 작고,
기다리면 이 기능 전체가 멈추기 때문이다.

**규칙: 남의 모듈에는 파일을 더하기만 하고 기존 파일은 고치지 않는다.** 예외는
`DailyPriceReader` 인터페이스에 메서드 한 줄을 더하는 것뿐이다. 리뷰에서 디프를 한눈에
확인할 수 있어야 하고, 담당자의 작업과 충돌할 여지를 줄여야 한다.

머지 전에 두 담당자에게 알린다 — 승인이 아니라 통보다. 내용은 「팀 전달」 절에 있다.

### ① `stock-daily-price` — 동기화 없이 읽는 계약

```java
/** 저장된 최근 일봉 N개. 외부 동기화를 하지 않으므로 부족하면 부족한 대로 돌려준다. */
DailyPrices readStoredLatest(Long stockId, int days);
```

우리가 필요한 것은 **일봉 행(`closePrice`, `volume`)**이다. 거래대금 근사와 평균은 이 모듈에서
직접 계산한다 — `averageVolume20d`처럼 완성된 스칼라를 받는 것이 아니다.

**`DailyPriceReader`의 기존 메서드 두 개 모두 쓸 수 없다.**

```java
public DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to) {
    StockWithMarket stock = stockReader.read(stockId);
    syncIfNeeded(stock, from);        // ← KIS 호출 가능
    return DailyPrices.of(repository.findByStockIdAndTradeAtBetween...);
}
```

| 메서드 | 왜 못 쓰나 |
| --- | --- |
| `averageVolume20d(stockId)` | **거래량** 평균이다. 거래대금이 아니라 값 자체가 다르다 |
| `findBetween(stockId, from, to)` | 행은 맞지만 첫 줄에서 `syncIfNeeded`가 KIS를 호출할 수 있다 |

read-through는 `DailyPriceReader`의 의도된 설계다 — 주석이 "호출자는 동기화의 존재를 알 필요가
없다"고 쓴다. 종목 하나를 보는 화면(차트·주요 지표)에는 이것이 맞다. 그러나 **종목 87개를 훑는
산업 갱신에서는 같은 편의가 재앙**이 된다. 모의투자 한도가 초당 2건(실측)이므로
한 산업 갱신이 87번의 KIS 호출을 유발하면 예산이 무너진다.

`stock-quote`의 `readWithoutTracking`(PR #27)이 같은 이유로 만들어진 선례다 —
"부수효과 없이 읽기만 하는 경로"를 따로 노출한다.

**날짜 대신 개수로 받는 이유** — "최근 20거래일"은 휴장일 때문에 날짜로 환산되지 않는다.
`averageVolume20d`가 `Limit.of(DailyPrices.AVERAGE_DAYS)`를 쓰는 것과 같은 이유다.

**거래대금 계산을 일봉 모듈에 두지 않는 이유** — `종가 × 거래량` 근사는 거래대금 컬럼이 없어서
내가 택한 임시 방편이다. 이 정의를 남의 모듈에 심으면, 나중에 `acml_tr_pbmn` 컬럼이 들어와
근사가 필요 없어졌을 때 그 모듈을 고쳐야 한다. 행만 받아 오면 교체가 내 모듈 안에서 끝난다.

**구현 디프** — 기존 리포지토리 메서드를 그대로 쓴다. 새 쿼리가 없다.

```java
@Override
public DailyPrices readStoredLatest(Long stockId, int days) {
    return DailyPrices.of(repository
            .findByStockIdOrderByTradeAtDesc(stockId, Limit.of(days)).stream()
            .map(StockDailyPrices::toDailyPrice)
            .toList());
}
```

`averageVolume20d`에서 `stockReader.read`·`syncIfNeeded`·평균 계산만 뺀 형태다.
`DailyPriceReader`에 메서드 한 줄, `StockDailyPriceService`에 이 메서드 하나, 테스트 하나가 전부다.

### ② `news` — 뉴스·공시 조회 계약

```java
// news/service/NewsReader.java
public interface NewsReader {
    /** 종목들에 연결된 뉴스를 발표 시각 구간으로 읽는다. 발표 시각 내림차순. */
    List<RelatedNews> readByStockIds(List<Long> stockIds,
            LocalDateTime from, LocalDateTime to);
}

public record RelatedNews(
        Long newsId,             // 중복 제거 키
        String title,
        String publisher,
        LocalDateTime publishedAt,
        String url
) {}
```

**평평한 `List`로 충분하다.** 뉴스가 선정에 관여하지 않으므로 산업이 이미 정해진 뒤에 부른다.
카드마다 한 번씩, 요청당 **2회** 호출한다.

**`newsId`가 필요한 이유** — `stock_news`가 N:M이라 한 뉴스가 한 산업의 여러 종목에 걸린다.
`newsId`로 중복을 제거한다.

`news` 모듈에는 지금 엔티티(`News`, `StockNews`)만 있고 `repository`·`service` 패키지가 없다.
셋을 새로 만든다 — `RelatedNews`(루트), `NewsReader`+`JpaNewsReader`(`service`),
`NewsRepository`(`repository`).

**`RelatedNews`는 `news` 모듈 루트에 둔다.** 내 모듈에 두면 계약을 제공하는 쪽이 소비하는 쪽을
import하게 되어 의존이 뒤집힌다. CLAUDE.md의 패키지 표가 `service` → "다른 모듈의 `service`·루트"를
허용하므로 이 방향이 맞다. `StockWithMarket`이 `stock` 루트에 있고 내 `QuotedStock`이 그것을
쓰는 것과 같은 모양이다 — **데이터를 가진 모듈이 값 객체를 소유한다.**

**`News` 엔티티를 수정하지 않는다.** `News`에는 접근자가 하나도 없다(`@Getter`도, getter 메서드도
없다). `@Getter`를 붙이는 대신 **JPQL 생성자 프로젝션**으로 값만 꺼낸다.

```java
@Query("""
        select distinct new com.swyp.ploutos.news.RelatedNews(
                n.newsId, n.title, n.publisher, n.publishedAt, n.url)
        from News n, StockNews sn
        where sn.newsId = n.newsId
          and sn.stockId in :stockIds
          and n.publishedAt between :from and :to
        order by n.publishedAt desc
        """)
List<RelatedNews> findRelated(@Param("stockIds") List<Long> stockIds,
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
```

`StockNews`에 `@ManyToOne`이 없어 연관 경로를 쓸 수 없으므로 `where`로 조인한다.
`distinct`가 N:M 중복(한 뉴스가 같은 산업의 여러 종목에 걸리는 경우)을 DB에서 해결한다 —
자바에서 `newsId`로 다시 걸러낼 필요가 없다.

#### 공시는 이번 범위에서 뺀다

RQ-0403이 "뉴스·공시"라고 쓰지만 **이번 구현은 뉴스만 읽는다.** 결정 근거는 셋이다.

1. `Announcements`에 **`url` 컬럼이 없다.** RQ-0405의 "관련 자료 선택 시 원문으로 이동"이
   공시에서는 성립하지 않는다. 컬럼 추가 또는 DART 공시번호로 URL을 조립하는 작업이 선행된다.
2. `Announcements`에 **출처(`publisher`)가 없다.** `name`과 `AnnouncementType`뿐이라
   목업의 `산업통상자원부 발표` 같은 표기를 만들 수 없다.
3. `News`에는 필요한 것이 **모두 있다** — `title`, `publisher`, `url`, `publishedAt`.
   뉴스만으로 화면이 완성되고, 공시는 컬럼이 갖춰진 뒤 **같은 자리에 더하면 된다.**

`SourceType {NEWS, DISCLOSURE}`를 응답에 싣지 않는다. 값이 하나뿐인 필드는 정보가 없다.
공시가 들어올 때 필드를 추가하는 편이 낫다 — 그때가 실제로 구분이 필요해지는 시점이다.

**프론트에 전달할 것:** 카드의 문구(`가격 움직임 전후 확인된 뉴스·공시`)는 목업 그대로 두어도
되지만, 당장 내려가는 자료는 뉴스뿐이다.

## API 계약

프론트엔드에 전달하는 계약이다. 봉투 규칙은 `SPEC-api-response.md`를 따른다.
구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.

### `GET /api/v1/industries/news?country=KR`

요청: 쿼리 `country` ∈ `KR` | `US`, 생략 시 `KR`. 화면의 시장 토글과 같다.

**200 성공**

```json
{
  "data": [
    {
      "code": "AUTOMOBILE",
      "displayName": "자동차",
      "direction": "RISING",
      "rank": 1,
      "avgChangeRate": 1.61,
      "stockCount": 4,
      "risingCount": 3,
      "fallingCount": 1,
      "news": [
        {
          "title": "자동차 수출 증가 발표",
          "publisher": "산업통상자원부",
          "publishedAt": "2026-09-04T09:00:00+09:00",
          "url": "https://example.com/news/1"
        }
      ],
      "calculatedAt": "2026-09-04T15:30:00+09:00"
    },
    {
      "code": "CHEMICAL",
      "displayName": "화학",
      "direction": "FALLING",
      "rank": 9,
      "avgChangeRate": -0.35,
      "stockCount": 4,
      "risingCount": 2,
      "fallingCount": 2,
      "news": [],
      "calculatedAt": "2026-09-04T15:30:00+09:00"
    }
  ]
}
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `code` | string | X | 산업 코드. `industryId`는 노출하지 않는다 |
| `displayName` | string | X | 한글 표시명 |
| `direction` | string | X | `RISING`(상승 카드) 또는 `FALLING`(하락 카드) |
| `rank` | number | X | 9개 중 등락률 순위. 1부터. 선정 순서와 무관하다 |
| `avgChangeRate` | number | X | 평균 등락률 %. 직전 거래일 종가 대비 |
| `stockCount` | number | X | 평균에 반영된 종목 수 |
| `risingCount` | number | X | 그중 상승 종목 수 |
| `fallingCount` | number | X | 그중 하락 종목 수 |
| `news` | array | X | 관련 뉴스. 없으면 `[]`. **1건** (가장 최근). 배열인 이유는 0건일 때 `[]`로 표현하고 늘릴 때 계약이 안 바뀌게 하기 위해서다 |
| `news[].title` | string | X | 제목 |
| `news[].publisher` | string | X | 출처 |
| `news[].publishedAt` | string | X | 발표 시각. 시장 현지 오프셋 |
| `news[].url` | string | X | 원문 링크 |
| `calculatedAt` | string | **O** | 계산 시각. 시장 현지 오프셋. 계산된 적 없으면 `null` |

배열은 항상 길이 2이며 `[RISING, FALLING]` 순이다. 대체 선정이 있어도 길이는 변하지 않는다.

**거래대금 변화율은 응답에 넣지 않는다.** 화면이 표시하지 않기 때문이다. 선정 관문으로만 쓰이며
`industry_flows`에 저장된다. RQ-0401의 수용 기준은 "거래대금 변화율이 한 카드에 표시된다"이고
목업 프로토타입에도 `거래대금 20일 평균 대비 +52.65%` 줄이 있지만, 실제 디자인에 없다는 확인을
받아 제외했다. 표시가 필요해지면 `IndustryNewsResponse`에 필드 한 줄을 더하면 된다 —
값은 이미 저장돼 있다.

**선정 근거(`selectedBy`)도 응답에 넣지 않는다.** 화면이 "거래대금 증가가 함께 나타난 경우"와
"등락률 기준"을 구분해 표시하지 않기 때문이다. 도메인(`IndustryCard.selectedBy`)에는 남겨
선정 규칙 테스트가 대체 경로를 검증한다 — 뽑힌 산업이 같을 때 두 경로를 구분할 길이
이 값뿐이다. 화면이 구분하게 되면 필드 한 줄을 더하면 된다.

### 오류

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| `country`가 허용되지 않은 값 | 400 | `P001` |

산업이 하나도 없는 경우는 발생하지 않는다 — 시드가 9행을 보장한다.

## 명령어

```bash
docker compose up -d mysql   # 로컬 DB (bootRun 전)
./gradlew build              # 빌드
./gradlew test               # 전체 테스트
./gradlew test --tests '*IndustryNews*' --tests '*IndustryCardSelector*'   # 이 모듈만
./gradlew bootRun            # 로컬 실행
```

DDL 재생성:

```bash
./gradlew test --tests '*PloutosApplicationTests*'   # build/schema.sql 생성
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/industry/flow/
  IndustryFlowSnapshot.java        # 수정 — 필드 3개 추가
  IndustryTradingValue.java        # 신규 — 변화율 계산 VO
  IndustryCardSelector.java        # 신규 — 선정·대체 규칙 (@Component)
  QuotedStock.java                 # 수정 — tradingValue(), averageTradingValue20d
  IndustryFlows.java               # 수정 — 컬럼 3개
  service/
    IndustryFlowCalculator.java    # 수정 — 세 값 계산
    IndustryFlowRefresher.java     # 수정 — 일봉 조회 의존 추가
    IndustryNewsService.java       # 신규
  controller/
    IndustryNewsController.java    # 신규
    IndustryNewsResponse.java      # 신규

src/test/java/com/swyp/ploutos/industry/flow/
  IndustryCardSelectorTest.java    # 신규 — 단위. 선정 규칙의 정본
  IndustryTradingValueTest.java    # 신규 — 단위
  IndustryFlowCalculatorTest.java  # 수정
  IndustryFlowRefresherTest.java   # 수정
  service/IndustryNewsServiceTest.java       # 신규 — 단위
  controller/IndustryNewsControllerTest.java # 신규 — 슬라이스
```

남의 모듈에 더하는 파일. **기존 파일 수정은 인터페이스 한 줄뿐이다.**

```
src/main/java/com/swyp/ploutos/news/
  RelatedNews.java                     # 신규 — 값 객체 (모듈 루트)
  repository/NewsRepository.java # 신규 — JPQL 생성자 프로젝션
  service/NewsReader.java              # 신규 — 계약
  service/JpaNewsReader.java           # 신규 — 구현
  News.java, StockNews.java            # 수정하지 않는다

src/main/java/com/swyp/ploutos/stock/price/service/
  DailyPriceReader.java                # 수정 — 메서드 선언 한 줄
  StockDailyPriceService.java           # 수정 — readStoredLatest 구현 (기존 메서드 건드리지 않음)

src/test/java/com/swyp/ploutos/news/service/JpaNewsReaderTest.java   # 신규 — 통합(Testcontainers)
src/test/java/com/swyp/ploutos/stock/price/service/DailyPriceReaderTest.java  # 수정 — 동기화 안 함 검증
```

## 코드 스타일

`CLAUDE.md`를 따른다. 이 모듈에서 특히 지키는 것:

```java
/** 한 산업의 거래대금. 오늘 금액을 자기 20거래일 평균과 견준다. */
public record IndustryTradingValue(BigDecimal today, BigDecimal average20d) {

    /** 20일 평균이 0이면 견줄 수 없다. 0.00으로 내려 실패를 숨기지 않는다. */
    public Optional<BigDecimal> changeRatePercent() {
        if (average20d.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(today.divide(average20d, DIVISION_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .movePointRight(2)
                .setScale(SCALE, RoundingMode.HALF_UP));
    }
}
```

- `else` 금지. guard clause로 중첩을 없앤다.
- setter 금지. 상태 변경은 의도가 드러나는 도메인 메서드로.
- 접근자는 `get` 없이 `ticker()` 형태.
- 계약만 `public`. `IndustryNewsController`·`IndustryNewsResponse`는 package-private.
- 파일 끝에 개행.

## 테스트 전략

JUnit 6, BDD(`// given` `// when` `// then`), 메서드명은 한글 `조건_결과`.

| 레벨 | 대상 | 예 |
| --- | --- | --- |
| 단위 60% | `IndustryCardSelector`, `IndustryTradingValue`, `IndustryFlowCalculator` | `거래대금이_평소_미달이면_차순위_산업을_고른다` |
| 통합 30% | `IndustryFlowRefresher` (Mockito), 저장 | `일봉이_스무개보다_적은_종목은_거래대금에서_제외한다` |
| E2E 10% | 컨트롤러 슬라이스 | `국가가_잘못되면_400과_P001을_반환한다` |

**선정 규칙** (`IndustryCardSelectorTest` — DB·시각 없이 순수 단위. 규칙의 정본)

- 등락률 1위가 거래대금 관문을 통과하면 그 산업을 고르고 `selectedBy`가 `MATCHED`다
- 등락률 1위가 미달이면 2위를, 2위도 미달이면 3위를 본다
- 상승군 전체가 미달이면 등락률 1위로 대체하고 `selectedBy`가 `CHANGE_RATE_ONLY`다
- 하락 카드는 최하위부터 위로 훑는다
- 전 산업이 하락한 날에도 상승 카드가 1건 나온다
- 전 산업이 상승한 날에도 하락 카드가 1건 나온다
- 상승 카드만 관문을 통과하면 `selectedBy`가 카드마다 다르다
- 거래대금 변화율이 `null`인 산업은 관문을 통과하지 못한다
- 등락률이 정확히 0인 산업은 상승군·하락군 어디에도 들어가지 않는다
- 선정된 산업의 `rank`가 원래 순위 그대로다(1위가 아닐 수 있다)
- 배열은 언제나 길이 2이고 `[RISING, FALLING]` 순이다
- 같은 산업이 두 카드에 뽑히지 않는다

**거래대금**

- 오늘 평균 600억·20일 평균 400억이면 변화율이 `50.00`이다
- 일봉 20개 미만·20일 평균 0인 종목이 분자·분모에서 함께 제외된다
- 전 종목이 제외되면 변화율이 `null`이다
- 대형주의 금액이 소형주보다 크게 반영된다(별도 가중치 없이)

**나머지**

- 보합 종목이 `risingCount`·`fallingCount` 어느 쪽에도 세지지 않는다
- 시간 창 밖의 뉴스가 포함되지 않는다
- 같은 뉴스가 한 산업의 두 종목에 걸려도 한 번만 실린다
- 뉴스가 2건 이상 걸려도 가장 최근 1건만 실린다
- 뉴스가 0건이면 `news`가 빈 배열이다
- 뉴스 조회가 실패해도 카드는 응답한다
- 조회가 외부 시세를 호출하지 않는다

## 경계

**항상 한다**
- 새 로직은 테스트와 함께 머지한다
- 계산 결과는 소수 둘째 자리로 반올림해 내려준다
- 실패를 0으로 덮어쓰지 않는다 — `null`로 드러낸다
- 다른 모듈의 데이터는 그 모듈의 `service` 계약으로만 읽는다

**먼저 묻는다**
- `industry_flows` 스키마 변경 (DDL을 로컬·서버에 수동 적용해야 한다)
- `stock-daily-price`·`news` 모듈의 계약 추가
- KIS 호출 횟수가 늘어나는 변경

**절대 안 한다**
- `stock_news`·`announcements` 리포지토리를 이 모듈에서 직접 조회
- 새 스케줄러·새 테이블 추가 (기존 `IndustryFlowRefresher`와 `industry_flows`를 쓴다)
- 비밀값 커밋
- 승인 없이 실패하는 테스트 삭제

## 성공 기준

1. `GET /api/v1/industries/news?country=KR`이 **정확히 2건**을 돌려주고 `direction`이
   `["RISING", "FALLING"]` 순이다. 어떤 데이터에서도 길이가 2가 아닌 경우가 없다.
2. 등락률 1위 산업의 거래대금이 평소 미달이고 3위 산업이 평소 이상이면 **상승 카드는 3위 산업**이고
   그 `rank`가 `3`이다(순위를 다시 매기지 않는다). 선정 근거는 `IndustryCard.selectedBy`로
   확인한다 — 응답에는 나가지 않는다.
3. 같은 시점에 `/industries/flows`를 호출했을 때 **같은 산업의 `avgChangeRate`와 `rank`가 일치한다.**
4. 오늘 거래대금 평균이 600억이고 20거래일 평균이 400억인 산업의
   `industry_flows.trading_value_change_rate`가 `50.00`으로 저장된다.
5. 일봉이 20개 미만인 종목만 있는 산업은 `trading_value_change_rate`가 `null`로 저장되고,
   `MATCHED`로 선정되지 않는다.
6. 상승 2·하락 1·보합 1인 산업에서 `stockCount=4`, `risingCount=2`, `fallingCount=1`이다.
7. 시간 창 밖(직전 거래일 마감 이전)에 발표된 뉴스가 `news`에 없다.
8. 뉴스가 0건인 산업의 `news`가 `[]`이고 200으로 응답한다.
9. `country=JP`가 400 `P001`이다.
10. 이 API 호출 중 KIS 호출이 **0회**다 (`QuoteReader` mock이 호출되지 않는다).
11. 전 산업의 거래대금이 평소 미달인 데이터에서도 2건이 나온다(선정 단계에서
    `selectedBy`가 둘 다 `CHANGE_RATE_ONLY`).
12. 뉴스 테이블이 비어 있어도 2건이 나오고 `news`가 `[]`다 — 뉴스 연동 전에 배포할 수 있다.
13. `NewsReader` 호출이 요청당 **2회**다(카드 수만큼. 후보 9개를 훑지 않는다).
    각 응답의 `news` 길이가 0 또는 1이다.
14. 산업 갱신 중 KIS 호출 횟수가 거래대금 도입 **전과 같다**(일봉은 저장된 것만 읽는다).
15. `./gradlew test`가 전부 통과한다.

## 미해결 질문

1. **장중 거래대금 비교가 왜곡된다.** 아래 「알려진 한계」 참고. 보정할지, 한계로 둘지 결정이 필요하다.
2. **공시를 언제 넣을 것인가.** 이번에는 뺐다. `Announcements`에 `url`과 출처 컬럼이 생기면
   `news` 배열 옆에 더하거나 같은 배열에 `sourceType`을 붙여 합친다. 기획에 "이번엔 뉴스만"을
   알려야 한다 — 카드 문구가 `뉴스·공시`라고 되어 있다.
3. **한쪽 군이 빈 날의 카드 문구.** 선정 동작은 확정됐다 — 전체에서 등락률 양 끝을 쓴다.
   남은 것은 표시 문구다. 전 산업이 상승한 날 최하위가 `+0.05%`인데 "하락 관련"이라고 쓸지,
   "상대적으로 부진" 같은 다른 말을 쓸지. 백엔드는 `direction`과 `avgChangeRate` 부호를
   주므로 프론트가 판단할 수 있고 **구현을 막지 않는다.**
4. `stock_industries` 매핑 데이터. `industry-flow`에서 이어지는 문제이며,
   현재 서버에는 임시 테스트 종목(9001~9072)만 들어 있다.

**닫힌 질문**

- ~~`stock-daily-price` 계약을 담당자에게 요청할지~~ → 직접 넣는다. 별도 메서드(`readStoredLatest`).
  플래그 방식은 호출부에서 부수효과 유무가 드러나지 않아 쓰지 않는다.
- ~~`RelatedNews`의 소유 모듈~~ → `news` 모듈 루트. 의존 방향이 한쪽으로 유지된다.
- ~~뉴스 상한 3건이 맞는가~~ → **1건**. 목업이 1건이고, 상수라 늘리기 쉽다.
- ~~거래대금 변화율을 응답에 넣을지~~ → **넣지 않는다**. 화면이 표시하지 않는다는 확인을 받았다.
  선정 관문으로만 쓰고 `industry_flows`에 저장한다. RQ-0401 수용 기준과 어긋나는 결정이다.
- ~~선정 근거(`selectedBy`)를 응답에 넣을지~~ → **넣지 않는다**. 화면이 `MATCHED`와
  `CHANGE_RATE_ONLY`를 구분해 표시하지 않는다. 도메인에는 남겨 선정 규칙 테스트가 쓴다.
- ~~뉴스를 어느 종목으로 찾는가~~ → **대표 종목(시총 상위 2개) 먼저, 없으면 산업 전체로 확장.**
  카드가 보여주는 종목과 기사가 같은 종목을 가리키게 하고, 확장 단계가 있어 손실이 없다.

## 팀 전달

머지 전에 알린다. 승인이 아니라 통보다 — 두 모듈 모두 기존 파일을 고치지 않는다.

**`stock-daily-price` 담당께**

> 산업 흐름 갱신에서 종목별 일봉 20행이 필요한데 `findBetween`·`averageVolume20d` 둘 다
> `syncIfNeeded`를 거쳐 KIS를 호출할 수 있어, 종목 87개 루프에서 모의투자 한도(초당 2건)에
> 걸립니다. 저장된 것만 읽는 `readStoredLatest(stockId, days)`를 `DailyPriceReader`에
> 추가했습니다. 기존 리포지토리 메서드를 그대로 쓰고 새 쿼리는 없습니다.
> `readWithoutTracking`(PR #27)과 같은 성격으로 봤습니다.

**`news` 담당께**

> 핵심 뉴스에서 종목별 뉴스가 필요해 `news` 모듈에 `repository`·`service`를 만들었습니다.
> `News` 엔티티에 접근자가 없어서 `@Getter`를 붙이는 대신 JPQL 생성자 프로젝션으로 읽습니다 —
> **엔티티는 수정하지 않았습니다.** 추가한 파일은 `RelatedNews`(루트),
> `NewsReader`·`JpaNewsReader`(service), `NewsRepository`(repository)입니다.
> 뉴스 모듈을 본격적으로 만드실 때 이 계약은 필요에 맞게 바꾸셔도 됩니다.
>
> 별건으로 `Announcements`에 `url`과 출처 컬럼이 없어 **공시는 이번 범위에서 뺐습니다.**
> RQ-0403이 "뉴스·공시"를 요구하므로 컬럼이 갖춰지면 같은 자리에 더하면 됩니다.

## 알려진 한계

### 장중에는 거래대금 변화율이 음수로 치우친다

`Quote.tradingValue`는 **당일 누적** 거래대금이다. 20거래일 평균은 **하루 전체**의 평균이다.
둘을 그대로 나누면 장이 진행된 만큼만 쌓인 값과 하루치를 비교하게 된다.

```
09:30 (개장 직후)  누적 5억   ÷ 평균 400억  →  −98.8%
12:00 (중반)       누적 180억 ÷ 평균 400억  →  −55.0%
15:30 (마감)       누적 610억 ÷ 평균 400억  →  +52.5%   ← 목업의 값
```

목업이 `15:30 KST`(마감 시각) 기준이라 이 문제가 드러나지 않았다. **장중에 보면 거의 항상 음수다.**

이것이 선정 규칙에도 영향을 준다 — 장 초반에는 모든 산업이 관문을 통과하지 못해
두 카드 모두 `CHANGE_RATE_ONLY`가 된다. 기능이 망가지지는 않지만 오전에는 거래대금 신호가
사실상 작동하지 않는다.

보정을 검토했으나 채택하지 않았다. 경과 시간에 비례해 나누는 방식(`누적 ÷ 경과비율`)은
거래가 개장 직후와 마감 직전에 몰리는 실제 분포와 맞지 않아 오전에 과대 추정된다.
정확한 보정은 시간대별 거래량 분포를 저장해야 하는데 이 기능 하나를 위해 감당할 구조가 아니다.

**현재 결정: 보정하지 않고 값을 그대로 내려준다.** 미해결 질문 1번으로 남긴다.

### 오늘 값과 과거 값의 성질이 다르다

| | 출처 | 정확도 |
| --- | --- | --- |
| 오늘 거래대금 | KIS `acml_tr_pbmn` / `tamt` | **실제 값** |
| 과거 20일 | 저장된 일봉의 `종가 × 거래량` | 근사 |

성질이 다른 두 값을 나누므로 **체계적 편향**이 생길 수 있다. 종가가 그날 평균 거래단가보다
높은 경향이 있으면 분모가 과대 추정되어 변화율이 일관되게 낮게 나온다.

`stock_daily_prices`에 거래대금 컬럼이 없어 지금은 다른 방법이 없다. 컬럼이 추가되면
분모도 실제 값이 되어 편향이 사라진다 — 그때 **계산식 한 줄만 교체**하면 되고 응답의
의미는 바뀌지 않는다.

### 뉴스 시간 창이 시장 마감 시각 상수에 의존한다

국내 15:30, 미국 16:00을 상수로 둔다. 조기 폐장일(연말, 미국 공휴일 전날 13:00 폐장 등)에는
실제 마감과 어긋난다. 조기 폐장은 연 수회이고 어긋나도 창이 조금 넓어질 뿐이라 수용한다.

### 20일 평균을 갱신마다 다시 계산한다

20일 평균은 하루에 한 번만 바뀌는데 갱신 주기(약 15분)마다 다시 계산한다. 낭비이지만
DB 조회뿐이고 KIS 호출이 없어 수용한다. 종목 수가 크게 늘어 갱신 한 바퀴가 느려지면
그때 캐시나 일별 저장을 검토한다.

### 뉴스 데이터가 아직 없다

`news`·`stock_news` 테이블이 비어 있다. `news`가 항상 `[]`인 상태로도 API는 완성되며,
데이터가 들어오면 코드 변경 없이 채워진다.

## 추후 구현

- **공시(`Announcements`) 연동** — `url`과 출처 컬럼이 갖춰진 뒤. `news` 배열에 `sourceType`을
  붙여 합치거나 별도 배열로 내린다. RQ-0403의 "뉴스·공시" 중 절반이 여기 남아 있다
- 산업 일별 거래대금 이력 테이블 — 추이 차트 요구사항이 생기면
- 거래대금을 KIS 일봉에서 정확히 받아 근사를 교체
- 카드 수를 2건에서 늘리기 (기획 확정 시)
- `top-movers`(RQ-0501)는 별도 모듈·별도 명세
