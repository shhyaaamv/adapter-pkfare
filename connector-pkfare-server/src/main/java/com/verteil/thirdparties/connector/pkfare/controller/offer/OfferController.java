package com.verteil.thirdparties.connector.pkfare.controller.offer;

import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.ItinReshopRS;
import com.verteil.schemas.ndc16_1.SeatAvailabilityRS;
import com.verteil.schemas.ndc16_1.ServiceListRS;
import com.verteil.thirdparties.connector.pkfare.offer.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequiredArgsConstructor
public class OfferController {
    private final OfferService offerService;

    @GetMapping(path = "ping", produces = TEXT_PLAIN_VALUE)
    public String ping(){
        return "pong";
    }

    @PostMapping(path = "airshop", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public AirShoppingRS shop(@RequestBody RequestWrapperDto requestWrapper) {
        return (AirShoppingRS) offerService.process(requestWrapper);
    }

    @PostMapping(path = "flightPrice", produces = APPLICATION_JSON_VALUE)
    public FlightPriceRS flightPrice(@RequestBody RequestWrapperDto requestWrapper) {
        return (FlightPriceRS) offerService.process(requestWrapper);
    }

}
