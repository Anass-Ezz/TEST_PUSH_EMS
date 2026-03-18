package io.openems.backend.common.metadata;

import java.util.concurrent.CompletableFuture;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.jsonrpc.request.TimeOfUseTariffMoroccoRequest;
import io.openems.common.jsonrpc.response.TimeOfUseTariffMoroccoResponse;

@ProviderType
public interface TimeOfUseTariffMoroccoMetadata {

	/**
	 * Retrieves the Morocco Time-Of-Use Tariff prices.
	 *
	 * @param edgeId  the Edge-ID
	 * @param request the {@link TimeOfUseTariffMoroccoRequest}
	 * @return a future with the {@link TimeOfUseTariffMoroccoResponse}
	 */
	public CompletableFuture<TimeOfUseTariffMoroccoResponse> getMoroccoTariff(String edgeId,
			TimeOfUseTariffMoroccoRequest request);

}
