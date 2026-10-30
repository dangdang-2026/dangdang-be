# 1단계: 빌드 (JDK)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 (JRE만, 비root, KST)
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
RUN useradd -r -u 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
