# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# 스테이징 VM에서 빌드하므로 Gradle 캐시를 빌드 사이에 재사용해 의존성을 매번 받지 않게 한다.
# 테스트는 CI에서 이미 돌렸으므로 여기서는 건너뛴다.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew bootJar --no-daemon -x test \
 && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
