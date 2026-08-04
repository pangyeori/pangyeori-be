# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

# 작업 디렉토리
WORKDIR /build

# Gradle 설정 및 wrapper 복사
COPY gradlew ./
COPY gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

# Gradle Wrapper 실행 권한 부여
RUN chmod +x gradlew

# 의존성 다운로드 (build 설정 파일이 변경되지 않으면 해당 Docker layer 재사용)
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src/ src/

# 실행 JAR 생성 (테스트는 별도의 CI 단계에서 수행)
RUN ./gradlew bootJar -x test --no-daemon


# Stage 2: Runtime
FROM eclipse-temurin:21-jre

# 애플리케이션 작업 디렉토리
WORKDIR /app

# Builder stage에서 생성한 JAR만 복사 (runtime 이미지 경량화)
COPY --from=builder /build/build/libs/*.jar app.jar

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
