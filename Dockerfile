# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Git 실행 권한 누락에 대비해 빌드 단계에서도 실행 권한을 보장한다.
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# non-root 실행 (보안 기본): 앱은 파일 쓰기가 필요 없어 소유권만 넘김
RUN useradd -r -u 1001 appuser && chown appuser:appuser app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
