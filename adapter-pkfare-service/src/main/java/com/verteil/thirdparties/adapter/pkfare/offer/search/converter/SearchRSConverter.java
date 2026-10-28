package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.schema.pkfare.SearchFlight;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_PRODUCT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_SEAT_COUNT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_TICKET_ISSUE_TIME;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PUBLIC_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SUCCESS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_SUCCESS_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TICKET_ISSUE_TIME;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.YES;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * VDC AirShoppingRS -> PKFARE SEARCH response. Each AirlineOffer becomes one shopping result; offers PKFARE cannot
 * represent are skipped (logged), never failing the whole search. {@code flightList} holds exactly the segments
 * referenced by the returned results, numbered 1...n in first-use order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRSConverter {

    private final ShoppingResultConverter shoppingResultConverter;
    private final FlightConverter flightConverter;

    public SearchResponse convert(PkfareAdapterContext context) {
        AirShoppingRS airShoppingRS = (AirShoppingRS) context.getProviderApiResponse(SERVICE_AIRSHOPPING);
        SearchMappingContext mappingContext = mappingContext(context, airShoppingRS);
        List<AirlineOffers> airlineOffers = airlineOffers(airShoppingRS);

        SearchResponse response = new SearchResponse();
        response.setStatus(PK_STATUS_SUCCESS);
        response.setMsg(PK_SUCCESS_MSG);
        // Results before flights: converting a result registers its flights' flightRefNums in first-use order.
        response.getShoppingResultList().addAll(shoppingResults(airlineOffers, mappingContext));
        response.getFlightList().addAll(flights(mappingContext));
        log.info("Search mapped {} shopping results and {} flights, {} offers skipped", response.getShoppingResultList().size(),
                response.getFlightList().size(), offerCount(airlineOffers) - response.getShoppingResultList().size());
        return response;
    }

    private static SearchMappingContext mappingContext(PkfareAdapterContext context, AirShoppingRS airShoppingRS) {
        SearchRequest request = (SearchRequest) context.getRequest();
        Optional<VdcConfiguration> configuration = Optional.ofNullable(context.getConfigurations().get(SERVICE_SEARCH));
        return new SearchMappingContext(
                request,
                airShoppingRS,
                YES.equalsIgnoreCase(trimToEmpty(request.getReturnTagPrice())),
                trimToEmpty(request.getTag()),
                configuration.map(vdcConfiguration -> vdcConfiguration.getInteger(PKFARE_API, DEFAULT_SEAT_COUNT, null)).orElse(null),
                configuration.map(vdcConfiguration -> vdcConfiguration.getInt(PKFARE_API, TICKET_ISSUE_TIME, DEFAULT_TICKET_ISSUE_TIME))
                        .orElse(DEFAULT_TICKET_ISSUE_TIME),
                configuration.map(vdcConfiguration -> vdcConfiguration.getString(PKFARE_API, DEFAULT_PRODUCT_TYPE, PK_PUBLIC_FARE))
                        .orElse(PK_PUBLIC_FARE),
                context.getCabinMapping());
    }

    /** Sequential on purpose, see {@link #convert}. */
    private List<SearchShoppingResult> shoppingResults(List<AirlineOffers> airlineOffers, SearchMappingContext mappingContext) {
        return airlineOffers.stream()
                .flatMap(offers -> offers.getAirlineOffer().stream()
                        .flatMap(offer -> shoppingResult(offers, offer, mappingContext).stream()))
                .toList();
    }

    /** An offer PKFARE cannot represent, or that fails to map, is skipped rather than failing the search. */
    private Optional<SearchShoppingResult> shoppingResult(AirlineOffers airlineOffers, AirlineOffer offer, SearchMappingContext mappingContext) {
        try {
            return shoppingResultConverter.convert(airlineOffers, offer, mappingContext);
        } catch (RuntimeException e) {
            log.warn("Unable to map offer {}, offer skipped", ItineraryConverter.offerId(offer), e);
            return Optional.empty();
        }
    }

    /** Exactly the segments the shopping results registered, in flightRefNum order. */
    private List<SearchFlight> flights(SearchMappingContext mappingContext) {
        return mappingContext.flightRefNums().entrySet().stream()
                .flatMap(flightRef -> mappingContext.index().segment(flightRef.getKey())
                        .map(segment -> flightConverter.convert(segment, flightRef.getValue()))
                        .stream())
                .toList();
    }

    private static int offerCount(List<AirlineOffers> airlineOffers) {
        return airlineOffers.stream().mapToInt(offers -> offers.getAirlineOffer().size()).sum();
    }

    private static List<AirlineOffers> airlineOffers(AirShoppingRS airShoppingRS) {
        return Optional.ofNullable(airShoppingRS)
                .map(AirShoppingRS::getOffersGroup)
                .map(AirShoppingRS.OffersGroup::getAirlineOffers)
                .orElse(List.of());
    }
}
