# adapter-pkfare: dev helper

PKFARE calls us in PKFARE's format, and we serve Verteil content through Entry-Gate (NDC 16.1 JSON). Only SEARCH
(→ AirShopping) exists so far. More detail is in `README.md` and `CLAUDE.md`.

## Components

| Where | What |
|---|---|
| `adapter-pkfare-server` | Spring Boot app: `OfferController` (`POST /search`), `ConfigurationController` (`POST /reload`), error handler |
| `adapter-pkfare-service/.../offer/search` | Search chain: auth → cache → RQ convert → Entry-Gate → RS convert |
| `adapter-pkfare-service/.../client` | `EntryGateClient` (+ OAuth2 token, Caffeine/Redis token cache) |
| `adapter-pkfare-service/.../config` | In-process configurator over `agency_adapter_tbls.pkfare_configs`; `config/sync` = Kafka reload broadcast |
| `adapter-pkfare-service/src/main/database` | Liquibase changelog (run separately via `run-liquibase.sh`) |
| `adapter-pkfare-acceptance` | Cucumber E2E: bootJar + WireMock (Entry-Gate) + local Postgres |

## Prerequisites

- JDK 25
- `CODEARTIFACT_AUTH_TOKEN` exported (add `--offline` once the Gradle cache is warm)
- Local Postgres `vdc_db` with `agency_adapter_tbls.pkfare_configs`, readable by `pkfare_user`/`pkfare123`
- Optional: Redis (`localhost:6379`) and Kafka

## Commands

```sh
./gradlew clean build                                         # compile + tests + bootJar
./gradlew :adapter-pkfare-service:test --tests '*SearchRSConverterTest'
./gradlew :adapter-pkfare-acceptance:runAcceptanceTests --continue
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar   # default profile = local

curl -s -H 'Content-Type: application/json' \
  --data @adapter-pkfare-service/src/test/resources/search/PKFARE_SearchRQ.json localhost:9022/search
curl -X POST localhost:9022/reload          # re-read pkfare_configs (all pods if Kafka is set); unauthenticated, keep it off the ingress
curl localhost:7045/actuator/health
```

## Service configuration

All in `adapter-pkfare-server/src/main/resources/`:
- `application.yaml`: the base, used locally.
- `application-eks-{dev,qa,stage,prod-in,prod-eu,prod-oc}.yaml` and `application-dr-{in,eu,oc}.yaml`: only the DB
  and jasypt overrides.
- `application-acceptance.yaml`: acceptance runs.

| Key | Env var | Meaning |
|---|---|---|
| `server.port` / `management.server.port` | | API 9022 / actuator 7045 |
| `server.forward-headers-strategy: native` | | trust X-Forwarded-For only from internal proxies (caller IP is logged) |
| `server.tomcat.accesslog.*` | | access log in `/var/log/vdc/adapter-pkfare` |
| `spring.threads.virtual.enabled` | | virtual threads for request handling |
| `spring.lifecycle.timeout-per-shutdown-phase` | | graceful shutdown wait (180 s) |
| `connector.http.poolMax` / `poolTimeoutMillis` | | Entry-Gate HTTP pool size / wait for a pooled connection |
| `connector.http.connectTimeoutMillis` / `responseTimeoutMillis` | | Entry-Gate connect / read timeout (per-call `TIMEOUT_MILLIS` from DB still applies) |
| `connector.monitor.idleTime` / `task.interval` | | idle-connection eviction (s) / its check interval (ms) |
| `adapter.search-cache.max-weight` | | per-pod SEARCH cache bound (results + flights) |
| `agency-adapter.datasource.{url,username,password}` | `DB_URL`, `DB_USER`, `DB_ENCRYPTED_PASSWORD` | the only DB (SELECT on `pkfare_configs`) |
| `jasypt.encryptor.password` | `JASYPT_ENCRYPTOR_PASSWORD` | decrypts `DB_ENCRYPTED_PASSWORD` (env profiles) |
| `ordermanagement.redis.{host,port,database,ssl.enabled}` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_SSL_ENABLED` | shared Entry-Gate token cache; optional |
| `kafka.bootstrapAddress` | `KAFKA_URL` | brokers for the cluster-wide `/reload`; blank = per-pod reload |
| `kafka.configurator-internal.topic` | `KAFKA_TOPIC_ADAPTER_PKFARE_CONFIGURATOR_INTERNAL` | reload topic (default `adapter-pkfare-configurator-internal`) |
| `kafka.configurator-internal.consumer-group` | | group prefix; each pod appends `#<uuid>` |
| `management.endpoints.web.exposure.include` | | health, prometheus, metrics |

Acceptance overrides: `ACCEPTANCE_SERVER_PORT` (9122), `ACCEPTANCE_DB_URL` / `_USER` / `_PASSWORD`, and
`ACCEPTANCE_KAFKA_URL` (empty, so Kafka is off).

Entry-Gate host, credentials, paths, timeouts and vendor codes are **DB rows**, not YAML. See README's
*Configuration* table.
