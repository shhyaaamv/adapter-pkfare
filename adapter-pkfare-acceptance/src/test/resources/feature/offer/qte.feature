Feature: PKFARE QTE
  PKFARE prices a SEARCH result by sending back its routing.data (the adapter's Base64 OfferData) with the flights.
  The adapter calls Entry-Gate FlightPrice (WireMock, which serves the real 7.7 MB sample FlightPriceRS unless a
  scenario stubs another answer) and answers PKFARE in its format, always with HTTP 200. Each scenario writes the
  PKFARE request and its decoded routing.data, the FlightPriceRQ Entry-Gate received and the PKFARE response to
  build/acceptance/output/<scenario>/.

  The request "request/PKFARE_QteRQ.json" with "request/qte_offer_data.json" is the PKFARE equivalent of the VDC
  sample FlightPriceRQ (an AI COK-DXB round trip, 2 ADT + 1 CHD + 1 INF). That offer is not in the AirShopping sample,
  so its offer data is the fixture; the SEARCH -> QTE scenario uses offer data SEARCH minted.

  Scenario: PKFARE QTE request is converted to the sample VDC FlightPriceRQ
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    When PKFARE calls QTE
    Then Entry-Gate receives one FlightPriceRQ with header Service "FlightPrice" and thirdpartyid the offer owner
    And the FlightPriceRQ matches the sample "samples/VDC_FlightPriceRQ_expected.json"

  Scenario: VDC FlightPriceRS is converted to a PKFARE QTE response shaped like the sample
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    When PKFARE calls QTE
    Then PKFARE receives QTE status 0 with message "success"
    And the QTE response has 2 fromSegments, 2 retSegments and 3 prices
    And the QTE response has every field of the sample "samples/PKFARE_QteRS.json"
    And the QTE routing.data is offer data for the priced offer

  Scenario: A SEARCH result's routing.data drives the FlightPriceRQ
    Given PKFARE search request "request/PKFARE_SearchRQ.json"
    And PKFARE calls SEARCH
    And a PKFARE QTE request for the first shopping result
    When PKFARE calls QTE
    Then Entry-Gate receives one FlightPriceRQ with header Service "FlightPrice" and thirdpartyid the offer owner
    And the FlightPriceRQ carries the offer, ShoppingResponseID and segment keys of the routing.data
    # The stubbed FlightPriceRS prices the sample's AI itinerary, not this offer's flights.
    And PKFARE receives QTE status 202 with message "Invalid fare returned"

  Scenario: Unknown vendor code is rejected before Entry-Gate is called
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And the QTE request field "vendorCode" is "SHA-UNKNOWN"
    When PKFARE calls QTE
    Then PKFARE receives QTE status 3 with message "Invalid vendorCode"
    And Entry-Gate receives no FlightPriceRQ

  Scenario: Undecodable routing.data is rejected before Entry-Gate is called
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And the QTE routing.data is "not-offer-data"
    When PKFARE calls QTE
    Then PKFARE receives QTE status 4 with message "Invalid routing.data"
    And Entry-Gate receives no FlightPriceRQ

  Scenario: Segments out of time order are rejected before Entry-Gate is called
    # Times are local, so they are only compared where two segments share an airport; changing one segment's own
    # times breaks its match with routing.data (status 4) instead. Swapping two segments gives 111.
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And the first two QTE fromSegments are swapped
    When PKFARE calls QTE
    Then PKFARE receives QTE status 111 with message "Invalid date range"
    And Entry-Gate receives no FlightPriceRQ

  Scenario: Entry-Gate answering after PKFARE's cutOffTime is an upstream timeout
    # cutOffTime 1500 ms minus the 500 ms buffer caps the Entry-Gate timeout at its 1 s floor.
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And the QTE request field "cutOffTime" is 1500
    And Entry-Gate answers FlightPrice after 4000 ms
    When PKFARE calls QTE
    Then PKFARE receives QTE status 7 with message "Upstream timeout"
    And Entry-Gate receives one FlightPriceRQ with header Service "FlightPrice" and thirdpartyid the offer owner

  Scenario: Fewer seats left than seated passengers
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And Entry-Gate answers FlightPrice with 2 seats left
    When PKFARE calls QTE
    Then PKFARE receives QTE status 1 with message "Insufficient remaining seats"
    And the QTE response has maxSeats 2 and no routing

  Scenario: No seats left
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And Entry-Gate answers FlightPrice with 0 seats left
    When PKFARE calls QTE
    Then PKFARE receives QTE status 2 with message "All seats have been sold"
    And the QTE response has maxSeats 0 and no routing

  Scenario: FlightPriceRS without a priced offer or errors means no fare
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And Entry-Gate answers FlightPrice with "entrygate/flightprice_no_offer_rs.json"
    When PKFARE calls QTE
    Then PKFARE receives QTE status 201 with message "No fare returned"

  Scenario: FlightPriceRS errors are passed on with their VDC code
    Given PKFARE QTE request "request/PKFARE_QteRQ.json" with routing.data from "request/qte_offer_data.json"
    And Entry-Gate answers FlightPrice with "entrygate/flightprice_error_rs.json"
    When PKFARE calls QTE
    Then PKFARE receives QTE status 3 with message "Offer not available"
    And the QTE response has errorCode "911"
