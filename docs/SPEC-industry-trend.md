# 명세: 산업별 동향 (industry-trend)

RQ-0601 ~ RQ-0606. 기능 맵: `docs/CAPABILITY-MAP-industry.md`의 `industry-trend` 행.

요구사항의 원본은 목업 `https://ploutos-requirements-qa.summmmmer-k.chatgpt.site/`의
**산업별 동향** 탭이다.

## 내가 세운 전제

1. 경로 접두어는 `/api/v1`, 응답 봉투는 `SPEC-api-response.md`의 `data`/`error` 규칙을 따른다.
2. **인증이 없다.** `SecurityConfig`가 `anyRequest().permitAll()`이고 로그인 엔드포인트도,
   세션·JWT도 없다. `Users` 엔티티만 존재한다.
3. **평균 등락률과 순위는 `industry-flow`가 계산한 것을 그대로 쓴다.** 다시 계산하지 않는다 —
   계산을 두 곳에 두면 시장 요약과 산업별 동향의 순위가 어긋난다.
4. **조회는 외부 시세를 호출하지 않는다.** `industry-flow`·`industry-news`가 지키는 규칙이고
   테스트로 고정돼 있다. 산업별 동향도 같다.
5. 이 저장소는 **JPA 연관관계 애너테이션을 한 곳도 쓰지 않는다.** 모든 FK가 평범한 `Long`
   필드다(`Stocks.marketId`, `StockIndustries.stockId`). 새 자식 테이블도 그 관례를 따른다.
6. 화면 전환·타이머·로컬 저장은 프론트엔드 몫이다. 서버는 데이터만 준다.

## 목표

산업별 동향 탭이 **국내·해외 각각 9개 산업 카드**를 그리는 데 필요한 데이터를 한 번에 준다.
카드 하나는 산업명·평균 등락률·순위와 **시가총액 상위 4개 종목**(종목명·종목코드·현재가·등락률)을
담는다.

### 요구사항 ↔ 담당

| RQ-ID | 기능 | 담당 | 비고 |
| --- | --- | --- | --- |
| RQ-0601 | 산업 카드 목록 | **`industry-trend`** | 이번 구현의 전부다 |
| RQ-0602 | 산업 필터 (전체·상승·하락) | **`industry-trend`** | `filter` 파라미터로 서버가 거른다 |
| RQ-0603 | 산업 정렬 | **`industry-trend`** | 동률 처리까지 서버가 정렬해 준다 |
| RQ-0604 | 관심 산업 고정 (최대 3개) | **프론트엔드** | 고정 산업을 상단으로 당기는 것은 받은 목록을 재배열하는 일이다 |
| RQ-0605 | 산업 종목 전체 보기 | **프론트엔드** + 종목 검색 화면 | 이 모듈은 넘길 식별자(`code`)만 준다 |
| RQ-0606 | 관심 산업 계정 저장 | **`industry-pin`** | **이번 범위 밖** — 인증이 선행 조건이다 |

### RQ-0606은 인증이 선행 조건이다

계정 단위 저장은 "요청이 누구인지"를 서버가 알아야 가능하다. 지금은 인증이 없으므로 만들 수 없고,
기능 맵이 이미 `industry-pin`을 별도 모듈로 두고 "로그인 도입 후"로 적어 두었다.

**이 모듈은 고정과 무관하다.** 응답에 고정 관련 필드를 넣지 않는다 — 산업 목록은 모든 사용자에게
같다.

## 규칙

### 종목 4개를 어떻게 고르는가

**시가총액 상위 4개.** 동률이면 `ticker` 오름차순으로 정해 갱신마다 순서가 흔들리지 않게 한다.

기존 대표 종목(`majorStocks`)과 **같은 규칙이고 개수만 2 → 4다.** 그래서 한 목록을 저장해
세 화면이 필요한 만큼 가져간다.

```
시가총액 상위 4개 저장
  ├─ 시장 요약 "오늘의 산업 흐름"  → 앞 2개 (현재가 미사용)
  ├─ 오늘의 핵심 뉴스              → 앞 2개 (뉴스 탐색용 stockId)
  └─ 산업별 동향                   → 4개 전부 + 현재가
```

등락률 순을 쓰지 않는 이유는 **상한가 친 소형주가 늘 1위로 올라와 산업을 대표하지 못하기**
때문이다. 기존 `IndustryFlowCalculator.majorStocks`의 주석이 같은 판단을 적어 두었다.
거래대금 순은 장중에 순서가 계속 흔들려 "오늘의 대표 종목"이 요청마다 바뀐다.

**목업으로는 이 규칙을 확인할 수 없다.** 산업마다 종목이 4개뿐이라 어떤 기준으로 정렬해도 같은
4개가 나오고, 실제 표시 순서는 시드 입력 순서(`stock_id`)와 일치한다. 규칙은 기획 확인으로 정했다.

### 현재가는 갱신 시점에 저장한다

`IndustryFlowRefresher`가 종목마다 이미 `Quote`를 읽고 있고 그 `Quote`가 `price`를 들고 있다.
지금은 `changeRate()`만 꺼내 쓰고 버린다. **저장해도 KIS 호출이 0건 늘어난다.**

조회 시점에 캐시(`QuoteReader`)에서 읽는 방안을 검토했으나 채택하지 않았다.

| | 갱신 시 저장 | 조회 시 캐시 |
| --- | --- | --- |
| 한 요청의 KIS 호출 | **0건** | 운영 규모에서 약 32건 |
| 현재가와 등락률의 시점 | **같다** | 다르다 |
| "조회는 외부를 부르지 않는다" | 지킨다 | 깨진다 |

캐시 TTL이 30초인데 갱신 한 바퀴는 종목 900개·초당 3건이면 300초다. **캐시가 비어 있는 시간이
10분의 9**라서 조회 36종목 중 대부분이 캐시 미스가 되고, 미스는 그 자리에서 KIS를 호출한다
(`StockQuoteService.quoteOf` → `fetchAndStore`).

더 결정적인 것은 **같은 줄의 두 숫자가 어긋난다**는 점이다. 등락률은
`(현재가 − 전일종가) ÷ 전일종가`인데, 현재가만 캐시에서 오고 등락률이 저장된 값이면 사용자가
화면의 두 숫자로 계산했을 때 맞지 않는다. 평균 등락률과 순위도 저장된 값이라 함께 어긋난다.

### 저장 구조 — 자식 테이블로 통합한다

지금은 대표 종목 2개가 `first_*`·`second_*` **평면 컬럼 8개**로 저장돼 있다. 4개 × 5필드면
**20개**가 된다. 자식 테이블을 만들고 평면 컬럼을 **없앤다.**

| | 자식 테이블 | 컬럼 4세트 |
| --- | --- | --- |
| 개수 변경(4→5) | 데이터 문제 | `ALTER TABLE` + 코드 전부 |
| 상한 상수 | 필요 없음 | 여러 곳에 흩어진다 |
| 조회 | 쿼리 1번 추가 | 조인 없음 |

**상한 상수가 사라지는 것이 핵심이다.** 지금 `MAJOR_STOCK_LIMIT = 2`가 `IndustryFlows`와
`IndustryFlowCalculator`에 따로 선언돼 있고, 두 숫자가 뜻이 다른데(저장 자리 수 / 표시 개수)
값만 같다. 계산기만 올리면 엔티티가 런타임에 터지고, 그 자리가 1초마다 도는 스케줄러라
**오류가 조용히 쌓인다.** 자식 테이블이면 "저장 자리 수"라는 개념 자체가 없어진다.

기능 맵이 관심 산업 핀에서 같은 상황에 **별도 테이블**을 고른 것과 같은 판단이다.

**연관관계 애너테이션은 쓰지 않는다.** 이 저장소에 `@OneToMany`가 한 곳도 없다. 별도 엔티티와
별도 리포지토리를 두고 서비스가 조립한다. 조회는 산업별로 돌지 않고
`findByIndustryFlowIdIn(ids)` **한 번**으로 9개분을 모두 읽어 N+1을 만들지 않는다.

### 필터와 정렬 — 서버가 정해서 준다

`filter` 파라미터로 탭을 받아 **거르고 정렬해서** 돌려준다.

| `filter` | 대상 | 정렬 |
| --- | --- | --- |
| `ALL` (기본) | 9개 전부 | **산업명 가나다순** |
| `RISING` | `avgChangeRate > 0` | **평균 등락률 내림차순** |
| `FALLING` | `avgChangeRate < 0` | **평균 등락률 오름차순** |

**정렬을 클라이언트에 맡기지 않는 이유**는 동률 처리에 있다. 응답의 `avgChangeRate`는 소수
둘째 자리로 반올림한 값이라 **클라이언트는 동률을 풀 방법이 없다.** 반올림 전 값과 거래대금을
가진 쪽이 정렬해야 한다.

`rank`를 뒤집어 하락 탭을 만드는 방법도 검토했으나 **틀린다.** 순서를 통째로 뒤집으면 등락률만
뒤집히는 것이 아니라 동률 처리의 방향까지 함께 뒤집혀, 거래대금이 적은 산업이 위로 오고 산업명이
역순이 된다.

### 동률 처리

RQ-0603: *동률일 경우 반올림 전 평균 등락률, 거래대금 변화율, 산업명 순*

```
RISING     1) 반올림 전 평균 등락률  내림차순
           2) 거래대금 비율          내림차순      ← 측정 못 한 산업은 뒤로
           3) 산업명                 가나다순

FALLING    1) 반올림 전 평균 등락률  오름차순      ← 이것만 방향이 뒤집힌다
           2) 거래대금 비율          내림차순
           3) 산업명                 가나다순
```

**2·3번은 방향이 같다.** 하락 탭에서도 "거래가 활발한 쪽이 위", "이름은 가나다순"이 자연스럽다.
뒤집히는 것은 "더 많이 내린 것이 위"를 만드는 1번뿐이다.

**반올림 전 값이 지금은 남아 있지 않다.** `IndustryFlowCalculator`가 `setScale(2)`로 자르고
컬럼도 `decimal(10,2)`다. 종목 등락률은 `Quote`가 이미 두 자리로 주므로 **추가 정밀도는 종목
수로 나눌 때 생기는데**, 그것을 바로 잘라 버린다 — `4.00 ÷ 3 = 1.333333`과 `1.330000`이
둘 다 `1.33`이 되어 구분할 수 없다.

**평균 등락률을 반올림하지 않고 저장하고, 응답을 만들 때만 두 자리로 반올림한다.**
PLO-41에서 거래대금을 "원재료를 저장하고 가공을 미룬다"로 바꾼 것과 같은 방식이다.

```
저장   avg_change_rate  decimal(12,6)   ← 반올림하지 않은 값
응답   avgChangeRate    1.61            ← setScale(2) 는 DTO 에서
```

거래대금 비율(`오늘 ÷ 20거래일 평균`)은 **PLO-41에서 금액 2개를 저장하도록 바꾼 덕에 가능해졌다.**
비율만 저장했다면 조회 시점에 만들 수 없었다. 시장 전체 대비 상대비율을 쓰지 않는 이유는 모든
산업을 같은 값으로 나누는 것이라 **순서가 바뀌지 않기** 때문이다 — 더 단순한 쪽을 쓴다.

### `rank`는 필터와 무관하다

`rank`는 **9개 전체를 놓고 매긴 평균 등락률 순위**이며 `filter`에 영향받지 않는다.
`FALLING`로 3건만 받아도 각 항목은 `7`·`8`·`9` 같은 원래 순위를 들고 나온다.

목업의 "평균 등락률 6위"가 이 값이다. **배열 인덱스로 세면 안 된다.**

### 관심 산업 고정 (RQ-0604)

**이 모듈은 관여하지 않는다.** 고정 산업을 상단으로 당기는 것은 서버가 정렬해 준 목록을
재배열하는 일이고, 최대 3개 제한과 `0/3` 표시도 화면 안에서 끝난다.

**고정해도 `rank`는 바뀌지 않는다.** 통신을 고정해도 "평균 등락률 6위"로 표시된다(목업 확인).

### 보합 산업

`avgChangeRate`가 정확히 `0.000000`인 산업은 **상승·하락 어느 탭에도 들어가지 않는다.**
오른 것도 내린 것도 아니다. 전체 탭에는 나온다.

`industry-news`가 상승군·하락군을 나눌 때 쓰는 규칙과 같다. **기획 확인을 받은 확정 사항이다.**

### 종목이 4개 미만인 산업

**있는 만큼만 준다.** 배열 길이가 0~4로 가변이다. 시세를 하나도 구하지 못한 산업은 빈 배열이고,
그때 `stockCount`도 0이라 화면이 "소속 종목 0개"로 표시할 수 있다.

`industry_flows`는 이미 "매핑된 종목이 있는데 시세를 하나도 구하지 못하면 저장하지 않는다"는
규칙을 갖고 있다(직전 값을 남긴다). 그 규칙을 그대로 잇는다.

### 해외 시장

`country=US`가 이미 `industry-flow`에 있다. 그대로 쓴다. **통화를 응답에 싣는다** —
국내는 `KRW`, 해외는 `USD`로 표시 단위가 다르다. `Markets.currency`에 있는 값이다.

### 갱신 한 바퀴의 흐름

```
산업 1개 선택 (cursor 순회)
  └─ 종목마다:
       ├─ 시세  quoteReader.readWithoutTracking()     캐시/KIS   ← 기존
       │    ├─ changeRate   → 평균 등락률, 상승·하락 종목 수
       │    ├─ tradingValue → 거래대금                  (PLO-41)
       │    ├─ marketCap    → 상위 4개 선정             ← 기존(2개에서 4개로)
       │    └─ price        → 현재가            ★ 새로 쓰는 값 (호출 0)
       └─ 일봉 20행  readStoredLatest()                 DB        (PLO-41)

  저장: industry_flows 1행 + industry_flow_stocks 0~4행
```

**KIS 호출이 늘지 않는다.** 현재가는 이미 받아 둔 `Quote`에서 꺼낸다.

## 선행 조건 — `industry-flow`에 함께 넣는다

이 화면이 요구하는 것 중 **평균 등락률·순위·대표 종목은 `industry-flow`가 소유한 개념**이다.
`industry-trend`가 따로 계산하면 두 화면의 순위가 어긋나므로, 그 모듈을 고쳐서 쓴다.
규칙의 정본은 `SPEC-industry-flow.md`에 있고 여기에는 **무엇을 왜 바꿨는지**만 적는다.

| | 변경 | 왜 |
| --- | --- | --- |
| ① | 평균 등락률을 **반올림하지 않고** 저장(`decimal(12,6)`), 응답에서만 두 자리로 자른다 | RQ-0603의 1차 동률 기준. `1.333333`과 `1.330000`이 둘 다 `1.33`이 되면 구분할 수 없다 |
| ② | 순위 동률 규칙을 **세 단계**로 확장 (반올림 전 등락률 → 거래대금 비율 → 산업명) | RQ-0603 |
| ③ | 대표 종목을 **시가총액 상위 4개**로 늘리고 **현재가**를 담는다 | RQ-0601 |
| ④ | 대표 종목을 **자식 테이블 `industry_flow_stocks`**에 저장하고 평면 컬럼 8개를 없앤다 | ③을 평면 컬럼으로 하면 20개가 된다 |

**이 변경들은 `industry-flow` 자체도 개선한다.** 지금 동률 처리가 "산업명 가나다순" 하나뿐이라
같은 `1.61%`인 두 산업의 순위가 **등락률과 무관하게 이름으로** 정해진다. 요구사항이 와서
드러났을 뿐 원래 약했던 부분이다.

④는 `SPEC-industry-flow.md`가 미리 적어 둔 결정이다 — *"그 화면은 산업당 종목을 4개 이상
보여주므로, 그때 대표 종목을 별도 테이블로 분리할지 결정한다."* 지금이 그때다.

**`/flows`와 `/news`의 응답은 바뀌지 않는다.** 저장이 4개로 늘어도 두 API는 앞 2개만 쓴다.
구현 중에 그 응답 테스트를 고쳐야 한다면 이전이 잘못된 것이므로 멈춘다.

## 스키마 변경

### 새 테이블

```sql
create table industry_flow_stocks (
    industry_flow_stock_id bigint       not null auto_increment primary key,
    industry_flow_id       bigint       not null,
    stock_id               bigint       not null,
    ticker                 varchar(20)  not null,
    name                   varchar(100) not null,
    price                  decimal(20,4) not null,
    change_rate            decimal(10,2) not null,
    display_order          int          not null,
    unique key uk_industry_flow_stocks_order (industry_flow_id, display_order),
    key idx_industry_flow_stocks_flow (industry_flow_id)
);
```

- `display_order`는 시가총액 순위(0부터)다. 유니크 제약이 같은 자리에 두 종목이 들어가는 것을
  DB 차원에서 막는다.
- `price`가 `decimal(20,4)`인 이유는 해외 종목의 센트 단위 때문이다. 국내는 정수다.
- `ticker`·`name`을 함께 저장하는 것은 기존 `first_ticker`와 같은 이유다 — 조회 시점에 다시
  구하면 KIS나 종목 테이블을 또 읽어야 한다. **계산 시점의 스냅샷**이다.

### `industry_flows` 변경

```sql
-- 평면 컬럼 8개 제거 (자식 테이블이 대체한다)
alter table industry_flows
    drop column first_stock_id,  drop column first_ticker,
    drop column first_name,      drop column first_change_rate,
    drop column second_stock_id, drop column second_ticker,
    drop column second_name,     drop column second_change_rate;

-- 평균 등락률을 반올림 전 값으로 저장 (RQ-0603 동률 처리)
alter table industry_flows
    modify column avg_change_rate decimal(12,6) not null;
```

**배포 순서를 지켜야 한다.** `drop`은 이 변경이 배포된 뒤에만 한다 — 배포 전 `main`은 아직
평면 컬럼을 쓰므로 1초마다 도는 갱신이 `Unknown column`으로 실패한다. API는 저장된 옛 값을
계속 읽어 주므로 화면은 멀쩡해 보이고 `calculatedAt`만 낡는다.

`modify`는 **넓히는 방향**(2 → 6자리)이라 옛 코드도 읽고 쓸 수 있어 먼저 해도 안전하다.

**컬럼명 주의**: Spring Boot 기본 네이밍 전략은 대문자 앞에만 밑줄을 넣는다. 손으로 쓰지 말고
Hibernate가 생성한 DDL에서 옮긴다(PLO-41에서 `avg_trading_value_20d`로 잘못 써서 값이 조용히
`null`로 남은 적이 있다).

## 설계

- `IndustryFlowStock`(신규 VO, `industry/flow` 루트): `stockId`·`ticker`·`name`·`price`·
  `changeRate`. 기존 `MajorStock`을 **대체한다** — 같은 데이터에 `price`가 붙은 것이다.
- `IndustryFlowStocks`(신규 엔티티, `industry/flow`): 자식 테이블 매핑. FK는 평범한 `Long`이다.
- `IndustryFlowStockRepository`(신규): `findByIndustryFlowIdIn`,
  `deleteByIndustryFlowId`.
- `IndustryFlows`(기존): 평면 컬럼 8개와 `majorStocks()`를 **제거**한다. `MAJOR_STOCK_LIMIT`도
  함께 사라진다.
- `IndustryFlowSnapshot`(기존): `majorStocks` → `stocks`(0~4개)로 바꾸고 `avgChangeRate`를
  반올림하지 않은 값으로 받는다.
- `IndustryFlowCalculator`(기존): 상위 **4개**를 고르고 `price`를 함께 담는다.
  평균 등락률의 `setScale(2)`를 **없앤다**.
- `IndustryFlowRefresher`(기존): `industry_flows` 저장 뒤 자식 행을 **지우고 다시 넣는다**.
  한 트랜잭션 안에서 처리한다.
- `RankedIndustryFlow`(기존): `majorStocks` → `stocks`.
- `IndustryFlowService`(기존): 자식 행을 한 번에 읽어 조립하고, 동률 처리를 반영해 `rank`를
  매긴다.
- `IndustryTrendFilter`(신규 enum, `industry/flow`): `ALL`·`RISING`·`FALLING`. **거르는 조건과
  비교자를 자기가 안다** — 서비스가 `switch`로 분기하지 않게 한다.
- `IndustryTrendService`(신규, `industry/flow/service`): `IndustryFlowService.read(country)`를
  받아 `filter`로 거르고 정렬한 뒤 통화를 붙인다.
- `IndustryTrendController`(신규): `GET /api/v1/industries/trends`.

**새 스케줄러도, 새 외부 호출도 없다.**

## API 계약

### `GET /api/v1/industries/trends?country=KR`

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `country` | `KR` \| `US` | X | `KR` | 화면의 시장 토글 |
| `filter` | `ALL` \| `RISING` \| `FALLING` | X | `ALL` | 화면의 탭. 거르고 정렬까지 서버가 한다 |

**응답** — `ApiResult<List<IndustryTrendResponse>>`. 정렬까지 끝난 목록이다.

| `filter` | 길이 |
| --- | --- |
| `ALL` | **항상 9** |
| `RISING` | 0~9 (오른 산업만) |
| `FALLING` | 0~9 (내린 산업만) |

전 산업이 오른 날 `FALLING`은 **빈 배열**이다. 보합(등락률 정확히 `0`)인 산업은 `RISING`·
`FALLING` 어디에도 없고 `ALL`에만 나오므로, 두 탭의 길이 합이 9보다 작을 수 있다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `code` | string | X | 산업 코드. 식별자로 이 값을 쓴다 (`"AUTOMOBILE"`) |
| `displayName` | string | X | 한글 산업명 (`"자동차"`) |
| `rank` | number | X | 9개 전체 기준 평균 등락률 순위 1~9. `filter`와 무관하다 — 배열 인덱스로 세면 안 된다 |
| `avgChangeRate` | number | X | 소속 종목 등락률의 단순평균 %, 소수 둘째 자리 |
| `stockCount` | number | X | 평균에 실제로 반영된 종목 수 |
| `currency` | string | X | `KRW` \| `USD`. 현재가의 표시 단위 |
| `stocks` | array | X | 시가총액 상위 **0~4개**. 빈 배열일 수 있다 |
| `stocks[].ticker` | string | X | 종목 코드 (`"005380"`) |
| `stocks[].name` | string | X | 종목명. 계산 시점의 값 |
| `stocks[].price` | number | X | 현재가 |
| `stocks[].changeRate` | number | X | 그 종목의 등락률 %, 소수 둘째 자리 |
| `calculatedAt` | string | **O** | 계산 시각(시장 현지, 오프셋 포함). 계산된 적 없으면 `null` |

```json
{
  "data": [
    {
      "code": "AUTOMOBILE",
      "displayName": "자동차",
      "rank": 1,
      "avgChangeRate": 1.61,
      "stockCount": 4,
      "currency": "KRW",
      "stocks": [
        { "ticker": "005380", "name": "현대차",   "price": 248000, "changeRate": 3.24 },
        { "ticker": "012330", "name": "현대모비스", "price": 254500, "changeRate": -0.78 },
        { "ticker": "000270", "name": "기아",     "price": 102000, "changeRate": 1.85 },
        { "ticker": "018880", "name": "한온시스템", "price": 4320,   "changeRate": 2.13 }
      ],
      "calculatedAt": "2026-09-04T15:30:00+09:00"
    }
  ]
}
```

**`stockId`는 응답에 넣지 않는다.** 환경마다 auto_increment 값이 달라질 수 있어 외부 식별자로
쓸 수 없다. 종목 상세로 이동할 때는 `ticker`를 쓴다 — 기존 `/flows`의 결정과 같다.

**프론트가 하는 일**

- 탭을 누르면 `filter`를 바꿔 다시 요청한다. **정렬은 하지 않는다**
- 고정한 산업을 앞으로 당긴다. **`rank`는 건드리지 않는다**
- 통화 기호·부호·`%` 조립

### 오류

| 상황 | 상태 | 코드 |
| --- | --- | --- |
| `country`가 `KR`·`US`가 아님 | 400 | `P001` |
| `filter`가 `ALL`·`RISING`·`FALLING`이 아님 | 400 | `P001` |

폴링 주기는 **10초**를 권장한다(`/flows`와 같다).

## 명령어

```bash
docker compose up -d mysql redis      # 로컬 DB·캐시
./gradlew build                        # 빌드
./gradlew test                         # 전체 테스트
./gradlew test --tests '*IndustryTrend*'
./gradlew bootRun                      # 로컬 실행
curl -s "http://localhost:8080/api/v1/industries/trends?country=KR"
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/industry/flow/
  IndustryFlowStock.java                 # 신규 — 종목 1개 VO (MajorStock 대체)
  IndustryFlowStocks.java                # 신규 — 자식 테이블 엔티티
  IndustryFlows.java                     # 수정 — 평면 컬럼 8개 제거
  IndustryFlowSnapshot.java              # 수정 — stocks 로 교체
  RankedIndustryFlow.java                # 수정 — stocks 로 교체
  repository/
    IndustryFlowStockRepository.java     # 신규
  service/
    IndustryFlowCalculator.java          # 수정 — 상위 4개 + price
    IndustryFlowRefresher.java           # 수정 — 자식 행 저장
    IndustryFlowService.java             # 수정 — 조립 + 동률 처리
    IndustryTrendService.java            # 신규
  controller/
    IndustryTrendController.java         # 신규
    IndustryTrendResponse.java           # 신규
```

## 코드 스타일

CLAUDE.md를 따른다. 이 모듈에서 특히 걸리는 것:

```java
// 접근자를 꺼내 바깥에서 판단하지 않는다
public record IndustryFlowStock(Long stockId, String ticker, String name,
                                BigDecimal price, BigDecimal changeRate) {

    public boolean rose() {
        return changeRate.signum() > 0;
    }
}
```

- `else` 금지. early return으로 중첩을 없앤다.
- setter 금지. 엔티티 상태 변경은 의도가 드러나는 도메인 메서드로 한다.
- `@Column`의 `name`은 쓰지 않는다. 필드명이 컬럼명을 정한다.
- 엔티티명은 복수형(`IndustryFlowStocks`), 필드명은 단수형(`industryFlowStockId`).
- 파일 끝에 개행.

## 테스트 전략

JUnit 6, BDD(`// given` `// when` `// then`), 메서드명은 한글 `조건_결과`.

**단위 (60%)**

- `IndustryFlowCalculator` — 시가총액 상위 4개를 고른다 / 동률이면 ticker 순 / 4개 미만이면
  있는 만큼 / `price`가 담긴다 / 평균 등락률을 반올림하지 않는다
- `IndustryFlowService` — `rank`가 반올림 전 등락률로 갈린다 / 거래대금 비율이 2차 기준이다 /
  거래대금을 못 구한 산업이 뒤로 간다 / 둘 다 같으면 산업명 순
- `IndustryTrendFilter` — `RISING`이 양수만, `FALLING`이 음수만, 보합은 `ALL`에만
- `IndustryTrendService` — `ALL`은 산업명 가나다순 / `RISING`은 등락률 내림차순 /
  `FALLING`은 등락률 오름차순이면서 **거래대금과 산업명은 방향이 그대로다** /
  `rank`가 필터와 무관하게 전체 기준이다 / 국내는 `KRW`, 해외는 `USD`

**통합 (30%)**

- `IndustryFlowStockRepository` (`@DataJpaTest`) — 같은 산업·같은 자리에 두 행을 넣을 수 없다 /
  `findByIndustryFlowIdIn`이 9개분을 한 번에 읽는다
- `IndustryFlowRefresher` — 갱신 때 자식 행이 지워지고 다시 들어간다 / **KIS 호출 횟수가
  변경 전과 같다**

**E2E (10%)**

- `IndustryTrendController` (`@WebMvcTest`) — `ALL`이 9건 / `country=JP`·`filter=UP`이
  400 `P001` / **`QuoteReader` mock이 한 번도 호출되지 않는다** / `stockId`가 응답에 없다 /
  고정 관련 필드가 없다

## 경계

**항상 한다**

- 커밋 전 `./gradlew test` 전부 통과
- 조회 경로에서 외부 시세를 호출하지 않는다
- 스키마를 바꾸면 `db/`에 DDL을 남기고 배포 순서를 적는다

**먼저 묻는다**

- `industry_flows`의 컬럼 제거 (배포 순서가 걸린다)
- 다른 사람 모듈의 파일 수정
- 응답 필드 추가·제거

**절대 안 한다**

- 인증 없이 `userId`를 파라미터로 받는 기능 추가
- 조회 API에서 KIS 호출
- 실패를 0이나 빈 값으로 위장 — 모르면 `null`이나 빈 배열로 둔다

## 성공 기준

1. `GET /api/v1/industries/trends?country=KR`이 **정확히 9건**을 돌려주고 산업명 가나다순이다.
2. 각 카드의 `stocks`가 **시가총액 내림차순 0~4개**이고, 동률이면 `ticker` 오름차순이다.
3. `stocks[].price`가 `null`이 아니고, 같은 줄의 `changeRate`와 **같은 시점의 값**이다.
4. 같은 시점에 `/industries/flows`를 호출했을 때 **같은 산업의 `avgChangeRate`와 `rank`가
   일치한다.**
5. 평균 등락률이 소수 둘째 자리에서 같은 두 산업이, 반올림 전 값이 큰 쪽이 앞선 `rank`를 받는다.
6. 반올림 전 값까지 같으면 거래대금 비율이 높은 쪽이 앞선다. 측정하지 못한 산업은 뒤로 간다.
7. 둘 다 같으면 산업명 가나다순으로 갈린다.
7-1. `filter=RISING`이 양수인 산업만, `FALLING`이 음수인 산업만 돌려준다. 보합은 `ALL`에만 나온다.
7-2. `FALLING`에서 등락률이 같은 두 산업이, **거래대금이 많은 쪽이 위**이고 **산업명은 가나다순**이다
   (등락률 방향만 뒤집히고 2·3차 기준은 그대로다).
7-3. 같은 산업의 `rank`가 `ALL`·`RISING`·`FALLING` 어느 응답에서도 같다.
8. `country=US`의 `currency`가 `USD`다.
9. 시세를 하나도 구하지 못한 산업의 `stocks`가 `[]`이고 200으로 응답한다.
10. `country=JP`가 400 `P001`이다.
11. 이 API 호출 중 KIS 호출이 **0회**다.
12. 산업 갱신 중 KIS 호출 횟수가 **변경 전과 같다**.
13. `industry_flow_stocks`에 같은 `(industry_flow_id, display_order)` 조합이 두 번 들어가지 않는다.
14. 갱신을 두 번 돌려도 자식 행이 산업당 4개를 넘지 않는다(지우고 다시 넣는다).
15. `./gradlew test`가 전부 통과한다.

## 미해결 질문

1. **`stock_industries` 매핑 데이터.** `industry-flow`에서 이어지는 문제이며, 현재 서버에는
   임시 테스트 종목(9001~9072)만 들어 있다. 실제 데이터가 들어오면 산업당 수십 종목이 되고
   그때 비로소 "상위 4개" 규칙이 의미를 갖는다.
2. **일봉 공백.** 거래대금을 동률 처리에 쓰는데, 일봉이 없는 산업은 측정되지 않아 뒤로 밀린다.
   `SPEC-industry-news.md`의 미해결 질문 4와 같은 문제다.

**닫힌 질문**

- ~~종목 4개를 무슨 기준으로 고르는가~~ → **시가총액 상위.** 등락률 순은 상한가 친 소형주가
  산업을 대표하게 되고, 거래대금 순은 장중에 순서가 흔들린다.
- ~~현재가를 저장할 것인가 조회 시 읽을 것인가~~ → **저장.** 이미 받아 둔 `Quote`에서 꺼내므로
  KIS 호출이 0건 늘고, 현재가와 등락률의 시점이 어긋나지 않는다.
- ~~저장 구조~~ → **자식 테이블로 통합.** 평면 컬럼 8개를 없앤다. `MAJOR_STOCK_LIMIT` 중복이
  원인째 사라진다.
- ~~엔드포인트~~ → **새로 만든다.** `/flows`는 종목 2개에 현재가가 없는 시장 요약용으로 둔다.
- ~~필터·정렬을 누가 하는가~~ → **서버.** 응답의 `avgChangeRate`가 반올림된 값이라 클라이언트는
  동률을 풀 수 없다. `filter` 파라미터 하나로 받고 엔드포인트를 늘리지 않는다.
- ~~보합 산업이 어느 탭에 속하는가~~ → **전체 탭에만.** 오른 것도 내린 것도 아니다.

## 팀 전달

**프론트엔드**

- **정렬하지 않는다.** 탭을 누르면 `filter`를 바꿔 다시 요청하면 되고, 응답은 이미 정렬돼 있다.
  응답의 `avgChangeRate`는 반올림된 값이라 그것으로 정렬하면 동률이 어긋난다.
- `rank`는 **9개 전체 기준**이라 `filter`와 무관하다. `FALLING`로 3건을 받아도 `7`·`8`·`9`가 온다.
  배열 인덱스로 순위를 세면 안 된다.
- 전 산업이 한쪽으로 쏠린 날 반대쪽 탭은 **빈 배열**이다. "해당 산업이 없습니다"를 표시해야 한다.
- 고정해도 `rank`는 바뀌지 않는다. 목업대로 "평균 등락률 6위"가 유지된다.
- `stocks`는 **0~4개 가변**이다. 4개를 가정하면 안 된다.
- `currency`로 통화 기호를 고른다. 국내 `KRW`, 해외 `USD`.
- 종목 상세로 이동할 때는 `ticker`를 쓴다. `stockId`는 주지 않는다.

**`industry-flow`·`industry-news`를 함께 보는 사람**

- `MajorStock`이 `IndustryFlowStock`으로 바뀌고 `price`가 붙는다. `IndustryFlows`의 평면 컬럼
  8개가 사라진다. `/flows`와 `/news`의 **응답 모양은 그대로**다 — 앞 2개만 쓴다.
- `MAJOR_STOCK_LIMIT`이 사라진다. PR #39가 지적한 중복이 원인째 해소된다.

**기획**

- RQ-0606(계정 저장)은 **로그인 도입 후**다. `industry-pin`으로 따로 만든다.
- 전 산업이 한쪽으로 쏠린 날 반대쪽 탭이 빈 화면이 된다. 그때의 문구가 필요하다.

## 실측 (2026-10-03 로컬)

DDL을 적용하고 갱신 한 바퀴를 돌린 뒤 관측한 값이다.

```
GET /api/v1/industries/trends?country=KR&filter=ALL      9건
  건설 → 에너지 → 운송 → 유통 → 음식료 → 자동차 → 철강 → 통신 → 화학   (산업명 가나다순)
  rank 는 8·1·4·7·6·5·9·3·2 로 뒤섞여 있다 — 배열 순서와 무관하다

filter=RISING   7건,  rank 1 → 7   (많이 오른 순)
filter=FALLING  2건,  rank 9 → 8   (많이 내린 순)
                7 + 2 = 9,  보합 없음

ENERGY 에너지  rank 1  5.94%  KRW
  096770  SK이노베이션   151,300.0000   +8.38%
  010950  S-Oil          164,900.0000  +10.82%
  078930  GS             105,100.0000   +2.84%
  036460  한국가스공사    35,850.0000   +1.70%
```

**종목 순서가 등락률 순이 아니다** — 시가총액 순이라 `+8.38 → +10.82 → +2.84 → +1.70`이다.
설계대로이고, 상한가 친 소형주가 산업을 대표하지 않게 하는 것이 목적이다.

**저장은 `5.935000`, 응답은 `5.94`**다. 반올림을 응답으로 미룬 것이 실데이터에서 확인됐다.

**해외는 `currency: USD`이고 가격이 `135.2050`처럼 소수 넷째 자리까지 나온다** —
`decimal(20,4)`로 잡은 이유다.

**`/flows`는 여전히 `majorStocks` 2개**이고 `rank`·`avgChangeRate`가 `/trends`와 정확히 일치한다.
두 화면이 같은 계산을 쓴다는 증거다.

전 산업이 산업당 정확히 4행이었다 — 갱신이 1초마다 돌며 지우고 다시 넣는데도 중복이 생기지 않았다.

## 알려진 한계

**현재가의 신선도는 갱신 한 바퀴다.** 종목 900개·초당 3건이면 5분이다. 화면의 현재가가 최대
5분 전 값일 수 있다. 평균 등락률·순위도 같은 시점이라 **카드 안에서는 일관되다.**
더 빠르게 하려면 호출 속도를 올려야 하고 그만큼 `QuoteRefresher`의 예산을 가져간다.

**시가총액이 없는 종목은 뒤로 밀린다.** `Quote.marketCap`이 없으면 상위 4개에 들지 못한다.
KIS 응답에 포함되는 값이라 정상 경로에서는 문제되지 않는다.

**자식 행을 지우고 다시 넣는다.** 갱신마다 산업당 최대 4행을 삭제·삽입한다. 9개 산업 × 2개국
= 72행이 한 바퀴에 바뀐다. 변경분만 갱신하는 방식도 있으나, 종목 구성이 바뀌는 경우를 다루려면
비교 로직이 필요하고 행 수가 작아 이득이 없다.
