## Acceptance tests for adapter-pkfare

The suite runs the real adapter bootJar end to end. Only its two dependencies are mocked, both by WireMock:

```
Cucumber (as PKFARE) --POST /search--> adapter-pkfare :9122 --> WireMock :8091
                                                              ├─ /vdc-configurator/evaluator...       (configurator)
                                                              ├─ /oauth2/token                         (Entry-Gate auth)
                                                              └─ /entrygate/rest/request (Service: AirShopping, serves the real sample AirShoppingRS)
```

The AirShoppingRQ the adapter sent is read back from WireMock's request journal.

### Run

```shell
./gradlew :adapter-pkfare-acceptance:runAcceptanceTests --continue
```

The task builds the server bootJar and starts WireMock on 8091 and the adapter on 9122 with the `acceptance` profile. It then runs `feature/**/*.feature` and stops both processes, even when a scenario fails. Add `--offline` when the Gradle cache is warm.

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
- cabin `S` (VDC `W`)
- `returnTagPrice=N`, which gives `ShopResultPreference` `OPTIMIZED`

How the assertions compare against the samples:
- **RQ:** the comparison is exact apart from two things. Traveler order may differ, and the sample's child `Age` is not expected because PKFARE sends no ages. `SortOrder` is checked in order, since it is a priority.
- **RS:** PKFARE's sample comes from a different search (NK, HOU→NYC), so values can't match. The suite checks the *shape* instead: every field path in the sample must appear in our response with the same JSON type. It also checks that every VDC offer became a shopping result and that `flightList` holds exactly the referenced flights.

Configurator values live in `wiremock/__files/configurator/`:
- `search_airshopping_combined.json` holds the `PKFARE_API.*` rows. The same rows are returned for the `Search` and `AirShopping` contexts. `SEARCH_CACHE_TTL_SECONDS` is 0, so every scenario reaches Entry-Gate.
- `cabin-brand-configs.json` holds `CABIN_BRAND.MAPPED_CABIN`.
- `pkfare_configs.json` holds the `ENABLE_SERVICE` gate.

### Debug from the IDE

1. Build the server and fetch WireMock:
   ```shell
   ./gradlew :adapter-pkfare-server:bootJar :adapter-pkfare-acceptance:copyWireMock
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
5. Stop both processes when you're done.

### Adding a scenario

1. Add steps to a `.feature` file under `src/test/resources/feature/`, with glue in `...acceptance.offer`/`order`.
2. Stub any new Entry-Gate service or configurator lookup under `src/test/resources/wiremock/` ([WireMock stubbing](https://wiremock.org/docs/stubbing/)).
