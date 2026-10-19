# Weather record-creation tests

## General tests

Start the isolated general test database and run the normal suite:

```sh
docker compose -f compose.test.yml up -d
./gradlew test
docker compose -f compose.test.yml down
```

The service uses MySQL 8.4, database `mulo_test`, and loopback port 3313. Gradle supplies its local test-only connection settings to the test JVM; it does not read or modify `.env` and does not pass these settings to `bootRun`. `down` stops the container but keeps the dedicated named volume; the schema and test data remain for the next run.

The 18 repeated record-creation concurrency checks stay out of `test` and run against a separate MySQL 8.4 database:

```sh
docker compose -f compose.weather-it.yml up -d
./gradlew recordCreateConcurrencyTest
docker compose -f compose.weather-it.yml down
```

This task uses database `mulo_weather_it` on loopback port 3314, with its own container and named volume. Both Gradle tasks check the expected MySQL container, host port, volume, and healthy status before starting tests. If the matching Compose service is not running, Gradle reports the exact `up -d` command. The Weather task injects its DB settings only into its test JVM; normal `test` does not start or require this database.

The Compose services bind only to `127.0.0.1`; their names, ports, accounts, and volumes are separate from the development MySQL, chat MySQL, Redis, and the older local test containers. Do not add `-v` to `down` unless intentionally deleting that test database's dedicated data volume.
