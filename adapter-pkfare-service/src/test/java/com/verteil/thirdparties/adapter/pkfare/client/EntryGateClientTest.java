package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.schemas.ndc16_1.FlightPriceRQ;
import com.verteil.thirdparties.adapter.pkfare.client.cache.LocalTokenCache;
import com.verteil.thirdparties.adapter.pkfare.client.interceptor.LoggerHelper;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.NDC_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildQteContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.readNdc;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.verteilProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryGateClientTest {

    private static final String URL = "https://api.example.com/entrygate/rest/request";

    private final ThirdPartyConnector connector = mock(ThirdPartyConnector.class);
    private final LocalTokenCache tokenCache = mock(LocalTokenCache.class);
    private final EntryGateClient client = new EntryGateClient(mock(LoggerHelper.class), connector, tokenCache,
            verteilProperties(), NDC_MAPPER);

    @Test
    void flightPriceSendsOfferOwnerAsThirdPartyId() {
        FlightPriceRQ flightPriceRQ = readNdc("qte/VDC_FlightPriceRQ_expected.json", FlightPriceRQ.class);
        PkfareAdapterContext context = buildQteContext(null);
        context.setProviderApiRequest(SERVICE_FLIGHTPRICE, flightPriceRQ);
        context.setProviderApiService(SERVICE_FLIGHTPRICE);

        HttpPost post = call(context);

        String owner = flightPriceRQ.getQuery().getOffers().getOffer().getFirst().getOfferID().getOwner();
        assertThat(owner).isNotBlank();
        assertThat(post.getFirstHeader("thirdpartyid").getValue()).isEqualTo(owner);
        assertThat(post.getFirstHeader("Service").getValue()).isEqualTo(SERVICE_FLIGHTPRICE);
    }

    @Test
    void airShoppingSendsNoThirdPartyId() {
        PkfareAdapterContext context = buildContext(null);
        context.setProviderApiRequest(SERVICE_AIRSHOPPING, new AirShoppingRQ());
        context.setProviderApiService(SERVICE_AIRSHOPPING);

        HttpPost post = call(context);

        assertThat(post.getFirstHeader("thirdpartyid")).isNull();
    }

    private HttpPost call(PkfareAdapterContext context) {
        when(tokenCache.getAccessToken()).thenReturn("token");
        when(connector.callSupplier(any(), any(), any())).thenReturn(new HttpCallResult(200, "{}", 10));
        client.callWebService(URL, context);
        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(connector).callSupplier(captor.capture(), any(), any());
        return captor.getValue();
    }
}
