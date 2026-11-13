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
| `adapter-pkfare-service` | Chains, commands, converters, Entry-Gate client, caches, settings model      |

## Services

| Group | PKFARE API          | Hosted by | Status                              |
|-------|---------------------|-----------|-------------------------------------|
| Offer | SEARCH → AirShopping | Verteil   | **implemented** (`POST /search`)     |
| Offer | QTE                 | Verteil   | to do                               |
| Order | Order, Order Status Push | Verteil | to do                            |
| Order | Get Pending Ticketing Orders, Ticket Issuance | PKFARE (adapter calls them) | to do |

### SEARCH chain (`offer/search/SearchConfiguration`)

```
AuthenticateRequest (vendorCode + IP whitelist) → SearchCacheLookup [hit: done] → SearchRQ → InvokeEntryGate
  → AirShoppingRSValidator [no offers: status 0, empty] → SearchRS → SearchCacheStore
```

* **Response statuses**: 0 success (also "no content": empty lists), 3 auth failure / disabled service / Entry-Gate
  error, 4 invalid request, 5 internal error. `msg` is kept under 64 characters.
* **Caller IP** is `request.getRemoteAddr()` with `server.forward-headers-strategy: native`, so `X-Forwarded-For` is
  honoured only from trusted internal proxies (Tomcat RemoteIpValve defaults: private ranges). Set
  `server.tomcat.remoteip.internal-proxies` if the ingress is outside those ranges.

## Caching

| Cache | Where | Key | Lifetime |
|-------|-------|-----|----------|
| Entry-Gate access token | Caffeine (per pod) + Redis (cluster, single-flight lock) | hash(host + username) | token `expires_in` − 30 s |
| SEARCH responses (successful, non-empty); **off by default** (`SEARCH_CACHE_ENABLED`) | Caffeine (per pod), weight-bounded (`SEARCH_CACHE_MAX_WEIGHT`) | vendorCode + all search criteria | `SEARCH_CACHE_TTL_SECONDS` (120 s) |

Redis is optional: without it the token cache works per pod.

## Configuration

There is no database or configurator. Only infrastructure and access settings are Spring properties; how a service
behaves is fixed in code:
- `AirShoppingConstants`: SEARCH → AirShopping behaviour (timeout 30 s capped by PKFARE's `cutOffTime` − 500 ms,
  fare preferences `PUBL,PVT`, `ShopResultPreference` `BEST` / `OPTIMIZED` for `returnTagPrice` N / Y, sort order
  `PRICE,STOP,DEPARTURE_TIME`, no-result error codes, `ticketIssueTime` 120, default product type `PUB`, SEARCH cache
  off by default (`SEARCH_CACHE_ENABLED`; 120 s / 50000 weight when on)).
- `PkfareConstants.CABIN_MAPPING`: PKFARE cabin → VDC cabin (`F:F, C:C, S:W, Y:Y`); the reverse is derived.

`application.yaml` holds the properties shared by all environments; `application-<profile>.yaml` holds what differs.
The YAML files carry no comments; this section is their documentation.

| Property | Default | Meaning |
|----------|---------|---------|
| `pkfare.enabled-services.<service>` | `search: true`, `qte: true` | PKFARE services served; others get status 3 |
| `pkfare.vendor-codes` | per env (dev/QA: `PKFARETESTCODE`) | accepted PKFARE vendor codes |
| `pkfare.ip-whitelist.enabled` | `true` (dev/QA: `false`) | check the caller IP against `addresses` |
| `pkfare.ip-whitelist.addresses` | **per env** | PKFARE source IPs / CIDRs; empty rejects all (when enabled) |
| `pkfare.message-log.enabled` | `true` | masked trace logging of the PKFARE and Entry-Gate request/response pairs |
| `pkfare.message-log.prod-error-monitoring` | `true` | with `enabled: false`, still log the PKFARE request/response of failed requests |
| `pkfare.message-log.mask-elements` / `mask-attributes` | empty | elements / attributes masked in those logs |
| `verteil.host` | **per env** (QA: `https://qaapi.sandbox.verteil.com`) | Entry-Gate and OAuth2 token server base URL |
| `verteil.path` | `/entrygate/rest/request` | Entry-Gate endpoint shared by all services (service picked by the `Service` header) |
| `verteil.auth-path` / `auth-scope` | `/oauth2/token` / `api` | token endpoint; body `grant_type=client_credentials&scope={auth-scope}` |
| `verteil.timeout-millis` | `30000` | token call timeout |
| `verteil.token.ttl-seconds` | `1800` | token lifetime when the token response has no `expires_in` |
| `verteil.token.lock-*` | `lock-lease-millis` 10000, `lock-wait-millis` 6000, `lock-poll-millis` 50 | Redis lock that lets one pod fetch a token for the cluster |
| `verteil.circuit-breaker.*` | see below | Entry-Gate circuit breaker |
| `aws.secretManager.name` / `region` | per env | Entry-Gate credentials secret (below) |
| `aws.secretManager.url` | empty | Secrets Manager endpoint override (acceptance tests only) |
| `ordermanagement.redis.*` | env vars `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_SSL_ENABLED` | shared Redis for the token cache (optional) |
| `adapter.http.*` | `poolMax` 300, `poolTimeoutMillis` 500, `connectTimeoutMillis` 5000, `responseTimeoutMillis` 180000 | Entry-Gate HTTP connection pool and timeouts |
| `adapter.monitor.*` | `idleTime` 180 s, `task.interval` 30000 ms | how often idle and expired pool connections are evicted |
| `server.forward-headers-strategy` | `native` | caller IP from `X-Forwarded-For` only via trusted proxies (see *Caller IP*) |

### Values still to be filled

These are empty in the profile files until the values are known. Until then the pod starts, but Entry-Gate calls fail
(no host) and every PKFARE request is rejected (no vendor code / IP whitelist).

| Profile | `verteil.host` | `pkfare.vendor-codes`, `pkfare.ip-whitelist.addresses` |
|---------|----------------|---------------------------------------------------------|
| `eks-dev` | missing | sample vendor, IP check off |
| `eks-qa` | set | sample vendor, IP check off |
| `eks-stage`, `eks-prod-in`, `eks-prod-eu`, `eks-prod-oc`, `dr-in`, `dr-eu`, `dr-oc` | missing | missing |

### Circuit breaker

Every Entry-Gate call (token and services) goes through one Resilience4j circuit breaker (`verteil.circuit-breaker.*`).
I/O failures and every HTTP 4xx/5xx (including 401) count as failures. It opens when
`failure-rate-threshold` % (50) of the last `sliding-window-size` (50) calls failed, or `slow-call-rate-threshold` %
(100) took longer than `slow-call-duration-millis` (30000), once at least `minimum-number-of-calls` (20) were made.
While open, calls fail at once with PKFARE status 3 and Entry-Gate is not called; after
`wait-duration-in-open-state-millis` (30000) `permitted-number-of-calls-in-half-open-state` (5) trial calls decide
whether it closes. State changes are logged, and its state and call counts are on `/actuator/prometheus`
(`resilience4j_circuitbreaker_*`, name `entryGate`), next to `supplier_transaction_duration` per service and status.

### Entry-Gate credentials (AWS Secrets Manager)

The Entry-Gate OAuth2 client id and secret are read at startup from the AWS secret `aws.secretManager.name`
in `aws.secretManager.region`, using the default AWS credentials chain (the pod's IAM role). The secret is a JSON
object:

```json
{"username": "<entry-gate client id (agent id)>", "password": "<entry-gate client secret>"}
```

The pod does not start if the secret is missing or lacks either field. A rotated secret is picked up without a
restart: only when the token endpoint reports invalid credentials (HTTP 401, or HTTP 400 with `invalid_client`), the
adapter re-reads the secret (at most once a minute per
pod) and, if it changed, retries the login with the new credentials. To rotate, update the secret first (or together
with Entry-Gate). Bearer tokens already issued are reused until they expire.

| Profile | Secret | Region |
|---------|--------|--------|
| `eks-dev` / `eks-qa` / `eks-stage` | `dev` / `qa` / `stage` `/adapter-pkfare/entry-gate` | `ap-south-1` |
| `eks-prod-in`, `eks-prod-oc` | `prod/adapter-pkfare/entry-gate` | `ap-south-1` |
| `eks-prod-eu` | `prod/adapter-pkfare/entry-gate` | `eu-north-1` |
| `dr-in`, `dr-oc` / `dr-eu` | `prod/adapter-pkfare/entry-gate` | `ap-south-2` / `eu-west-3` |

## Build & run

```sh
export CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token --domain vdc-repository --domain-owner 683455398069 --query authorizationToken --output text)
./gradlew clean build
# QA settings; needs AWS credentials that can read the qa secret (e.g. AWS_PROFILE). The sample request's vendor is SHA-TEST.
java -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-*.jar --spring.profiles.active=eks-qa --pkfare.vendor-codes=SHA-TEST
curl -s -H 'Content-Type: application/json' --data @adapter-pkfare-service/src/test/resources/search/PKFARE_SearchRQ.json localhost:9022/search
```

Ports: 9022 (API), 7045 (actuator: health, prometheus, metrics).
