package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.chain.Command;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.thirdparties.adapter.pkfare.client.EntryGateClient;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.configuration2.Configuration;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.HOST;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PATH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;

/**
 * Calls Entry-Gate for the current provider service: URL = {@code PKFARE_API.HOST} + {@code PKFARE_API.PATH}
 * (e.g. {@code https://qaapi.sandbox.verteil.com} + {@code /entrygate/rest/request}). Every Entry-Gate service shares
 * that endpoint; {@link EntryGateClient} selects the service with the {@code Service} header.
 */
@Component
@RequiredArgsConstructor
public class InvokeEntryGateCmd implements Command<PkfareAdapterContext> {

    private final EntryGateClient entryGateClient;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        final String providerApiService = context.getProviderApiService();
        VdcConfiguration vdcConfiguration = context.getConfigurations().get(providerApiService);
        Configuration providerConfiguration = vdcConfiguration.subset(PKFARE_API);
        String url = providerConfiguration.getString(HOST) + providerConfiguration.getString(PATH);
        Object providerApiResponse = entryGateClient.callWebService(url, context);
        context.setProviderApiResponse(providerApiService, providerApiResponse);
        return CONTINUE_PROCESSING;
    }
}
