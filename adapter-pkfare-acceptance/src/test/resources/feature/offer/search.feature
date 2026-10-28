Feature: PKFARE SEARCH
  PKFARE calls the adapter in its own format. The adapter calls Entry-Gate AirShopping (WireMock, which serves the
  real sample AirShoppingRS) and answers PKFARE in its format. Each scenario writes the PKFARE request, the
  AirShoppingRQ Entry-Gate received and the PKFARE response to build/acceptance/output/<scenario>/.

  Background:
    Given PKFARE search request "request/PKFARE_SearchRQ.json"

  Scenario: PKFARE SEARCH request is converted to the sample VDC AirShoppingRQ
    When PKFARE calls SEARCH
    Then Entry-Gate receives one AirShoppingRQ with header Service "AirShopping" and no ThirdpartyId
    And the AirShoppingRQ matches the sample "samples/VDC_AirShoppingRQ.json" apart from child ages

  Scenario: VDC AirShoppingRS is converted to a PKFARE SEARCH response shaped like the sample
    When PKFARE calls SEARCH
    Then PKFARE receives a shopping result for each of the VDC offers
    And every flightRefNum in the shopping results is a flight in flightList
    And the PKFARE response has every field of the sample "samples/PKFARE_SearchRS.json"

  Scenario: Unknown vendor code is rejected before Entry-Gate is called
    Given the search request field "vendorCode" is "SHA-UNKNOWN"
    When PKFARE calls SEARCH
    Then PKFARE receives status 3 with message "Invalid vendorCode"
    And Entry-Gate receives no AirShoppingRQ

  Scenario: Unsupported cabin is rejected before Entry-Gate is called
    Given the search request field "cabinClass" is "P"
    When PKFARE calls SEARCH
    Then PKFARE receives status 4 with message "cabinClass must be one of C, F, S, Y"
    And Entry-Gate receives no AirShoppingRQ
