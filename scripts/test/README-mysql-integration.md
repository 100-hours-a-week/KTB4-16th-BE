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

`MatchConcurrencyMysqlIntegrationTest`는 실제 MySQL 8.4에서 서로 다른 트랜잭션의 동시 매칭 요청을 실행해 하나의 WAITING 후보가 두 Match에 중복 배정되지 않는지 검증합니다. 현재 재현에서는 후보 잠금 조회 중 MySQL 오류 1213(deadlock)이 발생합니다. 이는 해결된 동작이 아니라 현재 확인된 실패이므로, 테스트 통과 또는 동시성 보장이 완료된 것으로 해석하지 않습니다.

원인과 수정 방안은 검토 중입니다. FIFO 순서를 바꾸는 `SKIP LOCKED` 적용 여부도 확정되지 않았으며, deadlock retry나 다른 잠금 전략은 아직 구현되지 않았습니다.
