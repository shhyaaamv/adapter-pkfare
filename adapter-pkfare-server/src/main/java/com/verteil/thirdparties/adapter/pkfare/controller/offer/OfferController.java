package com.verteil.thirdparties.adapter.pkfare.controller.offer;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.offer.OfferService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * PKFARE "Supplier Content Aggregation Direct Connection API v2.1" endpoints hosted by Verteil.
 * The caller IP is {@code request.getRemoteAddr()}: Tomcat's RemoteIpValve (server.forward-headers-strategy=native)
 * resolves it from X-Forwarded-For only when the hop is a trusted internal proxy, so it cannot be spoofed.
 */
@RestController
@RequiredArgsConstructor
public class OfferController {
    private final OfferService offerService;

    @GetMapping(path = "ping", produces = TEXT_PLAIN_VALUE)
    public String ping() {
        return "pong";
    }

    @PostMapping(path = "search", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public SearchResponse search(@RequestBody SearchRequest request, HttpServletRequest httpRequest) {
        return offerService.search(request, httpRequest.getRemoteAddr());
    }
}
