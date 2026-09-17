# MULO Backend Team Rules

이 문서는 `mulo-be` 저장소에서 백엔드 개발자들이 공통으로 따라야 하는 협업 규칙을 정의한다.

`AGENTS.md`는 AI 코딩 에이전트 전용 규칙이고, 이 `RULE.md`는 사람 개발자의 Git / PR / DB / Flyway / 테스트 협업 규칙이다.

---

## 1. 브랜치 역할

MULO 백엔드는 아래 3개의 고정 브랜치를 장기간 유지한다.

```text
main
develop
feature
```

### main
- 현재 배포 중이거나 배포 가능한 안정 버전이다.
- 직접 기능 개발하지 않는다.
- 직접 push하지 않는다.
- `develop`에서 검증이 끝난 변경만 PR을 통해 반영한다.

### develop
- 다음 `main` 배포를 준비하는 통합/검증 브랜치다.
- 직접 기능 개발하지 않는다.
- 직접 push하지 않는다.
- `feature`에서 개발이 충분히 모이고 검증된 변경을 PR을 통해 반영한다.

### feature
- 현재 개발 중인 기능들을 모으는 개발 통합 브랜치다.
- 장기간 유지한다.
- 개별 개발자가 직접 기능 구현을 하는 브랜치는 아니다.
- 실제 작업 브랜치의 변경을 PR로 받아 통합한다.
- 직접 push하지 않는다.

기본 흐름:

```text
작업 브랜치
    ↓ PR
feature
    ↓ PR
develop
    ↓ PR
main
    ↓
배포
```

---

## 2. 작업 브랜치 규칙

실제 개발 작업은 `feature` 브랜치에서 새 작업 브랜치를 만들어 진행한다.

허용되는 작업 브랜치 형식:

```text
feat/{기능명}-{이슈번호}
fix/{기능명}-{이슈번호}
docs/{기능명}-{이슈번호}
style/{기능명}-{이슈번호}
refactor/{기능명}-{이슈번호}
test/{기능명}-{이슈번호}
chore/{기능명}-{이슈번호}
```

예:

```text
feat/security-setup-51
feat/login-34
fix/token-expiration-52
docs/api-guide-53
style/code-format-54
refactor/user-service-55
test/auth-service-56
chore/gradle-config-57
```

작업 브랜치 이름에는 `#`를 사용하지 않는다.

---

## 3. 브랜치 Prefix 의미

| Prefix | 의미 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 기능 변경 없음 |
| `refactor` | 리팩토링 |
| `test` | 테스트 코드 |
| `chore` | 빌드/패키지 관리 |

브랜치 prefix와 commit type은 가능하면 동일한 의미를 사용한다.

---

## 4. 작업 시작 절차

새 작업은 항상 최신 `feature` 브랜치를 기준으로 시작한다.

```bash
git switch feature
git pull origin feature
git switch -c feat/{기능명}-{이슈번호}
```

예:

```bash
git switch feature
git pull origin feature
git switch -c feat/security-setup-51
```

버그 수정이라면:

```bash
git switch feature
git pull origin feature
git switch -c fix/token-expiration-52
```

---

## 5. 기본 원칙

- `main`, `develop`, `feature`에서 직접 기능 개발하지 않는다.
- `main`, `develop`, `feature`에 직접 push하지 않는다.
- 실제 개발은 별도 작업 브랜치에서 진행한다.
- 변경사항은 PR을 통해 다음 단계 브랜치로 반영한다.
- PR은 최소 1명의 리뷰어 승인을 받은 후 merge한다.
- force push를 사용하지 않는다.
- 앱이 정상적으로 실행되지 않거나 주요 테스트가 실패한 상태로 merge하지 않는다.
- 다른 개발자가 담당 중인 영역을 수정해야 하면 먼저 변경 의도를 공유한다.
- merge가 끝난 작업 브랜치는 삭제한다.
- `main`, `develop`, `feature`는 삭제하지 않는다.

---

## 6. 커밋 컨벤션

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
feat: login #34
fix: 중복 이메일 검증 오류 수정 #18
refactor: 사용자 조회 로직 분리 #25
test: 로그인 서비스 테스트 추가 #34
chore: MySQL 개발 설정 정리 #12
```

이슈 번호는 실제 이슈 번호를 사용하고 임의로 만들지 않는다.

---

## 7. Push 규칙

작업 브랜치만 push한다.

최초 push:

```bash
git push -u origin feat/security-setup-51
```

이후:

```bash
git push origin feat/security-setup-51
```

금지:

```bash
git push origin main
git push origin develop
git push origin feature
git push --force
git push --force-with-lease
```

보호 브랜치 규칙을 우회하지 않는다.

---

## 8. PR 흐름

### 8.1 작업 브랜치 → feature

개별 작업이 완료되면:

```text
feat/*, fix/*, docs/*, style/*,
refactor/*, test/*, chore/*
             ↓ PR
           feature
```

PR 전에는 최신 `feature`를 작업 브랜치에 반영한다.

```bash
git fetch origin
git switch feat/security-setup-51
git merge origin/feature
```

### 8.2 feature → develop

여러 개발 작업이 `feature`에 모이고 다음 배포 후보로 올릴 시점이 되면:

```text
feature
   ↓ PR
develop
```

이 단계에서는 통합 테스트, DB migration, API 변경, 설정 변경 등을 다시 확인한다.

`feature` 브랜치는 merge 후에도 유지한다.

### 8.3 develop → main

배포 전 검증이 완료되면:

```text
develop
   ↓ PR
 main
   ↓
 배포
```

`develop` 브랜치는 merge 후에도 유지한다.

---

## 9. PR 공통 규칙

모든 PR은 다음 조건을 만족해야 한다.

- 최소 리뷰어 1명 승인
- unresolved conflict 없음
- 필요한 테스트 통과
- 대상 브랜치의 최신 변경 반영
- 관련 없는 변경 포함 금지
- Secret 포함 금지
- DB/API/Dependency 변경 여부 명시

PR 제목과 설명은 팀 템플릿을 따른다.

---

## 10. PR 제목 규칙

기본 형식:

```text
<type>: <기능명> #<이슈번호>
```

예:

```text
feat: login #34
fix: token-expiration #52
docs: api-guide #53
refactor: user-service #54
```

### Branch / Commit / PR Title 구분

```text
Branch
feat/login-34

Commit
feat: 로그인 API 구현 #34

PR Title
feat: login #34
```

- Branch는 `<type>/<기능명>-<이슈번호>` 형식을 사용한다.
- Commit은 실제 변경 내용을 설명한다.
- PR Title은 `<type>: <기능명> #<이슈번호>` 형식을 사용한다.

---

## 11. 리뷰 및 Merge 규칙

- PR 작성자는 최소 1명의 승인을 받아야 한다.
- 작성자는 자기 PR을 승인한 것으로 간주하지 않는다.
- 리뷰 의견이 남아 있으면 처리 후 merge한다.
- 리뷰 후 중요한 commit이 추가되면 다시 리뷰한다.
- merge는 자동화하지 않는다.
- 팀원과 merge 여부를 확인한 뒤 수동 merge한다.
- 작업 브랜치는 merge 후 삭제한다.
- `main`, `develop`, `feature`는 유지한다.

가능하면 GitHub에서 `main`, `develop`, `feature`에 다음 보호 규칙을 적용한다.

- Require pull request before merging
- Require at least 1 approval
- Dismiss stale approvals when new commits are pushed
- Require status checks before merging
- Require branch to be up to date before merging
- Block force pushes
- Prevent branch deletion

---

## 12. Merge Conflict 규칙

충돌은 해당 작업 브랜치 작성자가 우선 해결한다.

1. 충돌 파일과 영역을 확인한다.
2. 자동으로 `ours` 또는 `theirs`를 선택하지 않는다.
3. 현재 작업 브랜치와 `feature` 변경 의도를 모두 확인한다.
4. 다른 개발자의 담당 로직이 포함되어 있으면 해당 개발자와 먼저 확인한다.
5. 작업 브랜치에서 해결한다.
6. 해결 후 테스트를 다시 수행한다.
7. 해결된 상태를 push하고 PR을 갱신한다.

`feature`, `develop`, `main`에서 직접 충돌 해결용 개발을 하지 않는다.

---

## 13. Database / Flyway 기본 원칙

DB 스키마 변경 이력의 Source of Truth는 Flyway migration이다.

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
```

핵심 규칙:

- DB schema 변경은 반드시 새로운 migration으로 작성한다.
- 이미 공유되었거나 적용된 migration은 수정하지 않는다.
- MySQL Workbench에서 직접 수정한 상태를 최종 변경으로 인정하지 않는다.
- migration 작성 전 `feature`의 최신 migration version을 확인한다.
- version 번호를 중복 사용하지 않는다.
- migration이 포함된 PR에는 변경 내용을 반드시 적는다.

---

## 15. 동시에 Flyway 작업할 때

현재 `feature`의 최신 migration이 `V8`이라면:

```text
개발자 A -> V9 예약
개발자 B -> V10 예약
```

동시에 같은 version을 사용하지 않도록 서로 확인한다.

이미 두 명이 같은 version을 만든 경우:

- 아직 공유/적용 전이면 번호를 조정한다.
- 이미 한쪽이 `feature`에 merge되었다면 다른 쪽은 새로운 version으로 변경한다.
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
feature/develop/main에 merge되지 않음
```

다음 상태부터는 수정 금지:

```text
다른 개발자가 적용함
OR
feature/develop/main에 merge됨
```

이후 변경은 새 migration으로 보정한다.

---

## 17. 로컬 DB 초기화 규칙

개발 초기 로컬 DB에서 migration version 충돌 등으로 이력이 꼬였을 경우:

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

## 18. 테스트 규칙

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

테스트 실패 상태로 merge하지 않는다.

---

## 19. Secret / 환경변수 규칙

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

## 20. 금지 사항

- `main`, `develop`, `feature`에서 직접 기능 개발
- `main`, `develop`, `feature` 직접 push
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

## 21. 일일 Git 작업 흐름

### 작업 시작

```bash
git switch feature
git pull origin feature
git switch -c feat/{기능명}-{이슈번호}
```

### 개발 후 commit

```bash
git add <변경 파일>
git commit -m "feat: 작업 내용 #34"
```

### Push

```bash
git push -u origin feat/{기능명}-{이슈번호}
```

### PR 전 최신 feature 반영

```bash
git fetch origin
git merge origin/feature
```

### 검증

```bash
./gradlew test
```

### 일반 작업 PR

```text
feat/* / fix/* / docs/* / style/* /
refactor/* / test/* / chore/*
                ↓
              feature
```

최소 1명 리뷰 후 수동 merge한다.

### 통합 단계

```text
feature
   ↓ PR
develop
   ↓ PR
main
```

---

## 22. 작업 종료 체크리스트

### 작업 브랜치 → feature PR 전

- [ ] Issue 번호가 맞는가
- [ ] Branch 이름이 규칙에 맞는가
- [ ] Commit 메시지가 규칙에 맞는가
- [ ] 최신 `feature`가 반영되었는가
- [ ] Merge conflict가 없는가
- [ ] 관련 테스트가 통과했는가
- [ ] Secret이 없는가
- [ ] 관련 없는 변경이 포함되지 않았는가
- [ ] DB 변경 여부를 확인했는가
- [ ] Flyway migration version 충돌이 없는가
- [ ] API 변경 여부를 확인했는가
- [ ] Dependency 변경 여부를 확인했는가

### feature → develop PR 전

- [ ] 통합 테스트가 통과했는가
- [ ] 포함될 기능 범위가 합의되었는가
- [ ] DB/Flyway migration 상태를 확인했는가
- [ ] API 변경 사항을 확인했는가
- [ ] 배포에 포함되면 안 되는 미완성 기능이 없는가

### develop → main PR 전

- [ ] 배포 전 검증이 완료되었는가
- [ ] 운영 설정을 확인했는가
- [ ] DB migration 영향도를 확인했는가
- [ ] 주요 테스트가 통과했는가
- [ ] 팀원과 배포 여부를 합의했는가

---

## 23. 핵심 요약

```text
고정 브랜치: main / develop / feature

main
= 현재 배포 중인 안정 버전

develop
= 다음 main 배포 전 통합/검증 브랜치

feature
= 현재 개발 중인 변경을 모으는 통합 브랜치

작업 브랜치
= feat/*, fix/*, docs/*, style/*,
  refactor/*, test/*, chore/*

작업 흐름
= 작업 브랜치 -> feature -> develop -> main

각 단계는 PR 사용
PR 최소 1명 승인
Merge는 수동
force push 금지

DB 변경은 Flyway
공유된 migration 수정 금지
ddl-auto=validate 유지
테스트 실패 상태 merge 금지
Secret commit 금지
```
