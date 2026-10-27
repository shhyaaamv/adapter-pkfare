package com.verteil.thirdparties.connector.pkfare.controller.order;

import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.schemas.ndc15_2.OrderViewRS;
import com.verteil.schemas.ndc16_1.OrderCancelRS;
import com.verteil.thirdparties.connector.pkfare.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping(path = "order", produces = APPLICATION_JSON_VALUE)
    public OrderViewRS orderCreate(@RequestBody RequestWrapperDto requestWrapper) {
        return (OrderViewRS) orderService.process(requestWrapper);
    }

    @PostMapping(path = "orderRetrieve", produces = APPLICATION_JSON_VALUE)
    public OrderViewRS orderRetrieve(@RequestBody RequestWrapperDto requestWrapper) {
        return (OrderViewRS) orderService.process(requestWrapper);
    }

    @PostMapping(path = "orderCancel", produces = APPLICATION_JSON_VALUE)
    public OrderCancelRS orderCancel(@RequestBody RequestWrapperDto requestWrapper) {
        return (OrderCancelRS) orderService.process(requestWrapper);
    }

    @PostMapping(path = "orderChange", produces = APPLICATION_JSON_VALUE)
    public OrderViewRS orderChange(@RequestBody RequestWrapperDto requestWrapper) {
        return (OrderViewRS) orderService.process(requestWrapper);
    }
}
