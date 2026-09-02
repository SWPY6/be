# 테스트 패턴 참조 (Java · Spring Boot 4 · JUnit 6)

`test-driven-development` 스킬이 **규율**을 정하고, 이 문서가 그 규율의 **실물**을 보인다. 왜 이렇게 쓰는지가 궁금하면 스킬을, 어떻게 쓰는지가 궁금하면 이 문서를 본다.

이 저장소의 테스트 스택:

| 구성 | 버전 | 비고 |
|---|---|---|
| JUnit Jupiter | 6.0.3 | Spring Boot 4.1.1이 관리. 패키지는 여전히 `org.junit.jupiter.api` |
| AssertJ | 3.27.7 | 모든 단언은 `assertThat`으로 |
| Mockito | 5.23.0 | Mockito 6은 없다. JUnit 6과 정상 동작 |
| Spring Test | 7.0.9 | `MockMvcTester`, `RestTestClient` |

## Spring Boot 4에서 바뀐 임포트 경로

**Spring Boot 3의 경로를 그대로 쓰면 컴파일되지 않는다.** 슬라이스 애노테이션이 모듈별로 흩어졌다.

| 애노테이션 | Spring Boot 3 (옛것) | **Spring Boot 4 (현재)** |
|---|---|---|
| `@WebMvcTest` | `...boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `@DataJpaTest` | `...boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| `@SpringBootTest` | `org.springframework.boot.test.context` | 그대로 |
| `@MockitoBean` | — | `org.springframework.test.context.bean.override.mockito.MockitoBean` |

`@MockBean`은 폐기되었다. 스프링 빈을 목으로 갈아 끼울 때는 `@MockitoBean`을 쓴다.

> **참고:** 이 저장소에는 아직 `spring-boot-starter-data-jpa`가 없다. 아래 `@DataJpaTest` 절은 JPA를 추가한 뒤에 적용된다.

## 기본형: Given-When-Then

모든 층에서 이 형태를 반복한다. 메서드명은 한글 `조건_결과` 형식으로 쓰고, 이름 자체가 문장으로 읽히므로 `@DisplayName`은 붙이지 않는다.

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

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
        TaskResponse response = taskService.complete(TASK_ID, NOW);

        // then
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(NOW);
    }
}
```

## 단위 테스트 (60%)

**스프링 컨텍스트를 띄우지 않는다.** `new`로 만들어 바로 호출한다. 가장 빠르고 가장 많아야 하는 층이다.

도메인 로직이 엔티티 안에 있을수록 이 층으로 덮을 면적이 넓어진다. 피라미드 비율은 습관이 아니라 설계의 결과다.

```java
class TaskTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void 진행중이면_상태와_완료시각이_바뀐다() {
        // given
        Task task = Task.of("장보기", null);

        // when
        task.complete(NOW);

        // then
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.completedAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_취소되었으면_완료에_실패한다() {
        // given
        Task task = Task.of("장보기", null);
        task.cancel("필요 없어짐", NOW);

        // when & then
        assertThatThrownBy(() -> task.complete(NOW))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("취소된 할 일은 완료할 수 없습니다");
    }
}
```

### 시각은 주입받는다

`Instant.now()`를 도메인 안에서 부르면 테스트가 시간에 의존해 불안정해진다. 시각은 **인자로 받거나 `Clock`으로 주입**한다.

```java
// 나쁨: 테스트가 실행 시각에 좌우되고, 정확한 단언이 불가능하다
public void complete() {
    this.completedAt = Instant.now();
}

// 좋음: 테스트가 시각을 고정할 수 있다
public void complete(Instant now) {
    this.completedAt = now;
}
```

값 객체는 생성 자체가 검증이므로 그것을 테스트한다.

```java
@Test
void 값이_null이면_TaskId_생성에_실패한다() {
    // when & then
    assertThatThrownBy(() -> new TaskId(null))
        .isInstanceOf(NullPointerException.class);
}
```

## 통합 테스트 (30%)

경계를 **하나씩** 확인한다. 여러 경계를 한 번에 보고 싶어지면 그건 E2E다.

### `@WebMvcTest` — 컨트롤러 슬라이스

웹 계층만 띄우고 서비스는 `@MockitoBean`으로 대체한다. 요청 바인딩, 검증, 상태 코드, 응답 본문을 본다.

`MockMvcTester`는 AssertJ 기반이라 `assertThat`으로 바로 단언한다.

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void 유효한_요청이면_201과_생성된_할일을_반환한다() {
        // given
        given(taskService.create(any())).willReturn(
            new TaskResponse(new TaskId("task-1"), "장보기", TaskStatus.PENDING, null));

        // when & then
        assertThat(mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "장보기"}
                        """))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$.title").isEqualTo("장보기");
    }

    @Test
    void 제목이_비어있으면_400을_반환한다() {
        // when & then
        assertThat(mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": ""}
                        """))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
```

검증 실패 테스트에서는 `given`이 필요 없다 — 요청이 서비스까지 닿지 않기 때문이다. 이럴 때 빈 `// given` 주석을 남기지 않는다.

### `@DataJpaTest` — 리포지토리 슬라이스

JPA 계층만 띄운다. 쿼리 메서드가 의도대로 조회하는지, 제약이 실제로 걸리는지 본다. 기본적으로 각 테스트가 끝나면 롤백된다.

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void 상태로_거르면_진행중인_할일만_조회된다() {
        // given
        em.persist(Task.of("장보기", null));
        Task inProgress = Task.of("보고서 작성", null);
        inProgress.start("김정훈", NOW);
        em.persist(inProgress);
        em.flush();

        // when
        List<Task> found = taskRepository.findByStatus(TaskStatus.IN_PROGRESS);

        // then
        assertThat(found)
            .hasSize(1)
            .extracting(Task::title)
            .containsExactly("보고서 작성");
    }
}
```

**엔티티 매핑 자체를 테스트하지 않는다.** `save` 후 `findById`가 같은 값을 준다는 테스트는 JPA를 테스트하는 것이지 우리 코드를 테스트하는 게 아니다. 우리가 작성한 쿼리와 제약만 본다.

## E2E (10%)

전체 컨텍스트를 띄우고 실제 HTTP로 호출한다. **핵심 경로로만 제한한다** — 느리고, 실패해도 원인을 좁혀 주지 않는다.

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TaskFlowTest {

    @Autowired
    private RestTestClient client;

    @Test
    void 생성_API를_거치면_목록에서_조회된다() {
        // given
        client.post().uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                    {"title": "장보기"}
                    """)
            .exchange()
            .expectStatus().isCreated();

        // when & then
        client.get().uri("/api/tasks")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data[0].title").isEqualTo("장보기");
    }
}
```

단위 테스트로 이미 덮은 도메인 규칙을 여기서 반복하지 않는다. E2E가 보는 것은 **경로가 연결되는지**다.

## BDDMockito

`when(...).thenReturn(...)` 대신 `given(...).willReturn(...)`을 쓴다. `// when` 주석 구역과 단어가 겹치지 않아 읽기 쉽다.

```java
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

// 값 반환
given(taskRepository.findById(TASK_ID)).willReturn(Optional.of(task));

// 예외 발생
willThrow(new DataIntegrityViolationException("중복")).given(taskRepository).save(any());

// 호출 검증 — 결과로 확인할 수 없는 부작용에만 쓴다
then(notificationSender).should().send(any(Notification.class));
then(notificationSender).should(never()).send(any());
```

### 인자를 붙잡아 확인할 때

무엇을 넘겼는지가 인수 기준의 일부라면 `ArgumentCaptor`로 잡는다.

```java
@Test
void 담당자가_지정되면_알림이_발송된다() {
    // given
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

    // when
    taskService.create(new CreateTaskRequest("장보기", "김정훈"));

    // then
    then(notificationSender).should().send(captor.capture());
    assertThat(captor.getValue().recipient()).isEqualTo("김정훈");
}
```

`@Mock`으로 선언한 스텁을 테스트가 쓰지 않으면 `MockitoExtension`이 `UnnecessaryStubbingException`으로 실패시킨다. 이건 버그를 알려 주는 것이므로 `@MockitoSettings(strictness = LENIENT)`로 덮지 말고, 쓰지 않는 스텁을 지운다.

## AssertJ 자주 쓰는 단언

```java
// 값
assertThat(task.title()).isEqualTo("장보기");
assertThat(task.completedAt()).isNotNull();
assertThat(task.isOverdue()).isTrue();

// 컬렉션 — 필드를 뽑아 비교하면 실패 메시지가 읽기 쉽다
assertThat(tasks).hasSize(3);
assertThat(tasks).extracting(TaskResponse::title).containsExactly("장보기", "보고서", "회의");
assertThat(tasks).extracting(TaskResponse::title).containsExactlyInAnyOrder("보고서", "장보기", "회의");
assertThat(tasks).allMatch(t -> t.status() == TaskStatus.PENDING);
assertThat(tasks).isSortedAccordingTo(Comparator.comparing(TaskResponse::createdAt).reversed());

// 예외
assertThatThrownBy(() -> taskService.complete(UNKNOWN_ID, NOW))
    .isInstanceOf(TaskNotFoundException.class)
    .hasMessageContaining("task-999");

// 여러 필드를 한 번에 — 하나가 실패해도 나머지를 모두 보고한다
assertThat(response)
    .extracting(TaskResponse::title, TaskResponse::status)
    .containsExactly("장보기", TaskStatus.PENDING);
```

`assertThat(...).extracting(...)`은 실패했을 때 "무엇이 달랐는지"를 보여 준다. `assertThat(a.equals(b)).isTrue()`는 `false`라는 사실만 알려 준다 — 후자를 쓰지 않는다.

## 자주 하는 실수

| 실수 | 왜 문제인가 | 대신 |
|---|---|---|
| `given`을 헬퍼 메서드로 추출 | 전제를 보려면 파일을 오가야 한다. BDD의 목적이 무너진다 | 각 테스트 안에 직접 쓴다. 불변 상수만 필드로 |
| 도메인 안에서 `Instant.now()` | 시각을 고정할 수 없어 단언이 느슨해지고 불안정해진다 | 인자나 `Clock`으로 주입 |
| 전부 `@SpringBootTest` | 느려지고, 실패 원인을 좁혀 주지 않는다 | 단위로 되는 것은 단위로 |
| `@MockBean` 사용 | Spring Boot 3.4에서 폐기 | `@MockitoBean` |
| Spring Boot 3 임포트 경로 | 컴파일되지 않는다 | 위의 경로 표 참고 |
| `save` 후 `findById` 테스트 | JPA를 테스트하는 것이지 우리 코드가 아니다 | 우리가 쓴 쿼리와 제약만 |
| `assertThat(x.equals(y)).isTrue()` | 실패해도 무엇이 달랐는지 안 나온다 | `assertThat(x).isEqualTo(y)` |
| `@MockitoSettings(LENIENT)`로 경고 덮기 | 쓰지 않는 스텁은 대개 테스트가 의도와 다르다는 신호 | 스텁을 지운다 |
