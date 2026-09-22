# MULO Backend Team Rules

이 문서는 `mulo-be` 저장소에서 백엔드 개발자들이 공통으로 따라야 하는 협업 규칙을 정의한다.

- `AGENTS.md`: AI 코딩 에이전트 전용 규칙
- `RULE.md`: 사람 개발자의 Git / PR / DB / Flyway / 테스트 협업 규칙

---

## 1. 고정 브랜치 전략

MULO 백엔드는 아래 2개의 장기 유지 브랜치를 사용한다.

```text
main
develop
```

역할:

### main

- 현재 배포 중이거나 배포 가능한 안정 버전
- `develop`에서 검증이 끝난 변경만 PR로 반영
- 직접 개발하지 않음
- 직접 push하지 않음
- 삭제하지 않음

### develop

- 개발 통합 및 다음 배포 준비 브랜치
- 실제 개발자는 최신 `develop`에서 기능별 작업 브랜치를 생성해 작업
- 작업 브랜치는 PR로 `develop`에 반영
- 검증 완료 후 `develop -> main` PR 생성
- 직접 개발하지 않음
- 직접 push하지 않음
- 삭제하지 않음

전체 흐름:

```text
작업 브랜치
   ↓ PR
develop
   ↓ PR
main
   ↓
배포
```

---

## 2. 작업 브랜치 규칙

작업 브랜치는 항상 최신 `develop`에서 생성한다.

형식:

```text
<type>/<function>-<issueNumber>
```

허용 type:

```text
feat
fix
docs
style
refactor
test
chore
```

예:

```text
feat/login-34
feat/security-setup-51
fix/token-expiration-52
docs/api-guide-53
refactor/user-service-54
test/login-service-55
chore/gradle-config-56
```

규칙:

- `#` 기호는 브랜치 이름에 넣지 않는다.
- 작업 브랜치에는 이슈 번호를 포함한다.
- 이슈 번호를 임의로 만들지 않는다.
- 작업 브랜치는 merge 완료 후 삭제할 수 있다.
- `main`, `develop`은 삭제하지 않는다.

---

## 3. 커밋 컨벤션

허용 type:

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
<type>: <작업 내용> #<이슈번호>
```

`<작업 내용>`은 한글 또는 영어를 모두 허용한다.

예:

```text
feat: 로그인 API 구현 #34
feat: implement login API #34
fix: 로그인 토큰 오류 수정 #52
docs: API 문서 수정 #53
```

---

## 4. PR 제목 규칙

PR 제목 형식:

```text
<type>: <작업 내용> #<이슈번호>
```

`<작업 내용>`은 한글 또는 영어를 모두 허용한다.

예:

```text
feat: login #34
feat: 로그인 API 구현 #34
fix: token expiration #52
docs: API 문서 수정 #53
```

Commit과 PR Title은 같은 언어일 필요가 없다.

예:

```text
Branch
feat/login-34

Commit
feat: 로그인 API 구현 #34

PR Title
feat: login #34

PR Target
develop
```

---

## 5. 작업 시작 절차

항상 최신 `develop`에서 작업 브랜치를 만든다.

```bash
git switch develop
git pull origin develop
git switch -c feat/login-34
```

오래된 local `develop`을 기준으로 작업 브랜치를 만들지 않는다.

---

## 6. 작업 중 기본 원칙

- 작업 브랜치에서만 기능 개발
- `main`, `develop`에서 직접 기능 개발 금지
- `main`, `develop` 직접 push 금지
- force push 금지
- 다른 개발자 담당 영역을 수정해야 하면 먼저 공유
- 관련 없는 변경을 한 작업 브랜치에 섞지 않음

---

## 7. PR 전 최신 develop 반영

작업 브랜치에서 PR을 만들기 전에 최신 `origin/develop`를 반영한다.

```bash
git fetch origin
git switch feat/login-34
git merge origin/develop
```

충돌이 없으면 테스트한다.

```bash
./gradlew test
```

충돌이 발생하면 작업 브랜치에서 해결한다.

다른 개발자 담당 코드와 충돌한 경우 임의로 한쪽을 덮어쓰지 않고 해당 개발자와 확인한다.

force push로 해결하지 않는다.

---

## 8. Push 규칙

작업 브랜치만 push한다.

예:

```bash
git push origin feat/login-34
```

금지:

```bash
git push origin develop
git push origin main
git push --force
git push --force-with-lease
```

---

## 9. PR 흐름

### 9.1 작업 브랜치 -> develop

일상적인 개발 PR:

```text
feat/login-34
   ↓ PR
develop
```

조건:

- 최신 `origin/develop` 반영
- conflict 없음
- 관련 테스트 통과
- Secret 없음
- DB/API/Dependency 변경 여부 확인
- 관련 이슈 번호 명시
- 최소 1명 리뷰 승인
- merge는 수동

merge 완료 후 작업 브랜치는 삭제할 수 있다.

### 9.2 develop -> main

배포 전 검증이 완료되면:

```text
develop
   ↓ PR
main
```

`develop` 브랜치는 merge 후에도 유지한다.

---

## 10. PR 템플릿

저장소에 다음 파일이 있으면 우선 사용한다.

```text
.github/PULL_REQUEST_TEMPLATE.md
```

PR에는 실제 변경 내용과 실제 테스트 결과를 작성한다.

필수 확인 항목:

- 관련 이슈
- 변경 내용
- 테스트
- DB/Flyway 변경 여부
- API 변경 여부
- Dependency 변경 여부
- Secret 포함 여부

---

## 11. 리뷰 및 Merge 규칙

- PR은 최소 1명 승인을 받아야 한다.
- 작성자는 자기 PR을 승인한 것으로 간주하지 않는다.
- 리뷰 의견이 남아 있으면 처리 후 merge한다.
- 리뷰 후 중요한 변경이 추가되면 다시 리뷰한다.
- merge는 사람이 직접 수행한다.
- auto-merge는 사용하지 않는다.
- force push를 사용하지 않는다.

권장 보호 설정:

### develop

- Require pull request before merging
- Require at least 1 approval
- Dismiss stale approvals when new commits are pushed
- Require status checks before merging
- Block force pushes
- Prevent deletion

### main

- Require pull request before merging
- Require at least 1 approval
- Dismiss stale approvals when new commits are pushed
- Require status checks before merging
- Block force pushes
- Prevent deletion

---

## 12. Database / JPA / Flyway 기본 원칙

DB Schema 변경 이력의 Source of Truth는 Flyway migration이다.

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

사용하지 않는다:

```text
ddl-auto=update
ddl-auto=create
ddl-auto=create-drop
```

---

## 13. Flyway Migration 규칙

형식:

```text
V{번호}__{변경내용}.sql
```

예:

```text
V1__init_schema.sql
V2__add_index_to_records.sql
V3__add_profile_image_to_users.sql
```

규칙:

- DB schema 변경은 새로운 migration으로 작성
- 이미 공유되거나 적용된 migration은 수정하지 않음
- Workbench 직접 수정으로 DB 변경을 완료하지 않음
- migration 작성 전 최신 `develop` 기준으로 version 확인
- version 번호를 중복 사용하지 않음
- DB 변경 PR에는 migration 파일과 변경 내용을 기록

---

## 14. 동시에 Flyway 작업할 때

최신 version이 `V8`이라면 예:

```text
개발자 A -> V9
개발자 B -> V10
```

같은 version이 생기지 않도록 서로 확인한다.

같은 version이 생겼다면:

- 아직 공유 전이면 번호 조정
- 이미 remote `develop`에 반영된 migration은 수정 금지
- 새로운 변경은 새로운 migration으로 작성

---

## 15. 기존 Migration 수정 기준

수정 가능:

```text
아직 본인만 작성
AND
다른 개발자가 적용하지 않음
AND
remote develop에 공유되지 않음
```

수정 금지:

```text
다른 개발자가 적용함
OR
remote develop/main에 반영됨
```

이후 변경은 새 migration을 추가한다.

---

## 16. 로컬 DB 초기화 규칙

개발 초기 로컬 DB에서 migration 이력이 꼬였고 데이터 보존이 필요하지 않은 경우에만:

```bash
docker compose down -v
docker compose up -d
./gradlew bootRun
```

사용 가능하다.

사용 금지:

- 공용 개발 DB
- 스테이징 DB
- 운영 DB

---

## 17. 테스트 규칙

기본:

```bash
./gradlew test
```

빌드 영향이 큰 변경:

```bash
./gradlew clean build
```

규칙:

- 기능 변경 시 관련 테스트 추가/수정
- 테스트 실패 숨기지 않음
- 실패 테스트를 삭제해서 통과시키지 않음
- 테스트 실패 상태로 merge하지 않음
- 실행하지 않은 테스트를 PASS라고 기록하지 않음

---

## 18. Secret / 환경변수 규칙

Git에 commit하지 않는다.

- `.env`
- DB password
- API key
- Access Token
- Refresh Token
- Cloud Credential
- Private Key
- 운영 Connection String

환경변수 또는 팀에서 정한 Secret 관리 방식을 사용한다.

---

## 19. 다른 개발자 담당 영역 수정

다른 개발자가 담당한 기능을 수정해야 하면:

1. 변경 이유 공유
2. 영향 범위 설명
3. 충돌 가능성이 있는 파일 공유
4. 필요한 경우 함께 설계 확인

특히 사전 공유를 권장:

- 공통 Entity
- 공통 Exception
- Security/Auth
- Flyway migration
- 공통 DTO
- 공통 설정
- 공용 Service
- `build.gradle`
- application 설정

---

## 20. 금지 사항

- `main`, `develop`에서 직접 기능 개발
- `main`, `develop` 직접 push
- force push
- PR 없이 고정 브랜치 변경
- 승인 없이 merge
- 실패 테스트 무시
- 기존 공유 migration 수정
- Workbench 직접 수정으로 DB 작업 종료
- Secret commit
- 다른 개발자 변경사항 무단 덮어쓰기
- branch protection 우회
- 자동 PR merge

---

## 21. 일일 작업 흐름

### 작업 시작

```bash
git switch develop
git pull origin develop
git switch -c feat/login-34
```

### 개발

작업 브랜치에서 개발한다.

### 검증

```bash
./gradlew test
```

### Commit

```bash
git add <변경 파일>
git commit -m "feat: 로그인 API 구현 #34"
```

### 최신 develop 반영

```bash
git fetch origin
git merge origin/develop
```

충돌이 발생하면 작업 브랜치에서 해결 후 테스트를 다시 수행한다.

### Push

```bash
git push origin feat/login-34
```

### PR

```text
feat/login-34 -> develop
```

예:

```text
PR Title
feat: login #34

Target
develop
```

최소 1명 리뷰 후 수동 merge한다.

### 배포 준비

검증 완료 후:

```text
develop -> main
```

PR 생성 후 수동 merge한다.

---

## 22. PR 생성 전 체크리스트

### 작업 브랜치 -> develop

- [ ] Issue 번호가 맞는가
- [ ] Branch 이름이 규칙에 맞는가
- [ ] Commit 메시지가 규칙에 맞는가
- [ ] PR 제목이 규칙에 맞는가
- [ ] 최신 remote `develop`가 반영되었는가
- [ ] conflict가 없는가
- [ ] 관련 테스트가 통과했는가
- [ ] Secret이 없는가
- [ ] DB 변경 여부를 확인했는가
- [ ] Flyway version 충돌이 없는가
- [ ] API 변경 여부를 확인했는가
- [ ] Dependency 변경 여부를 확인했는가

### develop -> main

- [ ] 배포 범위가 합의되었는가
- [ ] 주요 테스트가 통과했는가
- [ ] DB migration 영향도를 확인했는가
- [ ] 운영 설정을 확인했는가
- [ ] 최소 1명 리뷰 승인을 받았는가

---

## 23. 핵심 요약

```text
고정 브랜치
main / develop

작업 브랜치
<type>/<function>-<issueNumber>

예
feat/login-34

Commit
<type>: <작업 내용> #<이슈번호>

예
feat: 로그인 API 구현 #34

PR Title
<type>: <작업 내용> #<이슈번호>

예
feat: login #34

일반 개발 PR
작업 브랜치 -> develop

배포 PR
develop -> main

PR 최소 1명 승인
Merge는 수동
force push 금지

DB 변경은 Flyway
공유된 migration 수정 금지
ddl-auto=validate

테스트 실패 상태 merge 금지
Secret commit 금지
```
