---
name: test-driven-development
description: BDD 기반으로 테스트를 작성한다. 기능을 구현했을 때, 버그(bug)를 고칠 때, 기존 동작을 바꿀 때 사용한다. 명세의 인수 기준(acceptance criteria)을 Given-When-Then 테스트로 옮겨야 할 때, 테스트를 어느 층(단위·통합·E2E)에 둘지 판단해야 할 때 사용한다.
---

# BDD 기반 테스트 작성

## 개요

테스트는 명세의 인수 기준을 실행 가능한 형태로 옮긴 것이다. 우리는 명세를 기준점으로 삼는 SDD를 따르므로, 테스트가 개발을 이끌지 않는다 — **명세가 이끌고 테스트가 증명한다.**

그래서 순서는 이렇다. 명세의 인수 기준을 읽는다 → 그것을 만족하도록 구현한다 → 그 인수 기준이 실제로 만족되는지 Given-When-Then 테스트로 증명한다. "실패하는 테스트를 먼저 쓰고 통과시킨다"는 사이클은 쓰지 않는다.

다만 **버그 수정은 예외다.** 고치기 전에 재현 테스트를 먼저 쓴다 (아래 버그 수정 절 참고).

## 언제 사용하는가

- 태스크의 인수 기준을 충족하는 구현을 마쳤을 때
- 버그를 고칠 때
- 기존 동작을 수정할 때
- 엣지 케이스 처리를 추가할 때

**사용하지 않을 때:** 순수한 설정 변경, 문서 갱신, 동작에 영향이 없는 변경.

## 명세에서 테스트로

**인수 기준이 Given-When-Then의 원본이다.** 테스트를 쓰면서 시나리오를 지어내고 있다면, 그건 명세에 없는 요구사항을 발명하고 있다는 신호다. 멈추고 명세로 돌아간다.

```
명세의 인수 기준:
- 진행 중인 할 일을 완료하면 상태가 완료가 되고 완료 시각이 기록된다
- 이미 취소된 할 일은 완료할 수 없다

        ↓ 문장 구조를 그대로 옮긴다

given   진행 중인 할 일이 있고
when    완료를 요청하면
then    상태가 완료가 되고 완료 시각이 기록된다

given   이미 취소된 할 일이 있고
when    완료를 요청하면
then    IllegalStateException이 발생한다
```

인수 기준 하나가 테스트 하나에 대응한다. 인수 기준에 "그리고"가 들어 있다면 대개 테스트 두 개다.

**인수 기준으로 표현할 수 없는 것은 테스트하지 않는다.** "성능이 좋아야 한다" 같은 서술은 테스트가 아니라 측정 가능한 기준(p95 응답 시간 등)으로 먼저 바꿔야 한다.

## Given-When-Then 형식

테스트 본문은 `// given` `// when` `// then` 주석으로 세 구역을 나눈다. 이 세 줄이 테스트의 목차 역할을 한다.

이 저장소는 **JUnit 6**(`org.junit.jupiter` 6.x, Spring Boot 4가 관리)을 쓴다. 버전은 6이지만 **애노테이션 패키지는 `org.junit.jupiter.api` 그대로**이므로 임포트를 바꿀 것이 없다. 목 프레임워크는 Mockito 5.x이며 JUnit 6과 함께 정상 동작한다 — Mockito 6을 찾지 않는다.

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void 진행중이면_완료시각이_기록된다() {
        // given
        Task task = Task.of("장보기", null);
        given(taskRepository.findById(TASK_ID)).willReturn(Optional.of(task));

        // when
        TaskResponse response = taskService.complete(TASK_ID);

        // then
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }
}
```

**규칙:**

- **`when`은 한 줄이다.** 검증 대상 동작은 하나뿐이다. `when` 구역이 여러 줄로 늘어난다면 테스트가 두 개로 나뉘어야 한다는 뜻이다.
- **`given`에서 Mockito도 `given().willReturn()`으로 쓴다.** `when(...).thenReturn(...)`은 JUnit의 `// when` 구역과 단어가 충돌해 읽는 사람을 혼란시킨다. `org.mockito.BDDMockito.given`을 정적 임포트한다.
- **호출 검증도 BDD 형식으로.** 꼭 필요하다면 `then(taskRepository).should().save(any())`를 쓴다. `verify()`보다 `then().should()`가 문단 구조와 맞는다.
- **`given`이 비어 있으면 주석도 뺀다.** 준비할 것이 없는 순수 함수 테스트에 빈 `// given`을 남기지 않는다.
- **예외를 단언할 때만 `// when & then`으로 합친다.** `assertThatThrownBy(() -> ...)`는 실행과 검증이 한 표현식이라 억지로 나눌 수 없다. 이 경우를 빼면 세 구역을 유지한다.

**메서드 이름은 한글로 `조건_결과`** 형식으로 쓴다. 이름 자체가 문장으로 읽히므로 `@DisplayName`은 기본적으로 붙이지 않는다 — 메서드명만으로 설명이 부족한 경우에만 보탠다.

```java
// 좋음: 이름만 봐도 어떤 인수 기준인지 안다
@Test
void 이미_취소되었으면_완료에_실패한다() { ... }

@Test
void 진행중이면_완료시각이_기록된다() { ... }

// 나쁨: 무엇을 보장하는지 알 수 없다
@Test
void 완료테스트2() { ... }
```

`조건_결과`에서 조건은 given이고 결과는 then이다. 이름을 못 짓겠다면 대개 테스트가 두 가지를 검증하고 있다는 신호다.

## 테스트 피라미드

우리 목표 비율은 **단위 60% / 통합 30% / E2E 10%** 다.

```
          ╱╲
         ╱  ╲         E2E 테스트 (10%)
        ╱    ╲        @SpringBootTest — 실제 HTTP, 실제 DB, 전체 플로
       ╱──────╲
      ╱        ╲      통합 테스트 (30%)
     ╱          ╲     @WebMvcTest, @DataJpaTest — 경계 하나씩
    ╱────────────╲
   ╱              ╲   단위 테스트 (60%)
  ╱                ╲  스프링 컨텍스트 없음 — 엔티티, 값 객체, 도메인 규칙
 ╱──────────────────╲
```

| 층 | 비율 | 대상 | 스프링 컨텍스트 | 속도 |
|---|---|---|---|---|
| **단위** | 60% | 엔티티 상태 전이, 값 객체 검증, 도메인 규칙, 순수 계산 | 없음 — `new`로 만든다 | 밀리초 |
| **통합** | 30% | 컨트롤러 요청·응답 매핑, JPA 매핑과 쿼리, 트랜잭션 경계 | 슬라이스만 | 초 |
| **E2E** | 10% | 핵심 사용자 플로 전체 | 전체 | 분 |

**비율이 뒤집히는 것이 가장 흔한 실패다.** 모든 테스트를 `@SpringBootTest`로 쓰면 스위트가 느려지고, 느려진 스위트는 아무도 돌리지 않는다. 도메인 로직을 엔티티 안으로 밀어 넣을수록 단위 테스트로 덮을 수 있는 면적이 넓어진다 — 피라미드 비율은 테스트 습관의 문제이기 이전에 설계의 결과다.

### 어느 층에 둘 것인가

```
부작용 없는 도메인 로직인가? (상태 전이, 값 객체 검증, 계산)
  → 단위 테스트 — 스프링 없이 그냥 new 해서 테스트한다

경계 하나를 확인하는가? (요청 바인딩·검증, JPA 매핑, 쿼리 결과)
  → 통합 테스트 — @WebMvcTest, @DataJpaTest

여러 계층을 관통하는 핵심 플로인가?
  → E2E — @SpringBootTest, 핵심 경로로만 제한한다
```

같은 것을 두 층에서 반복 검증하지 않는다. 도메인 규칙을 단위 테스트로 이미 덮었다면, E2E에서는 그 규칙이 아니라 **경로가 연결되는지**를 본다.

## 좋은 테스트 쓰기

### 상호작용이 아니라 상태를 테스트한다

내부적으로 어떤 메서드가 호출됐는지가 아니라, 연산의 *결과*를 단언한다. 호출 순서를 검증하는 테스트는 동작이 그대로여도 리팩터링만 하면 깨진다.

```java
// 좋음: 무엇을 하는지 테스트 (상태 기반)
@Test
void 생성일_내림차순을_요청하면_최신순으로_정렬된다() {
    // given
    Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());

    // when
    List<TaskResponse> tasks = taskQuery.list(pageable);

    // then
    assertThat(tasks).isSortedAccordingTo(
        Comparator.comparing(TaskResponse::createdAt).reversed());
}

// 나쁨: 내부적으로 어떻게 동작하는지 테스트 (상호작용 기반)
@Test
void 리포지토리를_정렬조건과_함께_호출한다() {
    taskQuery.list(PageRequest.of(0, 20, Sort.by("createdAt").descending()));

    then(taskRepository).should().findAll(any(Pageable.class));
}
```

`then().should()`는 결과로 확인할 수 없는 것에만 쓴다 — 이메일 발송, 이벤트 발행처럼 부작용이 바깥으로 나가는 경우다.

### 헬퍼 메서드를 만들지 않는다

테스트에서는 DRY보다 **DAMP**(Descriptive And Meaningful Phrases)가 우선이다. 각 테스트는 다른 곳을 찾아보지 않고도 완결된 이야기를 들려줘야 한다. `given` 구역을 헬퍼 메서드로 빼는 순간, 읽는 사람은 무엇이 전제인지 알기 위해 파일을 위아래로 오가야 한다.

```java
// 좋음: given이 눈앞에 있다
@Test
void 제목이_비어있으면_생성에_실패한다() {
    // given
    CreateTaskRequest request = new CreateTaskRequest("", null);

    // when & then
    assertThatThrownBy(() -> taskService.create(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("제목은 필수입니다");
}

@Test
void 제목에_공백이_있으면_앞뒤가_제거된다() {
    // given
    CreateTaskRequest request = new CreateTaskRequest("  장보기  ", null);

    // when
    TaskResponse task = taskService.create(request);

    // then
    assertThat(task.title()).isEqualTo("장보기");
}

// 나쁨: createRequest()가 무엇을 만드는지 보려면 다른 곳으로 가야 한다
@Test
void 제목이_비어있으면_생성에_실패한다() {
    assertThatThrownBy(() -> taskService.create(createRequest("")))
        .isInstanceOf(IllegalArgumentException.class);
}
```

입력 형태를 반복하기 싫다는 이유만으로 헬퍼를 만들지 않는다. 테스트에서의 중복은 각 테스트가 독립적으로 이해되게 만든다면 받아들일 만한 값이다.

예외적으로, 여러 테스트가 공유하는 **불변 상수**(`TASK_ID`, `NOW` 같은 고정값)는 필드로 두어도 좋다. 상수는 시나리오를 감추지 않는다.

### 목보다 실제 구현을 우선한다

일을 해내는 가장 단순한 테스트 대역을 쓴다. 테스트가 실제 코드를 많이 쓸수록 신뢰도가 높다.

```
선호 순서 (높은 것부터):
1. 실제 구현  → 신뢰도 최고, 진짜 버그를 잡는다
2. Fake      → 의존성의 인메모리 구현 (예: 인메모리 리포지토리)
3. Stub      → 미리 정한 값만 반환, 동작 없음
4. Mock      → 호출을 검증 — 아껴 쓴다
```

**목은 이럴 때만 쓴다:** 실제 구현이 너무 느리거나, 비결정적이거나, 통제할 수 없는 부작용이 있을 때(외부 API, 이메일 발송, 결제). 과도한 목은 프로덕션이 깨지는데도 통과하는 테스트를 만든다.

스프링 빈을 목으로 갈아 끼울 때는 `@MockitoBean`을 쓴다 (`@MockBean`은 Spring Boot 3.4에서 폐기되었다).

### 개념 하나당 테스트 하나

```java
// 좋음: 각 테스트가 하나의 인수 기준을 검증한다
@Test void 제목이_비어있으면_생성에_실패한다() { ... }
@Test void 제목에_공백이_있으면_앞뒤가_제거된다() { ... }
@Test void 제목이_최대길이를_넘으면_생성에_실패한다() { ... }

// 나쁨: 하나의 테스트에 전부 — 첫 줄에서 실패하면 나머지는 실행조차 안 된다
@Test
void 제목_검증이_올바르게_동작한다() {
    assertThatThrownBy(() -> taskService.create(new CreateTaskRequest("", null))).isInstanceOf(...);
    assertThat(taskService.create(new CreateTaskRequest("  안녕  ", null)).title()).isEqualTo("안녕");
    assertThatThrownBy(() -> taskService.create(new CreateTaskRequest("a".repeat(256), null))).isInstanceOf(...);
}
```

같은 `when`에 대해 여러 속성을 확인하는 것은 괜찮다 — 위 예시의 `status`와 `completedAt`처럼 하나의 결과를 여러 각도에서 보는 경우다. 나누어야 하는 것은 `when`이 다를 때다.

## 버그 수정: 재현 테스트를 먼저 쓴다

신규 기능은 구현 후에 테스트를 쓰지만, **버그만은 예외다.** 고치기 전에 버그를 재현하는 테스트를 먼저 쓴다. 그래야 수정이 진짜로 듣는지 증명되고, 같은 버그가 다시 나타나지 않는다.

```
버그 리포트 도착
       │
       ▼
  버그를 드러내는 테스트를 작성
       │
       ▼
  테스트 실패 (버그의 존재를 확인)
       │
       ▼
  수정을 구현
       │
       ▼
  테스트 통과 (수정이 동작함을 증명)
       │
       ▼
  ./gradlew test (회귀 없음)
```

**재현 테스트가 실패하는 것을 눈으로 확인하는 단계를 건너뛰지 않는다.** 실패를 보지 않고 넘어가면, 실제로는 버그와 무관한 것을 검증하는 테스트를 쓰고도 "고쳤다"고 믿게 된다.

```java
// 버그: "할 일을 완료해도 completedAt 타임스탬프가 갱신되지 않는다"

// 1단계: 재현 테스트를 쓴다 (반드시 실패해야 한다)
@Test
void 진행중이면_완료시각이_기록된다() {
    // given
    Task task = Task.of("테스트", null);

    // when
    task.complete(NOW);

    // then
    assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(task.completedAt()).isEqualTo(NOW);  // 여기서 실패 → 버그 확인
}

// 2단계: 버그를 고친다 — 엔티티가 자기 상태 전이를 책임진다
public class Task {

    public void complete(Instant now) {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = now;  // 이게 빠져 있었다
    }
}

// 3단계: 테스트 통과 → 버그 수정 완료, 회귀 방지 확보
```

## 피해야 할 테스트 안티패턴

| 안티패턴 | 문제 | 해결 |
|---|---|---|
| given을 헬퍼로 빼기 | 무엇이 전제인지 보려면 파일을 오가야 하고, BDD의 목적이 무너진다 | 각 테스트 안에 given을 직접 쓴다 |
| when이 여러 줄 | 무엇을 검증하는 테스트인지 모호해지고, 실패해도 원인이 안 보인다 | 테스트를 나눈다 |
| 구현 세부를 테스트 | 동작이 그대로여도 리팩터링하면 깨진다 | 내부 구조가 아니라 입력과 출력을 테스트한다 |
| 전부 `@SpringBootTest` | 스위트가 느려지고, 느린 스위트는 아무도 안 돌린다 | 도메인 로직을 엔티티로 밀어 넣고 단위 테스트로 덮는다 |
| 전부 목으로 대체 | 테스트는 통과하는데 프로덕션이 깨진다 | 실제 구현 > fake > stub > mock 순으로 선호한다 |
| 불안정한 테스트 (타이밍·순서 의존) | 테스트 스위트에 대한 신뢰가 무너진다 | 시각은 주입받고, 각 테스트가 자기 상태를 준비한다 |
| 테스트 격리 없음 | 개별로는 통과하는데 함께 돌리면 실패한다 | 공유 상태를 두지 않는다 |
| 프레임워크 코드를 테스트 | 서드파티 동작을 테스트하느라 시간을 버린다 | 우리 코드만 테스트한다 |

## 흔한 자기합리화

| 자기합리화 | 실제로는 |
|---|---|
| "구현이 끝났으니 테스트는 나중에" | 태스크의 완료 조건에 테스트가 들어 있다. 테스트 없는 구현은 완료가 아니라 절반이다. |
| "이건 너무 단순해서 테스트할 게 없어" | 단순한 코드는 복잡해진다. 인수 기준이 있다면 테스트도 있어야 한다. |
| "수동으로 호출해 봤어" | 수동 확인은 남지 않는다. 내일의 변경이 그것을 깨뜨려도 알 방법이 없다. |
| "given이 길어서 헬퍼로 뺐어" | given이 길다는 건 대상 객체가 너무 많은 것을 요구한다는 신호다. 헬퍼로 가리지 말고 설계를 본다. |
| "`@SpringBootTest` 하나면 다 덮이잖아" | 다 덮이지만 아무것도 빨리 알려 주지 않는다. 실패 지점을 좁혀 주는 것이 테스트의 값이다. |
| "인수 기준에 없지만 이것도 테스트해 두자" | 명세에 없는 동작을 테스트로 고정하면, 그 동작이 계약이 된다. 필요하면 명세를 먼저 고친다. |
| "확실히 하려고 테스트를 한 번 더 돌려 보자" | 깨끗하게 통과한 뒤 같은 명령을 반복해 봐야, 그사이 코드가 바뀌지 않았다면 얻는 게 없다. |

## 위험 신호

- 인수 기준에 대응하는 테스트가 없다
- 명세에 없는 시나리오를 테스트가 검증하고 있다 (요구사항을 발명한 것이다)
- `// given` `// when` `// then` 구분이 없는 테스트
- `when` 구역이 여러 줄이다
- given을 만드는 헬퍼 메서드가 있다
- 단위 테스트로 충분한 것을 `@SpringBootTest`로 쓴다
- 실제로는 테스트를 돌리지도 않고 "모든 테스트 통과"라고 말한다
- 재현 테스트 없는 버그 수정, 또는 재현 테스트가 실패하는 것을 확인하지 않은 버그 수정
- 스위트를 통과시키려고 테스트를 건너뛰거나 비활성화한다

## 검증

구현을 마친 뒤:

- [ ] 태스크의 인수 기준마다 대응하는 테스트가 있다
- [ ] 모든 테스트가 `// given` `// when` `// then` 구조를 따른다
- [ ] 테스트 이름이 한글 `조건_결과` 형식이고 문장으로 읽힌다
- [ ] 테스트가 적절한 층에 있다 (단위로 충분한 것을 통합·E2E로 쓰지 않았다)
- [ ] given을 만드는 헬퍼 메서드가 없다
- [ ] `./gradlew test`로 전체 스위트가 통과한다
- [ ] 버그 수정에는 수정 전에 실패했던 재현 테스트가 포함되어 있다
- [ ] 건너뛰거나 비활성화된 테스트가 없다

**참고:** 각 테스트 명령은 결과에 영향을 줄 수 있는 변경 이후에 돌린다. 깨끗하게 통과한 뒤에는 코드가 바뀌지 않았다면 같은 명령을 반복하지 않는다.

## 함께 보기

이 원칙들을 보여 주는 구체적인 테스트 패턴 — JUnit 6, AssertJ, BDDMockito, `@WebMvcTest`·`@DataJpaTest` 슬라이스 테스트, `@SpringBootTest` — 은 `../../references/testing-patterns.md`를 참고한다.
