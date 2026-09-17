# MULO Backend Team Rules

이 문서는 `mulo-be` 저장소에서 백엔드 개발자들이 공통으로 따라야 하는 협업 규칙을 정의한다.

`AGENTS.md`는 AI 코딩 에이전트 전용 규칙이고, 이 `RULE.md`는 사람 개발자의 Git / PR / DB / Flyway / 테스트 협업 규칙이다.

---

## 1. 기본 원칙

- `main`, `develop` 브랜치에서 직접 기능 개발하지 않는다.
- 모든 기능 개발은 별도의 `feature/*` 브랜치에서 진행한다.
- `main`, `develop`에는 직접 push하지 않는다.
- 변경사항은 Pull Request(PR)를 통해서만 반영한다.
- PR은 최소 1명의 리뷰어 승인을 받은 후 merge한다.
- force push를 사용하지 않는다.
- 앱이 정상적으로 실행되지 않거나 주요 테스트가 실패한 상태로 merge하지 않는다.
- 다른 개발자가 담당 중인 영역을 수정해야 하면 먼저 변경 의도를 공유한다.
- merge가 끝난 feature 브랜치는 삭제한다.

---

## 2. 브랜치 전략

사용 브랜치:

```text
main
develop
feature/{기능명}-{이슈번호}
```

기본 흐름:

```text
feature/* -> develop -> main
```

### main

- 안정 버전을 관리한다.
- 직접 commit/push하지 않는다.
- develop에서 PR을 통해서만 merge한다.

### develop

- 개발 통합 브랜치다.
- 직접 기능 개발하지 않는다.
- feature 브랜치에서 PR을 통해서만 merge한다.

### feature

기능 개발 브랜치 형식:

```text
feature/{기능명}-{이슈번호}
```

예:

```text
feature/login-34
feature/friend-request-51
feature/monthly-report-72
```

브랜치 이름에는 `#`를 사용하지 않는다.

---

## 3. 작업 시작 절차

기능 개발 시작 전:

```bash
git switch develop
git pull origin develop
git switch -c feature/{기능명}-{이슈번호}
```

예:

```bash
git switch develop
git pull origin develop
git switch -c feature/login-34
```

작업은 항상 최신 `develop`을 기준으로 시작한다.

---

## 4. 커밋 컨벤션

허용 타입:

```text
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 수정
style: 코드 포맷팅 (기능 변경 없음)
refactor: 리팩토링
test: 테스트 코드
chore: 빌드/패키지 관리
```

커밋 메시지 형식:

```text
<type>: <변경 내용> #<이슈번호>
```

예:

```text
feat: 로그인 API 구현 #34
fix: 중복 이메일 검증 오류 수정 #18
refactor: 사용자 조회 로직 분리 #25
test: 로그인 서비스 테스트 추가 #34
chore: MySQL 개발 설정 정리 #12
```

이슈 번호는 실제 이슈 번호를 사용하고 임의로 만들지 않는다.

---

## 5. Push 규칙

feature 브랜치만 push한다.

최초 push:

```bash
git push -u origin feature/login-34
```

이후:

```bash
git push origin feature/login-34
```

금지:

```bash
git push origin main
git push origin develop
git push --force
git push --force-with-lease
```

보호 브랜치 규칙을 우회하지 않는다.

---

## 6. PR 생성 규칙

기본 PR 흐름:

```text
feature/* -> develop
develop -> main
```

PR은 반드시 다음 조건을 만족해야 한다.

- 최소 리뷰어 1명 승인
- 충돌 없음
- 필요한 테스트 통과
- 최신 develop 반영
- 관련 없는 변경 포함 금지
- Secret 포함 금지
- DB/API/Dependency 변경 여부 명시

PR 제목과 설명은 팀 템플릿을 따른다.

---

## 7. PR 제목 규칙

기본 형식:

```text
<type>: <작업 요약> #<이슈번호>
```

예:

```text
feat: 로그인 API 구현 #34
fix: 친구 요청 중복 처리 수정 #51
```

---

## 8. PR 본문 기본 형식

`.github/PULL_REQUEST_TEMPLATE.md`가 존재하면 해당 템플릿을 우선 사용한다.

기본 형식:

```markdown
## 작업 내용

- 실제 작업 내용을 요약

## 관련 이슈

- Closes #34

## 주요 변경 사항

- 변경 사항 1
- 변경 사항 2

## 테스트

- 실행한 명령
- 테스트 결과

## DB 변경

- [ ] DB 변경 없음
- [ ] Flyway migration 추가

Migration:
- 해당 시 파일명 작성

## API 변경

- [ ] 없음
- [ ] 있음

## Dependency 변경

- [ ] 없음
- [ ] 있음

## 체크리스트

- [ ] 관련 테스트를 수행했습니다.
- [ ] 현재 작업과 무관한 변경이 포함되지 않았습니다.
- [ ] Secret이 포함되지 않았습니다.
- [ ] API 변경 여부를 확인했습니다.
- [ ] DB 변경 여부를 확인했습니다.
```

---

## 9. 리뷰 규칙

- PR 작성자는 최소 1명의 승인을 받아야 한다.
- 작성자는 자기 PR을 승인한 것으로 간주하지 않는다.
- 리뷰 의견이 남아 있으면 처리 후 merge한다.
- 리뷰 후 중요한 commit이 추가되면 다시 리뷰한다.
- 다른 개발자의 담당 로직을 크게 변경했다면 반드시 해당 개발자에게 알린다.

가능하면 GitHub 설정에서 다음을 적용한다.

- Require pull request before merging
- Require at least 1 approval
- Dismiss stale approvals when new commits are pushed
- Require status checks before merging
- Require branch to be up to date before merging
- Block force pushes
- Prevent branch deletion

---

## 10. Merge 규칙

merge는 자동화하지 않는다.

PR 생성 이후:

```text
PR 생성
-> 팀원 리뷰
-> 수정 반영
-> Approve
-> 팀원과 merge 여부 확인
-> 수동 merge
```

merge 전에 확인:

- 최소 1명 승인
- 테스트 통과
- unresolved conflict 없음
- 최신 develop 반영
- DB/Flyway 변경 검토 완료

merge 후 feature 브랜치는 삭제한다.

---

## 11. develop 최신화 및 PR 전 동기화

PR merge 전에 최신 develop을 feature branch에 반영한다.

```bash
git fetch origin
git switch feature/login-34
git merge origin/develop
```

기본적으로 merge 방식을 사용한다.

팀 규칙이 변경되지 않는 한 다음 방식은 사용하지 않는다.

```bash
git rebase origin/develop
git push --force
git push --force-with-lease
```

---

## 12. Merge Conflict 규칙

충돌은 해당 feature 브랜치 작성자가 해결한다.

충돌 발생 시:

1. 충돌 파일과 영역을 확인한다.
2. 자동으로 `ours` 또는 `theirs`를 선택하지 않는다.
3. 현재 feature의 의도와 develop의 변경 의도를 모두 확인한다.
4. 다른 개발자의 담당 로직이 포함되어 있으면 해당 개발자와 먼저 확인한다.
5. feature 브랜치에서 해결한다.
6. 해결 후 테스트를 다시 수행한다.
7. 해결된 상태를 push하고 PR을 갱신한다.

develop 브랜치에서 직접 충돌을 해결하지 않는다.

---

## 13. Database / Flyway 기본 원칙

DB 스키마 변경 이력의 Source of Truth는 Flyway migration이다.

역할:

```text
Flyway
-> DB schema 생성/변경 이력 관리

JPA / Hibernate
-> Entity 매핑 및 schema 검증
```

Hibernate 설정:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

금지:

```text
ddl-auto=update
ddl-auto=create
ddl-auto=create-drop
```

DB 구조 변경을 Hibernate 자동 생성에 맡기지 않는다.

---

## 14. Flyway Migration 규칙

migration 파일 형식:

```text
V{번호}__{변경내용}.sql
```

예:

```text
V1__init_schema.sql
V2__add_index_to_records.sql
V3__add_profile_image_to_users.sql
V4__change_notification_fk.sql
```

핵심 규칙:

- DB schema 변경은 반드시 새로운 migration으로 작성한다.
- 이미 공유되었거나 적용된 migration은 수정하지 않는다.
- MySQL Workbench에서 직접 수정한 상태를 최종 변경으로 인정하지 않는다.
- migration 작성 전 develop의 최신 version을 확인한다.
- version 번호를 중복 사용하지 않는다.
- migration이 포함된 PR에는 변경 내용을 반드시 적는다.

---

## 15. 동시에 Flyway 작업할 때

현재 최신 migration이:

```text
V8
```

이라면 예:

```text
개발자 A -> V9 예약
개발자 B -> V10 예약
```

동시에 같은 version을 사용하지 않도록 서로 확인한다.

이미 두 명이 같은 version을 만든 경우:

- 아직 공유/적용 전이면 번호를 조정한다.
- 이미 한쪽이 develop에 merge되었다면 다른 쪽은 새로운 version으로 변경한다.
- 이미 로컬 DB에 잘못 적용한 경우 개발 초기 로컬 DB라면 초기화 후 재적용할 수 있다.
- 공용/운영 DB는 초기화하지 않는다.

---

## 16. 기존 Migration 수정 기준

다음 경우에만 수정 가능하다.

```text
아직 본인만 작성
AND
다른 개발자가 적용하지 않음
AND
develop/main에 merge되지 않음
```

다음 상태부터는 수정 금지:

```text
다른 개발자가 적용함
OR
develop/main에 merge됨
```

이후 변경은 새 migration으로 보정한다.

---

## 17. 로컬 DB 초기화 규칙

개발 초기 로컬 DB에서 migration version 충돌 등으로 이력이 꼬였을 경우 다음 방식으로 초기화할 수 있다.

```bash
docker compose down -v
docker compose up -d
./gradlew bootRun
```

이 방식은 로컬 개발 DB에서만 사용한다.

절대 사용하지 않는 대상:

- 공용 개발 DB
- 스테이징 DB
- 운영 DB

---

## 18. DB 변경 PR 규칙

DB 변경 PR에는 다음을 명확히 작성한다.

```text
Migration 파일명
변경 테이블
추가/수정/삭제 컬럼
Index 변경
Unique / Check / FK 변경
데이터 영향 여부
```

예:

```text
Migration
- V9__add_profile_image_to_users.sql

변경
- users.profile_image_url 추가

데이터 영향
- 기존 데이터 영향 없음
```

---

## 19. 테스트 규칙

기본 검증:

```bash
./gradlew test
```

빌드 영향이 큰 경우:

```bash
./gradlew clean build
```

DB 관련 검증이 필요한 경우:

```bash
docker compose up -d
./gradlew test
```

원칙:

- 기능 변경 시 관련 테스트를 작성하거나 수정한다.
- 실패 테스트를 삭제해서 build를 통과시키지 않는다.
- 테스트 실패 원인을 확인한다.
- 테스트 실패 상태로 merge하지 않는다.
- 실행하지 않은 테스트를 실행했다고 기록하지 않는다.

---

## 20. Secret / 환경변수 규칙

다음 값은 Git에 commit하지 않는다.

- `.env`
- DB password
- API key
- Access Token
- Refresh Token
- Cloud Credential
- Private Key
- 운영 Connection String

환경변수 또는 팀에서 정한 설정 방식을 사용한다.

---

## 21. 다른 개발자 담당 영역 수정

다른 개발자가 담당한 기능을 수정해야 할 경우:

1. 변경 이유를 먼저 공유한다.
2. 영향 범위를 설명한다.
3. 충돌 가능성이 있는 파일을 알린다.
4. 필요하면 함께 설계를 확인한다.

특히 다음 영역은 사전 공유를 권장한다.

- 공통 Entity
- 공통 Exception
- Security/Auth
- Flyway migration
- 공통 DTO
- 공통 설정
- 공용 Service
- build.gradle
- application 설정

---

## 22. 금지 사항

다음 작업을 금지한다.

- main/develop 직접 기능 개발
- main/develop 직접 push
- force push
- PR 없이 merge
- 승인 없이 merge
- 실패 테스트 무시
- 기존 공유 migration 수정
- Workbench 직접 DB 수정으로 작업 종료
- Secret commit
- 다른 개발자 변경사항을 설명 없이 덮어쓰기
- branch protection 우회

---

## 23. 일일 Git 작업 흐름

### 작업 시작

```bash
git switch develop
git pull origin develop
git switch -c feature/{기능명}-{이슈번호}
```

### 개발 후 commit

```bash
git add <변경 파일>
git commit -m "feat: 작업 내용 #34"
```

### Push

```bash
git push -u origin feature/{기능명}-{이슈번호}
```

### PR 전 최신 develop 반영

```bash
git fetch origin
git merge origin/develop
```

### 검증

```bash
./gradlew test
```

### PR

```text
feature/* -> develop
최소 1명 리뷰
```

### Merge 후

```bash
git switch develop
git pull origin develop
git branch -d feature/{기능명}-{이슈번호}
```

원격 feature branch도 삭제한다.

---

## 24. 작업 종료 체크리스트

PR 생성 전:

- [ ] Issue 번호가 맞는가
- [ ] Branch 이름이 규칙에 맞는가
- [ ] Commit 메시지가 규칙에 맞는가
- [ ] 최신 develop이 반영되었는가
- [ ] Merge conflict가 없는가
- [ ] 관련 테스트가 통과했는가
- [ ] Secret이 없는가
- [ ] 관련 없는 변경이 포함되지 않았는가
- [ ] DB 변경 여부를 확인했는가
- [ ] Flyway migration version 충돌이 없는가
- [ ] API 변경 여부를 확인했는가
- [ ] Dependency 변경 여부를 확인했는가

Merge 전:

- [ ] 최소 1명 승인
- [ ] 모든 리뷰 의견 처리
- [ ] 최신 테스트 통과
- [ ] unresolved conflict 없음
- [ ] DB/Flyway 변경 검토 완료

---

## 25. 핵심 요약

```text
main / develop 직접 작업 금지
feature/{기능명}-{이슈번호}에서 개발
commit에는 #이슈번호 포함
PR 최소 1명 승인
force push 금지
merge는 수동
DB 변경은 Flyway
공유된 migration 수정 금지
ddl-auto=validate 유지
테스트 실패 상태 merge 금지
Secret commit 금지
```
