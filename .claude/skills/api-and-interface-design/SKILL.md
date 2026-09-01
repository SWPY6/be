---
name: api-and-interface-design
description: 안정적인 API·인터페이스 설계를 안내한다. API, 모듈 경계(module boundaries), 또는 공개 인터페이스(public interface)를 설계할 때 사용한다. REST 엔드포인트를 만들 때, 모듈 간 타입 계약(type contracts)이나 DTO를 정의할 때, 계층·모듈 사이의 경계를 세울 때 사용한다.
---

# API·인터페이스 설계 (API and Interface Design)

## 개요

잘못 쓰기 어려운, 안정적이고 잘 문서화된 인터페이스를 설계한다. 좋은 인터페이스는 옳은 일을 쉽게, 그른 일을 어렵게 만든다. 이는 REST API, 요청·응답 DTO, 서비스 인터페이스, 모듈 경계 등 코드 조각끼리 대화하는 모든 표면에 적용된다.

## 언제 사용하는가

- 새 API 엔드포인트를 설계할 때
- 모듈 경계나 팀 간 계약을 정의할 때
- 요청·응답 DTO나 서비스 인터페이스를 만들 때
- API 형태에 영향을 주는 데이터베이스 스키마를 세울 때
- 기존 공개 인터페이스를 바꿀 때

## 핵심 원칙

### 하이럼의 법칙 (Hyrum's Law)

> API 사용자가 충분히 많아지면, 계약서에 무엇을 약속했든 시스템의 관찰 가능한 모든 동작에 누군가는 의존하게 된다.

즉, 문서화되지 않은 특이 동작, 에러 메시지 문구, 타이밍, 순서까지 포함해 모든 공개 동작은 사용자가 의존하는 순간 사실상의 계약이 된다. 설계상의 함의:

- **무엇을 노출할지 의도적으로 정한다.** 관찰 가능한 모든 동작이 잠재적 약속이다.
- **구현 세부를 흘리지 않는다.** 사용자가 관찰할 수 있으면 반드시 의존하게 된다.
- **폐기(deprecation)를 설계 시점에 계획한다.** 무언가를 내놓을 때 그것을 어떻게 거둬들일지도 함께 정한다 — 대체재를 먼저 제공하고, `@Deprecated`로 표시해 소비자가 옮겨 갈 기간을 준 뒤, 마지막에 제거한다.
- **테스트만으로는 부족하다.** 완벽한 계약 테스트가 있어도, 하이럼의 법칙 때문에 "안전한" 변경이 문서화되지 않은 동작에 의존하는 실제 사용자를 깨뜨릴 수 있다.

### 단일 버전 규칙 (One-Version Rule)

같은 의존성이나 API의 여러 버전 중에서 소비자가 골라야 하는 상황을 만들지 않는다. 서로 다른 소비자가 같은 것의 서로 다른 버전을 필요로 할 때 다이아몬드 의존성 문제가 생긴다. 한 시점에 하나의 버전만 존재하는 세계를 전제로 설계한다 — 분기시키지 말고 확장한다.

### 1. 계약 우선

구현하기 전에 인터페이스를 정의한다. 계약이 곧 명세이고, 구현이 그 뒤를 따른다.

```java
// 계약을 먼저 정의한다
public interface TaskApi {

    // 할 일을 만들고, 서버가 채운 필드를 포함해 생성된 할 일을 반환한다
    TaskResponse create(CreateTaskRequest request);

    // 필터에 맞는 할 일을 페이지 단위로 반환한다
    Page<TaskResponse> list(TaskSearchCondition condition, Pageable pageable);

    // 할 일 하나를 반환하거나 TaskNotFoundException을 던진다
    TaskResponse get(TaskId id);

    // 부분 갱신 — 제공된 필드만 바뀐다
    TaskResponse update(TaskId id, UpdateTaskRequest request);

    // 멱등 삭제 — 이미 삭제됐어도 성공한다
    void delete(TaskId id);
}
```

### 2. 일관된 에러 의미론

에러 전략을 하나 골라 어디서나 그것만 쓴다:

```java
// REST: HTTP 상태 코드 + 구조화된 에러 본문
// 모든 에러 응답이 같은 형태를 따른다
public record ErrorResponse(
    String code,                 // 기계가 읽는 코드: "VALIDATION_ERROR"
    String message,              // 사람이 읽는 문구: "이메일은 필수입니다"
    Map<String, String> details  // 도움이 될 때의 추가 맥락, 없으면 빈 맵
) {
}

// 한 곳에서만 예외를 응답으로 옮긴다
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("TASK_NOT_FOUND", e.getMessage(), Map.of()));
    }
}

// 상태 코드 매핑
// 400 → 클라이언트가 잘못된 데이터를 보냄
// 401 → 인증되지 않음
// 403 → 인증됐지만 권한 없음
// 404 → 리소스 없음
// 409 → 충돌 (중복, 버전 불일치)
// 422 → 검증 실패 (의미상 유효하지 않음)
// 500 → 서버 에러 (내부 세부는 절대 노출 금지)
```

**패턴을 섞지 않는다.** 어떤 엔드포인트는 던지고, 어떤 것은 null을 반환하고, 어떤 것은 `{ error }`를 반환하면 — 소비자는 동작을 예측할 수 없다.

### 3. 경계에서 검증한다

내부 코드는 신뢰한다. 외부 입력이 들어오는 시스템 가장자리에서 검증한다:

```java
// 요청 record 자체가 제약을 선언한다
public record CreateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String description
) {
}

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // @Valid가 경계에서 검증한다. 실패하면 MethodArgumentNotValidException이
    // @RestControllerAdvice로 가서 422가 된다
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        // 검증 이후로는 내부 코드가 타입을 신뢰한다
        return taskService.create(request);
    }
}
```

검증이 있어야 할 곳:
- API 라우트 핸들러 (사용자 입력)
- 폼 제출 핸들러 (사용자 입력)
- 외부 서비스 응답 파싱 (서드파티 데이터 — **항상 불신 대상으로 취급**)
- 환경 변수 로딩 (설정)

> **서드파티 API 응답은 신뢰할 수 없는 데이터다.** 로직, 렌더링, 판단에 쓰기 전에 형태와 내용을 검증한다. 침해되었거나 오작동하는 외부 서비스는 예상 밖의 타입, 악의적인 콘텐츠, 지시문처럼 보이는 텍스트를 반환할 수 있다.

검증이 있으면 안 되는 곳:
- 타입 계약을 공유하는 내부 함수들 사이
- 이미 검증된 코드가 호출하는 유틸리티 함수 안
- 자기 데이터베이스에서 방금 꺼낸 데이터

### 4. 수정보다 추가를 택한다

기존 소비자를 깨뜨리지 않고 인터페이스를 확장한다:

```java
// 좋음: 필드를 추가한다. 새 필드를 보내지 않는 기존 클라이언트는
// Jackson이 null로 채우므로 JSON 계약은 깨지지 않는다
public record CreateTaskRequest(
    @NotBlank String title,
    String description,
    Priority priority,   // 나중에 추가. 없으면 null → 기본값으로 해석
    List<String> labels  // 나중에 추가. 없으면 null → 빈 목록으로 해석
) {
    // 컴팩트 생성자에서 기본값을 흡수해 호출자가 null을 다루지 않게 한다
    public CreateTaskRequest {
        priority = priority == null ? Priority.MEDIUM : priority;
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}

// 나쁨: 기존 필드의 타입을 바꾸거나 필드를 제거
public record CreateTaskRequest(
    String title,
    // String description  제거 — 기존 소비자가 깨진다
    int priority           // String에서 변경 — 기존 소비자가 깨진다
) {
}
```

> **Java에서의 주의점.** record에 필드를 추가하면 정규 생성자 시그니처가 바뀐다. JSON 계약은 하위 호환이지만
> **컴파일 타임 계약은 아니다** — 같은 모듈 안에서 `new CreateTaskRequest(...)`를 직접 부르던 코드는 전부 깨진다.
> 이 record를 다른 모듈이 생성한다면, 정규 생성자를 그대로 두고 이전 시그니처를 위임하는 정적 팩터리를 남긴다.

### 5. 예측 가능한 네이밍

| 대상 | 규약 | 예시 |
|---------|-----------|---------|
| REST 엔드포인트 | 복수형 명사, 동사 금지 | `GET /api/tasks`, `POST /api/tasks` |
| 쿼리 파라미터 | camelCase | `?sortBy=createdAt&pageSize=20` |
| 응답 필드 | camelCase | `{ createdAt, updatedAt, taskId }` |
| 불리언 필드 | is/has/can 접두사 | `isComplete`, `hasAttachments` |
| Enum 값 | UPPER_SNAKE | `"IN_PROGRESS"`, `"COMPLETED"` |

### 6. 멱등성 키를 실제로 지키기

`Idempotency-Key`를 받아들이는 것은 계약이다. 그것을 지키는 것은 구현이고, 돈이 새는 지점도 거기다 — 서버가 받아 놓고 부주의하게 다루는 키는 아예 없느니만 못하다. 클라이언트가 재시도해도 안전하다고 믿게 되기 때문이다.

**키는 시도가 아니라 의도에서 파생한다.** 키는 하나의 의도를 재시도할 때는 동일해야 하고, 서로 다른 의도끼리는 달라야 한다:

```java
UUID.randomUUID().toString()                  // ✗ 시도마다 새 키 — 재시도가 곧 새 결제
userId + ":" + amount                         // ✗ 정당한 5만원 결제 두 건이 하나로 뭉개진다
orderId + ":" + System.currentTimeMillis()    // ✗ 타임스탬프는 모자만 쓴 randomUUID()다

request.getHeader("Idempotency-Key")          // ✓ 클라이언트가 한 번 만들고 재시도 때 재사용
"charge:v1:" + orderId                        // ✓ 불변 식별자에서 파생
```

키는 클라이언트나 최초 이벤트에서 나온다 — 재시도를 수행하는 계층에서 나오면 안 된다.

**원자적으로 선점한다. 확인하고 나서 행동하는 것은 경쟁 상태다:**

```java
// ✗ TOCTOU: 동시에 온 재시도 두 건이 모두 "본 적 없음"을 읽고, 둘 다 결제한다
if (!repository.existsByKey(key)) {
    paymentGateway.charge(amount);
    repository.save(new IdempotencyRecord(key));
}

// ✓ 유니크 제약이 승자를 고르게 한다
//   idempotency_record.key 컬럼에 UNIQUE 인덱스가 있어야 성립한다
try {
    repository.saveAndFlush(IdempotencyRecord.inProgress(key, requestHash));
} catch (DataIntegrityViolationException e) {
    return replayOrReject(key);
}

ChargeResult result = paymentGateway.charge(amount);
repository.save(record.succeeded(result));
```

유니크 제약 *자체가* 메커니즘이다. 한 번의 연산으로 유일성을 강제하지 못하는 저장소는 이것을 뒷받침할 수 없다.

**페이로드를 지킨다.** 같은 키에 다른 본문이 오는 것은 클라이언트 버그이며, 두 번째 요청에 첫 번째 응답을 내주는 대신 요란하게 실패해야 한다:

```java
if (!existing.requestHash().equals(hash(request))) {
    throw new IdempotencyConflictException(
        "같은 멱등성 키가 다른 페이로드로 재사용되었습니다");  // → 422
}
```

**처리 중인 중복에 무엇을 줄지 정한다.** 두 번째 요청이 도착했을 때 첫 번째가 아직 실행 중인 상황 — 재시도 폭주 시 흔한 경우다:

| 전략 | 응답 | 언제 쓰는가 |
|---|---|---|
| 거부 | `409 Conflict` | 클라이언트가 나중에 재시도할 수 있을 때. 가장 단순하고 안전 |
| 대기 | 상한을 두고 결과까지 블로킹 | 호출자가 동기적으로 결과를 필요로 할 때 |
| 진행 중 반환 | `202` + 상태 URL | 오래 걸리는 부작용일 때 |

첫 번째가 "멈춘 것 같다"는 이유로 두 번째 호출자를 통과시키지 않는다. 운명을 알 수 없는 멈춘 시도야말로 중복 비용이 가장 큰 순간이다.

**모든 호출의 결과는 둘이 아니라 셋이다: 성공, 실패, 그리고 _알 수 없음_.** 타임아웃은 부작용이 적용됐는지 아무것도 알려 주지 않는다. 외부를 호출하기 *전에* 의도를 기록해서, 호출과 응답 사이에 크래시가 나도 나중에 정리해야 할 무언가의 흔적이 남게 한다 — 말없이 재시도된 결제가 아니라.

**보존 기간은 디스크 비용이 아니라 가장 긴 재시도 사슬에 맞춘다.** 키는 같은 의도를 다시 전달할 수 있는 모든 경로보다 오래 살아야 한다. 일주일 뒤 재생되는 데드레터 큐와 결제사의 이의 제기 기간까지 포함해서다. 7일짜리 DLQ 뒤에 24시간짜리 키 TTL은 예정된 중복이다.

## REST API 패턴

### 리소스 설계

```
GET    /api/tasks              → 할 일 목록 (필터링은 쿼리 파라미터로)
POST   /api/tasks              → 할 일 생성
GET    /api/tasks/:id          → 할 일 하나 조회
PATCH  /api/tasks/:id          → 할 일 수정 (부분)
DELETE /api/tasks/:id          → 할 일 삭제

GET    /api/tasks/:id/comments → 할 일의 댓글 목록 (하위 리소스)
POST   /api/tasks/:id/comments → 할 일에 댓글 추가
```

### 페이지네이션

목록 엔드포인트에는 페이지네이션을 넣는다:

```
# 요청 — Spring의 Pageable이 page/size/sort를 그대로 바인딩한다
GET /api/tasks?page=0&size=20&sort=createdAt,desc

# 응답
{
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalItems": 142,
    "totalPages": 8
  }
}
```

### 필터링

필터에는 쿼리 파라미터를 쓴다:

```
GET /api/tasks?status=in_progress&assignee=user123&createdAfter=2025-01-01
```

### 부분 갱신 (PATCH)

부분 객체를 받는다 — 제공된 것만 갱신한다:

```
# title만 바뀌고 나머지는 그대로 보존된다
PATCH /api/tasks/123
{ "title": "제목 수정" }
```

## Java 인터페이스 패턴

### 변형에는 sealed interface를 쓴다

상태에 따라 필드의 유무가 갈린다면, nullable 필드를 한 타입에 몰아넣지 말고 변형마다 별도 타입을 둔다.

```java
// 좋음: 각 변형이 명시적이다
public sealed interface TaskStatus {

    record Pending() implements TaskStatus {
    }

    record InProgress(
        String assignee,
        Instant startedAt
    ) implements TaskStatus {
    }

    record Completed(
        Instant completedAt,
        String completedBy
    ) implements TaskStatus {
    }

    record Cancelled(
        String reason,
        Instant cancelledAt
    ) implements TaskStatus {
    }
}

// 소비자는 망라성 검사를 얻는다 — 변형을 추가하고 case를 빠뜨리면 컴파일 에러다
public String label(TaskStatus status) {
    return switch (status) {
        case Pending ignored -> "대기 중";
        case InProgress s -> "진행 중 (" + s.assignee() + ")";
        case Completed c -> c.completedAt() + " 완료";
        case Cancelled c -> "취소됨: " + c.reason();
    };
}
```

`default` 분기를 넣지 않는다. `default`를 넣는 순간 망라성 검사가 꺼지고, 새 변형을 추가해도 컴파일러가 아무 말을 하지 않는다.

### 입력과 출력을 분리한다

```java
// 입력: 호출자가 제공하는 것
public record CreateTaskRequest(
    @NotBlank String title,
    String description
) {
}

// 출력: 시스템이 반환하는 것 (서버가 생성한 필드 포함)
public record TaskResponse(
    TaskId id,
    String title,
    String description,
    Instant createdAt,
    Instant updatedAt,
    String createdBy
) {
}
```

엔티티를 그대로 응답으로 내보내지 않는다. 엔티티를 내보내면 컬럼 하나를 추가할 때마다 API 계약이 말없이 넓어지고,
하이럼의 법칙에 따라 그 컬럼에 누군가 의존하게 된다.

### id에는 값 객체를 쓴다

```java
public record TaskId(String value) {

    public TaskId {
        Objects.requireNonNull(value, "TaskId는 null일 수 없습니다");
    }
}

public record UserId(String value) {

    public UserId {
        Objects.requireNonNull(value, "UserId는 null일 수 없습니다");
    }
}

// TaskId 자리에 실수로 UserId를 넘기는 것을 막는다
public TaskResponse get(TaskId id) { ... }
```

`String id`를 그대로 넘기면 `getTask(userId)`가 조용히 컴파일된다. 별도 record로 감싸면 그 실수가 컴파일 에러가 된다.

**단, 경계에서 새는 것에 주의한다.** `taskId.value()`를 꺼내는 순간 다시 그냥 `String`이므로, 값 객체는 도메인 안쪽까지
끌고 들어가야 값을 한다. 컨트롤러에서 즉시 풀어 버리면 감싼 의미가 없다.

## 흔한 자기합리화

| 자기합리화 | 실제로는 |
|---|---|
| "API 문서는 나중에 쓸게" | 타입이 곧 문서다. 그것부터 정의해라. |
| "지금은 페이지네이션 필요 없어" | 누군가 항목이 100개를 넘는 순간 필요해진다. 처음부터 넣어라. |
| "PATCH는 복잡하니 그냥 PUT 쓰자" | PUT은 매번 전체 객체를 요구한다. 클라이언트가 실제로 원하는 건 PATCH다. |
| "필요해지면 그때 API 버전을 나누지" | 버저닝 없는 파괴적 변경은 소비자를 깨뜨린다. 처음부터 확장을 전제로 설계해라. |
| "그 문서화 안 된 동작은 아무도 안 써" | 하이럼의 법칙: 관찰 가능하면 누군가는 의존한다. 모든 공개 동작을 약속으로 취급해라. |
| "그냥 두 버전 유지하면 되잖아" | 버전이 늘면 유지 비용이 곱해지고 다이아몬드 의존성 문제가 생긴다. 단일 버전 규칙을 택해라. |
| "내부 API에는 계약이 필요 없어" | 내부 소비자도 소비자다. 계약이 결합을 막고 병렬 작업을 가능하게 한다. |
| "Idempotency-Key 헤더를 받는 걸로 충분해" | 헤더는 계약이고, 키를 결과와 함께 저장하는 것이 구현이다. 받아 놓고 지키지 않는 키는 안전하지 않은데도 재시도해도 된다고 클라이언트에게 말하는 셈이다. |
| "우리 큐는 정확히 한 번 전달을 보장해" | 컨슈머가 죽는 상황에서 그걸 보장하는 큐는 없다 — 브로커의 ack와 당신의 부작용은 한 트랜잭션 안에 있지 않다. 멱등 처리를 전제로 최소 한 번 전달로 설계해라. |
| "중복 요청은 드물어" | 중복은 *상관되어* 발생한다. 재시도는 의존 서비스가 나빠질 때 정확히 몰리며, 그때가 중복 가능성도 비용도 가장 큰 순간이다. |

## 위험 신호

- 조건에 따라 다른 형태를 반환하는 엔드포인트
- 엔드포인트마다 에러 포맷이 다르다
- 검증이 경계가 아니라 내부 코드 곳곳에 흩어져 있다
- 기존 필드에 대한 파괴적 변경 (타입 변경, 제거)
- 페이지네이션 없는 목록 엔드포인트
- REST URL에 들어간 동사 (`/api/createTask`, `/api/getUsers`)
- 검증이나 정제 없이 사용되는 서드파티 API 응답
- 멱등성 키를 `SELECT`한 다음 `INSERT`하는 코드 — 그건 방어가 아니라 경쟁 상태다
- UUID, 타임스탬프 등 시도마다 새로 생성되는 것에서 파생된 멱등성 키
- 같은 키에 다른 요청 본문이 왔는데 말없이 첫 응답을 반환한다
- 요청을 다시 전달할 수 있는 가장 긴 경로보다 짧은 키 보존 기간

## 검증

API를 설계한 뒤:

- [ ] 모든 엔드포인트에 타입이 붙은 입력·출력 스키마가 있다
- [ ] 에러 응답이 하나의 일관된 포맷을 따른다
- [ ] 검증이 시스템 경계에서만 일어난다
- [ ] 목록 엔드포인트가 페이지네이션을 지원한다
- [ ] 새 필드는 추가형이고 선택적이다 (하위 호환)
- [ ] 네이밍이 모든 엔드포인트에서 일관된 규약을 따른다
- [ ] API 문서 또는 타입이 구현과 함께 커밋되었다
- [ ] 상태를 바꾸는 엔드포인트는 멱등성 키를 지키거나, 재시도가 안전하지 않음이 문서화되어 있다
- [ ] 키가 유니크 제약으로 보호되는 단일 원자 연산으로 선점된다
- [ ] 다른 페이로드로 재사용된 키는 잘못된 응답을 재생하지 않고 요란하게 실패한다
- [ ] 처리 중인 중복에 대한 응답이 (409, 대기, 202 중) 의도적으로 선택된 것이지 어쩌다 나온 결과가 아니다
- [ ] 키 보존 기간이 데드레터 재생을 포함한 가장 긴 재시도 경로보다 길다
