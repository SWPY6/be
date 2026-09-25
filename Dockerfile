FROM eclipse-temurin:26-jdk-jammy AS build
WORKDIR /app
# lombok.config가 없으면 Lombok이 ticker() 대신 getTicker()를 만들어 컴파일이 깨진다.
# 루트에 빌드 설정 파일을 추가하면 여기에도 함께 적어야 한다.
COPY gradlew build.gradle settings.gradle lombok.config ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:26-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
