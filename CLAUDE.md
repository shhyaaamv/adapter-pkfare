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
./gradlew :adapter-pkfare-acceptance:runAcceptanceTests --continue   # bootJar + WireMock end to end; see that module's README
./gradlew :adapter-pkfare-server:bootJar
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar --spring.profiles.active=eks-qa   # needs AWS credentials for the qa secret
```

Ports: API 9022, actuator 7045. There is no database or configurator. At runtime the adapter needs Entry-Gate, AWS Secrets Manager (once, at startup) and optionally Redis.

## Architecture

Three modules:
- `adapter-pkfare-server`: Spring Boot app, controller, `PkfareExceptionHandler`, MDC filter, the primary (PKFARE) `ObjectMapper`.
- `adapter-pkfare-service`: all logic, plus the settings model `config/PkfareProperties`.
- `adapter-pkfare-acceptance`: Cucumber end-to-end tests. They run the bootJar (`acceptance` profile) against WireMock, which stands in for AWS Secrets Manager and Entry-Gate and serves the real 5 MB sample AirShoppingRS. Each scenario writes the PKFARE RQ, the converted VDC RQ and the PKFARE RS to `build/acceptance/output/<scenario>/`.

**Request path**:
1. `OfferController.search` calls `OfferService.search`.
2. `OfferService` builds a `PkfareAdapterContext` (a `HashMap` implementing verteil-chain's `Context`, with typed accessors).
3. It checks that `pkfare.enabled-services.<service>` is `true` (service name lower-cased, e.g. `search`).
4. It runs the chain bean whose name equals the service (`"Search"`) from the `Catalog`.
5. Any `PkfareAdapterException`/`Exception` is turned into a PKFARE body by `ErrorResponseConverter`.

PKFARE always gets **HTTP 200**, with `status` 0 success (also "no results": empty lists), 3 auth/disabled/Entry-Gate error, 4 bad request, 5 internal. `msg` must stay < 64 chars.

**Search chain** (`offer/search/SearchConfiguration`, verteil-chain `ImmutableChain`):
`AuthenticateRequestCmd` → `SearchCacheLookupCmd` → `SearchRQCmd` → `InvokeEntryGateCmd` → `AirShoppingRSValidatorCmd` → `SearchRSCmd` → `SearchCacheStoreCmd`. The two cache commands are only in the chain when `AirShoppingConstants.SEARCH_CACHE_ENABLED` (off by default).

Commands return `CONTINUE_PROCESSING` or `PROCESSING_COMPLETE`. A cache hit and a "no offers" result both stop early. Commands are stateless; data moves through typed context accessors. Provider requests and responses are keyed by the Entry-Gate service name (`"AirShopping"`), never by the PKFARE service.

**Configuration model** (non-obvious, important):
- YAML holds only infrastructure and access settings: `pkfare.*` (`PkfareProperties`: `enabled-services` map, `vendor-codes`, `ip-whitelist`, `message-log`), `verteil.*` (`VerteilProperties`: Entry-Gate host/paths/scope, token-call timeout, token cache lock, `circuit-breaker`), `aws.secretManager.*`, `ordermanagement.redis.*`, plus Spring's `server`/`spring`/`management` and `adapter.http.*` (connection pool, timeouts) and `adapter.monitor.*` (idle-connection eviction). There are no per-vendor overrides.
- Service behaviour is **constants**, not properties: `common/utils/AirShoppingConstants` (SEARCH → AirShopping: timeout and cutoff buffer, fare preferences, `ShopResultPreference`, sort order, no-result error codes, `ticketIssueTime`, default product type, SEARCH cache switch (off) and TTL/weight). A new service gets its own `<EntryGateService>Constants` next to it; shared PKFARE/NDC codes stay in `PkfareConstants`.
- Cabin codes are the map `PkfareConstants.CABIN_MAPPING` (PKFARE cabin → VDC cabin); `CabinMapping.DEFAULT` wraps it (case-insensitive, derived reverse direction) and converters use it directly.
- `application.yaml` holds what all environments share; `application-<profile>.yaml` holds what differs (`verteil.host`, `pkfare.vendor-codes`, `pkfare.ip-whitelist.*`, `aws.secretManager.name`/`region`). dev/QA use the sample vendor `PKFARETESTCODE` with the IP check off. Values not known for an environment are left empty (listed in README's *Values still to be filled*), never filled with placeholders. YAML files carry no comments: property explanations go in README's *Configuration* section.
- The Entry-Gate username/password are **not** properties. `client/EntryGateCredentialsProvider` reads the AWS secret `aws.secretManager.name` (JSON `{"username","password"}`) at startup and fails startup if it is missing or incomplete. Only when the token endpoint reports invalid credentials (401, or 400 with `invalid_client`), `EntryGateAuthClient` calls `refresh()` (re-reads the secret at most once per `CREDENTIALS_REFRESH_INTERVAL`) and retries once if they changed, so rotation needs no restart. `aws.secretManager.url` overrides the endpoint (acceptance only).
- There is no connector-common / vdc-configurator dependency: `RedisConfig` (Redis), `PkfareAdapterContext` and the log/metric constants are the adapter's own.

**Entry-Gate client** (`client/`):
- `EntryGateClient` calls `POST {host}{path}` (`verteil.*`). `path` (`/entrygate/rest/request`) is the same for every Entry-Gate service; the `Service` header (e.g. `AirShopping`) picks the operation. Headers: `Authorization: Bearer`, `Service` and `VDC_TransactionId`. No `ThirdpartyId` header is sent: it would restrict the airlines Entry-Gate shops, and PKFARE gets all airlines.
- On HTTP 401 it invalidates the cached token and retries once.
- Add new Entry-Gate services to its `SERVICE_SPEC_MAP`.
- `EntryGateAuthClient` does OAuth2 client-credentials: `POST {host}{auth-path}` with Basic `username:password` (from the secret) and a form body `grant_type=client_credentials&scope={auth-scope}`. A 401 from the token endpoint is re-thrown as `UNAVAILABLE` so it isn't retried as an expired bearer token.
- `LocalTokenCache` keeps a Caffeine L1 cache plus a Redis L2 copy behind a `SET NX PX` cluster lock, keyed by host + current username. Redis comes from `config/RedisConfig` (`ordermanagement.redis.*` props); it's optional (null connection bean) and the cache degrades to per-pod.
- `ThirdPartyConnector` runs every Entry-Gate HTTP call through the `entryGate` Resilience4j circuit breaker (bean in `AdapterPkfareConfiguration`, settings `verteil.circuit-breaker.*`). I/O failures and every HTTP 4xx/5xx (401 included) are recorded as failures; HTTP errors are thrown as `EntryGateHttpException` (status + body), with reason `UNAUTHORIZED` for 401. An open breaker gives `CONNECTION_ERROR_MSG` / `UNAVAILABLE` (PKFARE status 3). It also records the `supplier_transaction_duration` timer (tags `service`, `status`); breaker metrics come from `resilience4j-micrometer`.

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

**Caller IP**: `request.getRemoteAddr()` with `server.forward-headers-strategy: native`, so X-Forwarded-For is trusted only from internal proxies. `IpWhitelist` accepts IPs/CIDRs and fails closed when empty. The check runs unless `pkfare.ip-whitelist.enabled` is `false` (default `true`).

## Conventions

- New PKFARE service:
  - add a constant in `PkfareConstants`
  - add a `<Service>Configuration` with `@Bean("<ServiceName>")` and a chain per the anatomy above
  - add `command/` + `converter/` packages under `offer/` or `order/`
  - add a case in `ErrorResponseConverter`
  - add `<service>: true` under `pkfare.enabled-services` in `application.yaml`
  - put its behaviour in a `<EntryGateService>Constants` class next to `PkfareConstants`; only infrastructure settings go in YAML
- Tests: plain JUnit 5 + AssertJ (+ JSONAssert). Converters are built by hand with `new`. Fixtures are in `adapter-pkfare-service/src/test/resources/search/`: a real Entry-Gate RS trimmed to 3 offers, plus the PKFARE RQ. `utils/TestUtils.pkfareProperties()` / `verteilProperties()` give code defaults plus the test vendor / an Entry-Gate host; tests change them with setters and pass them to the class under test. `TestUtils.buildContext(request)` builds the chain context.
- Style: use streams instead of `for` loops, and `Optional.ofNullable(...).map(...)` chains instead of `isNull`/`nonNull` ternaries for nullable mappings (including `isNull(configuration) ? default : configuration.getX(...)`). Plain guard clauses that throw or return early stay as they are. Keep methods short: move multi-argument builds and long stream pipelines into named private methods.
- Logging of messages runs on virtual threads with a copied MDC (`LoggerHelper`); never log payloads on the request thread.
