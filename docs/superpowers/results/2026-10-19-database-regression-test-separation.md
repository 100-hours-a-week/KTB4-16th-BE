# DB 관리자 회귀 테스트 분리

## 목적과 변경 전후

일반 작업의 `test` 결과에 관리자 DB 설정이 없는 5개가 skip으로 나타나 실패처럼 보이는 혼동을 제거한다. 테스트 자체의 검증은 보존한다.

| 항목 | 기존 | 변경 |
| --- | --- | --- |
| 소스 위치 | `src/test` | 두 클래스만 `src/integrationTest`로 이동 |
| 일반 `test` | 관리자 설정이 없으면 5개 skip | 이 5개를 수집하지 않음 |
| 잠금 검사 | test 필터 + 환경변수 조건 | `friendDeadlockTest`, 2개 실행 |
| migration 검사 | assumption 미충족 시 3개 중단 | `notificationMigrationTest`, 3개 실행 |
| 전용 검사 환경 누락 | skip | 필요한 변수명을 표시하고 실패 |
| CI | 데드락 전용 검사만 구성 | 네 경로 모두 잠금·migration 5개 실행 |
| 실패 artifact | 일반 test 보고서만 | 모든 테스트 태스크 보고서 |

`build.gradle`은 기존 integrationTest 소스 출력과 의존성을 재사용하는 두 Test 태스크를 추가한다. 클래스 필터가 잘못되면 실패하며, 관리자 환경의 URL/USERNAME/PASSWORD를 실행 전에 확인한다. 데드락 클래스의 조건부 활성화 annotation만 제거하고 기존 검증 본문은 그대로 유지했다. migration 클래스는 assumption을 명시적인 로컬 URL·계정 검증으로 교체했다.

## 최초 작업 폴더 검증

- 관리자 환경 없이 `friendDeadlockTest --rerun-tasks`: 예상대로 실패하며 `MULO_DEADLOCK_IT_ADMIN_URL 환경변수가 필요합니다.` 메시지 확인. 실제 테스트를 실행하지 않은 성공으로 오인하지 않는다.
- `friendDeadlockTest notificationMigrationTest --rerun-tasks`: 일회용 MySQL 8.4.11에서 2개 + 3개 = **5개 통과, 실패·오류·skip 0**. 태스크별 XML에서 수를 확인했다.
- 전용 실행 동안 enabled InnoDB `lock_deadlocks`: 0 → 0.
- dev/prod YAML 파싱과 각 2개 전용 실행 단계·전체 테스트 실패 보고서 경로 확인.
- `git diff --check` 통과.
- 관리자 전용 환경변수를 제거한 `checkstyleMain test jacocoTestCoverageVerification --rerun-tasks`: **일반 844개 통과, 실패·오류·skip 0**, 종료 코드 0. XML에 두 관리자 클래스가 없음을 확인했다. Checkstyle·60% 커버리지 게이트도 통과했다. 이 결과는 현재 다른 작업의 미커밋 코드도 있는 작업 폴더 전체를 대상으로 한다.

원시 실행 자료: `/tmp/mulo-db-test-separation-20261019/`. 최초 집계 스크립트가 이전 다른 태스크의 XML까지 합산한 값은 사용하지 않고 `friendDeadlockTest`·`notificationMigrationTest` 디렉터리의 XML만 집계하여 5개를 확인했다.

## 범위

운영 코드·API·DB 스키마·Flyway·의존성 변경 없음. 현재 다른 작업 브랜치의 사용자 수정 및 기존 staged 명세를 보존했다. 최초 정리 단계에서는 업로드하지 않았으며, 이후 사용자 요청으로 #248 후속 신규 PR을 준비한다. Git에서 기존 파일은 삭제, 새 경로는 미추적으로 보일 수 있으나 내용상 이동이며 이후 선택 staging 시 rename으로 인식할 수 있다.

실행 방법과 환경변수 의미: [DB 회귀 검사 안내](../../testing/database-regressions.md).

## 신규 PR 후보 재검증 (2026-10-19)

- 브랜치: `test/database-regressions-248`, 대상: `develop`.
- 기준: 최신 `origin/develop`의 `1d1d8243a93ffc8bb9995cd7ee1c865b5ca9f26b`. 다른 작업의 수정은 포함하지 않았다.
- 최신 develop의 일반 DB 격리 및 기존 별도 테스트 구성은 유지했다. 검증은 CI 경로(`GITHUB_ACTIONS=true`)와 일회용 MySQL 3307을 사용했다.
- `checkstyleMain test jacocoTestCoverageVerification --rerun-tasks`: **833개 통과, 실패·오류·skip 0**, 종료 코드 0. 일반 XML에 두 관리자 클래스가 없음을 확인했다.
- `friendDeadlockTest notificationMigrationTest --rerun-tasks`: **5개 통과, 실패·오류·skip 0**, 종료 코드 0. MySQL 8.4.11, REPEATABLE READ, InnoDB lock_deadlocks 0 → 0.
- 관리자 환경 없이 `friendDeadlockTest --rerun-tasks`: 필요한 변수명이 표시되는 예상 실패 확인. 재실행 옵션 없이 UP-TO-DATE라면 검사는 실행되지 않는다.
- 전체 `integrationTest` 및 `clean build`는 실행하지 않았다.
- 최초 844개는 다른 작업이 섞인 이전 폴더의 결과이므로 신규 PR 검증 결과로 사용하지 않는다.
- 원시 자료: `/tmp/mulo-db-test-pr-20261019/`.
