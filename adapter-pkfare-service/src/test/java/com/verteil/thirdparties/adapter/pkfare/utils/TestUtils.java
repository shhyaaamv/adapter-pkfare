package com.verteil.thirdparties.adapter.pkfare.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.config.PkfareProperties;
import com.verteil.thirdparties.adapter.pkfare.config.VerteilProperties;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestUtils {

    public static final String VENDOR_CODE = "SHA-TEST";
    public static final String CLIENT_IP = "203.0.113.10";

    public static final ObjectMapper NDC_MAPPER = JsonMappers.ndc();
    public static final ObjectMapper PKFARE_MAPPER = JsonMappers.pkfare();
    /** Code defaults plus the test vendor with SEARCH and QTE enabled. */
    public static PkfareProperties pkfareProperties() {
        PkfareProperties properties = new PkfareProperties();
        properties.setEnabledServices(new LinkedHashMap<>(Map.of("search", true, "qte", true)));
        properties.setVendorCodes(new ArrayList<>(List.of(VENDOR_CODE)));
        return properties;
    }

    /** Code defaults plus an Entry-Gate host. */
    public static VerteilProperties verteilProperties() {
        VerteilProperties properties = new VerteilProperties();
        properties.setHost("https://api.example.com");
        return properties;
    }

    public static PkfareAdapterContext buildContext(SearchRequest request) {
        return buildContext(request, request == null ? VENDOR_CODE : request.getVendorCode(), SERVICE_SEARCH);
    }

    public static PkfareAdapterContext buildQteContext(QteRequest request) {
        return buildContext(request, request == null ? VENDOR_CODE : request.getVendorCode(), SERVICE_QTE);
    }

    private static PkfareAdapterContext buildContext(Object request, String vendorCode, String service) {
        PkfareAdapterContext context = new PkfareAdapterContext();
        context.setService(service);
        context.setTxnId("TXN-1");
        context.setRequest(request);
        context.setVendorCode(vendorCode);
        context.setClientIp(CLIENT_IP);
        return context;
    }

    public static SearchRequest searchRequest() {
        return readPkfare("search/PKFARE_SearchRQ.json", SearchRequest.class);
    }

    public static AirShoppingRS airShoppingRS() {
        return readNdc("search/VDC_AirShoppingRS.json", AirShoppingRS.class);
    }

    /** {@code qte/PKFARE_QteRQ.json} with {@code routing.data} = standard Base64 of the UTF-8 {@code qte/offer_data.json}. */
    public static QteRequest qteRequest() {
        QteRequest request = readPkfare("qte/PKFARE_QteRQ.json", QteRequest.class);
        request.getRouting().setData(encodedOfferData());
        return request;
    }

    /** Standard Base64 of the UTF-8 bytes of {@code qte/offer_data.json}. */
    public static String encodedOfferData() {
        return Base64.getEncoder().encodeToString(readString("qte/offer_data.json").getBytes(StandardCharsets.UTF_8));
    }

    public static FlightPriceRS flightPriceRS() {
        return readNdc("qte/VDC_FlightPriceRS.json", FlightPriceRS.class);
    }

    public static <T> T readNdc(String resource, Class<T> type) {
        return read(NDC_MAPPER, resource, type);
    }

    public static <T> T readPkfare(String resource, Class<T> type) {
        return read(PKFARE_MAPPER, resource, type);
    }

    public static String readString(String resource) {
        try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T read(ObjectMapper mapper, String resource, Class<T> type) {
        try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resource)) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
