# CI에서 ./gradlew generateDocs + bootJar로 이미 빌드/검증된 JAR만 담는 런타임 전용 이미지
FROM eclipse-temurin:21-jre

# 애플리케이션 작업 디렉토리
WORKDIR /app

# CI에서 미리 빌드한 JAR 복사
COPY build/libs/*.jar app.jar

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
