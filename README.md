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

테스트는 `localhost:3307`의 **테스트 전용 MySQL**에 붙는다.
`./gradlew test` 전에 반드시 띄워야 한다.

```bash
docker compose up -d mysql-test
./gradlew test
```

DB를 띄우지 않으면 `PloutosApplicationTests`가 컨텍스트 로딩 단계에서 실패한다.
에러 메시지에 원인이 드러나지 않으니, 테스트가 갑자기 깨지면 이것부터 확인한다.

테스트 DB는 개발용 DB(`localhost:3306`)와 별개 컨테이너다.
자격증명은 `test/test`로 고정되어 있고 `src/test/resources/application.properties`에 들어 있다.
데이터를 `tmpfs`에 두므로 컨테이너를 내리면 초기화된다.

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

`.github/workflows/ci.yml`이 PR과 `main` 푸시에서 `./gradlew test`를 실행한다.
테스트용 MySQL은 GitHub Actions의 `services` 블록이 `3307` 포트로 띄우므로
로컬과 동일한 접속 설정이 그대로 동작한다.

**CI가 통과한 뒤에 리뷰를 요청한다.**

## 컨벤션

코드 스타일, 엔티티 규칙, 테스트 작성 방식은 [CLAUDE.md](CLAUDE.md)를 따른다.
