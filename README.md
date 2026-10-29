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
| `adapter-pkfare-service` | Chains, commands, converters, Entry-Gate client, caches, Liquibase changelog |

## Services

| Group | PKFARE API          | Hosted by | Status                              |
|-------|---------------------|-----------|-------------------------------------|
| Offer | SEARCH → AirShopping | Verteil   | **implemented** (`POST /search`)     |
| Offer | QTE                 | Verteil   | to do                               |
| Order | Order, Order Status Push | Verteil | to do                            |
| Order | Get Pending Ticketing Orders, Ticket Issuance | PKFARE (adapter calls them) | to do |

### SEARCH chain (`offer/search/SearchConfiguration`)

```
AddProviderApiParams(Search, AirShopping) → FetchConfigurations → AuthenticateRequest (vendorCode + IP whitelist)
  → SearchCacheLookup [hit: done] → SearchRQ → InvokeEntryGate → AirShoppingRSValidator [no offers: status 0, empty]
  → SearchRS → SearchCacheStore
```

* **Response statuses**: 0 success (also "no content": empty lists), 3 auth failure / disabled service / Entry-Gate
  error, 4 invalid request, 5 internal error. `msg` is kept under 64 characters.
* **Caller IP** is `request.getRemoteAddr()` with `server.forward-headers-strategy: native`, so `X-Forwarded-For` is
  honoured only from trusted internal proxies (Tomcat RemoteIpValve defaults: private ranges). Set
  `server.tomcat.remoteip.internal-proxies` if the ingress is outside those ranges.

## Caching

| Cache | Where | Key | Lifetime |
|-------|-------|-----|----------|
| Entry-Gate access token | Caffeine (per pod) + Redis (cluster, single-flight lock) | hash(HOST + USERNAME) | token `expires_in` − 30 s |
| `ENABLE_SERVICE` gate | Caffeine | vendorCode + service | 5 min |
| SEARCH responses (successful, non-empty) | Caffeine (per pod), weight-bounded (`adapter.search-cache.max-weight`) | vendorCode + all search criteria | `SEARCH_CACHE_TTL_SECONDS` (0 = off) |

Redis is optional: without it the token cache works per pod.

## Configuration (configurator, `config_group = 'PKFARE_API'`)

All settings are resolved with the PKFARE **vendorCode as `office_code`**, so any value can be overridden per vendor
(e.g. different Entry-Gate credentials or carriers per PKFARE channel). `service` is either the adapter service
(`Search`) or the Entry-Gate service (`AirShopping`).

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
| `IP_WHITELIST_ENABLED` | – | `true` (dev/QA: `false`) | check the caller IP against `IP_WHITELIST`; `true` when unset |
| `IP_WHITELIST` | – | **no, per env** | PKFARE source IPs / CIDRs (CSV); empty rejects all (when enabled) |
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
INSERT INTO ${configurator_tbls}.provider_api_configs(config_group, "name", carrier, iata, service, "version", value)
VALUES ('PKFARE_API', 'HOST',          NULL, NULL, NULL, NULL, '<entry-gate base url, e.g. https://qaapi.sandbox.verteil.com>'),
       ('PKFARE_API', 'USERNAME',      NULL, NULL, NULL, NULL, '<entry-gate client id (agent id)>'),
       ('PKFARE_API', 'PASSWORD',      NULL, NULL, NULL, NULL, '<entry-gate client secret>'),
       ('PKFARE_API', 'VENDOR_CODES',  NULL, NULL, NULL, NULL, '<PKFARE vendorCode(s), CSV>'),
       ('PKFARE_API', 'IP_WHITELIST',  NULL, NULL, NULL, NULL, '<PKFARE egress IPs / CIDRs, CSV>')
ON CONFLICT DO NOTHING;
```

## Build & run

```sh
export CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token --domain vdc-repository --domain-owner 683455398069 --query authorizationToken --output text)
./gradlew clean build
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar --client.address.vdcConfigurator=http://localhost:8090/vdc-configurator
curl -s -H 'Content-Type: application/json' --data @adapter-pkfare-service/src/test/resources/search/PKFARE_SearchRQ.json localhost:9022/search
```

Ports: 9022 (API), 7045 (actuator: health, prometheus, metrics).
