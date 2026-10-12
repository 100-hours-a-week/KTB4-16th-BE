# 월간 리포트 500명 단위 처리와 실패 복구

관련 작업: [#207](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/207) · [백엔드 PR #90](https://github.com/100-hours-a-week/KTB4-16th-BE/pull/90)

## 목적과 결정

월간 리포트 준비를 사용자 500명 단위의 독립 트랜잭션으로 처리한다. 500명은 처리 속도나 메모리 사용량에서 최적이라고 입증된 값이 아니다. 1,000명보다 한 번에 롤백될 수 있는 사용자 범위를 줄이기 위해 선택한 운영 기준이다.

여기서 500명은 **DB 집계·저장 트랜잭션의 사용자 수**다. k6 동시 사용자 수, 조회 쿼리의 IN 조건 크기, AI 배치 요청 크기를 뜻하지 않는다. 집계 결과는 사용자별로 계산하되 각 사용자의 월간 리포트와 기분 통계를 같은 묶음 트랜잭션에서 저장한다.

## 처리 흐름

1. `MonthlyReportGenerationService.generate(month)`가 트랜잭션 밖에서 준비를 시작한다.
2. `MonthlyReportPreparationChunkService.findActiveUserIds()`가 대상 월의 활성 사용자 ID를 읽는다.
3. `MonthlyReportPreparationService.prepare()`가 ID 목록을 최대 500명씩 나눈다.
4. 각 묶음을 `MonthlyReportPreparationChunkService.prepareChunk()`에 위임한다. 이 메서드는 `REQUIRES_NEW` 트랜잭션에서 사용자별 집계와 월간 리포트·기분 통계를 저장한다.
5. 모든 묶음이 커밋된 뒤 준비된 사용자 ID를 한 번의 AI 배치 요청으로 전달한다. DB 묶음 저장 중 실패하면 예외가 전파되어 AI 요청 단계에 도달하지 않는다.
6. AI 접수 후 상태를 `PROCESSING`으로 바꾸고, 콜백에서 결과를 `COMPLETED` 또는 `FAILED`로 저장한다.

핵심 코드는 [`MonthlyReportPreparationService.java`](../src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationService.java#L28)와 [`MonthlyReportPreparationChunkService.java`](../src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationChunkService.java#L47)에 있다. 오케스트레이터와 각 청크가 별도 Spring 서비스 빈이므로 청크 호출이 Spring 트랜잭션 프록시를 거친다. 상태 재사용은 [청크 서비스 57–75행](../src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationChunkService.java#L57), AI 요청 실패 처리는 [`MonthlyReportGenerationService.java`](../src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportGenerationService.java#L27), 콜백 오류 기록은 [`MonthlyReportAiCallbackController.java`](../src/main/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackController.java#L26), 로컬 재실행 경로는 [`LocalMonthlyReportGenerationController.java`](../src/main/java/com/ktb4/team16/mulo/report/controller/LocalMonthlyReportGenerationController.java#L20)에 있다.

## 이전 방식과 변경된 방식

| 구분 | 이전 방식 | #207 변경 방식 |
| --- | --- | --- |
| 집계 준비 트랜잭션 | 전체 사용자 준비가 하나의 경계에 참여 | 사용자 최대 500명마다 독립 `REQUIRES_NEW` 경계 |
| 실패 시 DB 결과 | 전체 트랜잭션이 롤백될 수 있음 | 실패한 묶음은 롤백되고 앞서 커밋된 묶음은 유지 |
| AI 요청 시점 | 전체 집계 준비가 끝난 뒤 | 모든 500명 묶음이 끝난 뒤 한 번 요청 |
| 사용자별 통계 | 사용자별 집계 | 사용자별 집계 유지; 묶음은 저장·롤백 경계만 제한 |
| 빈 집계 결과 | 삭제 등 경합으로 0건이 될 때 저장 오류 위험 | 기록 건수가 0이면 리포트와 평균 기분을 만들지 않고 건너뜀 |

청크 실패는 자동으로 다음 청크로 진행하지 않는다. 실패를 기록하고 예외를 다시 던져 전체 생성 요청을 중단한다. 앞서 성공한 청크의 DB 커밋은 남고 실패 청크의 트랜잭션은 롤백된다.

## 500명 선택 근거와 측정 해석

로컬 8 VU 시험에서 조회 묶음 1,000명과 INSERT 묶음 500행을 고정하고 트랜잭션 크기 500명·1,000명만 비교했다. 각 조건 5쌍의 측정에서 다음 결과가 나왔다.

| 지표 | 트랜잭션 1,000명 | 트랜잭션 500명 | 해석 |
| --- | ---: | ---: | --- |
| HTTP p95 측정값 중앙값 | 2,203.632 ms | 2,100.418 ms | 500명이 중앙값은 낮았지만 5쌍 중 3쌍에서만 낮았다. |
| 처리량 | 2.776 req/s | 2.690 req/s | 500명에서 더 낮았다. |
| 묶음 커밋 수 | 10 | 20 | 500명은 커밋 경계가 두 배였다. |
| Hikari acquire 호출 / 누적 대기 | 384회 / 26.173초 | 704회 / 29.280초 | 500명에서 호출이 늘었고 누적 대기도 증가했다. |
| MySQL CPU 누적 | 21.728초 | 19.650초 | 방향만으로 유의미한 자원 개선을 결론내릴 수 없다. |
| MySQL working set peak | 1,985.652 MiB | 1,991.535 MiB | 사실상 비슷했다. |
| JVM 표본 heap peak | 199.877 MiB | 204.262 MiB | 500명에서 낮아지지 않았다. |

따라서 **500명이 더 빠르거나 CPU·메모리를 적게 쓴다는 성능 주장은 하지 않는다.** 실패 주입 시험은 500명 설정에서 실패 트랜잭션의 최대 대상이 1,000명 설정보다 작아지고, 앞서 커밋한 결과가 보존되는 동작을 확인했다. 현재 선택 이유는 그 롤백 범위를 줄이는 데 있다. 실제 측정 조건과 표본 한계는 성능 조사 통합 기록(작업 #193, 2026-10-11)의 `monthly-report-performance-and-chunking-summary-2026-10-11.md` 및 관련 원시 결과를 참조한다.

500명 묶음은 전체 메모리를 500명으로 제한하지 않는다. 현재 구현은 활성 사용자 ID 목록과 AI 전달용 준비 사용자 ID 목록을 전체 대상으로 메모리에 보유한다. 메모리 상한을 보장하려면 ID 페이지 조회나 스트리밍 등 별도 변경과 측정이 필요하다.

## 실패 로그와 재실행 동작

| 실패 지점 | 로그에 남기는 값 | 동작 |
| --- | --- | --- |
| 집계 청크 트랜잭션 실패 | 월, 1부터 시작하는 청크 번호, 실제 청크 크기, 예외 stack trace | 현재 청크 롤백 후 예외 전파; 이전 청크 커밋은 유지 |
| AI 배치 요청 실패 | 월, 대상 수, 실패 유형 | 집계 스냅샷은 보존하고 상태를 `FAILED`로 전환 시도 |
| `FAILED` 상태 저장 실패 | 월, 대상 수, 실패 유형, 롤백 예외 | 예외 전파 |
| AI가 접수된 뒤 `PROCESSING` 상태 저장 실패 | 월, 대상 수, AI `jobId`, 실패 유형, 롤백 예외 | 예외 전파; `jobId`는 로그에만 있고 DB에는 저장되지 않음 |
| 콜백 전체 트랜잭션 롤백 | 연·월, 결과 수, 예외 stack trace | 콜백의 DB 변경 전체 롤백 |
| 콜백의 개별 사용자 AI 실패 | 연·월, 사용자 ID, 정제한 오류 코드 | 해당 리포트 상태 `FAILED`; 완료 리포트는 유지 |

이 흐름에서 추가한 로그는 운영자가 월과 청크 위치, 실패 유형을 찾도록 돕는다. AI 토큰, 회고 문장, 콜백 본문은 기록하지 않는다. 개별 오류 코드는 영문·숫자·`_ . -`만 허용하고 64자로 제한한다.

현재 구현은 **DB에 청크별 실행 ID·완료 체크포인트를 저장하지 않는다.** `local` 프로필의 `POST /api/internal/monthly-reports/generate?year=YYYY&month=MM`는 월 전체 생성을 다시 시작하는 도구이며 특정 청크만 선택하는 API가 아니다. 재실행 시 이미 저장된 월간 리포트의 상태를 확인한다. `PENDING`은 AI 전달 대상에 다시 포함하고, `FAILED`는 회고 상태를 `PENDING`으로 돌려 다시 전달한다. 그 밖의 기존 상태는 건너뛴다. 신규 데이터가 남은 사용자는 다시 집계한다.

AI 요청에는 현재 멱등 키가 없고, 반환된 `jobId`도 영속화하지 않는다. 따라서 AI가 요청을 접수한 뒤 백엔드의 `PROCESSING` 상태 저장이 실패한 상황에서는 로그의 `jobId`를 확인한 뒤 AI 작업 상태를 대조해야 한다. 계약 확인 없이 즉시 월 생성을 다시 실행하면 동일 사용자에 대한 AI 작업이 중복될 수 있다. 이 PR은 청크 트랜잭션 분리와 로그를 제공하며, 영속 체크포인트·AI 요청 outbox·멱등 전달을 구현하지 않는다.

## 검증 결과

- `./gradlew test --tests '*MonthlyReport*Test' checkstyleMain checkstyleTest` 통과. 현재 JUnit XML 기준 월간 리포트 테스트는 27개이며 실패 0건이다.
- [`MonthlyReportPreparationTransactionTest.java`](../src/test/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationTransactionTest.java#L29)는 1,001명 입력에서 500·500·1명 트랜잭션 경계와 두 번째 묶음 실패 시 앞 묶음 커밋 보존을 검증한다. 이 테스트는 목 저장소와 기록용 트랜잭션 매니저를 사용한다. 별도의 로컬 MySQL 실패 주입·재시도 측정 결과는 작업 #193 조사 자료에 있다.
- PR 작성 전 전체 테스트 실행은 560개 중 18개 실패했다. 실패는 `RecordCreateConcurrencyMysqlIntegrationTest`의 Hikari 최대 풀 크기 사전조건에서 발생했다. 테스트는 최소 10을 요구하지만 해당 실행의 로컬 설정은 3이었다. 이번 문서 변경에서는 전체 테스트를 다시 실행하지 않았다.
- DB 스키마, API 계약, 의존성은 변경하지 않았다.

## 공식 참고 자료

- [Spring Framework: 트랜잭션 롤백](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html) — 기본 설정에서 `RuntimeException`과 `Error`가 롤백을 유발하는 규칙.
- [Spring Framework: 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html) — `REQUIRES_NEW`가 기존 트랜잭션과 독립된 물리 트랜잭션을 사용하는 규칙.
- [MySQL 8.4: InnoDB 트랜잭션 관리 최적화](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-transaction-management.html) — 커밋 빈도와 큰 변경의 롤백 부담 사이의 균형. 특정 500명 단위를 권장하지 않는다.
