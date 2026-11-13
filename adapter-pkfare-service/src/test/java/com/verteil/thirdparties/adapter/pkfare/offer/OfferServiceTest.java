package com.verteil.thirdparties.adapter.pkfare.offer;

import com.verteil.chain.Catalog;
import com.verteil.chain.Command;
import com.verteil.schema.pkfare.QteResponse;
import com.verteil.thirdparties.adapter.pkfare.client.interceptor.LoggerHelper;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.config.PkfareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.CLIENT_IP;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.pkfareProperties;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.qteRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code OfferService.qte} runs the {@code Qte} chain and always answers with a {@code QteResponse}. */
class OfferServiceTest {

    private final PkfareProperties properties = pkfareProperties();
    @SuppressWarnings("unchecked")
    private final Catalog<PkfareAdapterContext> catalog = mock(Catalog.class);
    @SuppressWarnings("unchecked")
    private final Command<PkfareAdapterContext> chain = mock(Command.class);
    private OfferService offerService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService(properties, catalog, new ErrorResponseConverter(), mock(LoggerHelper.class));
        when(catalog.getCommand("Qte")).thenReturn(chain);
    }

    @Test
    void runsTheQteChainWithTheVendorAndReturnsItsResponse() throws Exception {
        QteResponse chainResponse = new QteResponse();
        chainResponse.setStatus(0);
        doAnswer(invocation -> {
            invocation.<PkfareAdapterContext>getArgument(0).setResponse(chainResponse);
            return false;
        }).when(chain).execute(any());

        assertThat(offerService.qte(qteRequest(), CLIENT_IP)).isSameAs(chainResponse);

        ArgumentCaptor<PkfareAdapterContext> context = ArgumentCaptor.forClass(PkfareAdapterContext.class);
        verify(chain).execute(context.capture());
        assertThat(context.getValue().getService()).isEqualTo("Qte");
        assertThat(context.getValue().getVendorCode()).isEqualTo("SHA-TEST");
    }

    @Test
    void missingVendorIsStatus3WithoutRunningTheChain() throws Exception {
        var request = qteRequest();
        request.setVendorCode(" ");

        QteResponse response = offerService.qte(request, CLIENT_IP);

        assertThat(response.getStatus()).isEqualTo(3);
        verify(chain, never()).execute(any());
    }

    @Test
    void nullRequestStillGetsAQteResponse() {
        assertThat(offerService.qte(null, CLIENT_IP).getStatus()).isEqualTo(3);
    }

    @Test
    void disabledServiceIsStatus3() throws Exception {
        properties.getEnabledServices().put("qte", false);

        QteResponse response = offerService.qte(qteRequest(), CLIENT_IP);

        assertThat(response.getStatus()).isEqualTo(3);
        assertThat(response.getMsg()).isEqualTo("Qte service is not enabled");
        verify(chain, never()).execute(any());
    }

    @Test
    void chainReasonsBecomeQteStatuses() throws Exception {
        doThrow(new PkfareAdapterException("VALIDATION_FAILURE", "Invalid date range", "INVALID_DATE_RANGE")).when(chain).execute(any());

        assertThat(offerService.qte(qteRequest(), CLIENT_IP).getStatus()).isEqualTo(111);
    }

    @Test
    void unexpectedFailureIsStatus5WithTheTransaction() throws Exception {
        doThrow(new IllegalStateException("boom")).when(chain).execute(any());

        QteResponse response = offerService.qte(qteRequest(), CLIENT_IP);

        assertThat(response.getStatus()).isEqualTo(5);
        assertThat(response.getMsg()).startsWith("Internal error, txn: ").hasSizeLessThan(64);
        assertThat(response.getErrorCode()).isNull();
    }
}
