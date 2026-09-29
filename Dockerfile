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

# 실행에는 JDK가 아닌 JRE만 필요하므로 더 가벼운 이미지 사용.
# Alpine(musl libc)은 GCS 클라이언트가 쓰는 Conscrypt 네이티브 라이브러리가
# glibc 기반이라 로드에 실패한다(UnsatisfiedLinkError). 빌드 스테이지와
# 동일 계열(Ubuntu Noble, glibc)의 JRE 이미지를 사용해 이 문제를 피한다.
FROM eclipse-temurin:21-jre-noble AS runner

# 작업 디렉토리 설정
WORKDIR /app

# HEIC(아이폰 사진) → JPEG 변환용 네이티브 의존성.
# heif-convert(HEIC 디코딩+변환) → jpegtran(EXIF/GPS 메타데이터 제거, ICC 컬러 프로파일 유지)
# 순서로 사용한다. root 권한이 필요해 USER 전환 전에 설치해야 한다.
# 버전은 eclipse-temurin:21-jre-noble 기준 실제 설치되는 값으로 고정해서, 재배포 시
# 다른 마이너 버전이 섞여 들어가지 않게 한다.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libheif-examples=1.17.6-1ubuntu4.8 \
    libheif-plugin-libde265=1.17.6-1ubuntu4.8 \
    libjpeg-turbo-progs=2.1.5-2ubuntu2 \
    && rm -rf /var/lib/apt/lists/*

# 보안을 위해 root가 아닌 일반 실행 사용자 생성 및 전환
# (Alpine의 BusyBox adduser 전용 옵션(-D) 대신 Debian/Ubuntu adduser 문법 사용)
RUN adduser --disabled-password --gecos "" springuser
USER springuser

# 빌드 스테이지에서 생성된 jar 파일만 추출하여 복사
# (app.jar 대신 실제 생성되는 빌드 결과물 이름을 유연하게 매칭)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
