# ploutos

Spring Boot 4.1.1 / Java 26 기반 백엔드.

## 요구사항

- JDK 26
- Docker (테스트와 로컬 실행 모두 필요)

## 처음 세팅

`docker-compose.yml`이 개발용 DB 자격증명을 환경변수로 읽는다.
프로젝트 루트에 `.env`를 만든다. (`.gitignore`에 등록되어 커밋되지 않는다)

```
DB_NAME=<이름>
DB_USER=<이름>
DB_PASSWORD=<비밀번호>
DB_ROOT_PASSWORD=<루트 비밀번호>
```

값은 팀에 문의한다.

## 테스트

테스트용 MySQL은 **Testcontainers**가 실행 시점에 띄운다. DB를 미리 준비할 필요 없이
Docker만 떠 있으면 된다.

```bash
./gradlew test
```

`PloutosApplicationTests`가 `mysql:8.4` 컨테이너를 띄우고 접속 정보를 `@DynamicPropertySource`로
주입한다. 컨테이너는 테스트가 끝나면 정리되므로 개발용 DB(`localhost:3301`)와 완전히 분리된다.
포트도 자동 매핑이라 로컬 포트와 충돌하지 않는다.

Docker가 떠 있지 않으면 컨테이너 기동 단계에서 실패한다. 테스트가 갑자기 깨지면 이것부터 확인한다.

`src/test/resources/application.properties`에는 datasource 설정을 두지 않는다.
운영 설정(`jdbc:mysql://mysql:3306/${DB_NAME}`)이 테스트로 새는 것을 막는 역할만 한다.

테스트 스키마는 `spring.jpa.hibernate.ddl-auto=create`로 엔티티에서 자동 생성된다.
컨테이너가 매번 폐기되므로 drop은 하지 않는다.
운영·로컬 DB는 `none`이라 자동 생성되지 않으며, 스키마 관리 도구 도입은 별도 논의 대상이다.

## 로컬 실행

```bash
docker compose up -d --build     # mysql + app
curl -i http://localhost:8080/   # Spring Security가 걸려 있어 401이 정상
```

앱만 IDE에서 띄우려면 DB만 먼저 올린다.

```bash
docker compose up -d mysql
./gradlew bootRun
```

## 빌드

```bash
./gradlew build
```

## CI

`.github/workflows/ci-cd.yml`이 PR과 `main` 푸시에서 `./gradlew test`를 실행한다.
DB는 Testcontainers가 러너의 Docker로 직접 띄우므로 워크플로에 DB 설정이 없다.
로컬과 CI가 완전히 같은 방식으로 돈다.

**CI가 통과한 뒤에 리뷰를 요청한다.**

## 컨벤션

코드 스타일, 엔티티 규칙, 테스트 작성 방식은 [CLAUDE.md](CLAUDE.md)를 따른다.
