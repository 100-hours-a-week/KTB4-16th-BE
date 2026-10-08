# Weather MySQL integration test

Run the full test suite with an isolated, disposable MySQL 8.4 database:

```bash
./scripts/test/run-weather-mysql-integration.sh
```

Requirements: Java 21, the project Gradle wrapper, Docker CLI, a running Docker
daemon, and OpenSSL. The script starts a fresh `mysql:8.4` container with no
volume, publishes it only on `127.0.0.1:3307`, and creates the test database
`mulo_weather_it`. It refuses to start if a running container already publishes
port 3307. The integration test independently asserts the JDBC URL and catalog,
and Flyway creates the schema in the disposable database. The container and its
data are removed when the Gradle process exits or is interrupted.

The script generates an ephemeral database password at runtime and does not
print it or write it to a file. It does not read application `.env` files and
does not connect to `mulo` or `mulo_load_test`.

Do not run `RecordCreationWeatherMysqlIntegrationTest` directly without these
environment variables and the isolated database. Its test profile intentionally
has no datasource fallback. CI runners can use this script when Docker is
available; the repository's current pull-request workflow does not invoke the
test suite or this script.
