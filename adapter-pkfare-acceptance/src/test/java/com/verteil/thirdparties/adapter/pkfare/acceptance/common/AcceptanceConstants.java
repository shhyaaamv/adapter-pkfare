package com.verteil.thirdparties.adapter.pkfare.acceptance.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/** Endpoints and output location; {@code runTests} passes them as system properties, the defaults suit an IDE run. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptanceConstants {

    public static final String ADAPTER_URL = System.getProperty("acceptance.adapterUrl", "http://localhost:9122");
    public static final String WIREMOCK_URL = System.getProperty("acceptance.wiremockUrl", "http://localhost:8091");
    public static final Path OUTPUT_DIR = Path.of(System.getProperty("acceptance.outputDir", "build/acceptance/output"));

    public static final String SEARCH_PATH = "/search";
    public static final String ENTRY_GATE_REQUEST_PATH = "/entrygate/rest/request";
    public static final String ENTRY_GATE_AIRSHOPPING_RS = "wiremock/__files/entrygate/airshopping_rs.json";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";

    public static final String PKFARE_SEARCH_RQ_FILE = "1_PKFARE_SearchRQ.json";
    public static final String VDC_AIRSHOPPING_RQ_FILE = "2_VDC_AirShoppingRQ.json";
    public static final String VDC_AIRSHOPPING_RQ_HEADERS_FILE = "2_VDC_AirShoppingRQ_headers.json";
    public static final String PKFARE_SEARCH_RS_FILE = "3_PKFARE_SearchRS.json";
    public static final String SHAPE_REPORT_FILE = "3_PKFARE_SearchRS_vs_sample.txt";

    public static final String QTE_PATH = "/qte";
    public static final String SERVICE_HEADER = "Service";
    public static final String FLIGHT_PRICE_SERVICE = "FlightPrice";
    /** Classpath copy of the default FlightPrice stub body (the full 7.7 MB VDC sample). */
    public static final String ENTRY_GATE_FLIGHTPRICE_RS = "wiremock/__files/entrygate/flightprice_rs.json";
    /** The same file as WireMock's {@code bodyFileName}, relative to {@code __files}. */
    public static final String ENTRY_GATE_FLIGHTPRICE_BODY_FILE = "entrygate/flightprice_rs.json";

    public static final String PKFARE_QTE_RQ_FILE = "1_PKFARE_QteRQ.json";
    public static final String PKFARE_QTE_RQ_DATA_FILE = "1_PKFARE_QteRQ_routing_data.json";
    public static final String VDC_FLIGHTPRICE_RQ_FILE = "2_VDC_FlightPriceRQ.json";
    public static final String VDC_FLIGHTPRICE_RQ_HEADERS_FILE = "2_VDC_FlightPriceRQ_headers.json";
    public static final String PKFARE_QTE_RS_FILE = "3_PKFARE_QteRS.json";
    public static final String PKFARE_QTE_RS_DATA_FILE = "3_PKFARE_QteRS_routing_data.json";
    public static final String QTE_SHAPE_REPORT_FILE = "3_PKFARE_QteRS_vs_sample.txt";
}
