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
응답 순서      = 순위 오름차순. 단 순위는 값으로 실려 나가므로 나중에 순서만 바뀔 수 있다
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

### 대표 종목

**시가총액 상위 4개를 저장**하고, 화면이 필요한 만큼 가져간다.

```
저장   시가총액 상위 4개 (현재가 포함)
  ├─ 시장 요약 "오늘의 산업 흐름" (/flows)   → 앞 2개, 현재가 미사용
  ├─ 오늘의 핵심 뉴스 (/news)                 → 앞 2개, 뉴스 탐색용 stockId
  └─ 산업별 동향 (/trends)                    → 4개 전부 + 현재가
```

`/flows`의 응답은 **여전히 0~2개**다. 저장만 4개로 늘어나고 이 API의 계약은 바뀌지 않는다.
개수를 2개에서 4개로 올린 것은 `industry-trend`(RQ-0601)가 카드마다 종목 4개를 요구해서다.

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
ploutos.industry-flow.calls-per-second = 1     ← 유일한 조절 장치
한 바퀴(= 갱신 주기) = 대상 종목 수 ÷ 이 값
KIS 부하 = 항상 초당 1건. 버스트가 없다
```

`5건으로 3분 일하고 2분 쉬는` 방식보다 **순간 최대 부하가 낮다.**

### 값이 3이 아니라 1인 이유 — 모의투자 한도는 초당 2건이다

처음에는 실전 한도(초당 20건)의 15%인 **3건**으로 잡았다. 그런데 **현재 앱키는 모의투자용이고,
모의투자 한도는 초당 2건이다.** 3건으로 두면 배치 혼자서 한도를 넘는다.

실측으로 확인한 내용:

| 확인 | 결과 |
| --- | --- |
| 모의투자 도메인 + 초당 5건으로 72종목 조회 | 67 성공 / 5 실패, 전부 `초당 거래건수를 초과하였습니다` |
| 앱을 초당 3건으로 실행 | 산업마다 4종목 중 1~2개가 빠짐(`stockCount` 2~3) |
| 실전 도메인 + 국내 현재가(`FHKST01010100`) | `rt_cd=0` 통과 |
| 실전 도메인 + 해외 현재가상세(`HHDFS76200200`) | `rt_cd=1` 거부 → **모의투자 키 확정** |

마지막 줄이 중요하다. 실전 도메인으로 바꿔 한도를 늘리는 선택지가 **없다** — 국내는 통과하지만
해외가 막혀서, 국내·해외를 모두 계산하는 이 모듈은 반쪽만 동작하게 된다.
`SPEC-kis-client.md`가 경고한 "게이트웨이가 API마다 검사를 다르게 적용한다"가 그대로 나타났다.

**따라서 모의투자를 쓰는 동안 전체 예산은 초당 2건이고**, 이 배치·`QuoteRefresher`·사용자
요청이 나눠 써야 한다. 배치는 절반인 1건만 쓴다. 실전 키로 전환하면 3으로 올린다.

> 이 실측은 `SPEC-kis-client.md`의 미해결 질문 *"모의투자 도메인의 호출 한도. 실전은 초당
> 20건인데 모의투자 수치는 확인하지 못했다"* 에 대한 답이기도 하다.

재배포 없이 환경변수 `PLOUTOS_INDUSTRYFLOW_CALLSPERSECOND` 로 조절할 수 있다.

### 실전 키를 받으면 할 일

실전 앱키 발급을 요청해 둔 상태다. 받으면 **셋을 함께** 바꾼다 — 하나라도 빠지면 조용히 어긋난다.

| 무엇 | 값 | 빠뜨리면 |
| --- | --- | --- |
| `KIS_APP_KEY` · `KIS_APP_SECRET` | 실전 키 | 그대로 모의투자 |
| `KIS_BASE_URL` | `https://openapi.koreainvestment.com:9443` | 실전 키로 모의 도메인 호출 → 거부 |
| `PLOUTOS_INDUSTRYFLOW_CALLSPERSECOND` | `3` | 한도가 20건인데 1건만 써서 한 바퀴가 10배 느리다 |

세 번째가 잊히기 쉽다. 오류가 나지 않고 `calculatedAt`이 낡을 뿐이라 눈에 띄지 않는다.

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

**동점은 세 단계로 가른다** (RQ-0603, `industry-trend`가 요구해 확장했다).

```
1) 반올림 전 평균 등락률  내림차순
2) 거래대금 비율(오늘 ÷ 20거래일 평균)  내림차순   ← 측정하지 못한 산업은 뒤로
3) 산업 표시명  가나다순
```

처음에는 3번 하나뿐이었는데, 그러면 **같은 `1.61%`인 두 산업의 순위가 등락률과 무관하게 이름으로
정해진다.** 응답에 나가는 `avgChangeRate`는 소수 둘째 자리라 그 둘을 구분할 수 없지만, 서버는
반올림 전 값을 들고 있으므로 1번으로 가를 수 있다 — 아래 「평균 등락률은 반올림하지 않고
저장한다」 참고.

2번은 `industry-news`가 쓰는 거래대금 금액(`today_trading_value`, `avg_trading_value20d`)으로
조회 시점에 만든다. 어느 쪽이든 순위가 매 요청 흔들리지 않는다.

### 순위는 순서에서 나오지만 값으로 실려 나간다

**지금은 응답 순서와 `rank`가 일치한다** — 등락률 높은 순으로 내려주고 `rank`는 1부터 올라간다.
오늘의 산업 흐름 카드는 등락률이 높은 산업부터 보여주는 것이 요구사항이다.

그래도 `rank`를 배열 위치에서 계산하게 두지 않고 **응답에 값으로 담는다.** 순서를 바꿀 계획이
이미 두 개 있기 때문이다.

| 언제 | 순서를 어떻게 바꾸는가 | `rank` |
| --- | --- | --- |
| 관심 산업 고정 (`industry-pin`, RQ-0205) | 고정한 최대 3개를 앞으로 당긴다 | 그대로 |
| 산업별 동향 탭 `전체` 필터 (`industry-trend`) | 산업명 가나다순으로 놓는다 | 그대로 |

고정했다고 3위가 1위가 되면 안 되고, 가나다순으로 놨다고 건설이 1위가 되면 안 된다. `rank`가
`RankedIndustryFlow`의 필드이므로 **완성된 목록을 재배열해도 각 원소가 자기 순위를 들고 간다** —
순위를 다시 매기는 코드를 어디에도 두지 않는다.

**따라서 프론트는 `rank`를 쓰고 배열 인덱스로 "N위"를 세지 않는다.** 지금은 둘이 같아서 인덱스로
세도 맞는데, 고정 기능이 들어오는 순간 조용히 틀린 화면이 된다.

### 고정 순서는 백엔드가 정한다

응답에 `pinned` 플래그만 실어 보내고 프론트가 정렬하는 방법도 있었지만 **백엔드가 정렬해 내려주는
쪽으로 정했다.** "고정 3개 먼저, 나머지는 등락률 순"은 두 화면(오늘의 산업 흐름, 산업별 동향 탭)이
같아야 하는 규칙이고, 프론트에 두면 화면마다 따로 구현돼 어긋난다.

`IndustryFlowService.read(country)`에는 사용자 인자가 없다. 고정은 사용자별이므로 로그인이
들어올 때 인자를 늘리거나, 이 리더를 감싸 재배열하는 별도 리더를 둔다 — 후자가 로그인 없는 현재
API를 그대로 살려 둘 수 있어 유력하지만 로그인 방식이 정해진 뒤 판단한다.

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

**위 표의 2행과 3행은 결과가 같아 보이지만 정반대로 처리한다.** 둘 다 "반영된 종목 0개"인데,
하나는 건너뛰고 하나는 저장한다. 구분하려면 시세 조회와 별개로 **"이 국가에 매핑된 종목이
있었는가"**를 알아야 한다. 그래서 종목 정보(DB)를 먼저 모으고 시세(KIS)를 나중에 받는다 —
시세 조회가 실패해도 그 종목이 국내였는지 해외였는지는 남는다.

```
매핑 있음 + 반영 0  →  건너뜀   (실패를 평균 0 으로 위장하지 않는다)
매핑 없음 + 반영 0  →  저장     (0 이 사실이다. calculatedAt 으로 계산이 돌고 있음을 알린다)
```

구분하지 않으면 매핑이 없는 산업은 `calculatedAt`이 영원히 `null`이라, **"배치가 고장났다"와
구분되지 않는다.** 감시 지표를 무력화하므로 같은 문제의 다른 얼굴이다.

**종목별 시세 실패는 로그를 남기지 않는다.** 실패 사유는 KIS 클라이언트가 이미 기록하므로 이 계층이
또 남기면 같은 내용이 중복되고, 장애 때 대상 종목 수만큼(수백 줄) 쏟아져 정작 원인이 묻힌다.
몇 종목이 빠졌는지는 저장되는 `stockCount`로 드러난다. 다만 **매핑이 없는 종목을 가리키는 경우**는
외부 장애가 아니라 데이터가 깨진 상태이고 사람이 고쳐야 하므로, 그때만 `stockId`와 함께 WARN을 남긴다.

### 감시 — `calculatedAt`

**산업별로 `calculatedAt`을 응답에 넣는다.** 스케줄러가 죽어도 API는 200을 반환하고 사용자는
멈춘 값을 정상으로 본다 — 2026-09-26의 13시간 502와 같은 종류의 조용한 고장이다.

`calculatedAt`이 있으면 프론트가 *"10분 전 기준"*을 표시하거나 경고할 수 있고, 개발자는 응답만
보고 스케줄러가 멈춘 것을 안다. 산업별로 주는 이유는 **특정 산업만 갱신이 멈춘 경우**(위 부분 실패)도
드러나야 하기 때문이다. 프론트는 9개 중 가장 오래된 값을 화면 기준 시각으로 쓸 수 있다.

### 설계

- `IndustryFlows`(엔티티): `industryId`, `country`, `avgChangeRate`, `stockCount`,
  `risingCount`, `fallingCount`, `todayTradingValue`, `avgTradingValue20d`, `calculatedAt`.
  유일 제약 `(industry_id, country)`.
- `IndustryFlowStocks`(엔티티, 자식 테이블): 대표 종목 스냅샷을 **행으로** 담는다.
  `industryFlowId`·`stockId`·`ticker`·`name`·`price`·`changeRate`·`displayOrder`.
  유일 제약 `(industry_flow_id, display_order)`.

  처음에는 `first_*`·`second_*` 평면 컬럼으로 두었다. 개수가 2개로 고정이라는 전제였는데
  `industry-trend`가 4개를 요구하면서 그 전제가 깨졌고, 현재가까지 붙으면 컬럼이 20개가 된다.
  **"몇 개까지 담을 수 있는가"가 스키마에 굳는 것**이 문제의 핵심이다 — 그 숫자가
  `MAJOR_STOCK_LIMIT`이라는 상수로 엔티티와 계산기 두 곳에 흩어져 있었고, 뜻이 다른데
  (저장 자리 수 / 표시 개수) 값만 같아 한쪽만 올리면 런타임에 터졌다.

  행으로 바꾸면 개수 변경이 **스키마 변경이 아니라 데이터 문제**가 되고 상한 상수가 사라진다.
  **연관관계 애너테이션은 쓰지 않는다** — 이 저장소에 `@OneToMany`가 한 곳도 없다.
  별도 리포지토리를 두고 `IndustryFlowService`가 `findByIndustryFlowIdIn`으로 9개분을
  한 번에 읽어 조립한다.
- `IndustryFlowService`(public): `List<RankedIndustryFlow> read(Country country)`.
  9행을 읽어 정렬·순위를 부여한다. `industry-trend`와 `industry-news`가 같은 값을 재사용한다.
  인터페이스를 두지 않고 컨트롤러가 이 클래스에 직접 의존한다 — 구현이 하나뿐이고,
  `stock-quote`가 `StockQuoteDetailReader` 인터페이스를 없앤 선례를 따른다.
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
| `calculatedAt` | string(ISO-8601, 오프셋 포함) | **O** | 이 산업의 값이 계산된 시각(시장 현지). 한 번도 계산되지 않았으면 `null` — 과거 시차는 한 바퀴 시간만큼이다 |

`majorStocks`를 배열로 두면 종목이 0·1개인 산업도 같은 형태로 표현되고, 나중에 4개로 늘릴 때
프론트 계약이 깨지지 않는다.

배열은 항상 **9개**이며 `rank` 오름차순으로 정렬돼 있다. 다만 `rank`는 값이므로 프론트는 배열
인덱스가 아니라 이 필드를 써야 한다 — 관심 산업 고정이 들어오면 둘이 어긋난다. `industryId`는
내려주지 않는다 —
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
src/main/java/com/swyp/ploutos/industry/flow/service/         → IndustryFlowService, IndustryFlowCalculator,
                                                                IndustryFlowRefresher, IndustryFlowProperties
src/main/java/com/swyp/ploutos/industry/flow/controller/      → IndustryFlowController, IndustryFlowResponse
src/main/resources/application.properties                     → ploutos.industry-flow.calls-per-second,
                                                                spring.task.scheduling.pool.size=2
db/                                                           → industry_flows 테이블 DDL
src/test/java/com/swyp/ploutos/industry/flow/                  → 단위·슬라이스 테스트
```

`industry/flow`를 하위 모듈로 두는 것은 `stock/price`·`stock/quote`의 선례를 따른다.
바깥이 써야 하는 `IndustryFlowService`만 `public`이고, 나머지(`IndustryFlowRefresher`,
`IndustryFlowProperties`, 컨트롤러·응답 DTO)는 package-private다.

## 코드 스타일

- 엔티티명은 복수형(`IndustryFlows`), 식별자는 `industryFlowId`. `@Column`의 `name`은 쓰지 않는다.
- `LocalDateTime` 필드는 `~At`으로 끝낸다(`calculatedAt`).
- 응답 DTO는 `record`. `@Setter`·`@Data` 금지, `@Getter`는 클래스 단위.
- `else` 없이 guard clause.
- 시각은 `Clock`으로 얻는다(`ClockConfig`가 이미 있다).
- 평균은 `BigDecimal`로 계산하고 **반올림하지 않은 채 저장한다.** 소수 둘째 자리로 자르는 것은
  응답 DTO에서 한다.

```java
// IndustryFlowCalculator — 반영된 종목이 없으면 0을 돌려준다
if (changeRates.isEmpty()) {
    return BigDecimal.ZERO;
}
return changeRates.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(changeRates.size()), SCALE, RoundingMode.HALF_UP);
```

### 평균 등락률은 반올림하지 않고 저장한다

`avg_change_rate`는 `decimal(12,6)`이고 **응답을 만들 때만** `setScale(2)`로 자른다.

**추가 정밀도는 입력이 아니라 나눗셈에서 나온다.** `Quote.changeRate()`가 종목 등락률을 이미
소수 둘째 자리로 반올림하므로, 평균이 두 자리를 넘는 것은 종목 수로 나눌 때다.

```
1.00 · 1.00 · 2.00  →  4.00 ÷ 3 = 1.333333      표기 1.33
1.33 한 종목         →         = 1.330000      표기 1.33
                                 ↑ 표기는 같고 저장된 값은 다르다
```

반올림해 저장하면 이 둘을 구분할 수 없어 **순위 동률을 풀 수 없다**(위 「순위는 저장하지 않는다」의
1번 기준). 응답에 나가는 값은 그대로 두 자리이므로 프론트가 보는 것은 달라지지 않는다.

PLO-41에서 거래대금을 비율이 아니라 금액으로 저장하도록 바꾼 것과 같은 판단이다 —
**원재료를 저장하고 가공을 미룬다.**

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위(60%)**
  - `IndustryFlowCalculatorTest` — 단순평균, 반영 종목 0개, 일부 종목 실패 시 제외,
    같은 ticker 중복 제거, 평균을 반올림하지 않는다, **시가총액 상위 4개 선정**(동점 시 ticker 순,
    종목이 그보다 적은 경우), 현재가를 함께 담는다. 가짜 `QuoteReader`를 쓴다
  - `IndustryFlowServiceTest` — 순위 부여(내림차순), **동점 시 반올림 전 등락률 → 거래대금 →
    가나다순**, 9개 보장, 자식 행을 한 번의 조회로 읽는다. `@Mock` 리포지토리
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
| 6 | 시가총액 상위 4개를 대표 종목으로 고른다(`/flows` 응답은 앞 2개). | `시가총액_상위_네_개를_대표_종목으로_고른다` |
| 7 | 시가총액이 같으면 ticker 순으로 정한다. | `시가총액이_같으면_ticker_순으로_고른다` |
| 8 | 반영된 종목이 1개면 대표 종목도 1개다. | `반영된_종목이_하나면_대표_종목도_하나다` |
| 9 | 대표 종목은 계산 시점의 종목명·등락률·현재가를 담는다. | `대표_종목은_계산_시점의_값을_담는다` |
| 10 | 평균 등락률 내림차순으로 1위부터 순위를 매긴다. | `IndustryFlowServiceTest.평균_등락률이_높은_순으로_순위를_매긴다` |
| 11 | 동점이면 반올림 전 등락률 → 거래대금 비율 → 산업명 순으로 갈린다. | `등락률이_동점이면_반올림_전_값으로_순위를_매긴다` 외 2개 |
| 12 | 저장된 값이 없어도 9개 산업을 모두 응답한다. | `저장된_값이_없어도_9개_산업을_모두_응답한다` |
| 13 | 매핑된 종목의 시세를 하나도 못 구하면 그 산업·국가만 저장을 건너뛴다. | `IndustryFlowRefresherTest.매핑된_종목의_시세를_하나도_구하지_못하면_저장하지_않는다` |
| 14 | 한 번 깨어날 때 산업 하나만 처리한다. | `한_번_실행하면_산업_하나만_처리한다` |
| 15 | 마지막 산업 다음에는 처음 산업으로 돌아간다. | `마지막_산업_다음에는_처음_산업으로_돌아간다` |
| 16 | 한 종목이 예외를 던져도 나머지 종목을 계속 조회한다. | `한_종목이_실패해도_나머지를_계속_조회한다` |
| 17 | 응답 배열은 9개이며 `rank` 오름차순이다. | `IndustryFlowServiceTest.평균_등락률이_높은_순으로_순위를_매긴다` |
| 18 | 산업마다 `calculatedAt`을 내려준다. | `산업마다_계산_시각을_내려준다` |
| 19 | 대표 종목이 없는 산업은 `majorStocks`가 빈 배열이다. | `대표_종목이_없으면_빈_배열을_응답한다` |
| 20 | `country`가 `KR`·`US`가 아니면 400 / `P001`. | `국가가_잘못되면_400과_P001을_반환한다` |
| 21 | 요청 처리 중 KIS를 호출하지 않는다. | `조회는_외부_시세를_호출하지_않는다` |
| 22 | 컨트롤러는 받은 순서를 다시 정렬하지 않는다. | `산업을_받은_순서_그대로_응답한다` |
| 23 | 매핑된 종목이 없는 국가는 평균 0 · 종목 0 으로 저장한다. | `IndustryFlowRefresherTest.매핑된_종목이_없는_국가는_0으로_저장한다` |

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
   **등락률 최고·최저**를 보여주지만, 이 명세는 **시가총액 상위**로 정했다(위 "대표 종목").
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
   `IndustryFlowService`를 그대로 쓴다. 계산을 두 곳에 두면 같은 화면의 두 영역이 다른 순위를 보인다.
   그 화면이 산업당 종목 4개를 요구해 **대표 종목을 자식 테이블 `industry_flow_stocks`로 분리했다**
   (`SPEC-industry-trend.md`). 여기서 예고한 결정을 그때 내렸다.
3. **갱신 실패 알림** — 지금은 `calculatedAt`으로 드러내기만 한다. 일정 시간 이상 갱신이 없으면
   경고하는 장치는 운영하며 필요해지면 붙인다.
