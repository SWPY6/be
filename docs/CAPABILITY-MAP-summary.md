# 기능 맵: 종목 요약·관련 뉴스·공시 (RQ-0501, RQ-0502)

종목 상세 화면 상단의 "종목 요약 정보"와 그 아래 "관련 뉴스·공시"를 구성하는 모듈과 빌드 순서다.
세 영역은 따로 출시·검증할 수 있어 명세를 모듈별로 나눈다.

종목 상세의 차트·주요 지표 맵은 `CAPABILITY-MAP.md`, 산업 맵은 `CAPABILITY-MAP-industry.md`에 따로 있다.
이 맵은 그 위에 얹힌다 — 종목 요약 화면이 `stock-quote`의 시세 API와 조합되고, 산업은 `industry` 모듈에서 읽는다.

원본 기획의 RQ-0501/RQ-0502와 차트 명세의 RQ-1001~1008은 번호 체계가 달라 서로 치환하지 않는다.

## 모듈

| 모듈 id | 책임 | 의존 대상 | 명세 | 상태 |
| --- | --- | --- | --- | --- |
| `stock-summary` | 1차: 종목 기본정보 API(`GET /api/v1/stocks/{stockId}`)와 기존 시세 API의 화면 조합. 후속: 근거 기반 변동 배경(RQ-0501) | 1차: 종목·시장 조회, `industry`(종목별 산업 조회, 협의 중). 화면에서 `stock-quote` 조합. 후속: `stock-news`, `stock-disclosure` | `SPEC-stock-summary.md` | 1차 기본정보 API 구현, 산업 연결 대기 |
| `stock-news` | RQ-0502 뉴스 조회, RQ-0802 뉴스 연결·중복 제거 | 종목·시장 조회, NAVER API HUB 뉴스 검색 | [SPEC-stock-news.md](SPEC-stock-news.md) |
| `stock-disclosure` | RQ-0502 공시 조회, RQ-0802 발행 법인 연결·중복 제거 | 종목·시장 조회, 공시 공급 계약 | `SPEC-stock-disclosure.md` | 요구사항 초안 |

빌드 순서: 종목·시장 조회(기존) → `stock-summary` 1차 → `stock-news`, `stock-disclosure` → `stock-summary` 변동 배경

## 의존 규칙

- 기본정보 API는 quote·뉴스·공시 서비스를 호출하지 않는다. 가격은 프론트가 같은 stockId로 `stock-quote` API를 따로 불러 조합한다.
- 가격 캐시(Redis)는 `stock-quote`가 소유한다. 이 맵의 모듈은 필요하면 기존 Redis 설정을 재사용할 수 있다.
- 산업 식별·표시명·정렬은 `industry` 모듈 규칙(`SPEC-industry.md`)을 따른다. 종목별 산업 조회 메서드 추가는 산업 담당과 협의한다.
- 뉴스와 공시는 서로 구현에 의존하지 않는다. 둘 다 `stock-summary`에 의존하지 않는다.
- 후속 변동 배경의 의존 순서: 공통 evidence 계약 → 뉴스·공시 조회 → 근거를 참조하는 변동 배경. 공유 evidence 컨트롤러 한 곳에서 분기하는 안이며 경로·타입·기간·페이지 정책은 후속 구현 전에 확정한다.
