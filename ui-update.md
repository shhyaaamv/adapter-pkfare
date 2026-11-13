# UI update: PKFARE QTE (pricing)

Self-contained brief for whoever updates the UI that drives the `adapter-pkfare` API. This brief comes from the implemented code on branch `POD-39968` and from the QTE schema. It does not change any UI code.

## 1. Service

| Item | Value |
|---|---|
| Service | `qte` (PKFARE "QTE", pricing / fare confirmation) |
| Purpose | Re-prices one offer from a SEARCH result (live Entry-Gate `FlightPrice`, no cache) and returns the confirmed fare, seats, baggage and fare rules |
| Endpoint | `POST {baseUrl}/qte` (no context path; local `baseUrl` = `http://localhost:9022`) |
| Content type | Request `Content-Type: application/json`; response `application/json` |
| HTTP status | Always **200**. The outcome is in the body field `status` (section 5) |
| Auth | No header. The adapter authenticates with `vendorCode` (`pkfare.vendor-codes`) and checks the caller IP against `pkfare.ip-whitelist.addresses` unless `pkfare.ip-whitelist.enabled` is `false`. dev/QA have test vendor `PKFARETESTCODE` with the whitelist off |
| Flow dependency | `routing.data` must be copied unchanged from a SEARCH response (`POST /search`, `shoppingResultList[].data`). QTE cannot run on its own |
| Server code | `adapter-pkfare-server/src/main/java/com/verteil/thirdparties/adapter/pkfare/controller/offer/OfferController.java` (`qte`), `adapter-pkfare-service/src/main/java/com/verteil/thirdparties/adapter/pkfare/offer/qte/` |

## 2. Schema and sample files

| Role | Path |
|---|---|
| IncomingRequest + FinalResponse schema (PKFARE OpenAPI) | `/home/shyam/Documents/Verteil/pkfare-api-schema/src/main/resources/QTE_SCHEMA.yaml` (`QteRequest`, `QteRequestRouting`, `QteSegment`, `QteResponse`, `QteRouting`, `QtePrice`, `QteFreeBaggage`, `QteRefundInfo`/`QteRefundCondition`, `QteChangeInfo`/`QteChangeCondition`, `QteOtherFareRule`). Java models are `com.verteil.schema.pkfare.*` in `pkfare-api-schema:1.3.0-SNAPSHOT` |
| Vendor's original schema (has a doc error: `retSegments` typed `string`) | `~/Documents/POD/PKFare/Services/QTE(Pricing)/qte_schema.yaml` |
| Outgoing (Entry-Gate FlightPriceRQ/RS) schema | `~/Documents/POD/PKFare/Services/QTE(Pricing)/vdc-flightprice.yaml` (internal; the UI does not see it) |
| Sample PKFARE request | `adapter-pkfare-service/src/test/resources/qte/PKFARE_QteRQ.json` (with `data: ""`), `adapter-pkfare-acceptance/src/test/resources/request/PKFARE_QteRQ.json`, vendor sample `~/Documents/POD/PKFare/Services/QTE(Pricing)/QTE_RQ.http` |
| Sample decoded `routing.data` | `adapter-pkfare-service/src/test/resources/qte/offer_data.json`, `adapter-pkfare-acceptance/src/test/resources/request/qte_offer_data.json` |
| Sample PKFARE response | `adapter-pkfare-acceptance/src/test/resources/samples/PKFARE_QteRS.json` (= vendor `~/Documents/POD/PKFare/Services/QTE(Pricing)/QTE_RS.json`) |
| Scenario list (good test cases for the UI) | `adapter-pkfare-acceptance/src/test/resources/feature/offer/qte.feature` |
| Existing API test client (only SEARCH so far) | `.ai/PKFARE_API_TEST.postman_collection.json` (git-excluded). It already stores `shoppingResultList[0].data` in the variable `lastSearchData` |

## 3. Request fields the UI must send (`QteRequest`)

"Schema req" means required by `QTE_SCHEMA.yaml`. "Adapter" says what the code actually does with the field. Send every schema-required field even when the adapter ignores it.

| field path | type | schema req | allowed values | notes (adapter behaviour) |
|---|---|---|---|---|
| `vendorCode` | string | Y | a configured vendor code | Authentication. Unknown or blank gives status 3 `Invalid vendorCode` |
| `referenceId` | string | Y | free text | PKFARE correlation id; logged only |
| `tripType` | string | Y | `"1"` one-way, `"2"` round-trip | Anything else gives status 4 |
| `adultNumber` | integer | Y | 1-9 | Out of range gives status 4 |
| `childNumber` | integer | Y | 0-9 | Out of range gives status 4 |
| `infantNumber` | integer | Y | 0-9, and <= `adultNumber` | Otherwise status 4 |
| `cutOffTime` | integer (ms) | Y | > 0 | Caps the Entry-Gate timeout: min(30000, `cutOffTime` - 500) (`FlightPriceConstants`), never below 1000. Missing or <= 0 means 30000 |
| `routing` | object | Y | | |
| `routing.data` | string | Y | the SEARCH `shoppingResultList[].data`, verbatim | Base64 offer data minted by SEARCH (stage SHOP). It is opaque: don't edit, trim or decode it in the UI. About 7 KB (max seen 7068 chars). Blank, corrupt, or offer data from a QTE response gives status 4 `Invalid routing.data` |
| `routing.fromSegments[]` | array of `QteSegment` | Y | >= 1 element | Outbound segments in flight order. Must be the same segments the chosen SEARCH result had (`flightList` entries that `segmentList` references for travelDirection 1) |
| `routing.retSegments[]` | array of `QteSegment` | Y | `[]` when `tripType` = 1; >= 1 element when 2 | Return segments in flight order |
| `routing.tag` | string | Y | free text, may be `""` | The SEARCH result's `tag`. Used only as the response `tag` fallback |

`QteSegment` (each element of `fromSegments`/`retSegments`):

| field path | type | schema req | allowed values | notes (adapter behaviour) |
|---|---|---|---|---|
| `marketingCarrier` | string | Y | IATA 2-letter | **Adapter-required**; part of the segment match key |
| `flightNumber` | integer | Y | no carrier prefix, no leading zeros | **Adapter-required**; part of the match key |
| `depAirport` | string | Y | IATA 3-letter | **Adapter-required**; part of the match key (trimmed, case-insensitive) |
| `arrAirport` | string | Y | IATA 3-letter | **Adapter-required**; part of the match key |
| `depTime` | string | Y | `yyyyMMddHHmm`, local time, strict (e.g. `202611180750`) | **Adapter-required**; part of the match key. A bad format gives 4 |
| `arrTime` | string | Y | `yyyyMMddHHmm`, local time, strict | **Adapter-required**; part of the match key |
| `cabinClass` | string | Y | PKFARE cabin with a `CABIN_BRAND.MAPPED_CABIN` row, seeded as `F`, `C`, `S`, `Y` | Optional for the adapter. Any other value gives 4 `cabinClass must be one of ...` |
| `rbd` | string | Y | booking class letter | Sent upstream as the booking class; blank or missing gives status 4 |
| `operatingCarrier` | string | Y | IATA 2-letter | Sent upstream; the offer data's value is the fallback |
| `aircraftCode` | string | Y | IATA 3-char | Sent upstream when present |
| `duration` | integer (min) | Y | | Ignored |
| `codeShare` | string | Y | `"true"` / `"false"` (string in the generated model; the vendor sample sends a boolean) | Ignored |
| `operatingFlightNumber` | integer | Y | | Ignored |
| `travelDirection` | integer | Y | 1 out, 2 return | Ignored. The direction comes from which list holds the segment |
| `flightSeq` | string | Y | `"1"`..`"n"` per direction | Ignored. The order comes from the array position |
| `depTerminal` / `arrTerminal` | string | N | | Ignored |
| `stopAirport` | string | N | `/`-separated | Ignored |
| `flightRefNum` | integer | N | | Ignored |

Minimal valid one-way body:
```json
{
  "vendorCode": "PKFARETESTCODE", "referenceId": "ui-123", "tripType": "1",
  "adultNumber": 1, "childNumber": 0, "infantNumber": 0, "cutOffTime": 28000,
  "routing": {
    "data": "<shoppingResultList[i].data from /search>",
    "fromSegments": [{"marketingCarrier": "AI", "flightNumber": 9896, "depAirport": "COK", "depTime": "202611180750",
                      "arrAirport": "BOM", "arrTime": "202611181015", "cabinClass": "Y", "rbd": "W",
                      "operatingCarrier": "IX", "aircraftCode": "320", "duration": 145, "codeShare": "true",
                      "operatingFlightNumber": 9896, "travelDirection": 1, "flightSeq": "1"}],
    "retSegments": [],
    "tag": ""
  }
}
```

## 4. Response fields the UI will receive (`QteResponse`)

Null top-level and `routing` fields are always present. `errorCode`/`errorMsg` are serialised as `null`, `retSegments` as `[]`, and `routing` is `null` on any non-zero status.

| field path | type | schema req | notes |
|---|---|---|---|
| `status` | integer | Y | See section 5 |
| `msg` | string | Y | `success`, or error text (<= 63 chars, abbreviated with `...`) |
| `errorCode` | string \| null | Y | Entry-Gate error code. Set only for status 3 caused by FlightPriceRS `Errors` (e.g. `911`) |
| `errorMsg` | string \| null | Y | Entry-Gate error text (<= 299 chars). Same condition as `errorCode` |
| `sessionId` | string | Y | VDC ShoppingResponseID (~47 chars; the spec says < 20). Present on status 0/1/2 |
| `maxSeats` | integer | Y | 0-9. Min `SeatsLeft` over the priced segments, else adults+children; capped at 9. Status 2 sends 0. Present on 0/1/2 |
| `routing` | object \| null | Y | Only on status 0 |
| `routing.data` | string | Y | **New** Base64 offer data (stage PRICE, priced OfferID). It is **not** the request's `data`. Keep it for the future Order call; opaque |
| `routing.fromSegments[]` / `retSegments[]` | `QteSegment[]` | Y | Priced itinerary; `retSegments` is `[]` for one-way. Fields below |
| `...Segments[].marketingCarrier` / `flightNumber` | string / integer | Y | |
| `...Segments[].depAirport` / `arrAirport` | string | Y | |
| `...Segments[].depTime` / `arrTime` | string | Y | `yyyyMMddHHmm` local |
| `...Segments[].duration` | integer | Y | Minutes |
| `...Segments[].rbd` | string | Y | Priced booking class |
| `...Segments[].cabinClass` | string | Y | PKFARE cabin (`F`/`C`/`S`/`Y`), default `Y` |
| `...Segments[].codeShare` | string | Y | `"true"`/`"false"` |
| `...Segments[].aircraftCode` | string | Y | `""` when unknown |
| `...Segments[].operatingCarrier` / `operatingFlightNumber` | string / integer | Y | Fall back to the marketing values |
| `...Segments[].travelDirection` | integer | Y | 1 / 2 |
| `...Segments[].flightSeq` | string | Y | `"1"`..`"n"` per direction |
| `...Segments[].depTerminal` / `arrTerminal` / `stopAirport` | string | N | Omitted when absent; `stopAirport` is `/`-joined |
| `...Segments[].flightRefNum` | integer | N | Never sent |
| `routing.priceList[]` | `QtePrice[]` | Y | One per requested passenger type, ordered 0, 1, 2 |
| `routing.priceList[].passengerType` | integer | Y | 0 adult, 1 child, 2 infant |
| `routing.priceList[].fare` | number | Y | Base fare **per passenger**, 2 decimals |
| `routing.priceList[].tax` | number | Y | Per passenger, decimal (the spec calls it integer) |
| `routing.priceList[].minFare` | number | N | Never sent |
| `routing.eligibility` | string | Y | Always `""` |
| `routing.validatingCarrier` | string | Y | Offer owner, e.g. `AI` |
| `routing.fareBasis` | string | Y | One per segment, `;`-joined (out then return); `""` if any is unknown |
| `routing.freeBaggageList[]` | `QteFreeBaggage[]` | Y | Per passenger type x segment |
| `...freeBaggageList[].travelDirection` / `flightSeq` / `passengerType` | integer | Y | |
| `...freeBaggageList[].baggagePiece` / `baggageWeight` | integer | Y | Checked bags; `-1` = not stated; none = piece 0 / weight -1 |
| `...freeBaggageList[].carryOnPiece` / `carryOnWeight` | integer | Y | Same convention |
| `...freeBaggageList[].carryOnSize` | integer[] | N | `[L, W, H]` when known |
| `routing.refundInfoList[]` | `QteRefundInfo[]` | Y | `passengerType`, `refundType` (0 whole trip, 1 return part only, round trip) |
| `...refundInfoList[].conditionList[]` | `QteRefundCondition[]` | Y | `start`/`end` (`"-1"`), `unit` (`"h"`), `refundable` (bool), `refundFee` (integer \| null), `currency` |
| `routing.changeInfoList[]` | `QteChangeInfo[]` | Y | `passengerType`, `changeType` (as refundType) |
| `...changeInfoList[].conditionList[]` | `QteChangeCondition[]` | Y | `start`, `end`, `unit`, `changeable` (bool), `changeFee` (integer \| null), `currency` |
| `routing.currency` | string | Y | e.g. `INR` |
| `routing.productType` | string | Y | `PUB` or `PRV` (default `PUB`, `FlightPriceConstants.DEFAULT_PRODUCT_TYPE`) |
| `routing.tag` | string | N | Price class (brand) names, `/`-joined; else the request `tag` |
| `routing.otherFareRules[]` | `QteOtherFareRule[]` | N | `passengerType` + `detail` (string[] of fare-rule texts) |
| `routing.note` / `routing.penalty` | string | N | Never sent (structured rules are sent instead) |

## 5. Behaviour changes

New QTE status codes. The SEARCH ones (0/3/4/5) are unchanged.

| status | when | msg (exact or pattern) | body |
|---|---|---|---|
| 0 | Priced | `success` | Full `routing` |
| 1 | Seats left (from VDC) < adults + children | `Insufficient remaining seats` | `sessionId`, `maxSeats`, `routing: null` |
| 2 | Any priced segment has 0 seats left | `All seats have been sold` | `maxSeats: 0`, `routing: null` |
| 3 | Unknown or blank vendor; IP not whitelisted; service disabled; Entry-Gate auth or connection failure; empty FlightPriceRS | `Invalid vendorCode`, `Request IP is not whitelisted`, `Qte service is not enabled`, `Empty response from Verteil`, ... | errorCode/errorMsg null |
| 3 | FlightPriceRS has `Errors` and no priced offer | VDC error text | `errorCode` = VDC code, `errorMsg` = text |
| 4 | Request validation (below) or an unreadable JSON body | Validation text, or `Request Parameters error` for bad JSON | |
| 5 | Unexpected error | `Internal error, txn: <id>` | |
| 7 | Entry-Gate did not answer before the capped timeout | `Upstream timeout` | |
| 111 | Duplicate segment, a segment departing before the previous one arrives at the same airport, or segments in a different order from the SEARCH result | `Invalid date range` | |
| 201 | FlightPriceRS has no priced offer and no errors | `No fare returned` | |
| 202 | Priced offer unusable: passenger types differ, an amount is missing or total <= 0, or the priced itinerary differs from the requested segments | `Invalid fare returned` | |
| 6 | Currency mismatch | Never sent (the request has no currency) | |

Validation messages (status 4) the UI may want to surface or prevent:
- `adultNumber must be between 1 and 9`
- `childNumber/infantNumber must be between 0 and 9`
- `infantNumber cannot exceed adultNumber`
- `tripType must be 1 (one-way) or 2 (round-trip)`
- `routing.fromSegments is required`
- `routing.retSegments is required for tripType 2`
- `routing.retSegments must be empty for tripType 1`
- `Segment depAirport, arrAirport, depTime, arrTime, marketingCarrier and flightNumber are required`
- `depTime must be formatted as yyyyMMddHHmm` (also for `arrTime`)
- `cabinClass must be one of F, C, S, Y` (list comes from config)
- `Invalid routing.data`
- `Passenger numbers do not match routing.data`: passenger counts per type must equal those used in the SEARCH (CHD and CNN count as the same type)
- `Segments do not match routing.data`: segment count differs, or a segment is in the wrong direction
- `Unknown segment <DEP|yyyyMMddHHmm|ARR|yyyyMMddHHmm|CARRIER|FLIGHTNO>` (truncated to 63 chars)

**SEARCH also changed:** `shoppingResultList[].data` is no longer the bare VDC OfferID. It is now Base64 offer data of about 7 KB, the input QTE needs. Offers without a VDC shopping response id are dropped from the SEARCH results. Any UI that shows or edits `data` as an id must treat it as opaque.

Unavailable fields: response `routing.note`, `routing.penalty`, `priceList[].minFare` and `segment.flightRefNum` are never filled; `eligibility` is always `""`.

## 6. Suggested UI changes

Derived from the schema and code above. **Assumption:** no UI code for PKFARE was found in this repo. The only client is the Postman collection `.ai/PKFARE_API_TEST.postman_collection.json` (SEARCH folders 00-05 only). Apply the items below to whichever UI or collection drives `/search`.

1. **New "QTE / Price" action on each SEARCH result** (primary flow):
   - Build the request from the selected `shoppingResultList[i]`:
     - `routing.data` = `data`
     - `routing.tag` = `tag`
     - `fromSegments` / `retSegments` = the `flightList` entries in `segmentList` (or `flightRefNum` order), split by `travelDirection`, each mapped to a `QteSegment` with the same field names
     - `vendorCode`, `tripType`, `adultNumber`, `childNumber`, `infantNumber` copied from the SEARCH request
     - `referenceId` generated
     - `cutOffTime` taken from config or input
   - Don't let the user edit `data`, segment times, flight numbers or passenger counts. Any change gives status 4 or 111.
2. **QTE request form (manual / advanced mode)**:
   - Inputs: vendorCode, referenceId, tripType (select 1/2), adults (1-9), children (0-9), infants (0..adults), cutOffTime (ms), routing.data (textarea, paste), tag.
   - Editable segment tables for outbound and return. The return table is shown and required only when tripType = 2.
   - Client-side checks mirroring section 5: counts, tripType vs return segments, required segment fields, `yyyyMMddHHmm` format, cabinClass in `F/C/S/Y`.
3. **QTE result view**:
   - Header: `status` + `msg`, sessionId, maxSeats. Show `errorCode`/`errorMsg` when not null.
   - Segment table per direction (carrier+flight, dep/arr airport+time, duration, rbd, cabinClass, aircraft, operating carrier, terminals, stops).
   - Price table: passengerType label (0 ADT / 1 CHD / 2 INF), fare, tax, fare+tax per passenger, and the total over the requested counts, in `currency`.
   - Also show validatingCarrier, productType, tag, fareBasis (split on `;` per segment).
   - Baggage grid (direction x seq x passenger type). Render `-1` as "not stated" and `0` pieces as "none".
   - Refund and change rule tables (type 0/1, allowed, fee or "n/a" when null, currency).
   - otherFareRules texts per passenger type.
   - Keep `routing.data` (the PRICE offer data) hidden or copyable, for the future Order step.
4. **Status handling**: map statuses 1, 2, 7, 111, 201 and 202 to user-facing messages (section 5). On 1/2 there is no routing; show maxSeats. On 7, offer a retry with a larger `cutOffTime`.
5. **SEARCH view**: stop labelling `data` as "Offer ID". Treat it as an opaque, long value (truncate in the display, copy button).
6. **Postman collection** (if that is the UI): add a folder "06 QTE" that uses `{{lastSearchData}}` plus the segments of `shoppingResultList[0]`. Add cases mirroring the acceptance scenarios: success, unknown vendor (3), garbage `data` (4), swapped segments (111), mismatched passenger counts (4), and one-way with `retSegments` (4).

## 7. Open items (confirm with the user)

1. Which UI or app is the target? No UI code is in this repo; the Postman collection is the only client found.
2. `pkfare-api-schema:1.3.0-SNAPSHOT` (QTE models) must be committed and published to CodeArtifact before CI builds; it exists only in the local `~/.m2` today.
3. `sessionId` is the ~47-char VDC id; PKFARE's spec says < 20 chars. Should the UI validate or truncate? (The adapter sends it unchanged.)
4. PKFARE's limit on `data` length is unconfirmed (offer data ~7 KB). UI inputs and storage must not cap it.
5. The QTE `routing.data` is new stage-PRICE offer data for the future Order service, not an echo of the request. Confirm the Order design before the UI wires it into booking.
6. `pkfare.enabled-services.qte` is `true` in `application.yaml`, so every environment serves QTE, prod included. Confirm the rollout before exposing QTE in a prod UI.
7. `codeShare` is a string in the generated model (`"true"`/`"false"`), but PKFARE's sample sends a boolean. Confirm which type the UI should send. The adapter ignores it on input.
8. Status 1/2 only fire when Entry-Gate returns `SeatsLeft`; the VDC sample has none. Confirm on QA before relying on them in the UI.
