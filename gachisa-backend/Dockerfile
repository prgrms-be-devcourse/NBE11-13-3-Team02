FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY . .
# 소스 변경으로 bootJar 레이어가 다시 실행돼도 Gradle 배포본과 의존성은 재사용합니다.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
