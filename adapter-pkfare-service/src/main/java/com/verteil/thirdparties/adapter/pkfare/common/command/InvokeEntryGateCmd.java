package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.adapter.pkfare.client.EntryGateClient;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.config.VerteilProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Calls Entry-Gate for the current provider service: URL = {@code verteil.host} + {@code verteil.path}
 * (e.g. {@code https://qaapi.sandbox.verteil.com} + {@code /entrygate/rest/request}). Every Entry-Gate service shares
 * that endpoint; {@link EntryGateClient} selects the service with the {@code Service} header.
 */
@Component
@RequiredArgsConstructor
public class InvokeEntryGateCmd implements Command<PkfareAdapterContext> {

    private final EntryGateClient entryGateClient;
    private final VerteilProperties verteilProperties;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        final String providerApiService = context.getProviderApiService();
        String url = verteilProperties.getHost() + verteilProperties.getPath();
        Object providerApiResponse = entryGateClient.callWebService(url, context);
        context.setProviderApiResponse(providerApiService, providerApiResponse);
        return CONTINUE_PROCESSING;
    }
}
