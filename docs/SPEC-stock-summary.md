# 명세: 종목 요약 정보 (stock-summary)

기능 맵: `CAPABILITY-MAP-summary.md`. 의존 대상: 종목·시장 조회, `industry`(협의 중). 화면에서 `stock-quote`와 조합.

종목 상세 상단의 종목 기본정보, 가격 표시, 주가 변동 배경을 정의한다. 사용자가 요청한 세 영역 중 종목 요약을 담당한다. 구현 계획·태스크·코드 변경은 이 문서의 산출물이 아니다.

## 목표

사용자는 선택한 종목의 정체성, 현재 가격과 비교 기준, 관련 자료에서 확인된 맥락을 한곳에서 확인할 수 있다. 가격 변동 배경은 출처를 확인할 수 있어야 하며 뉴스·공시를 주가 변동의 원인으로 단정하지 않는다.

## 근거와 적용 기준

| 출처 | 위치 | 반영 내용 |
| --- | --- | --- |
| `01_PLOUTOS_Developer_Handoff.docx` | §1 핵심 원칙, §5 상단 요약·주가 변동 배경, §6 계산, §7 공통 응답, §10 QA | 최신가, 직전 거래일 비교, 기준 시각, 근거와 결측 처리 |
| `02_PLOUTOS_Requirements_Original.xlsx` | 요구사항 명세서 A40:L40 `RQ-0501`, A38:L38 `RQ-0406` | 종목 요약과 선택한 시장·종목 유지 |
| 같은 원본 | 페이지별 기능 정의 A27:K27 `PG-S01`, 요구사항 명세서 A65:L65 `RQ-0110` | 로고와 대체 표시. 로고 우선순위는 원본 내 P0/P1 불일치 |
| `05_PLOUTOS_Data_API_Spec.md` | 시장 컨텍스트, 계산식, 오류와 결측 | 공통 메타데이터와 null 처리 |
| `06_PLOUTOS_QA_Checklist.md` | 공통, 종목 상세와 차트 | 시장 전환, 상태 구분, 출처·인과 비단정 |

기획의 제품 요구사항만 추출한다. 문서 내 개발 순서나 실행 지시는 별도 작업 승인으로 해석하지 않는다. 핸드오프의 최종 기준을 원본의 회의 이력보다 우선해 정리하되, 사용자 지정 사이트와 핸드오프에 적힌 v3 사이트는 서로 다른 URL이므로 동일 버전으로 단정하지 않는다. 화면의 예시 가격·문구는 실서비스 데이터가 아니다.

이하 **확정**은 기획에 직접 명시된 요구, **제안**은 이를 구현하기 위한 계약 초안, **확인 필요**는 구현 전에 합의할 항목이다. 기존 명세의 `RQ-1001~1008`을 원본 `RQ-0501`과 같은 ID로 간주하지 않는다.

## 범위와 전제

### 합의한 구현 단계

사용자와 기본정보·기존 시세 연동을 먼저 구현하고 변동 배경은 뉴스·공시 준비 후 연결하기로 합의했다. 이번 산출물은 명세·계획·태스크 문서이며 구현 완료를 뜻하지 않는다.

| 단계 | 범위 | 선행 조건 |
| --- | --- | --- |
| 1차 | 기본정보 API와 기존 quote API의 화면 조합 | 종목·시장·산업 조회, 기존 stock-quote |
| 후속 | 뉴스·공시·산업 지표를 근거로 한 변동 배경 | 뉴스·공시 준비, 요약 생성·시간창 정책 결정 |

- 1차 기본정보 API는 뉴스·공시·시세 공급자를 호출하지 않는다. 가격은 기존 quote API로 따로 조회한다.
- 1차에서는 변동 배경 영역을 숨기는 안을 적용한다. 미구현을 실제 조회 결과인 “관련 자료 없음”으로 표시하지 않는다. `movementContext` 필드는 후속 도입 대상으로 남긴다.
- 1차 완료는 전체 RQ-0501 완료가 아니다. SUM-04·05·08과 관련 인수 기준은 후속 단계에서 검증한다.
- Redis는 필요하면 기존 설정을 재사용해도 된다는 사용자 승인을 받았다. 의존성·접속 설정·compose 서비스·기본 10초 quote 캐시 설정을 확인했으며 실행 중 연결 상태는 아직 검증하지 않았다.
- 계획 기본안은 가격 캐시만 재사용하고 기본정보는 DB에서 조회하는 것이다. 별도 기본정보 캐시가 필요해지면 키·TTL·무효화·장애 정책을 문서에 추가한다. 변동 배경 캐시는 후속에서 결정한다.

### 전체 기능 범위

- 확정: 종목명, 코드·티커, 산업, 현재가 또는 최신가, 직전 거래일 종가 대비 등락률, 기준 시각, 주가 변동 배경.
- 기존 [stock-quote](SPEC-stock-quote.md)가 가격·등락률·주요 지표를 소유한다. 이 모듈이 별도 시세 공급자를 호출하거나 산식을 중복 구현하지 않는다.
- 후속 단계에서 `stock-news`(`SPEC-stock-news.md`), `stock-disclosure`(`SPEC-stock-disclosure.md`)의 근거를 참조한다. 1차는 두 모듈에 의존하지 않는다. 뉴스·공시는 요약 모듈에 의존하지 않는다.
- 차트·주요 지표 8종, 알림 저장·발송, 로그인, 목록 검색·필터 구현은 제외한다. 알림 버튼과 목록 복귀는 해당 기능으로 종목·시장 정보를 넘기는 화면 연동 범위다.
- 제안: 로고 URL은 있으면 제공하고 없으면 대체 표시한다. 외부 로고 수집을 P0 필수로 확정하지 않는다.

## 기능 요구사항

| ID | 상태 | 요구사항 |
| --- | --- | --- |
| SUM-01 | 확정 | 선택한 종목명·코드 또는 티커·산업을 표시하고 상세 이동 전의 시장과 일치시킨다. |
| SUM-02 | 확정 | 현재가 또는 최신가, 직전 거래일 종가 대비 등락률, 가격 기준 시각과 데이터 상태를 제공한다. |
| SUM-03 | 확정 | 등락률은 `(현재가 - 직전 거래일 종가) / 직전 거래일 종가 × 100`이다. 통화와 시장 시간대를 함께 제공한다. |
| SUM-04 | 확정 | 변동 배경은 연결된 뉴스·공시와 산업 지표에 근거하며 출처를 확인할 수 있다. 근거 없는 설명을 생성하지 않는다. |
| SUM-05 | 확정 | 인과관계를 단정하지 않는다. 안내 문구 예: “관련 자료는 함께 확인된 맥락이며, 가격 변동의 원인으로 단정하지 않습니다.” |
| SUM-06 | 확정 | 결측 수치는 null로 전달하고 화면은 `-`로 표시한다. 마지막 정상값을 노출하면 실제 기준 시각과 stale 상태를 유지한다. |
| SUM-07 | 확정 | 국내·해외 변경 시 종목·통화·시간대·산업이 함께 변경되며 이전 종목 응답을 새 종목에 표시하지 않는다. |
| SUM-08 | 제안 | 근거가 없으면 변동 배경은 null로 반환하고 “확인된 관련 자료가 없습니다”로 표시한다. 조회 실패와 구분한다. |
| SUM-09 | 제안 | 로고가 없거나 로딩에 실패하면 대체 이미지를 사용하고 종목명은 유지한다. |

## 데이터와 API 계약 초안

기본정보와 기존 `/api/v1/stocks/{stockId}/quote`를 화면에서 조합한다. 기본정보 경로는 `GET /api/v1/stocks/{stockId}`를 제안한다. 1차 필드 구성은 아래 절을 따르고 전체 기능 필드 표의 배경·공통 메타데이터 확장은 후속 검토 대상으로 구분한다.

### 1차 계약 (확정)

SUM-T01에서 기존 코드와 대조해 확정했다. 대조 결과는 아래 "기존 코드 대조 결과"에 둔다.

- 요청: `GET /api/v1/stocks/{stockId}`. 양의 정수 stockId, 쿼리 없음. market은 종목의 Markets에서 결정한다. 기본정보 자체에는 가격 기준 asOf나 과거 조회를 넣지 않는다.
- 성공: `data.stockId`, `data.profile.name`, `data.profile.ticker`, `data.profile.industries`, `data.profile.logoUrl`, `data.market`, `data.currency`, `data.timezone`.
- 산업: `{code, name}` 배열. `code`는 `IndustryCode` 이름(예: `AUTOMOBILE`), `name`은 서버가 주는 한글 표시명(예: `자동차`)이다. [산업 마스터 명세](SPEC-industry.md)에 따라 `industryId`는 노출하지 않고, 한글 표시명 가나다순으로 정렬한다. 기존 매핑을 중복 제거하며 임의 대표 산업을 고르지 않는다. 미매핑은 빈 배열이다. 조회는 산업 모듈의 `IndustryReader.readByStockId`를 쓴다.
  - **단계 도입:** 1차(SUM-T03~T05)는 `profile.industries` 없이 나갔고, SUM-T02(`readByStockId`, PLO-40) 이후 SUM-T06에서 필드를 추가했다. 기존 필드는 바꾸지 않았다. 빈 배열은 "연결된 산업 없음"을 뜻한다.
- logoUrl은 기존 `Stocks.imgUrl`을 사용하고 없으면 null이다. 새 로고 공급자는 도입하지 않는다.
- market은 `Markets.country`(KR/US), timezone은 `Country.zoneId`의 IANA 이름(Asia/Seoul, America/New_York)이다. 거래소 코드와 국가 단위 market을 혼용하지 않는다.
- currency는 `Markets.currency`를 그대로 전달한다. 1차 대상 시장은 KRW·USD이며 enum의 다른 값을 걸러내거나 대체하지 않는다.
- price·changeRate·priceAt·priceTiming은 기존 quote에서 표시한다. quote 응답(`StockQuoteResponse`)에도 stockId가 있으므로 프론트는 요청 stockId와 응답 stockId로 결합하고 다른 종목의 늦은 응답을 폐기한다.
- 입력 오류: 숫자가 아닌 stockId는 기존 타입 불일치 처리로 400/P001이다. 0·음수는 컨트롤러에서 `Precondition.require(stockId > 0, INVALID_INPUT_VALUE)`로 400/P001을 반환한다. 공통 예외 핸들러는 변경하지 않는다.
- 미등록 종목은 기존 `StockReader`의 404/P002, 그 밖의 내부 오류는 기존 500/P006을 따른다. priceAt을 기본정보 조회 시각으로 갱신하지 않는다.
- 공통 기획의 source·dataStatus·delayMinutes·comparisonBase는 **1차 유예**다. 기본정보 응답에 넣지 않고, 미지원 메타데이터를 임의 생성하지 않으며, 1차에서 충족했다고 기록하지 않는다. quote 계약과의 조율은 미해결 질문 2에서 다룬다.

### 기존 코드 대조 결과 (SUM-T01)

| 대상 | 확인 결과 | 조치 |
| --- | --- | --- |
| `Stocks.ticker` | `String` 컬럼이라 선행 0 보존 | 그대로 사용 |
| `Country` | KR·US만 존재하고 `zoneId` 보유 | 시장·시간대 매핑을 새로 만들지 않고 재사용. 미지원 국가 분기는 두지 않는다 |
| `Currency` | KRW·USD·USDT | 그대로 전달 |
| `StockWithMarket` | `name()`은 있고 `imgUrl()` 위임 메서드 없음 | SUM-T03에서 `imgUrl()` 추가 |
| `Industries`·`IndustryReader` | 산업 모듈이 `displayName()`과 가나다순 정렬 규칙을 소유. 종목별 산업 조회 메서드 없음. `stock_industries`에 (stockId, industryId) 유니크 제약 없음 | SUM-T02에서 `IndustryReader.readByStockId` 추가(DISTINCT 조회 + Java 가나다순 정렬). 스키마 변경 없음 |
| 요청 ID 검증 | 기존 API에 0·음수 검증 없음. `@Positive`는 처리되지 않은 예외로 500이 됨 | 요약 API만 `Precondition`으로 검증 |
| `StockQuoteResponse` | stockId·ticker·name·currency 포함(main 반영 후 확인). 응답 봉투는 `ApiResult` | 차이 해소. 기본정보도 `ApiResult` 봉투를 쓴다 |

### 전체 기능 필드 검토안

아래 필드는 새 API의 제안이며 현재 구현됐다는 의미가 아니다. 응답 봉투는 [공통 API 명세](SPEC-api-response.md)의 `{"data": {...}}`를 따른다.

| 필드 | 형식·결측 | 의미 |
| --- | --- | --- |
| `stockId` | integer, 필수 | 기존 Long 식별자. 문자열 티커와 구분 |
| `profile.name`, `profile.ticker` | string, 필수 | 종목명, 선행 0을 보존한 코드·티커 |
| `profile.industries` | array | 산업 코드와 한글 표시명(1차 계약과 같음). 복수 매핑을 보존하고 대표 산업 선정은 확인 필요 |
| `profile.logoUrl` | string 또는 null | 기존 Stocks.imgUrl에 대응하는 제안 |
| `market`, `currency`, `timezone` | 필수 | KR/KRW/Asia/Seoul 또는 US/USD/America/New_York. IANA 시간대 사용은 제안 |
| `asOf` | ISO-8601 시각 | 이 응답의 기준 시각. 가격 기준 시각은 quote.priceAt을 별도로 유지 |
| `comparisonBase` | object | 직전 거래일 정규장 종가 비교임을 식별하고 기준 거래일 제공. 필드 구성 확인 필요 |
| `dataStatus`, `source`, `delayMinutes` | 상태·출처·지연 정보 | 공통 기획 필드. enum과 출처 객체 형식은 공통 계약에서 확정, 미확인 지연 분은 null |
| `movementContext` | object 또는 null | `summary`, `evidenceRefs`, `asOf`를 갖는 배경 요약 제안 |
| `movementContext.evidenceRefs` | array | 문서 ID·유형·출처·발표 시각·원문 URL. 산업 지표를 인용하면 지표 값·기간·출처도 추적 가능해야 함 |

제안 요청 검증: stockId는 양의 정수다. market을 받으면 종목의 실제 시장과 일치해야 하며 불일치는 400이다. 임의 과거 asOf 조회는 원본 `RQ-0804`의 P1 범위와 구분하며 이번 P0에서 지원한다고 약속하지 않는다.

### 가격 의존 계약의 차이

- 기존 quote의 `priceAt`은 공급자 발표 시각이 아니라 서버 수신 시각이다. 실제 거래 시각으로 표시하지 않는다.
- 기존 quote 응답에는 market·timezone·comparisonBase·dataStatus·source·delayMinutes가 모두 갖춰져 있지 않다. 상단 전체의 메타데이터 충족 방법을 기존 담당과 합의해야 한다.
- 기존 quote는 전일 종가가 0이면 등락률 0.00을 반환한다. 신규 요구의 결측·산식 해석과 충돌할 수 있어 **계산 불가 시 null 제안**을 미해결로 남기며 기존 계약을 여기서 변경하지 않는다.
- 기존 quote는 공급자 실패 시 오래된 값으로 대체하지 않고 502/P007을 반환한다. 본 문서의 stale 요구는 오래된 값을 채택할 때의 조건이며 폴백 도입 요구가 아니다.

## 프론트 연동 인계 (1차, SUM-T05)

1차 백엔드 구현 기준의 실제 계약이다. 살아 있는 문서는 Swagger(`/swagger-ui.html`, 태그 "종목 기본정보")다.

```json
GET /api/v1/stocks/1
{
  "data": {
    "stockId": 1,
    "profile": { "name": "삼성전자", "ticker": "005930", "logoUrl": null },
    "market": "KR",
    "currency": "KRW",
    "timezone": "Asia/Seoul"
  }
}
```

| 규칙 | 내용 |
| --- | --- |
| 두 API 조합 | 같은 stockId로 기본정보(`/api/v1/stocks/{stockId}`)와 시세(`/api/v1/stocks/{stockId}/quote`)를 각각 요청한다. 두 응답 모두 `data.stockId`가 있으므로 현재 선택한 stockId와 다르면 버린다(종목 전환 중 늦게 온 이전 종목 응답 폐기) |
| 가격 기준 시각 | quote의 `priceAt`을 그대로 표시한다. 기본정보 조회 시각으로 바꾸지 않는다 |
| 부분 실패 | 시세만 실패(502/P007)하면 기본정보는 유지하고 가격 영역만 오류·재시도를 표시한다 |
| 로고 | `logoUrl`이 null이거나 이미지 로딩에 실패하면 대체 이미지를 쓰고 종목명은 유지한다 |
| 산업 | `profile.industries`는 `[{code, name}]`(한글 표시명 가나다순)이다. 빈 배열은 "연결된 산업 없음"이다 (SUM-T06에서 추가) |
| 변동 배경 | 1차에서는 영역을 노출하지 않는다. "관련 자료 없음"으로 표시하지 않는다 |
| 오류 | 숫자 아님·0·음수 400/P001, 없는 종목 404/P002, 그 밖 500/P006 |
| 미충족 메타데이터 | source·dataStatus·delayMinutes·comparisonBase는 1차 미제공(유예) |

## 상태와 책임 경계

| 상황 | 백엔드 요구 | 프론트엔드 요구 |
| --- | --- | --- |
| 최초 조회 | 선택한 stockId 데이터 제공 | 로딩 표시, 다른 종목의 잔여 정보 방지 |
| 기본 종목 없음 | 404/P002 | 미등록 종목 안내와 목록 이동 |
| 잘못된 식별자 | 400/P001 | 입력 오류 안내 |
| 시세만 실패 | 기존 quote의 502/P007 | 기본정보 유지, 가격 영역 오류·재시도 표시 |
| 1차 변동 배경 미구현 | 근거 조회·생성 없음 | 배경 영역 미노출, 실제 0건으로 표시하지 않음 |
| 후속 요약 근거 없음 | 정상 조회 결과의 null | 빈 상태 표시 또는 해당 배경 영역 생략 |
| 후속 근거 조회 실패 | 실패를 빈 결과로 변환하지 않음 | 오류와 재시도 표시, 원인 설명 생성 금지 |
| 숫자 결측 | null, 0으로 대체 금지 | `-` 표시 |

부분 실패를 표현하는 세부 응답 구조와 재시도 가능 여부 전달 방식은 확인 필요다. 공통 오류 봉투에는 현재 retryable 필드가 없으므로 임의로 확정 필드처럼 추가하지 않는다.

## 프로젝트 구조와 코드 스타일

기술 기반은 기존 Java 26 / Spring Boot 4.1.1 / MySQL 프로젝트다. Stocks, StockReader, Markets, IndustryReader를 재사용한다. 가격은 별도 quote API를 재사용하며 기본정보 서비스에 quote 서비스를 주입하지 않는다. 기본정보·요약 서비스는 `src/main/java/com/swyp/ploutos/stock/summary/` 아래 모듈 루트·service·controller로 배치하는 안을 제안한다. 테스트는 대응하는 `src/test/java/com/swyp/ploutos/stock/summary/`에 둔다.

현재 Stocks에는 변동 배경과 근거 참조가 없다. 저장형 요약인지 조회 시 조합인지, 생성 주체가 편집자·규칙·모델 중 무엇인지는 확정되지 않았다. LLM 도입을 전제하지 않는다.

`CLAUDE.md`의 생성자 주입, setter 금지, guard clause, DTO sealed interface + record 규약을 따른다. 예: `public sealed interface SummaryResponse permits SummaryResponse.Profile { record Profile(Long stockId, String name, String ticker) implements SummaryResponse {} }`. 숫자는 문자열로 포맷하지 않고 시각은 Clock으로 테스트 가능하게 한다. 구현 후 실제 계약은 Swagger에 반영한다.

## 테스트 전략과 성공 기준

JUnit 6 / BDD 한글 테스트명으로 단위 테스트에 매핑·요약 정책, MVC 테스트에 상태 코드·JSON, 통합 테스트에 DB 연결을 둔다. 외부 공급자는 스텁한다. 아래는 구현 시 검증할 기준이며 현재 테스트 통과 보고가 아니다.

| 기준 | Given / When / Then |
| --- | --- |
| SUM-01 | 등록된 국내·미국 종목 각각 조회 → 이름·티커·산업·시장 일치 |
| SUM-02~03 | 현재가 110, 전일 종가 100 → quote 계약의 10%를 표시하고 기준 시각·통화 유지 |
| SUM-04~05 | 근거가 있는 배경 조회 → 모든 인용의 원문 확인 가능, 원인 단정 문구 없음 |
| SUM-06 | 가격 결측 → 0이 아닌 null과 `-`; 오래된 값 채택 시 과거 시각과 stale 유지 |
| SUM-07 | A 조회 후 B로 변경하고 A 응답이 늦게 도착 → B 화면에 A를 표시하지 않음 |
| SUM-08 | 자료 0건과 자료 조회 실패 → 서로 다른 빈 상태·오류 상태 |
| SUM-09 | logoUrl 없음 → 종목명과 대체 표시 유지 |
| 오류 | abc·0·음수 ID → 400; 유효 형식의 미등록 ID → 404 |
| 화면 공통 | 360px 이상에서 겹침 없음, 링크·버튼 키보드 접근 가능 |

## 명령어

구현 후 검증용이며 이번 문서 작성에서 실행할 필요는 없다. JDK 26과 통합 테스트용 Docker가 필요하다.

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests '*StockSummary*Test'
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootRun
```

## 경계

- 항상: 원본 RQ와 자체 SUM ID를 구분하고 가격은 기존 모듈 계약을 재사용한다. 출처·기준 시각·결측을 보존한다.
- 먼저 확인: 1차 세부 필드와 메타데이터 유예 범위, quote 계약 변경, 요약 생성 방식, 데이터 스키마 변경. 기존 Redis 재사용은 승인돼 있으므로 그 자체를 다시 승인받지 않는다.
- 절대 안 함: 예시 문구를 실제 분석으로 사용, 근거 없는 배경 생성, 매수·매도 추천, 차트나 알림 발송 범위 확장.

## 미해결 질문

1. SUM-T01에서 확정했다. 프론트에는 산업 코드의 한글 표시명 매핑과 로고 null 대체 표시를 인계한다.
2. 기존 시세 담당과 조율: 전일 종가 0·결측, 거래정지, 가격 지연·비교 기준·출처 필드 차이. 1차에서 기존 quote 동작을 임의 변경하지 않는다.
3. 후속: 배경 요약 생성 주체·갱신 주기·최대 길이·산업 지표 공급 계약.
4. 후속: 근거 시간창, 부분 실패·retryable, 배경 캐시 정책. 이 항목은 1차 기본정보 구현의 선행 조건이 아니다.
