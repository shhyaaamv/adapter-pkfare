## Acceptance tests for adapter-pkfare

The suite runs the real adapter bootJar end to end. Entry-Gate is mocked by WireMock, and the settings come from a
real Postgres table:

```
Cucumber (as PKFARE) --POST /search--> adapter-pkfare :9122 --> WireMock :8091
                                            │                 ├─ /oauth2/token                         (Entry-Gate auth)
                                            │                 └─ /entrygate/rest/request (Service: AirShopping, serves the real sample AirShoppingRS)
                                            └── JDBC (startup) --> Postgres agency_adapter_tbls.pkfare_configs
```

The AirShoppingRQ the adapter sent is read back from WireMock's request journal.

### Run

```shell
./gradlew :adapter-pkfare-acceptance:runAcceptanceTests --continue
```

The task:
1. builds the server bootJar
2. seeds the database (`seedAcceptanceDb`, below)
3. starts WireMock on 8091 and the adapter on 9122 with the `acceptance` profile
4. runs `feature/**/*.feature`
5. stops both processes and restores the database (`restoreAcceptanceDb`), even when a scenario fails

Add `--offline` when the Gradle cache is warm.

### Database

The adapter loads `agency_adapter_tbls.pkfare_configs` once at startup, so the rows must be in place before it starts.
The database must already have that schema and table, and the user needs `SELECT`/`INSERT`/`DELETE` on the table and
`USAGE` on its id sequence. No other schema is touched. Docker isn't required.

By default the suite uses the local database `jdbc:postgresql://localhost:5432/vdc_db?currentSchema=agency_adapter_tbls`
as `pkfare_user`/`pkfare123`. To use another one, set `ACCEPTANCE_DB_URL`, `ACCEPTANCE_DB_USER` and
`ACCEPTANCE_DB_PASSWORD`. Both the Gradle tasks and the adapter read them.

- `seedAcceptanceDb` saves the table's current rows to `build/acceptance/db/pkfare_configs_backup.json`. It then
  replaces them, in one transaction, with `src/test/resources/db/pkfare_configs.sql`.
- `restoreAcceptanceDb` puts the saved rows back (ids included) and deletes the backup file.
- If a run was interrupted, the next seed restores the leftover backup first. A developer's own rows therefore
  survive either way.

`./gradlew build` compiles this module but does not run it: its `test` task is disabled.

### What a run leaves behind

Each scenario writes what went over the wire to `build/acceptance/output/<scenario>/`:

| File | What it is |
|---|---|
| `1_PKFARE_SearchRQ.json` | The request PKFARE sent to the adapter |
| `2_VDC_AirShoppingRQ.json` | The converted request Entry-Gate received |
| `2_VDC_AirShoppingRQ_headers.json` | Its HTTP headers (`Service`, `Authorization`, `VDC_TransactionId`, ...; no `ThirdpartyId`) |
| `3_PKFARE_SearchRS.json` | The response PKFARE received |
| `3_PKFARE_SearchRS_vs_sample.txt` | Field paths missing from, typed differently from, or extra to PKFARE's sample response |

Other places to look:
- `build/acceptance/logs/adapter-pkfare.log` holds the adapter log, including the VDC message log.
- `build/wiremock/logs/wiremock.log` holds every stubbed call, including unmatched ones.
- `build/reports/cucumber/cucumber.html` is the Cucumber report.

### Fixtures

The samples come from `~/Documents/POD/PKFare/Services/SEARCH/` and are copied verbatim:

| File | Source | Used for |
|---|---|---|
| `samples/VDC_AirShoppingRQ.json` | `VDC_AirshoppingRQ.json` | Expected converted RQ |
| `samples/PKFARE_SearchRS.json` | `SEARCH_RS.json` | Expected response shape |
| `wiremock/__files/entrygate/airshopping_rs.json` | `VDC_AirshoppingRS.json` (5 MB, 100 offers) | Entry-Gate response |

`request/PKFARE_SearchRQ.json` is the PKFARE request that corresponds to the sample VDC RQ:
- COK→DXB round trip, 20 Oct to 28 Oct 2026
- 2 ADT + 1 CHD + 1 INF
- cabin `S` (VDC `W`, via the static `CabinMapping.PKFARE`)
- `returnTagPrice=N`, which gives `ShopResultPreference` `OPTIMIZED`

How the assertions compare against the samples:
- **RQ:** the comparison is exact apart from two things. Traveler order may differ, and the sample's child `Age` is not expected because PKFARE sends no ages. `SortOrder` is checked in order, since it is a priority.
- **RS:** PKFARE's sample comes from a different search (NK, HOU→NYC), so values can't match. The suite checks the *shape* instead: every field path in the sample must appear in our response with the same JSON type. It also checks that every VDC offer became a shopping result and that `flightList` holds exactly the referenced flights.

The `PKFARE_API.*` settings are in `db/pkfare_configs.sql`. Every row has a NULL `service`, so `Search` and `AirShopping` read the same values. They differ from the Liquibase defaults on purpose:
- `HOST` is WireMock.
- `SEARCH_CACHE_TTL_SECONDS` is 0, so every scenario reaches Entry-Gate.

### Debug from the IDE

1. Build the server, fetch WireMock and seed the database:
   ```shell
   ./gradlew :adapter-pkfare-server:bootJar :adapter-pkfare-acceptance:copyWireMock :adapter-pkfare-acceptance:seedAcceptanceDb
   ```
2. Start WireMock:
   ```shell
   java -jar adapter-pkfare-acceptance/build/wiremock/wiremock-standalone-3.13.1.jar --port=8091 --verbose --root-dir=adapter-pkfare-acceptance/src/test/resources/wiremock
   ```
3. Start the adapter with a debug port on 5010. You can also run `AdapterPkfareApplication` from the IDE with the `acceptance` profile.
   ```shell
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5010 -Dspring.profiles.active=acceptance -jar adapter-pkfare-server/build/libs/adapter-pkfare-server-0.0.1-SNAPSHOT.jar
   ```
4. Run `com.verteil.thirdparties.adapter.pkfare.acceptance.RunAcceptanceTest`. With the working directory set to `adapter-pkfare-acceptance`, outputs land in `build/acceptance/output/`.
5. Stop both processes when you're done, then put your rows back with `./gradlew :adapter-pkfare-acceptance:restoreAcceptanceDb`.

### Adding a scenario

1. Add steps to a `.feature` file under `src/test/resources/feature/`, with glue in `...acceptance.offer`/`order`.
2. Stub any new Entry-Gate service under `src/test/resources/wiremock/` ([WireMock stubbing](https://wiremock.org/docs/stubbing/)). Add any new setting to `src/test/resources/db/pkfare_configs.sql`.
