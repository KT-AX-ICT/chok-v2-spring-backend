# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# ponytail: Windows 체크아웃이면 gradlew에 CR이 붙어 shebang이 깨짐 → 방어적으로 제거
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# non-root 실행 (보안 기본): 앱은 파일 쓰기가 필요 없어 소유권만 넘김
RUN useradd -r -u 1001 appuser && chown appuser:appuser app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
