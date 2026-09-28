# 명세: 산업 마스터 (industry)

산업 9종을 정본으로 관리하고, 다른 모듈이 산업을 식별·표시할 수 있게 하는 모듈이다.
HTTP API가 없다. `industry-pin`, `industry-flow`, `industry-trend`, `industry-news`가 모두 이 모듈에 의존한다.

기능 맵: `CAPABILITY-MAP-industry.md`. 의존 대상: 없음.

## 내가 세운 전제

1. 산업 9종은 고정이다. 추가·삭제는 코드 변경(enum)과 DB 변경(`ALTER TABLE ... enum`)을 함께 요구한다.
2. 국내·해외 시장이 **같은 9개 산업**을 쓴다. 시장별로 다른 분류를 두지 않는다.
3. 한 종목이 여러 산업에 속할 수 있다(`StockIndustries`가 N:M).
4. 산업명은 서버가 한글로 내려준다. 다국어는 요구사항에 없다.
5. 이 모듈은 읽기 전용이다. 산업을 만들거나 고치는 **서비스·API를 두지 않는다** — 9행은 시드 SQL로 넣는다.
   엔티티 생성자는 JPA와 테스트가 객체를 만드는 통로이며, `IndustryRepository.save()`를 호출하는
   코드는 어디에도 없다. `Markets`·`Stocks`도 같은 상태다(public 생성자가 프로덕션 코드에서
   호출되는 곳이 0이고 테스트에서만 쓰인다). DB에 데이터를 넣는 경로는 시드 SQL 하나뿐이다.

## 목표

다른 모듈이 "이 산업이 무엇이고 화면에 어떻게 쓰이는지"를 한 곳에서 얻게 한다.
지금은 `IndustryCode`에 한글명이 **주석으로만** 있어 API가 `"자동차"`를 내려줄 수 없고,
`AUTOMOBILE` 알파벳순은 가나다순과 달라 목업이 요구하는 정렬도 불가능하다.

성공 기준은 **"`IndustryCode` 하나로 코드·한글명·정렬 순서가 모두 나오고, DB의 9행과 1:1로 대응한다"**다.

## 규칙

### 산업 9종

| `IndustryCode` | 한글 표시명 | `industry_id` |
| --- | --- | --- |
| `AUTOMOBILE` | 자동차 | 1 |
| `CONSTRUCTION` | 건설 | 2 |
| `TRANSPORT` | 운송 | 3 |
| `RETAIL` | 유통 | 4 |
| `FOOD_BEVERAGE` | 음식료 | 5 |
| `TELECOM` | 통신 | 6 |
| `STEEL` | 철강 | 7 |
| `ENERGY` | 에너지 | 8 |
| `CHEMICAL` | 화학 | 9 |

### 식별자

- **외부(API·프론트)에는 `IndustryCode`를 쓴다.** `"AUTOMOBILE"` 같은 문자열이다.
- **`industryId`는 내부 FK 전용이다.** 응답에 노출하지 않는다.

`industry_id`가 `auto_increment`라 시드를 넣는 순서·환경에 따라 값이 달라질 수 있다. 프론트가
`industryId`를 저장해두면 로컬과 서버에서 다른 산업을 가리킬 수 있다. `IndustryCode`는 코드가
정본이라 환경과 무관하다. 그래서 **시드 SQL이 `industry_id`를 명시적으로 지정**해 세 환경(로컬·테스트·서버)에서
같은 값이 되게 하되, 그 값에 의존하는 코드는 두지 않는다.

### 정렬

`readAll()`은 **한글 표시명 가나다순**으로 돌려준다. 목업의 산업별 동향 화면이 `전체` 필터에서
산업명 순으로 표시하기 때문이다.

**정렬은 SQL이 아니라 Java에서 한다.** `industries.name`은 MySQL 네이티브 `ENUM` 컬럼이고,
MySQL은 ENUM을 **선언 순서의 정수**로 정렬한다. 문자열로 비교해도 값은 `AUTOMOBILE`·`CHEMICAL`
같은 영문 코드라 한글 순서와 무관하다. 그래서 `ORDER BY name`으로는 가나다순을 얻을 수 없다.
9행이라 Java 정렬에 비용이 없고, 덕분에 가짜 리포지토리로 정렬 규칙을 단위 테스트할 수 있다.

비교는 `String.compareTo`로 한다. 별도 `Collator`가 필요 없다 — 완성형 한글은 유니코드에
`가`(U+AC00)부터 `힣`(U+D7A3)까지 가나다순으로 연속 배치되어 있어 코드포인트 순서가 사전순과 같다.

```
건설(U+AC74) < 에너지(U+C5C8) < 운송(U+C674) < 유통(U+C720) < 음식료(U+C74C)
             < 자동차(U+C790) < 철강(U+CCA0) < 통신(U+D1B5) < 화학(U+D654)
```

목업의 표시 순서와 정확히 일치한다. 9종 모두 첫 글자가 서로 달라 첫 글자 비교에서 결론이 난다.
**산업이 추가돼 한글이 아닌 이름이나 옛한글이 들어오면 이 전제가 깨진다** — 그때 `Collator`를 쓴다.

### 종목 매핑

`StockIndustries`(`stock_industries`)가 종목↔산업 N:M 매핑을 갖는다. **시드 데이터는 아직 넣지 않는다.**
매핑을 읽는 메서드는 `industry-flow`를 만들 때 추가한다(아래 "추후 구현" 1번) — 지금 만들면
쓰는 곳이 없는 추측성 코드다.

### 노출 인터페이스

```java
public interface IndustryReader {

    /** 산업 9종을 한글 표시명 가나다순으로 읽는다. */
    List<Industries> readAll();

    /** 산업 코드에 대응하는 산업. 시드가 없으면 예외를 던진다. */
    Industries read(IndustryCode code);
}
```

**값 객체를 따로 두지 않고 엔티티를 그대로 돌려준다.** `MarketReader`가 `Markets`를 그대로
반환하는 선례를 따른다. `Industries`는 필드가 둘(`industryId`, `name`)뿐이고 지연 로딩 연관도
없어, 값 객체를 만들면 매핑 코드만 늘고 얻는 게 없다. 이 프로젝트에서 값 객체를 쓰는 곳은
`StockWithMarket`처럼 **두 엔티티를 묶을 때**다.

**`read`는 `Optional`이 아니라 예외를 던진다.** `IndustryCode`는 enum이라 호출자가 없는 값을
넣을 수 없다. 조회 실패는 곧 **시드 9행이 없다는 뜻이고 그것은 배포 실수**다. `Optional.empty()`를
돌려주면 "핀 추가가 조용히 안 되는" 경로가 생긴다. `JpaMarketReader`를 따라
`IllegalStateException`을 던진다 — `BusinessException`은 HTTP 응답 코드로 번역되는 사용자
오류용이고(`JpaStockReader`가 종목 없음을 404로 내보낼 때 쓴다), 이 경우는 사용자 잘못이 아니다.

### 시드 데이터

`db/seed-industries.sql`을 로컬·서버 DB에 각각 실행한다. 운영은 `ddl-auto=none`이라 앱이
데이터를 넣지 않는다.

```sql
INSERT INTO industries (industry_id, name) VALUES
    (1, 'AUTOMOBILE'),
    (2, 'CONSTRUCTION'),
    (3, 'TRANSPORT'),
    (4, 'RETAIL'),
    (5, 'FOOD_BEVERAGE'),
    (6, 'TELECOM'),
    (7, 'STEEL'),
    (8, 'ENERGY'),
    (9, 'CHEMICAL') AS new
ON DUPLICATE KEY UPDATE name = new.name;
```

`ON DUPLICATE KEY UPDATE`를 붙여 **여러 번 실행해도 안전하게** 만든다. 시드를 이미 넣었는지
기억하지 못해도 다시 돌리면 같은 상태가 된다.

**`industries.name`은 MySQL 네이티브 `enum` 컬럼이다.** Hibernate 6.2+가 `@Enumerated(STRING)`을
네이티브 enum으로 매핑했기 때문이다. 그래서 Java enum에 상수를 추가하면 `ALTER TABLE`이 따라온다 —
컬럼이 받아주지 않는 값은 INSERT가 거부된다.

테스트는 시드 SQL을 쓰지 않는다. 리포지토리를 가짜로 대체해 필요한 값을 직접 넣는다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*IndustryCodeTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/common/enums/IndustryCode.java   → 한글 표시명 필드 추가
src/main/java/com/swyp/ploutos/industry/Industries.java         → @Getter, displayName()
src/main/java/com/swyp/ploutos/industry/repository/             → IndustryRepository (신규)
src/main/java/com/swyp/ploutos/industry/service/                → IndustryReader, JpaIndustryReader (신규)
db/seed-industries.sql                                          → 9행 시드 (신규)
src/test/java/com/swyp/ploutos/common/enums/IndustryCodeTest.java
src/test/java/com/swyp/ploutos/industry/IndustriesTest.java
src/test/java/com/swyp/ploutos/industry/service/JpaIndustryReaderTest.java
```

`IndustryReader`만 `public`이고 `JpaIndustryReader`는 package-private다. Spring은 비공개 클래스도
빈으로 만든다. 패키지 배치는 `market` 모듈(`MarketReader` ↔ `JpaMarketReader`)의 선례를 따른다.

## 코드 스타일

`IndustryCode`는 표시명을 필드로 갖는다. 접근자는 `get` 없이 쓴다.

```java
public enum IndustryCode {

    AUTOMOBILE("자동차"),
    CONSTRUCTION("건설"),
    TRANSPORT("운송"),
    RETAIL("유통"),
    FOOD_BEVERAGE("음식료"),
    TELECOM("통신"),
    STEEL("철강"),
    ENERGY("에너지"),
    CHEMICAL("화학");

    private final String displayName;

    IndustryCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

`lombok.accessors.fluent`는 Lombok이 생성하는 접근자에만 적용된다. 손으로 쓴 enum에는 해당하지
않으므로 `displayName()`을 직접 쓴다. enum에 `@Getter`를 붙이면 `getDisplayName()`이 되므로 붙이지 않는다.

`Industries` 엔티티는 표시명을 스스로 답한다. 호출자가 enum을 꺼내 한 단계 더 타지 않게 한다.

```java
@Getter
@Entity
@Table(name = "industries")
public class Industries {

    // ...

    public String displayName() {
        return name.displayName();
    }
}
```

- `@Setter`·`@Data` 금지. `@Getter`는 클래스 단위.
- `else` 없이 guard clause.
- 파일 끝에 개행.

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **전부 단위 테스트다.** `JpaStockReaderTest`의 선례를 따라 `@ExtendWith(MockitoExtension.class)`와
  `@Mock` 리포지토리로 검증한다. 정렬을 Java에서 하므로 가짜 리포지토리로 정렬 규칙까지 덮인다.
  - `IndustryCodeTest` — 9종의 표시명, 가나다순 정렬, 표시명 중복·공백 없음
  - `IndustriesTest` — 엔티티가 표시명을 위임하는지
  - `JpaIndustryReaderTest` — 가나다순 정렬, 코드 단건 조회, 시드 없을 때 예외
- Testcontainers 통합 테스트를 두지 않는다. 엔티티 매핑과 DDL 생성은 `PloutosApplicationTests`가
  컨텍스트를 띄울 때 이미 검증되고, 이 모듈에는 DB에서만 드러나는 로직이 없다.
- E2E 없음 — HTTP API가 없는 모듈이다.
- `ArchitectureTest`가 통과해야 한다. 이 모듈은 기존 모듈과 같은 레이아웃이라 새 규칙을 추가하지 않는다.

## 경계

- **항상:** 산업을 추가·삭제하면 enum과 `industries` 테이블(`ALTER TABLE ... enum`)과 시드 SQL을 함께 고친다.
  실행한 SQL은 저장소에 커밋한다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** 산업 9종의 구성·표시명 변경(프론트 표시가 바뀐다), `industryId`를 API에 노출,
  시장별로 다른 산업 분류 도입, `StockIndustries` 구조 변경.
- **절대 안 함:** 산업을 런타임에 만들거나 고치는 서비스·API 추가(`IndustryRepository.save()` 호출),
  `Industries`에 평균 등락률·순위 같은 시점 의존 값 저장, 응답에 `industryId` 노출,
  한글명을 프론트가 매핑하도록 코드만 내려주기, `ORDER BY name`으로 가나다순을 얻으려 시도하기.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 9종이 각자 한글 표시명을 돌려준다. | `IndustryCodeTest.모든_산업이_한글_표시명을_가진다` |
| 2 | 표시명으로 정렬하면 목업의 가나다순과 같다. | `표시명으로_정렬하면_가나다순이_된다` |
| 3 | 표시명이 비거나 중복된 산업이 없다. | `표시명은_비어있지_않고_서로_다르다` |
| 4 | 엔티티가 표시명을 위임한다. | `IndustriesTest.엔티티는_표시명을_위임한다` |
| 5 | `readAll()`은 9건을 가나다순으로 돌려준다. | `JpaIndustryReaderTest.전체를_읽으면_가나다순으로_돌려준다` |
| 6 | `read(code)`는 해당 산업을 돌려준다. | `코드로_읽으면_해당_산업을_돌려준다` |
| 7 | 시드가 없는 코드로 읽으면 예외를 던진다. | `시드가_없는_코드로_읽으면_예외를_던진다` |

## 미해결 질문

- 없음. (`stock_industries` 매핑 시드는 `industry-flow`의 선행 조건으로 기능 맵에 기록돼 있다.)

## 추후 구현

1. **산업별 종목 조회.** `industry-flow`를 만들 때 `IndustryReader`에 추가한다. 9개 산업의 매핑을
   한 번에 읽는 형태(`Map<IndustryCode, List<Long>>`)로 두어 산업마다 쿼리를 날리는 N+1을 피한다.
2. **`stock_industries` 시드.** 매핑 데이터 확보 방법이 정해지면 `db/`에 시드 SQL을 추가한다.
3. 산업이 9종을 넘어가면 표시명 정렬에 `Collator`를 도입한다(한글이 아닌 이름이 섞이는 경우).
