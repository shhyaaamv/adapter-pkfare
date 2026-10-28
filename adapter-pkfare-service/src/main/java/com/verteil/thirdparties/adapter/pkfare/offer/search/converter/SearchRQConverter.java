package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShopReqAttributeQueryType;
import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.schemas.ndc16_1.AnonymousTravelerType;
import com.verteil.schemas.ndc16_1.CabinPreferencesType;
import com.verteil.schemas.ndc16_1.CabinType;
import com.verteil.schemas.ndc16_1.Departure;
import com.verteil.schemas.ndc16_1.FarePreferencesType;
import com.verteil.schemas.ndc16_1.FlightArrivalType;
import com.verteil.schemas.ndc16_1.FlightDepartureType;
import com.verteil.schemas.ndc16_1.TravelerCoreType;
import com.verteil.schemas.ndc16_1.Travelers;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_FARE_PREFERENCES;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_SHOP_RESULT_PREFERENCE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_SHOP_RESULT_PREFERENCE_TAGGED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_SORT_ORDER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.FARE_PREFERENCES;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OD_KEY_PREFIX;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DATE_FORMAT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_ADULT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_CHILD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_INFANT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SHOP_RESULT_PREFERENCE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SHOP_RESULT_PREFERENCE_TAGGED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SORT_ORDER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TRIP_TYPE_ONE_WAY;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TRIP_TYPE_ROUND_TRIP;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.YES;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * PKFARE SEARCH request -> VDC AirShoppingRQ.
 * <pre>
 * adultNumber/childNumber/infantNumber  -> Travelers.Traveler[].AnonymousTraveler.PTC (one traveler per passenger)
 * depCity/arrCity/fromDate              -> CoreQuery.OriginDestinations.OriginDestination OD1
 * tripType=2 + retDate                  -> OD2 (arrCity -> depCity on retDate)
 * cabinClass (default Y)                -> Preference.CabinPreferences.CabinType.Code via CABIN_BRAND.MAPPED_CABIN (S -> W)
 * returnTagPrice                        -> ResponseParameters.ShopResultPreference (Y: tagged, N: plain)
 * (config)                              -> Preference.FarePreferences, ResponseParameters.SortOrder
 * </pre>
 */
@Slf4j
@Component
public class SearchRQConverter {

    private static final DateTimeFormatter PK_DATE = DateTimeFormatter.ofPattern(PK_DATE_FORMAT.replace("yyyy", "uuuu"))
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_PASSENGERS_PER_TYPE = 9;

    private final DatatypeFactory datatypeFactory;

    public SearchRQConverter() {
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), "No XML DatatypeFactory available", e, PROCESS_FAILURE_REASON_CODE);
        }
    }

    public AirShoppingRQ convert(PkfareAdapterContext context) {
        SearchRequest request = (SearchRequest) context.getRequest();
        CabinMapping cabinMapping = context.getCabinMapping();
        validate(request, cabinMapping);
        VdcConfiguration configuration = context.getConfigurations().get(SERVICE_SEARCH);
        AirShoppingRQ airShoppingRQ = new AirShoppingRQ();
        airShoppingRQ.setTravelers(mapTravelers(request));
        airShoppingRQ.setCoreQuery(mapCoreQuery(request));
        airShoppingRQ.setPreference(mapPreference(request, configuration, cabinMapping));
        airShoppingRQ.setResponseParameters(mapResponseParameters(request, configuration));
        return airShoppingRQ;
    }

    public static boolean isRoundTrip(SearchRequest request) {
        return TRIP_TYPE_ROUND_TRIP.equals(trimToEmpty(request.getTripType()));
    }

    private void validate(SearchRequest request, CabinMapping cabinMapping) {
        if (isNull(request)) {
            throw validationError("Empty search request");
        }
        int adults = requireNonNullElse(request.getAdultNumber(), 0);
        int children = requireNonNullElse(request.getChildNumber(), 0);
        int infants = requireNonNullElse(request.getInfantNumber(), 0);
        if (adults < 1 || adults > MAX_PASSENGERS_PER_TYPE) {
            throw validationError("adultNumber must be between 1 and 9");
        }
        if (children < 0 || children > MAX_PASSENGERS_PER_TYPE || infants < 0 || infants > MAX_PASSENGERS_PER_TYPE) {
            throw validationError("childNumber/infantNumber must be between 0 and 9");
        }
        if (infants > adults) {
            throw validationError("infantNumber cannot exceed adultNumber");
        }
        if (!isIataCode(request.getDepCity()) || !isIataCode(request.getArrCity())) {
            throw validationError("depCity/arrCity must be 3-letter IATA codes");
        }
        String tripType = trimToEmpty(request.getTripType());
        if (!TRIP_TYPE_ONE_WAY.equals(tripType) && !TRIP_TYPE_ROUND_TRIP.equals(tripType)) {
            throw validationError("tripType must be 1 (one-way) or 2 (round-trip)");
        }
        LocalDate fromDate = parseDate(request.getFromDate(), "fromDate");
        if (isRoundTrip(request)) {
            LocalDate retDate = parseDate(request.getRetDate(), "retDate");
            if (retDate.isBefore(fromDate)) {
                throw validationError("retDate cannot be before fromDate");
            }
        }
        if (!isBlank(request.getCabinClass()) && cabinMapping.toVdc(request.getCabinClass()).isEmpty()) {
            throw validationError("cabinClass must be one of " + String.join(", ", cabinMapping.pkToVdc().keySet()));
        }
    }

    private Travelers mapTravelers(SearchRequest request) {
        Travelers travelers = new Travelers();
        addTravelers(travelers, PTC_ADULT, requireNonNullElse(request.getAdultNumber(), 0));
        addTravelers(travelers, PTC_CHILD, requireNonNullElse(request.getChildNumber(), 0));
        addTravelers(travelers, PTC_INFANT, requireNonNullElse(request.getInfantNumber(), 0));
        return travelers;
    }

    private static void addTravelers(Travelers travelers, String ptc, int count) {
        IntStream.range(0, count).mapToObj(_ -> traveler(ptc)).forEach(travelers.getTraveler()::add);
    }

    private static Travelers.Traveler traveler(String ptc) {
        TravelerCoreType.PTC travelerPtc = new TravelerCoreType.PTC();
        travelerPtc.setValue(ptc);
        AnonymousTravelerType anonymousTraveler = new AnonymousTravelerType();
        anonymousTraveler.setPTC(travelerPtc);
        Travelers.Traveler traveler = new Travelers.Traveler();
        traveler.getAnonymousTraveler().add(anonymousTraveler);
        return traveler;
    }

    private AirShoppingRQ.CoreQuery mapCoreQuery(SearchRequest request) {
        AirShopReqAttributeQueryType originDestinations = new AirShopReqAttributeQueryType();
        String origin = request.getDepCity().trim().toUpperCase(Locale.ROOT);
        String destination = request.getArrCity().trim().toUpperCase(Locale.ROOT);
        originDestinations.getOriginDestination().add(mapOriginDestination(origin, destination, request.getFromDate(), 1));
        if (isRoundTrip(request)) {
            originDestinations.getOriginDestination().add(mapOriginDestination(destination, origin, request.getRetDate(), 2));
        }
        AirShoppingRQ.CoreQuery coreQuery = new AirShoppingRQ.CoreQuery();
        coreQuery.setOriginDestinations(originDestinations);
        return coreQuery;
    }

    private AirShopReqAttributeQueryType.OriginDestination mapOriginDestination(String origin, String destination, String pkDate, int index) {
        FlightDepartureType.AirportCode departureCode = new FlightDepartureType.AirportCode();
        departureCode.setValue(origin);
        Departure departure = new Departure();
        departure.setAirportCode(departureCode);
        departure.setDate(toXmlDate(parseDate(pkDate, "date")));

        FlightArrivalType.AirportCode arrivalCode = new FlightArrivalType.AirportCode();
        arrivalCode.setValue(destination);
        FlightArrivalType arrival = new FlightArrivalType();
        arrival.setAirportCode(arrivalCode);

        AirShopReqAttributeQueryType.OriginDestination originDestination = new AirShopReqAttributeQueryType.OriginDestination();
        originDestination.setDeparture(departure);
        originDestination.setArrival(arrival);
        originDestination.setOriginDestinationKey(OD_KEY_PREFIX + index);
        return originDestination;
    }

    private AirShoppingRQ.Preference mapPreference(SearchRequest request, VdcConfiguration configuration, CabinMapping cabinMapping) {
        FarePreferencesType.Types types = new FarePreferencesType.Types();
        fareTypes(configuration).forEach(code -> {
            FarePreferencesType.Types.Type type = new FarePreferencesType.Types.Type();
            type.setCode(code);
            types.getType().add(type);
        });
        FarePreferencesType farePreferences = new FarePreferencesType();
        farePreferences.setTypes(types);

        CabinType cabinType = new CabinType();
        String pkCabin = isBlank(request.getCabinClass()) ? DEFAULT_CABIN : request.getCabinClass();
        // An unmapped requested cabin is rejected in validate(), so only an unmapped default cabin ends up here.
        cabinType.setCode(cabinMapping.toVdc(pkCabin).orElseThrow(() -> new PkfareAdapterException(INTERNAL_ERROR.getCode(),
                "No VDC cabin mapped for PKFARE cabin " + pkCabin, PROCESS_FAILURE_REASON_CODE)));
        CabinPreferencesType cabinPreferences = new CabinPreferencesType();
        cabinPreferences.getCabinType().add(cabinType);

        AirShoppingRQ.Preference preference = new AirShoppingRQ.Preference();
        preference.setFarePreferences(farePreferences);
        preference.setCabinPreferences(cabinPreferences);
        return preference;
    }

    private AirShoppingRQ.ResponseParameters mapResponseParameters(SearchRequest request, VdcConfiguration configuration) {
        AirShoppingRQ.ResponseParameters responseParameters = new AirShoppingRQ.ResponseParameters();
        // returnTagPrice=Y: PKFARE wants every branded-fare (tag) solution per flight; N: only the lowest one.
        boolean tagged = YES.equalsIgnoreCase(trimToEmpty(request.getReturnTagPrice()));
        String preference = tagged
                ? configString(configuration, SHOP_RESULT_PREFERENCE_TAGGED, DEFAULT_SHOP_RESULT_PREFERENCE_TAGGED)
                : configString(configuration, SHOP_RESULT_PREFERENCE, DEFAULT_SHOP_RESULT_PREFERENCE);
        responseParameters.setShopResultPreference(AirShoppingRQ.ShopResultPreference.valueOf(preference.trim().toUpperCase(Locale.ROOT)));

        // getSortOrder() is a plain getter on this class (no lazy list), so the list is set explicitly
        responseParameters.setSortOrder(sortOrder(configuration).stream().map(parameter -> {
            AirShoppingRQ.Sort sort = new AirShoppingRQ.Sort();
            sort.setParameter(AirShoppingRQ.Sort.Parameter.valueOf(parameter.trim().toUpperCase(Locale.ROOT)));
            sort.setOrder(AirShoppingRQ.Sort.Order.ASCENDING);
            return sort;
        }).collect(Collectors.toCollection(ArrayList::new)));
        return responseParameters;
    }

    private static List<String> fareTypes(VdcConfiguration configuration) {
        return Optional.ofNullable(configuration)
                .map(vdcConfiguration -> vdcConfiguration.getListFromCsv(PKFARE_API, FARE_PREFERENCES, DEFAULT_FARE_PREFERENCES))
                .orElse(DEFAULT_FARE_PREFERENCES);
    }

    private static List<String> sortOrder(VdcConfiguration configuration) {
        return Optional.ofNullable(configuration)
                .map(vdcConfiguration -> vdcConfiguration.getListFromCsv(PKFARE_API, SORT_ORDER, DEFAULT_SORT_ORDER))
                .orElse(DEFAULT_SORT_ORDER);
    }

    private static String configString(VdcConfiguration configuration, String name, String defaultValue) {
        return Optional.ofNullable(configuration)
                .map(vdcConfiguration -> vdcConfiguration.getString(PKFARE_API, name, defaultValue))
                .orElse(defaultValue);
    }

    private XMLGregorianCalendar toXmlDate(LocalDate date) {
        return datatypeFactory.newXMLGregorianCalendarDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private static LocalDate parseDate(String value, String field) {
        if (isBlank(value)) {
            throw validationError(field + " is required (YYYYMMDD)");
        }
        try {
            return LocalDate.parse(value.trim(), PK_DATE);
        } catch (DateTimeParseException _) {
            throw validationError(field + " must be formatted as YYYYMMDD");
        }
    }

    private static boolean isIataCode(String code) {
        return !isBlank(code) && code.trim().matches("[A-Za-z]{3}");
    }

    private static PkfareAdapterException validationError(String message) {
        return new PkfareAdapterException(VALIDATION_FAILURE.getCode(), message, VALIDATION_FAILURE_REASON_CODE);
    }
}
