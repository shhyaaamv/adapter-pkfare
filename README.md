# adapter-pkfare

Reverse integration: exposes PKFARE's *Supplier Content Aggregation Direct Connection API v2.1* so PKFARE can
consume Verteil (VDC) content. Each PKFARE call is authenticated, converted to a VDC NDC 16.1 request, sent to
Entry-Gate and the answer converted back to PKFARE's format.

```
PKFARE ──POST /search──▶ adapter-pkfare ──POST {HOST}/oauth2/token (client credentials, cached)──▶ Entry-Gate auth
                              │          ──POST {HOST}/entrygate/rest/request (Service: AirShopping)─▶ Entry-Gate
                              ◀── SearchResponse (always HTTP 200, business status 0/3/4/5)
```

| Module                   | Content                                                                      |
|--------------------------|------------------------------------------------------------------------------|
| `adapter-pkfare-server`  | Spring Boot app: controller, PKFARE JSON mapper, MDC filter, error handler    |
| `adapter-pkfare-service` | Chains, commands, converters, Entry-Gate client, caches, in-process configurator, Liquibase changelog |
| `adapter-pkfare-acceptance` | Cucumber end-to-end tests (bootJar + WireMock + Postgres); see its README |

## Services

| Group | PKFARE API          | Hosted by | Status                              |
|-------|---------------------|-----------|-------------------------------------|
| Offer | SEARCH → AirShopping | Verteil   | **implemented** (`POST /search`)     |
| Offer | QTE                 | Verteil   | to do                               |
| Order | Order, Order Status Push | Verteil | to do                            |
| Order | Get Pending Ticketing Orders, Ticket Issuance | PKFARE (adapter calls them) | to do |

### SEARCH chain (`offer/search/SearchConfiguration`)

```
AddProviderApiParams(Search, AirShopping) → FetchConfigurations → AuthenticateRequest (vendorCode)
  → SearchCacheLookup [hit: done] → SearchRQ → InvokeEntryGate → AirShoppingRSValidator [no offers: status 0, empty]
  → SearchRS → SearchCacheStore
```

* **Response statuses**: 0 success (also "no content": empty lists), 3 auth failure / disabled service / Entry-Gate
  error, 4 invalid request, 5 internal error. `msg` is kept under 64 characters.
* **Caller IP** is `request.getRemoteAddr()` with `server.forward-headers-strategy: native`, so `X-Forwarded-For` is
  honoured only from trusted internal proxies (Tomcat RemoteIpValve defaults: private ranges). It is only logged; there
  is no IP whitelist.

## Caching

| Cache | Where | Key | Lifetime |
|-------|-------|-----|----------|
| Entry-Gate access token | Caffeine (per pod) + Redis (cluster, single-flight lock) | hash(HOST + USERNAME) | token `expires_in` − 30 s |
| SEARCH responses (successful, non-empty) | Caffeine (per pod), weight-bounded (`adapter.search-cache.max-weight`) | vendorCode + all search criteria | `SEARCH_CACHE_TTL_SECONDS` (0 = off) |

Redis is optional: without it the token cache works per pod.

## Configuration (`agency_adapter_tbls.pkfare_configs`, `config_group = 'PKFARE_API'`)

The adapter reads this one table over JDBC with an in-process configurator (configurator-core); it doesn't call
vdc-configurator. `service` is either the adapter service (`Search`) or the Entry-Gate service (`AirShopping`).
A row for the requested service wins over the `service IS NULL` row. There is no per-vendor dimension.

* **Database:** `agency-adapter.datasource.{url,username,password}`. It's a single non-pooled DataSource, and the
  only SQL is `SELECT … FROM agency_adapter_tbls.pkfare_configs`. The DB user only needs `SELECT` on that table.
  * Locally (`application.yaml`): `jdbc:postgresql://localhost:5432/vdc_db?currentSchema=agency_adapter_tbls`,
    as `pkfare_user`.
  * In the environment profiles (`application-eks-*`, `application-dr-*`): `${DB_URL}`, `${DB_USER}` and
    `${DB_ENCRYPTED_PASSWORD}`, decrypted by jasypt with `${JASYPT_ENCRYPTOR_PASSWORD}`.
* **When changes apply:** rows are loaded at startup, and the app won't start if the table can't be read. After that they
  are served from memory; requests never query the database. To apply changed rows, call `POST /reload` (port 9022)
  **once**, e.g. through the Kubernetes service. The pod it reaches re-reads the table, then publishes the reload to
  Kafka, and every other pod re-reads too (see *Cluster-wide reload* below).

  ```sh
  curl -X POST http://localhost:9022/reload
  # 200 {"reloaded":["pkfareConfigTree"],"failed":[],"broadcast":"SENT","success":true}
  ```

  | `broadcast` | Meaning | Status |
  |---|---|---|
  | `SENT` | Kafka acknowledged the reload; every pod re-reads the table | 200 (500 if a tree failed on this pod) |
  | `DISABLED` | no `KAFKA_URL`: only this pod reloaded. Call it on each pod, or restart them | 200 (500 if a tree failed) |
  | `FAILED` | Kafka didn't acknowledge it within 10 s: only this pod reloaded. Retry | 500 |

  `/reload` has no authentication. The ingress must route only PKFARE's paths (`/search`, …) and **not** `/reload`;
  call it from inside the cluster.

  A tree that fails to load (`failed`) keeps its last loaded values. How the other pods fared is in their own logs
  (`Reloading configuration tree … as published by …`).
* **Cluster-wide reload** (`config/sync`, the way connector-c6-v4 syncs its configurator trees):
  * `kafka.bootstrapAddress` = `${KAFKA_URL:}`. Blank turns it off.
  * Topic `kafka.configurator-internal.topic` = `${KAFKA_TOPIC_ADAPTER_PKFARE_CONFIGURATOR_INTERNAL:adapter-pkfare-configurator-internal}`.
    It must exist (or the brokers auto-create topics). One partition is enough.
  * Each pod consumes it under a consumer group of its own, `adapter-pkfare-configurator-cg#<uuid>`, so every pod
    gets every message. It starts at the latest offset (old reloads are covered by the startup load).
  * Message: vdc-configurator-lib's envelope, `{"nodeId":"<pod>#<uuid>","event":"#<RELOAD_TREE>pkfareConfigTree"}`,
    keyed by the tree name. A pod skips its own messages, and other events are ignored.
  * Publishing that message yourself (e.g. with `kafka-console-producer`) reloads every pod without calling the API.
  * A pod re-reads the table once its partitions are assigned, so a reload published while it was starting isn't lost.
  * Without Kafka the app still starts and serves. Only the broadcast fails (`FAILED`), and the consumer keeps retrying.

| Name | Service | Seeded | Meaning |
|------|---------|--------|---------|
| `ENABLE_SERVICE` | `Search` | `true` (global `false`) | service on/off |
| `HOST` | – | per env (QA seeded) | Entry-Gate and OAuth2 token server base URL |
| `USERNAME` / `PASSWORD` | – | per env (QA seeded) | Entry-Gate OAuth2 client id / client secret (sent as HTTP Basic) |
| `AUTH_PATH` | – | `/oauth2/token` | token endpoint; body `grant_type=client_credentials&scope={AUTH_SCOPE}` (form-urlencoded) |
| `AUTH_SCOPE` | – | `api` | OAuth2 scope |
| `PATH` | – | `/entrygate/rest/request` | Entry-Gate endpoint shared by all services (service picked by the `Service` header) |
| `OFFICE_ID` | – | no (optional) | sent as `officeId` header |
| `VENDOR_CODES` | – | per env (dev/QA: `PKFARETESTCODE`) | accepted PKFARE vendor codes (CSV) |
| `TIMEOUT_MILLIS` | `AirShopping` | `30000` | Entry-Gate timeout, capped by `cutOffTime − CUTOFF_BUFFER_MILLIS` |
| `CUTOFF_BUFFER_MILLIS` | `AirShopping` | `500` | safety margin under PKFARE's cutoff |
| `FARE_PREFERENCES` | `Search` | `PUBL,PVT` | `Preference.FarePreferences` |
| `SHOP_RESULT_PREFERENCE` / `_TAGGED` | `Search` | `BEST` / `OPTIMIZED` | for `returnTagPrice` N / Y |
| `SORT_ORDER` | `Search` | `PRICE,STOP,DEPARTURE_TIME` | `ResponseParameters.SortOrder` |
| `NO_RESULT_ERROR_CODES` | `Search` | no | Entry-Gate error codes meaning "no availability" → status 0 |
| `TICKET_ISSUE_TIME` | `Search` | `120` | `ticketList[].ticketIssueTime` (not in VDC) |
| `DEFAULT_PRODUCT_TYPE` | `Search` | `PUB` | when FareGroup remarks carry neither PUBL nor PVT |
| `DEFAULT_SEAT_COUNT` | `Search` | no | `seatCount` when VDC has no `SeatsLeft` (default: adults + children) |
| `SEARCH_CACHE_TTL_SECONDS` | `Search` | `120` | SEARCH cache TTL |
| `TOKEN_TTL_SECONDS` | – | `1800` | token lifetime when the auth response has no `expires_in` |
| `TOKEN_LOCK_LEASE_MILLIS`, `TOKEN_LOCK_WAIT_MILLIS`, `LOCK_POLL_MILLIS` | – | 10000 / 6000 / 50 | Redis token lock |
| `MESSAGE_LOG`, `MESSAGE_LOG.*`, `PROD_ERROR_MONITORING.ENABLED` | – | `true` / – / `true` | masked trace logging |

### Rows to add per environment (QA is seeded by `pkfare_adapter/entry_gate_auth_configs`; other environments by hand)

```sql
INSERT INTO ${agency_adapter_tbls}.pkfare_configs(config_group, "name", service, value)
VALUES ('PKFARE_API', 'HOST', NULL, '<entry-gate base url, e.g. https://qaapi.sandbox.verteil.com>'),
       ('PKFARE_API', 'USERNAME', NULL, '<entry-gate client id (agent id)>'),
       ('PKFARE_API', 'PASSWORD', NULL, '<entry-gate client secret>'),
       ('PKFARE_API', 'VENDOR_CODES', NULL, '<PKFARE vendorCode(s), CSV>')
ON CONFLICT DO NOTHING;
```

## Build & run

```sh
export CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token --domain vdc-repository --domain-owner 683455398069 --query authorizationToken --output text)
./gradlew clean build
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar   # needs local Postgres: vdc_db, pkfare_user
curl -s -H 'Content-Type: application/json' --data @adapter-pkfare-service/src/test/resources/search/PKFARE_SearchRQ.json localhost:9022/search
```

Ports: 9022 (API), 7045 (actuator: health, prometheus, metrics).
