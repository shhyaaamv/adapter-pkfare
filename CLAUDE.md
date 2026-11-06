# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`adapter-pkfare` is a **reverse** integration: PKFARE (an aggregator) calls us in *its* API format, and we serve Verteil (VDC) content by calling **Entry-Gate** (Verteil's public NDC 16.1 JSON API). This is the opposite of the `connector-*-v1` repos, which receive NDC from Verteil and call an airline. Patterns come from `../connector-vj-v1` (and the blueprint at `~/Documents/POD/TEMP/CONNECTOR_BLUEPRINT.md`), but the direction differs:
- There is no inbound `RequestWrapperDto` / NDC. Controllers take PKFARE models (`com.verteil.schema.pkfare.*`, from the `pkfare-api-schema` jar, source in `../pkfare-api-schema`).
- The "supplier" being called is Entry-Gate, not an airline.

Scope so far: only **SEARCH** (→ Entry-Gate AirShopping) is implemented. Still planned:
- QTE (Offer group)
- Order and Order Status Push (Order group)
- Get Pending Ticketing Orders and Ticket Issuance: these are hosted by PKFARE and called by us.

Background docs live in `.ai/`:
- `project_description.md`: requirements
- `search_unmapped_fields.md`: mapping gaps and assumptions
- The field mapping analysis: `~/Documents/POD/PKFare/Services/SEARCH/search_analysis.md`, with sample payloads alongside it

## Commands

Dependencies come from AWS CodeArtifact; `CODEARTIFACT_AUTH_TOKEN` must be set. Use `--offline` when the Gradle cache is already warm.

```sh
./gradlew clean build                                   # compile + all tests + bootJar
./gradlew :adapter-pkfare-service:test                  # service tests only (all tests live here)
./gradlew :adapter-pkfare-service:test --tests '*SearchRSConverterTest'
./gradlew :adapter-pkfare-service:test --tests '*SearchRSConverterTest.mapsFlightDetails'
./gradlew codeCoverageReport                            # jacoco (CI runs this + sonarqube)
./gradlew :adapter-pkfare-acceptance:runAcceptanceTests --continue   # bootJar + WireMock + Postgres end to end; see that module's README
./gradlew :adapter-pkfare-server:bootJar
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar   # local Postgres vdc_db as pkfare_user (application.yaml)
```

Ports: API 9022, actuator 7045. At runtime the adapter needs Postgres (the `agency_adapter_tbls.pkfare_configs` table), plus Redis and Kafka optionally (Kafka only for cluster-wide config reloads). Every Entry-Gate setting (host, credentials, paths) comes from that table.

## Architecture

Three modules:
- `adapter-pkfare-server`: Spring Boot app, controller, `PkfareExceptionHandler`, MDC filter, the primary (PKFARE) `ObjectMapper`.
- `adapter-pkfare-service`: all logic plus the Liquibase changelog in `src/main/database`.
- `adapter-pkfare-acceptance`: Cucumber end-to-end tests. They run the bootJar (`acceptance` profile) against WireMock, which stands in for Entry-Gate and serves the real 5 MB sample AirShoppingRS. `seedAcceptanceDb` first replaces the rows of `pkfare_configs` (local Postgres by default) with the acceptance values, and `restoreAcceptanceDb` puts the previous rows back afterwards. Each scenario writes the PKFARE RQ, the converted VDC RQ and the PKFARE RS to `build/acceptance/output/<scenario>/`.

**Request path**:
1. `OfferController.search` calls `OfferService.search`.
2. `OfferService` builds a `PkfareAdapterContext` (which extends connector-common's `ConnectorContext`) through a synthetic `RequestWrapperDto`.
3. It checks the `ENABLE_SERVICE` gate (`AdapterCommonService`).
4. It runs the chain bean whose name equals the service (`"Search"`) from the `Catalog`.
5. Any `PkfareAdapterException`/`Exception` is turned into a PKFARE body by `ErrorResponseConverter`.

PKFARE always gets **HTTP 200**, with `status` 0 success (also "no results": empty lists), 3 auth/disabled/Entry-Gate error, 4 bad request, 5 internal. `msg` must stay < 64 chars.

**Search chain** (`offer/search/SearchConfiguration`, verteil-chain `ImmutableChain`):
`AddProviderApiParamsInConfigContextCmd(PKFARE_API, "Search", "AirShopping")` → `FetchConfigurationsCmd` → `AuthenticateRequestCmd` → `SearchCacheLookupCmd` → `SearchRQCmd` → `InvokeEntryGateCmd` → `AirShoppingRSValidatorCmd` → `SearchRSCmd` → `SearchCacheStoreCmd`.

Commands return `CONTINUE_PROCESSING` or `PROCESSING_COMPLETE`. A cache hit and a "no offers" result both stop early. Commands are stateless; data moves through typed context accessors. Provider requests and responses are keyed by the Entry-Gate service name (`"AirShopping"`), never by the PKFARE service.

**Configuration model** (non-obvious, important):
- Everything is a row in `agency_adapter_tbls.pkfare_configs` (columns `config_group`, `name`, `service`, `value`; unique on `(config_group, name, service)`) with `config_group = 'PKFARE_API'` (names in `PkfareConstants`). See `.ai/db-change.md`.
- The configurator runs **in process** (`config/`), not over HTTP:
  - `DataSourceConfigs`: a single non-pooled `DriverManagerDataSource` bound to `agency-adapter.datasource.*`.
  - `ConfiguratorConfigs`: configurator-core's `SqlConfigurationDataStream` (`select id, config_group, name, service as api_service, value from agency_adapter_tbls.pkfare_configs`), one prototype `ImmutableConfigurationTree` with `ARG_API_SERVICE` as its only dimension, and a `ConfigurationEvaluator`.
  - `EmbeddedVdcConfiguratorService` implements `VdcConfiguratorService` (from `vdc-configurator-common`) over the evaluator. connector-common's `FetchConfigurationsCmd` and `AdapterCommonService` use it. `vdc-configurator-lib`'s `VdcConfiguratorServiceImpl` can't be used: it bundles its own incompatible copies of `VdcConfiguratorService`/`VdcConfiguration`. `vdc-configurator-client` is excluded from the classpath.
  - Extra SELECT columns are tree dimensions **by position** (matched to `setArguments`), not by name.
  - Rows load at startup (the app fails to start if the table can't be read). After that they are served from memory and re-read only on a reload. Failed trees keep their last loaded values.
  - `POST /reload` (`ConfigurationController` → `config/sync/ClusterConfigurationReloader`) reloads the pod it reaches (`ConfigurationReloader`, implemented by `EmbeddedVdcConfiguratorService`), then broadcasts over Kafka (`ReloadBroadcaster` = `KafkaReloadPublisher`). It answers 200, or 500 if a tree failed or the broadcast wasn't acknowledged; `broadcast` is `SENT`/`DISABLED`/`FAILED`. It is unauthenticated on the API port; the ingress must not route `/reload` (only PKFARE's paths).
  - Cluster sync is connector-c6-v4's pattern on plain kafka-clients (vdc-configurator-lib can't be on the classpath): `KafkaReloadSubscriber` polls `kafka.configurator-internal.topic` under a per-pod consumer group (`<consumer-group>#<uuid>`, so it's a broadcast), skips its own node id, and reloads the named tree. It also reloads all trees on partition assignment, which closes the startup gap.
  - The message is vdc-configurator-lib's envelope (`ReloadMessage`): `{"nodeId","event":"#<RELOAD_TREE><tree>"}`.
  - `KafkaClusterSyncConfigs` is active only when `kafka.bootstrapAddress` (`${KAFKA_URL:}`) is non-blank. Without it, reloads are per pod (`broadcast: DISABLED`); acceptance runs that way.
- **Database access is restricted to `agency_adapter_tbls`.** The DB user (`pkfare_user` locally) can only use that schema, and the app must issue no SQL except that SELECT. Boot's JDBC and Mongo auto-configurations are excluded in `AdapterPkfareApplication` (no SQL init, no DB health check, no `JdbcTemplate`). Don't add JPA, Liquibase or other DataSources to the app; Liquibase runs separately (`run-liquibase.sh`).
- The tree's only dimension is `service`. The contexts still carry `officeCode` (= `vendorCode`), `officeIataCode` and carrier (`PKFARE`), but they select nothing: rows are global, and per-vendor overrides are not possible.
- The configurator's context builder **rejects null values**, so `OfferService.createContext` must always fill username, officeCode and officeIataCode.
- `ConnectorContext.putAll(wrapper)` reads `getThirdPartyIds().get(0)`, so the wrapper must carry `thirdPartyIds = [PKFARE]`.
- Which service a setting is read under:
  - `"Search"` (inbound PKFARE service): credentials/`HOST`/`AUTH_PATH`/`AUTH_SCOPE`/`VENDOR_CODES`/logging (read by `EntryGateClient`, `AuthenticateRequestCmd`)
  - `"AirShopping"` (Entry-Gate service): `TIMEOUT_MILLIS` (and `CUTOFF_BUFFER_MILLIS`)
  - Rows with a NULL service apply to both.
- Cabin codes (PKFARE `S` ↔ VDC `W`, etc.) are **not** in the database. They are a static map in code, `CabinMapping.PKFARE` (`F→F, C→C, S→W, Y→Y`); `CabinMapping.of` derives the reverse direction. Converters use it directly (no chain command, no context key).
- Environment-specific or secret rows (`HOST`, `USERNAME`, `PASSWORD`, `VENDOR_CODES`) are not in the shared Liquibase changesets. QA's `HOST`/`USERNAME`/`PASSWORD` are seeded by the `qa`-context changeset `pkfare_adapter/entry_gate_auth_configs`, and dev/QA get the sample vendor `PKFARETESTCODE` from `pkfare_adapter/test_vendor_configs`; for other environments the SQL is in the README. Don't seed placeholders: `ON CONFLICT DO NOTHING` would block the real value later.

**Entry-Gate client** (`client/`):
- `EntryGateClient` calls `POST {HOST}{PATH}`. `PATH` (`/entrygate/rest/request`, service NULL) is the same for every Entry-Gate service; the `Service` header (e.g. `AirShopping`) picks the operation. Headers: `Authorization: Bearer`, `Service`, optional `officeId`, and `VDC_TransactionId`. No `ThirdpartyId` header is sent: it would restrict the airlines Entry-Gate shops, and PKFARE gets all airlines.
- On HTTP 401 it invalidates the cached token and retries once.
- Add new Entry-Gate services to its `SERVICE_SPEC_MAP`.
- `EntryGateAuthClient` does OAuth2 client-credentials: `POST {HOST}{AUTH_PATH}` with Basic `USERNAME:PASSWORD` and a form body `grant_type=client_credentials&scope={AUTH_SCOPE}`. A 401 from the token endpoint is re-thrown as `UNAVAILABLE` so it isn't retried as an expired bearer token.
- `LocalTokenCache` keeps a Caffeine L1 cache plus a Redis L2 copy behind a `SET NX PX` cluster lock. Redis comes from connector-common's `ConnectorRedisConfig` (`ordermanagement.redis.*` props); it's optional and the cache degrades to per-pod.
- `ThirdPartyConnector` is VJ's HTTP executor with Resilience4j and metrics.

**Two JSON mappers** (`common/json/JsonMappers`). Never mix them up:
- `ndc()` (bean `ndcObjectMapper`): Entry-Gate JSON. JAXB introspector, NON_EMPTY, lexical `XMLGregorianCalendar` so date-only values serialize as `2026-10-20`.
- `pkfare()` (primary bean, used by Spring MVC): NON_NULL. It is **not** NON_EMPTY because PKFARE requires empty required lists like `shoppingResultList: []`.

**NDC reference handling**:
- `@XmlIDREF` fields are `Object`. They stay unresolved strings because JaxbResolver isn't used here, so always read them with `RefUtil.key(...)`.
- Some JAXB getters are plain (non-lazy) and return null lists, e.g. `AirShoppingRQ.ResponseParameters.getSortOrder()`. Set those lists explicitly.

**Response mapping** (`offer/search/converter`):
- `SearchRSConverter` maps one `AirlineOffer` to one `shoppingResultList[]` entry via `ShoppingResultConverter`, which composes `ItineraryConverter`, `TicketPriceConverter`, `FreeBaggageConverter` and `FareRuleConverter`.
- `ItineraryConverter` groups segments by origin-destination and orders the groups by departure. That gives `travelDirection` (1 out / 2 return) and `flightSeq`.
- `SearchMappingContext` holds an `AirShoppingRSIndex` (DataLists lookups) and the `flightRefNums` registry. `flightList` is emitted only for registered segments, so build everything that can fail *before* registering flight refs.
- Offers PKFARE can't represent are skipped and logged, never failing the search.
- VDC `OfferPrice` amounts are **per passenger** (verified against the sample).
- `data` = `AirlineOffer.OfferID` value, which the QTE flow will use to price the offer.

**Caller authentication** is vendor code only (`AuthenticateRequestCmd` against `VENDOR_CODES`); there is no IP whitelist. The caller IP (`request.getRemoteAddr()`, with `server.forward-headers-strategy: native` so X-Forwarded-For is trusted only from internal proxies) is just logged and set as the wrapper's `remoteAddr`.

## Conventions

- New PKFARE service:
  - add a constant in `PkfareConstants`
  - add a `<Service>Configuration` with `@Bean("<ServiceName>")` and a chain per the anatomy above
  - add `command/` + `converter/` packages under `offer/` or `order/`
  - add a case in `ErrorResponseConverter`
  - add an `ENABLE_SERVICE` row in Liquibase (no `PATH` row: every Entry-Gate service shares the NULL-service `PATH`)
- Liquibase: `src/main/database/vdc-change-log.xml` includes the `dml/*.xml` files, grouped by topic. Each changeset uses `runOnChange="true"`, context `dev, qa, stage, prod.in, prod.eu, prod.openconnect`, `INSERT INTO ${agency_adapter_tbls}.pkfare_configs(config_group, "name", service, value) VALUES(...) ON CONFLICT DO NOTHING`. Note: the unique key `(config_group, name, service)` treats NULL `service` as distinct, so `ON CONFLICT` only dedupes rows with a non-NULL service (see `.ai/db-change.md`).
- Tests: plain JUnit 5 + AssertJ (+ JSONAssert). Converters are built by hand with `new`. Fixtures are in `adapter-pkfare-service/src/test/resources/search/`: a real Entry-Gate RS trimmed to 3 offers, plus the PKFARE RQ. `utils/TestUtils.buildContext(request, Map<name,value>)` fakes configurator values as `PKFARE_API.<NAME>`.
- Style: use streams instead of `for` loops, and `Optional.ofNullable(...).map(...)` chains instead of `isNull`/`nonNull` ternaries for nullable mappings (including `isNull(configuration) ? default : configuration.getX(...)`). Plain guard clauses that throw or return early stay as they are. Keep methods short: move multi-argument builds and long stream pipelines into named private methods.
- Logging of messages runs on virtual threads with a copied MDC (`LoggerHelper`); never log payloads on the request thread.
