# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`adapter-pkfare` is a **reverse** integration: PKFARE (an aggregator) calls us in *its* API format, and we serve Verteil (VDC) content by calling **Entry-Gate** (Verteil's public NDC 16.1 JSON API). This is the opposite of the `connector-*-v1` repos, which receive NDC from Verteil and call an airline. Patterns come from `../connector-vj-v1` (and the blueprint at `~/Documents/POD/TEMP/CONNECTOR_BLUEPRINT.md`), but the direction differs:
- There is no inbound `RequestWrapperDto` / NDC. Controllers take PKFARE models (`com.verteil.schema.pkfare.*`, from the `pkfare-api-schema` jar, source in `../pkfare-api-schema`). The QTE models (`Qte*`) come from its `QTE_SCHEMA.yaml` and exist only from `1.3.0-SNAPSHOT`. **That version must be published to CodeArtifact before CI can build**; locally `mavenLocal()` resolves a `mvn install`ed copy.
- The "supplier" being called is Entry-Gate, not an airline.

Scope so far: **SEARCH** (→ Entry-Gate AirShopping) and **QTE** (→ Entry-Gate FlightPrice) are implemented. Still planned:
- Order and Order Status Push (Order group)
- Get Pending Ticketing Orders and Ticket Issuance: these are hosted by PKFARE and called by us.

Background docs live in `.ai/`:
- `project_description.md`: requirements
- `search_unmapped_fields.md`, `qte_unmapped_fields.md`: mapping gaps and assumptions
- The field mapping analyses: `~/Documents/POD/PKFare/Services/SEARCH/search_analysis.md` and `~/Documents/POD/PKFare/Services/QTE(Pricing)/qte-mapping-analysis.md`, with sample payloads alongside them

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
- `adapter-pkfare-acceptance`: Cucumber end-to-end tests. They run the bootJar (`acceptance` profile) against WireMock, which stands in for AWS Secrets Manager and Entry-Gate and serves the real 5 MB sample AirShoppingRS and 7.7 MB sample FlightPriceRS, picked by the `Service` header. QTE variants (delay, `SeatsLeft`, error or no-offer bodies) are priority stubs added through the WireMock admin API and reset before every scenario. Only the SEARCH → QTE scenario prices SEARCH-minted offer data; the others use the fixture offer data, because the FlightPrice sample's offer isn't in the AirShopping sample. Each scenario writes the PKFARE RQ, the converted VDC RQ and the PKFARE RS (plus the decoded `routing.data` for QTE) to `build/acceptance/output/<scenario>/`.

**Request path**:
1. `OfferController.search` / `qte` (`POST /search`, `POST /qte`) call `OfferService.search` / `qte`.
2. `OfferService` builds a `PkfareAdapterContext` (a `HashMap` implementing verteil-chain's `Context`, with typed accessors).
3. It checks that `pkfare.enabled-services.<service>` is `true` (service name lower-cased, e.g. `search`, `qte`).
4. It runs the chain bean whose name equals the service (`"Search"`, `"Qte"`) from the `Catalog`.
5. Any `PkfareAdapterException`/`Exception` is turned into a PKFARE body by `ErrorResponseConverter` (a `switch` per service).

PKFARE always gets **HTTP 200**, with `status` 0 success (also "no results": empty lists), 3 auth/disabled/Entry-Gate error, 4 bad request, 5 internal. `msg` must stay < 64 chars. QTE adds (`ErrorResponseConverter.mapQteErrorResponse`):
- 7: `VERTEIL_ERROR` with a `SocketTimeoutException` in the cause chain (connect/pool timeouts stay 3)
- 111 / 201 / 202: picked by the exception **reason** `INVALID_DATE_RANGE` / `NO_FARE` / `INVALID_FARE` (constants in `PkfareConstants`)
- 1 / 2 (fewer `SeatsLeft` than adults + children / any `SeatsLeft` 0): built by `QteRSConverter`, not thrown, so they keep `sessionId`/`maxSeats` (no `routing`)
- `errorCode`/`errorMsg` (≤ 299 chars) only for `VERTEIL_ERROR`s whose reason is an Entry-Gate error code, not one of the adapter's own reasons; otherwise `null` (still serialised: the QTE models mark required props `@JsonInclude(ALWAYS)`)

`PkfareExceptionHandler` (unreadable JSON, wrong content type) answers `/qte` with a `QteResponse` and every other path with a `SearchResponse`, both status 4.

**Search chain** (`offer/search/SearchConfiguration`, verteil-chain `ImmutableChain`):
`AuthenticateRequestCmd` → `SearchCacheLookupCmd` → `SearchRQCmd` → `InvokeEntryGateCmd` → `AirShoppingRSValidatorCmd` → `SearchRSCmd` → `SearchCacheStoreCmd`. The two cache commands are only in the chain when `AirShoppingConstants.SEARCH_CACHE_ENABLED` (off by default).

**QTE chain** (`offer/qte/QteConfiguration`, bean `"Qte"`):
`AuthenticateRequestCmd` → `QteRQCmd` → `InvokeEntryGateCmd` → `FlightPriceRSValidatorCmd` → `QteRSCmd`. No cache: the price must be live.
- `QteRQCmd` decodes `routing.data` (format only). `QteRQConverter` then validates the offer data's contents (SRID, offer, OfferItemIDs, segments; else 4 `Invalid routing.data`) and the request before building the FlightPriceRQ, and every failure is status 4 except these, which are 111:
  - a duplicate segment
  - a segment departing before the previous one arrives at the same airport (times are local)
  - segments in another order than the offer data's (its `segments` map keeps SEARCH's itinerary order: outbound then return)
- `FlightPriceRSValidatorCmd`: no priced offer and no errors → 201. Errors and no priced offer → 3 with `errorCode`/`errorMsg`.
- `QteRSConverter` returns 202 when the priced passenger types differ from the request, when an amount is missing or the total ≤ 0, or when the priced itinerary (match keys per direction, in order) differs from the requested one.

Commands return `CONTINUE_PROCESSING` or `PROCESSING_COMPLETE`. A SEARCH cache hit and a "no offers" result both stop early. Commands are stateless; data moves through typed context accessors. Provider requests and responses are keyed by the Entry-Gate service name (`"AirShopping"`, `"FlightPrice"`), never by the PKFARE service.

**Configuration model** (non-obvious, important):
- YAML holds only infrastructure and access settings: `pkfare.*` (`PkfareProperties`: `enabled-services` map, `vendor-codes`, `ip-whitelist`, `message-log`), `verteil.*` (`VerteilProperties`: Entry-Gate host/paths/scope, token-call timeout, token cache lock, `circuit-breaker`), `aws.secretManager.*`, `ordermanagement.redis.*`, plus Spring's `server`/`spring`/`management` and `adapter.http.*` (connection pool, timeouts) and `adapter.monitor.*` (idle-connection eviction). There are no per-vendor overrides.
- Service behaviour is **constants**, not properties, one class per Entry-Gate service in `common/utils`:
  - `AirShoppingConstants` (SEARCH → AirShopping): timeout and cutoff buffer, fare preferences, `ShopResultPreference`, sort order, no-result error codes, `ticketIssueTime`, default product type, SEARCH cache switch (off) and TTL/weight.
  - `FlightPriceConstants` (QTE → FlightPrice): timeout and cutoff buffer, default product type.
  - A new service gets its own `<EntryGateService>Constants` next to them; shared PKFARE/NDC codes stay in `PkfareConstants`.
- `EntryGateTimeout.resolve(timeout, buffer, cutOffTime)` caps a service's timeout by PKFARE's `cutOffTime` minus the buffer, never below 1 s.
- Cabin codes are the map `PkfareConstants.CABIN_MAPPING` (PKFARE cabin → VDC cabin); `CabinMapping.DEFAULT` wraps it (case-insensitive, derived reverse direction) and converters use it directly.
- `application.yaml` holds what all environments share; `application-<profile>.yaml` holds what differs (`verteil.host`, `pkfare.vendor-codes`, `pkfare.ip-whitelist.*`, `aws.secretManager.name`/`region`). dev/QA use the sample vendor `PKFARETESTCODE` with the IP check off. Values not known for an environment are left empty (listed in README's *Values still to be filled*), never filled with placeholders. YAML files carry no comments: property explanations go in README's *Configuration* section.
- The Entry-Gate username/password are **not** properties. `client/EntryGateCredentialsProvider` reads the AWS secret `aws.secretManager.name` (JSON `{"username","password"}`) at startup and fails startup if it is missing or incomplete. Only when the token endpoint reports invalid credentials (401, or 400 with `invalid_client`), `EntryGateAuthClient` calls `refresh()` (re-reads the secret at most once per `CREDENTIALS_REFRESH_INTERVAL`) and retries once if they changed, so rotation needs no restart. `aws.secretManager.url` overrides the endpoint (acceptance only).
- There is no connector-common / vdc-configurator dependency: `RedisConfig` (Redis), `PkfareAdapterContext` and the log/metric constants are the adapter's own.

**Entry-Gate client** (`client/`):
- `EntryGateClient` calls `POST {host}{path}` (`verteil.*`). `path` (`/entrygate/rest/request`) is the same for every Entry-Gate service; the `Service` header (e.g. `AirShopping`) picks the operation. Headers: `Authorization: Bearer`, `Service` and `VDC_TransactionId`. FlightPrice also sends `thirdpartyid` = the FlightPriceRQ offer owner (airline code). AirShopping sends none: it would restrict the airlines Entry-Gate shops, and PKFARE gets all airlines.
- On HTTP 401 it invalidates the cached token and retries once.
- Add new Entry-Gate services to its `SERVICE_SPEC_MAP` (`AirShopping`, `FlightPrice`).
- `EntryGateAuthClient` does OAuth2 client-credentials: `POST {host}{auth-path}` with Basic `username:password` (from the secret) and a form body `grant_type=client_credentials&scope={auth-scope}`. A 401 from the token endpoint is re-thrown as `UNAVAILABLE` so it isn't retried as an expired bearer token.
- `LocalTokenCache` keeps a Caffeine L1 cache plus a Redis L2 copy behind a `SET NX PX` cluster lock, keyed by host + current username. Redis comes from `config/RedisConfig` (`ordermanagement.redis.*` props); it's optional (null connection bean) and the cache degrades to per-pod.
- `ThirdPartyConnector` runs every Entry-Gate HTTP call through the `entryGate` Resilience4j circuit breaker (bean in `AdapterPkfareConfiguration`, settings `verteil.circuit-breaker.*`). I/O failures and every HTTP 4xx/5xx (401 included) are recorded as failures; HTTP errors are thrown as `EntryGateHttpException` (status + body), with reason `UNAUTHORIZED` for 401. An open breaker gives `CONNECTION_ERROR_MSG` / `UNAVAILABLE` (PKFARE status 3). It also records the `supplier_transaction_duration` timer (tags `service`, `status`); breaker metrics come from `resilience4j-micrometer`.

**Two JSON mappers** (`common/json/JsonMappers`). Never mix them up:
- `ndc()` (bean `ndcObjectMapper`): Entry-Gate JSON. JAXB introspector, NON_EMPTY, lexical `XMLGregorianCalendar` so date-only values serialize as `2026-10-20`.
- `pkfare()` (primary bean, used by Spring MVC): NON_NULL. It is **not** NON_EMPTY because PKFARE requires empty required lists like `shoppingResultList: []`.

**NDC reference handling**:
- `@XmlIDREF` fields are `Object`. They stay unresolved strings because JaxbResolver isn't used here, so always read them with `RefUtil.key(...)`. Offer-level `refs` arrive as `[{"Ref": "..."}]` and deserialize to a `Map`, which `RefUtil.key` also unwraps. FlightPriceRQ sends no offer `refs` and no `Metadata`.
- Some JAXB getters are plain (non-lazy) and return null lists, e.g. `AirShoppingRQ.ResponseParameters.getSortOrder()`. Set those lists explicitly.
- ndc16_1 `FlightPriceRQ` `FlightType.Flight` has no `SegmentType`, so the direction is carried only by `OriginDestination[0]`/`[1]`. Its `details` property would serialize as `Details`, so no `FlightDetail` is sent.

**Shared offer mapping** (`offer/common`): SEARCH (`AirShoppingRS`) and QTE (`FlightPriceRS`) share the itinerary, price, baggage, fare-rule and fare logic. The shared converters return neutral records, and each service maps those to its own PKFARE models (`SearchTicketMapper`, `QteTicketMapper`, `QteSegmentConverter`).
- `model/DataListsIndex(DataListType)` handles the DataLists lookups for both responses. `segmentKeyOf(FareComponent)` reads `SegmentReference`, else the first `refs` key that is a segment, because FlightPriceRS fare components carry the segment only in `refs`. `fareGroups(offerPrice)` collects the FareGroups referenced from `FareComponent.refs` and `ClassOfService.refs`.
- `converter/ItineraryConverter` groups segments by origin-destination and orders the groups by departure. That gives `travelDirection` (1 out / 2 return) and `flightSeq`. The caller passes the associations: SEARCH passes the offer-level ones (else the first OfferPrice's), QTE the first OfferPrice's.
- `TicketPriceConverter` (one OfferPrice per requested passenger type), `FreeBaggageConverter`, `FareRuleConverter`, `OfferFareConverter` (productType, fare basis, price classes), `SegmentDetailsConverter` (segment → `SegmentDetails`, incl. `matchKey()`).
- VDC `OfferPrice` amounts are **per passenger** (verified against the SEARCH and QTE samples).

**SEARCH response** (`offer/search/converter`):
- `SearchRSConverter` maps one `AirlineOffer` to one `shoppingResultList[]` entry via `ShoppingResultConverter`, which composes the shared converters and `SearchOfferDataFactory`.
- `SearchMappingContext` holds a `PricingContext` (`DataListsIndex` and requested types), an `AirShoppingMetadataIndex` (SRIDs) and the `flightRefNums` registry. `flightList` is emitted only for registered segments, so build everything that can fail *before* registering flight refs.
- Offers PKFARE can't represent are skipped and logged, never failing the search. That includes offers without a ShoppingResponseID: one comes from the `SHOPPING_RESPONSE_IDS` DescriptionMetadata for the offer owner, else from the root `ShoppingResponseID`, which is null in the sample.

**QTE response** (`offer/qte/converter/QteRSConverter`): uses the first `PricedFlightOffer`, with the itinerary from its first OfferPrice's associations. `sessionId` is the FlightPriceRS SRID, unmodified (PKFARE asks for < 20 chars). `maxSeats` is the min `SeatsLeft` capped at 9, else adults + children. When FlightPriceRS lacks them, `rbd` and `cabinClass` fall back to the request segment and `fareBasis` to the SEARCH offer data. `fareBasis` is `""` when any segment has none.

**OfferData** (`offer/common/data`): SEARCH `shoppingResultList[].data` and QTE `routing.data` are the standard Base64 of an `OfferData`'s JSON (`OfferDataCodec`, PKFARE mapper). It is offer metadata PKFARE carries back to us, not a token, so don't name it one. It has no version or stage field: unknown fields are ignored on decode, and each service checks that the offer data holds what it needs. `data` is not the bare OfferID, because the adapter is stateless across pods and FlightPriceRQ needs more than the QTE request carries. The offer data holds **only what the PKFARE request lacks**; everything PKFARE echoes per segment (times, carriers incl. `operatingCarrier`, `rbd` → `ClassOfService.Code`, `cabinClass` → `CabinType.Code`, aircraft, direction) is taken from the request.
- The SEARCH offer data (`SearchOfferDataFactory`), echoed to QTE, carries:
  - the SRID and the OfferID/owner. The OfferID `Channel` is not carried: every service sends the constant `VDC_CHANNEL` (`DIRECT_CONNECT`).
  - per OfferPrice: OfferItemID, PTC and traveler refs
  - `segments`: an insertion-ordered map `matchKey → "segmentKey|fareBasis"` (`DataSegment.encode/decode`; just `segmentKey` when the fare basis is unknown), in itinerary order. The order is part of the offer data (QTE checks it), so build it as a `LinkedHashMap`.
  - no AirShopping `PriceMetadata`/offer `refs`: FlightPriceRQ doesn't need them
- The QTE offer data is minted from FlightPriceRS (`QteOfferDataFactory`; its OfferID and segment keys differ) for Order, in the same shape. Nothing marks which service minted it, so QTE can't reject QTE offer data sent back to it: it builds a FlightPriceRQ from the priced OfferID.
- QTE finds each requested segment by `SegmentMatchKey` (`dep|depTime|arr|arrTime|marketingCarrier|flightNumber`, `yyyyMMddHHmm`, flight number without leading zeros, upper-case), with no positional fallback. Status 4 for any of these:
  - an unknown segment
  - a different segment count
  - different passenger counts per PTC (`CHD` = `CNN`)
- FlightPriceRQ `FareList.FareGroup` keys are adapter-made: `FG<n>`, where n is the segment index over fromSegments then retSegments. `ClassOfService.refs` points at them.
- Size: SEARCH offer data is ~1 KB of Base64 per offer (996 chars max on the 5 MB sample, round trip with 3 PTCs), uncompressed. `SearchRSConverterTest.keepsTheOfferDataCompact` caps it at 1200 on the fixture.

**Caller IP**: `request.getRemoteAddr()` with `server.forward-headers-strategy: native`, so X-Forwarded-For is trusted only from internal proxies. `IpWhitelist` accepts IPs/CIDRs and fails closed when empty. The check runs unless `pkfare.ip-whitelist.enabled` is `false` (default `true`).

## Conventions

- New PKFARE service:
  - add a constant in `PkfareConstants`
  - add a `<Service>Configuration` with `@Bean("<ServiceName>")` and a chain per the anatomy above
  - add `command/` + `converter/` packages under `offer/` or `order/`
  - add a case in `ErrorResponseConverter`
  - add `<service>: true` under `pkfare.enabled-services` in `application.yaml`
  - put its behaviour in a `<EntryGateService>Constants` class next to `PkfareConstants`; only infrastructure settings go in YAML
  - add the Entry-Gate service to `EntryGateClient.SERVICE_SPEC_MAP`
  - add an acceptance feature with a WireMock stub keyed on the `Service` header
- Tests: plain JUnit 5 + AssertJ (+ JSONAssert). Converters are built by hand with `new`. Fixtures are in `adapter-pkfare-service/src/test/resources/`:
  - `search/`: a real Entry-Gate RS trimmed to 3 offers (its `Metadata` keeps `SHOPPING_RESPONSE_IDS`, plus PriceMetadata the adapter no longer reads), plus the PKFARE RQ
  - `qte/`: the PKFARE QTE RQ, the decoded SEARCH offer data `offer_data.json`, the expected FlightPriceRQ, and the 7.7 MB sample FlightPriceRS trimmed to ~155 KB
  - `utils/TestUtils.pkfareProperties()` / `verteilProperties()` give code defaults plus the test vendor / an Entry-Gate host; tests change them with setters and pass them to the class under test. `TestUtils.buildContext(request)` / `buildQteContext(request)` build the chain context. `qteRequest()` puts the Base64 of `offer_data.json` into `routing.data`.
- Style: use streams instead of `for` loops, and `Optional.ofNullable(...).map(...)` chains instead of `isNull`/`nonNull` ternaries for nullable mappings (including `isNull(configuration) ? default : configuration.getX(...)`). Plain guard clauses that throw or return early stay as they are. Keep methods short: move multi-argument builds and long stream pipelines into named private methods.
- Converters hold only mapping methods. A record with a body, a nested class or a nested enum goes to the matching `model` package (`offer/<service>/model`, or `offer/common/model` for the shared converters) as a top-level type (`QteMappingContext`, `RequestedSegment`, `PenaltyRule`); only a one-line record with no body may stay nested.
- Method names in converters, mappers and offer-data factories: every method that maps or derives a value is `map<Name>()`, public or private, including lookups and fallbacks (`mapRouting`, `mapPassengerPrices`, `mapComponentRbd`, `mapFareBasis`, `mapPrice`). A method that builds a mapping context is `mapMappingContext`. These keep their names:
  - verb names: `convert`, `create`, `resolve`, `validate*`, `has*`, `is*`, `matches*`, `require*`, `add*`, `to*`, `parse*`, `normalise`, `matchSegments`
  - exception factories (`invalidFare`, `validationError`) and generic utilities (`nullSafe`, `isNullOrEmpty`, collectors)
- Name mapping-context variables after their type: `qteMappingContext`, `searchMappingContext`, never `mapping`, `mappingContext` or `context`.
- Logging of messages runs on virtual threads with a copied MDC (`LoggerHelper`); never log payloads on the request thread.
