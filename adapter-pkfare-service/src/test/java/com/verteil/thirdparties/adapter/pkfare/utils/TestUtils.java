package com.verteil.thirdparties.adapter.pkfare.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.commons.core.web.dto.UserDetailsDto;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestUtils {

    public static final String VENDOR_CODE = "SHA-TEST";
    public static final String CLIENT_IP = "203.0.113.10";

    public static final ObjectMapper NDC_MAPPER = JsonMappers.ndc();
    public static final ObjectMapper PKFARE_MAPPER = JsonMappers.pkfare();
    /** The seeded {@code CABIN_BRAND.MAPPED_CABIN} rows for carrier PKFARE. */
    public static final CabinMapping CABIN_MAPPING = CabinMapping.of(Map.of("F", "F", "C", "C", "S", "W", "Y", "Y"));

    /** Configuration as the configurator returns it: flat {@code "PKFARE_API.<NAME>" -> value}. */
    public static VdcConfiguration createVdcConfiguration(Map<String, String> pkfareApiValues) {
        Map<String, String> values = new HashMap<>();
        pkfareApiValues.forEach((name, value) -> values.put(PKFARE_API + "." + name, value));
        return new VdcConfiguration(values);
    }

    public static PkfareAdapterContext buildContext(SearchRequest request, Map<String, String> pkfareApiValues) {
        PkfareAdapterContext context = new PkfareAdapterContext();
        UserDetailsDto userDetails = new UserDetailsDto();
        userDetails.setOfficeCode(VENDOR_CODE);
        context.setService(SERVICE_SEARCH);
        context.setCarrier(PKFARE);
        context.setTxnId("TXN-1");
        context.setUserDetails(userDetails);
        context.setRequest(request);
        context.setVendorCode(request == null ? VENDOR_CODE : request.getVendorCode());
        context.setClientIp(CLIENT_IP);
        context.setCabinMapping(CABIN_MAPPING);
        VdcConfiguration configuration = createVdcConfiguration(pkfareApiValues);
        context.addConfiguration(SERVICE_SEARCH, configuration);
        context.addConfiguration(SERVICE_AIRSHOPPING, configuration);
        return context;
    }

    public static SearchRequest searchRequest() {
        return readPkfare("search/PKFARE_SearchRQ.json", SearchRequest.class);
    }

    public static AirShoppingRS airShoppingRS() {
        return readNdc("search/VDC_AirShoppingRS.json", AirShoppingRS.class);
    }

    public static <T> T readNdc(String resource, Class<T> type) {
        return read(NDC_MAPPER, resource, type);
    }

    public static <T> T readPkfare(String resource, Class<T> type) {
        return read(PKFARE_MAPPER, resource, type);
    }

    public static String readString(String resource) {
        try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes());
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
