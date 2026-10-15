# MULO 백엔드 개발환경 초기 설정

대상 환경:

- Java: JDK 21
- Spring Boot: 4.1.1
- Database: MySQL 8.4
- Migration: Flyway
- ORM: Spring Data JPA / Hibernate

## 1. 팀의 DB 관리 원칙

MULO에서는 DB 스키마의 변경 주체를 Flyway로 통일한다.

- `src/main/resources/db/migration`의 SQL이 DB 변경 이력의 기준이다.
- Hibernate는 DB를 자동 변경하지 않는다. `ddl-auto=validate`만 사용한다.
- Workbench/DataGrip에서 스키마를 직접 수정하고 끝내지 않는다.
- 이미 실행된 `V1__`, `V2__` 등의 migration 파일은 수정하지 않는다.
- 새로운 DB 변경은 항상 다음 버전의 migration 파일을 추가한다.

예:

```text
V1__init_schema.sql
V2__add_xxx_column.sql
V3__add_xxx_index.sql
```

## 2. MySQL 8.4 실행

### 방법 A: 별도로 관리하는 Docker Compose 사용

팀 백엔드 저장소에는 MySQL Compose 파일이 없다. Docker를 사용하는 개발자는
자신이 관리하는 Compose 파일의 디렉터리에서 MySQL 8.4를 실행한다.
그 디렉터리의 `.env`에는 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 직접 입력하고
Git에서 제외한다.
Compose는 해당 디렉터리의 `.env`를 자동으로 읽지만 Spring Boot/Gradle은 자동으로 읽지 않는다.

비밀번호는 쉘에서도 읽을 수 있도록 작은따옴표로 감싸 작성한다. 기존 DB 볼륨을
사용한다면 기존 비밀번호를 사용한다. `.env` 변경만으로 DB 계정 비밀번호가 바뀌지 않는다.

준비 후:

```bash
docker compose up -d
```

로컬 개발용 기본값:

```text
host     localhost
port     3306
database mulo
username mulo
password .env의 DB_PASSWORD
```

> 실제 비밀번호는 파일에 하드코딩하지 않는다. 운영에서는 배포 환경의 비밀정보 관리 기능으로 주입한다.

DB 상태 확인:

```bash
docker compose ps
```

종료:

```bash
docker compose down
```

DB 데이터까지 완전히 초기화:

```bash
docker compose down -v
```

### 방법 B: 로컬에 MySQL 8.4 직접 설치

직접 설치하는 경우 최소한 다음 DB/계정을 동일하게 준비한다.

```sql
CREATE DATABASE mulo
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'mulo'@'localhost'
IDENTIFIED BY '<직접 선택한 로컬 비밀번호>';

GRANT ALL PRIVILEGES ON mulo.* TO 'mulo'@'localhost';
FLUSH PRIVILEGES;
```

## 3. AI 기억 검색 대화 저장소

기억 검색의 최근 5턴은 백엔드 서버 한 대의 프로세스 메모리에 저장한다.
마지막 완료 질문으로부터 30분 동안 사용하지 않으면 만료된다. 서버를 재시작하면
이전 대화는 사라지며, 기존 대화 ID로 질문하면 `410` 응답을 받고 새 대화를 시작한다.
로컬 Redis 실행은 필요하지 않다.

`SPRING_PROFILES_ACTIVE=local`에서는 실제 AI 대신 현재 사용자의 활성 자물쇠를
최신 생성일·ID 내림차순으로 최대 50건 반환한다. 질문 내용과 이전 문맥은 아직
검색 조건으로 쓰지 않으며 제안 카드는 반환하지 않는다. 카드의 답변 문구는
개발용 임시 결과임을 표시한다. 로컬 외 환경에서는 실제 AI가 연결되기 전까지
기억 검색 질문에 `AI_SERVICE_ERROR`를 반환한다.

## 4. 애플리케이션 실행

macOS/Linux에서 직접 작성한 신뢰할 수 있는 `.env`를 환경변수로 로드한 후 실행한다:

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

DB가 필요한 테스트도 같은 방식으로 `.env`를 로드한 셸에서 실행한다. `.env`가
존재하는 것만으로 `./gradlew test`가 비밀번호를 읽지는 않는다.

```bash
set -a
source .env
set +a
./gradlew test
```

Windows에서는 IDE 실행 설정 또는 환경변수에 `DB_PASSWORD`와
`SPRING_PROFILES_ACTIVE=local`을 설정한 후 실행한다:

```bat
gradlew.bat bootRun
```

다른 DB 설정을 사용한다면 환경 변수로 덮어쓴다.

```bash
export DB_URL='jdbc:mysql://localhost:3306/mulo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul'
export DB_USERNAME='mulo'
export DB_PASSWORD='your_password'
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

전체 검증도 위 환경변수를 로드한 같은 터미널에서 `./gradlew clean build`로 수행한다.

## 5. 첫 실행에서 일어나는 일

애플리케이션 시작 시 순서는 다음과 같다.

```text
MySQL 연결
  -> Flyway가 flyway_schema_history 확인
  -> 아직 실행하지 않은 V*.sql 실행
  -> migration 성공 기록
  -> Hibernate가 Entity <-> DB 구조 validate
  -> Spring Boot 시작
```

현재 최초 migration은:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

이다.

Flyway가 최초 실행되면 DB에 `flyway_schema_history` 테이블도 자동 생성한다.

## 6. 두 개발자의 일상적인 작업 방식

개발 시작:

```bash
git pull
./gradlew bootRun
```

다른 개발자가 새로운 migration을 커밋했다면 애플리케이션 시작 시 내 로컬 DB에도 자동 적용된다.

### DB 변경이 필요한 경우

예를 들어 `places`에 컬럼을 하나 추가해야 한다면 DB에서 직접 ALTER하고 끝내지 않는다.

새 파일을 생성한다.

```text
src/main/resources/db/migration/V2__add_xxx_to_places.sql
```

내용 예시:

```sql
ALTER TABLE places
ADD COLUMN xxx VARCHAR(100) NULL;
```

그리고 관련 Java 코드와 migration을 같이 커밋한다.

## 7. 절대 지켜야 할 규칙

### 이미 적용된 migration을 수정하지 않는다

`V1__init_schema.sql`이 두 개발자의 DB에 적용된 뒤 요구사항이 바뀌었다면 V1을 고치지 않는다.

```text
잘못된 방법:
V1__init_schema.sql 수정

올바른 방법:
V2__change_xxx.sql 추가
```

### migration 버전을 중복 사용하지 않는다

두 명이 동시에 DB 변경을 한다면 PR/브랜치에서 다음 migration 번호를 확인한다.

```text
현재 마지막: V7
개발자 A: V8
개발자 B: V9
```

merge 과정에서 번호가 겹쳤다면 merge 전에 한쪽 번호를 변경한다. 아직 어떤 공용 DB에도 적용되지 않은 브랜치 migration에 한해서 조정한다.

### Hibernate `ddl-auto=update`를 사용하지 않는다

MULO는 다음 구조로 관리한다.

```text
Flyway    = DB 구조 변경
Hibernate = DB와 Entity 구조 검증
```

따라서 설정은:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

로 유지한다.

## 8. V1 스키마와 Entity 개발 순서

초기 구축 순서는 다음으로 통일한다.

```text
최신 ERD/테이블 정의서
  -> MySQL 8.4용 V1 DDL
  -> Flyway로 양쪽 로컬 DB에 동일 적용
  -> JPA Entity 작성
  -> Hibernate validate
```

Entity를 먼저 작성해 Hibernate가 스키마를 임의 생성하게 하지 않는 이유는 MULO가 Generated Column, CHECK, 복합 UNIQUE, ENUM, `ON DELETE CASCADE/SET NULL` 등 DB 레벨 제약을 적극적으로 사용하기 때문이다.

## 9. 현재 파일 역할

```text
build.gradle
  Spring Web MVC / JPA / Validation / MySQL / Flyway 등의 의존성

application.yaml
  DB 접속 / Flyway / Hibernate validate 설정

별도 관리하는 MySQL Compose 파일
  Docker를 사용하는 개발자의 로컬 MySQL 8.4 환경. 팀 백엔드 저장소에는 없음

src/main/resources/db/migration/V1__init_schema.sql
  최초 DB 스키마

README_DEV_SETUP.md
  팀 개발환경 및 Flyway 운영 규칙
```

## 10. 주의

`record_drafts`는 테이블 정의서에 포함되어 있어 V1 DDL에도 포함했지만, 현재 V1 서비스 정책에서는 브라우저 로컬 저장소를 사용하여 서버 기능에서는 사용하지 않는 예약 테이블이다.

운영용 DB 계정, 비밀번호, 접근 권한은 로컬 개발 설정과 분리한다.
