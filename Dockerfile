# =========================================
# 1: 스프링부트 애플리케이션 빌드 스테이지
# =========================================

# Java 21 JDK 및 Gradle이 포함된 베이스 이미지 사용
FROM eclipse-temurin:21-jdk-noble AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 래퍼 및 빌드 설정 파일만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 의존성만 먼저 다운로드 (레이어 캐싱 + BuildKit 캐시 마운트 활용)
# build.gradle이 바뀌어 이 레이어 캐시가 깨지더라도, 캐시 마운트에 남은
# 의존성 파일을 재사용하므로 매번 처음부터 다시 받지 않는다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon || true

# 실제 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# =========================================
# 2: 실행 스테이지 - 최종 이미지 용량 최소화
# =========================================

# 실행에는 JDK가 아닌 JRE만 필요하므로 더 가벼운 이미지 사용
FROM eclipse-temurin:21-jre-alpine AS runner

# 작업 디렉토리 설정
WORKDIR /app

# 보안을 위해 root가 아닌 일반 실행 사용자 생성 및 전환
RUN adduser -D springuser
USER springuser

# 빌드 스테이지에서 생성된 jar 파일만 추출하여 복사
# (app.jar 대신 실제 생성되는 빌드 결과물 이름을 유연하게 매칭)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
