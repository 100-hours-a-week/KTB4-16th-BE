# MULO Backend Team Rules

이 문서는 `mulo-be` 저장소에서 백엔드 개발자들이 공통으로 따라야 하는 협업 규칙을 정의한다.

- `AGENTS.md`: AI 코딩 에이전트 전용 규칙
- `RULE.md`: 사람 개발자의 Git / PR / DB / Flyway / 테스트 협업 규칙

---

## 1. 고정 브랜치 전략

MULO 백엔드는 아래 3개의 브랜치만 고정해서 사용한다.

```text
feature
develop
main
```

역할:

### feature

- 현재 개발 중인 코드를 모으는 브랜치다.
- 백엔드 개발자는 기본적으로 `feature`에서 작업한다.
- 작업 후 `feature -> develop` PR을 생성한다.
- 직접 삭제하지 않는다.

### develop

- 다음 배포 전에 기능을 통합하고 검증하는 브랜치다.
- `feature`에서 PR을 통해 변경을 받는다.
- 검증이 끝난 후 `develop -> main` PR을 생성한다.
- 직접 삭제하지 않는다.

### main

- 현재 배포 중이거나 배포 가능한 안정 버전이다.
- `develop`에서 검증된 변경만 PR로 받는다.
- 직접 개발하지 않는다.
- 직접 삭제하지 않는다.

전체 흐름:

```text
feature
   ↓ PR
develop
   ↓ PR
 main
   ↓
 배포
```

---

## 2. 기본 Git 원칙

- 브랜치는 `feature`, `develop`, `main` 3개를 유지한다.
- 별도의 `feat/*`, `fix/*` 작업 브랜치를 만들지 않는다.
- 기능 개발은 `feature`에서 진행한다.
- `develop`, `main`에서는 직접 기능 개발하지 않는다.
- `develop`, `main`에 직접 push하지 않는다.
- `feature -> develop`, `develop -> main`은 PR로 반영한다.
- PR은 최소 1명의 리뷰어 승인을 받은 후 merge한다.
- merge는 자동화하지 않고 팀원이 직접 수행한다.
- force push를 사용하지 않는다.
- 테스트가 실패한 상태로 merge하지 않는다.
- 다른 개발자 담당 영역을 수정해야 하면 먼저 변경 의도를 공유한다.

---

## 3. 작업 시작 절차

작업 시작 전 반드시 최신 `feature`를 받는다.

```bash
git switch feature
git pull origin feature
```

다른 팀원이 `feature`에 변경을 push했을 수 있으므로 오래된 local `feature`에서 개발을 시작하지 않는다.

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
<type>: <작업 내용> #<이슈번호>
```

`<작업 내용>`은 한글 또는 영어를 모두 허용한다.

예:

```text
feat: 로그인 API 구현 #34
feat: implement login API #34
fix: 로그인 토큰 오류 수정 #52
fix: fix token expiration issue #52
docs: API 문서 수정 #53
refactor: 사용자 조회 로직 정리 #54
test: 로그인 서비스 테스트 추가 #55
chore: Gradle 설정 수정 #56
```

이슈 번호는 실제 이슈 번호를 사용하고 임의로 만들지 않는다.

---

## 5. PR 제목 규칙

`feature -> develop` PR 제목 형식:

```text
<type>: <작업 내용> #<이슈번호>
```

`<작업 내용>`은 한글 또는 영어를 모두 허용한다.

예:

```text
feat: 로그인 API 구현 #34
feat: login #34
fix: 로그인 토큰 오류 수정 #52
fix: token expiration #52
docs: API 문서 수정 #53
```

예를 들어 로그인 API 작업은 다음과 같다.

```text
현재 브랜치
feature

Commit
feat: 로그인 API 구현 #34

PR Title
feat: login #34

PR Target
develop
```

---

## 6. Push 규칙

개발자는 `feature`에 push한다.

push 전 반드시 remote 최신 상태를 확인한다.

```bash
git switch feature
git pull origin feature
```

충돌이 없고 테스트가 통과한 뒤:

```bash
git push origin feature
```

금지:

```bash
git push origin develop
git push origin main
git push --force
git push --force-with-lease
```

`develop`, `main`은 PR을 통해서만 변경한다.

---

## 7. feature 동시 작업 규칙

두 명의 개발자가 같은 `feature` 브랜치를 사용하므로 push 전에 반드시 최신 상태를 반영한다.

권장 흐름:

```bash
git switch feature
git pull origin feature

# 개발

./gradlew test

git pull origin feature
# 충돌이 있으면 해결하고 다시 테스트

git push origin feature
```

다른 개발자의 변경과 충돌하면 임의로 덮어쓰지 않는다.

충돌 파일에 상대 개발자의 담당 로직이 포함되어 있다면 먼저 의도를 확인한다.

---

## 8. PR 흐름

### feature -> develop

일반 개발 완료 후:

```text
feature
   ↓ PR
develop
```

PR 조건:

- 최소 1명 승인
- 필요한 테스트 통과
- conflict 없음
- Secret 없음
- DB/API/Dependency 변경 여부 표시
- 관련 이슈 번호 명시

### develop -> main

다음 배포 준비가 완료되면:

```text
develop
   ↓ PR
 main
```

이 PR은 배포 전 최종 통합 PR이다.

확인:

- 주요 테스트 통과
- Flyway migration 검토
- 운영 설정 검토
- 배포 범위 확인
- 팀원 합의

---

## 9. PR 본문 기본 형식

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
- [ ] Secret이 포함되지 않았습니다.
- [ ] API 변경 여부를 확인했습니다.
- [ ] DB 변경 여부를 확인했습니다.
```

---

## 10. 리뷰 및 Merge 규칙

- PR은 최소 1명 승인을 받아야 한다.
- 작성자는 자기 PR을 승인한 것으로 간주하지 않는다.
- 리뷰 의견이 남아 있으면 처리 후 merge한다.
- 리뷰 후 중요한 변경이 추가되면 다시 리뷰한다.
- merge는 사람이 직접 수행한다.
- auto-merge는 사용하지 않는다.

권장 GitHub 보호 설정:

### feature

- Block force pushes
- Prevent deletion

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

## 11. Database / JPA / Flyway 기본 원칙

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

다음 값은 사용하지 않는다.

```text
ddl-auto=update
ddl-auto=create
ddl-auto=create-drop
```

---

## 12. Flyway Migration 규칙

migration 파일 형식:

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

- DB schema 변경은 새로운 migration으로 작성한다.
- 이미 공유되거나 적용된 migration은 수정하지 않는다.
- Workbench에서 직접 수정한 DB 상태를 최종 결과로 사용하지 않는다.
- migration 작성 전 최신 `feature`의 migration version을 확인한다.
- version 번호를 중복 사용하지 않는다.
- DB 변경이 있는 PR에는 migration 파일과 변경 내용을 기록한다.

---

## 13. 동시에 Flyway 작업할 때

현재 최신 version이 `V8`이라면 예:

```text
개발자 A -> V9
개발자 B -> V10
```

version을 서로 확인한 후 작업한다.

같은 version이 생긴 경우:

- 아직 공유 전이면 번호를 조정한다.
- 이미 `feature`에 반영된 version은 수정하지 않는다.
- 다른 변경은 새로운 version으로 작성한다.

공용/운영 DB는 초기화하지 않는다.

---

## 14. 기존 Migration 수정 기준

수정 가능:

```text
아직 본인만 작성
AND
다른 개발자가 적용하지 않음
AND
remote feature에 공유되지 않음
```

다음부터 수정 금지:

```text
다른 개발자가 적용함
OR
remote feature/develop/main에 반영됨
```

이후 변경은 새로운 migration으로 작성한다.

---

## 15. 로컬 DB 초기화 규칙

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

## 16. 테스트 규칙

기본:

```bash
./gradlew test
```

빌드 영향이 큰 변경:

```bash
./gradlew clean build
```

DB 관련 검증이 필요한 경우:

```bash
docker compose up -d
./gradlew test
```

규칙:

- 기능 변경 시 관련 테스트를 추가/수정한다.
- 테스트 실패를 숨기지 않는다.
- 실패 테스트를 삭제해서 build를 통과시키지 않는다.
- 테스트 실패 상태로 PR merge하지 않는다.
- 실행하지 않은 테스트를 실행했다고 기록하지 않는다.

---

## 17. Secret / 환경변수 규칙

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

## 18. 다른 개발자 담당 영역 수정

다른 개발자가 담당한 기능을 수정해야 하면:

1. 변경 이유를 공유한다.
2. 영향 범위를 설명한다.
3. 충돌 가능성이 있는 파일을 알린다.
4. 필요한 경우 함께 설계를 확인한다.

특히 다음 영역은 사전 공유를 권장한다.

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

## 19. 금지 사항

- `develop`, `main`에서 직접 기능 개발
- `develop`, `main` 직접 push
- force push
- PR 없이 `develop`, `main` 변경
- 승인 없이 merge
- 실패 테스트 무시
- 기존 공유 migration 수정
- Workbench 직접 수정으로 DB 작업 종료
- Secret commit
- 다른 개발자 변경사항 무단 덮어쓰기
- branch protection 우회
- 자동 PR merge

---

## 20. 일일 작업 흐름

### 작업 시작

```bash
git switch feature
git pull origin feature
```

### 개발

`feature`에서 작업한다.

### 검증

```bash
./gradlew test
```

### Commit

```bash
git add <변경 파일>
git commit -m "feat: 로그인 API 구현 #34"
```

### Push 전 최신화

```bash
git pull origin feature
```

충돌이 발생하면 해결 후 테스트를 다시 수행한다.

### Push

```bash
git push origin feature
```

### PR

```text
feature -> develop
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

PR을 생성하고 팀원 검토 후 수동 merge한다.

---

## 21. PR 생성 전 체크리스트

### feature -> develop

- [ ] Issue 번호가 맞는가
- [ ] Commit 메시지가 규칙에 맞는가
- [ ] PR 제목이 규칙에 맞는가
- [ ] 최신 remote `feature`가 반영되었는가
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

## 22. 핵심 요약

```text
브랜치는 3개만 유지
feature / develop / main

개발
feature

일반 PR
feature -> develop

배포 PR
develop -> main

Commit
feat: 로그인 API 구현 #34

PR Title
feat: login #34

PR 최소 1명 승인
Merge는 수동
force push 금지

DB 변경은 Flyway
공유된 migration 수정 금지
ddl-auto=validate

테스트 실패 상태 merge 금지
Secret commit 금지
```
