# MySQL 통합 테스트

실제 MySQL 8.4와 Flyway schema가 필요한 통합 테스트를 개발 DB와 분리해 실행합니다. Docker가 volume 없는 임시 컨테이너를 만들고, 테스트가 끝나거나 실패하면 컨테이너와 데이터가 제거됩니다. 자격 증명은 실행 중 생성되어 프로세스 환경으로만 전달됩니다.

필요 조건: Docker CLI와 실행 중인 Docker daemon, Java 21, OpenSSL.

모든 통합 테스트 실행:

```bash
./scripts/test/run-mysql-integration.sh
```

특정 테스트 실행:

```bash
./scripts/test/run-mysql-integration.sh --tests '*MatchConcurrencyMysqlIntegrationTest'
```

## 매칭 동시성 테스트

초기에는 동시 요청에서 같은 WAITING 후보가 둘 이상의 Match에 배정되는 일을 막기 위해 후보 MatchRequest에 비관적 쓰기 잠금을 적용했습니다. 실제 MySQL 동시성 검증에서는 각 요청이 자기 MatchRequest를 저장한 뒤 상대 후보를 잠그려는 순서가 엇갈려 MySQL 1213 deadlock이 발생했습니다.

이를 개선하기 위해 후보 조회 전에 동일 VoteQuestion 행을 `PESSIMISTIC_WRITE`로 잠가 같은 질문의 매칭 요청이 잠금을 획득하는 순서를 통일했습니다. 그 뒤 FIFO 순서로 WAITING 후보를 조회하고 후보 MatchRequest에도 비관적 쓰기 잠금을 적용합니다.

2026-10-12에 MySQL 8.4 및 Flyway V8까지 적용된 격리 DB에서 `./scripts/test/run-mysql-integration.sh`를 실행했습니다. 전체 integration suite 23개가 통과했습니다. `MatchConcurrencyMysqlIntegrationTest`는 동시 요청 시나리오를 10회 반복했고 10회 모두 통과했습니다. 실행 중 MySQL 1213 deadlock이나 중복 매칭은 재현되지 않았으며, 각 반복에서 Match 1개, 동일 Match에 연결된 두 요청자, 남은 요청자의 WAITING 및 `match_id = NULL` 조건이 확인됐습니다. Repository 테스트에서는 동일 질문·반대 선택지·WAITING 조건과 `created_at ASC`, 동률 시 `match_request_id ASC` FIFO가 확인됐고, 만료 통합 테스트에서는 WAITING 만료와 MATCHING/MATCHED 보존 및 진행 중인 매칭과 만료 작업의 경합을 검증했습니다.

이 결과는 현재 테스트 시나리오에서의 관찰 결과이며 deadlock이 모든 가능한 실행에서 발생하지 않는다는 보장은 아닙니다. V8 반영 이후 테스트 환경 guard는 성공한 Flyway migration이 V8까지 적용됐는지 확인합니다.
