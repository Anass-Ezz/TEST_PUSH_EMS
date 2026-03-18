package io.openems.backend.common.metadata;

import java.util.concurrent.CompletableFuture;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.jsonrpc.request.TimeOfUseTariffMoroccoRequest;

public final class TimeOfUseTariffMoroccoHandler {

	/**
	 * Handles a request from the edge to get the Morocco tariff prices.
	 * 
	 * @param metadata the {@link TimeOfUseTariffMoroccoMetadata}
	 * @param request  the {@link TimeOfUseTariffMoroccoRequest}
	 * @param edgeId   the edge id
	 * @return the {@link CompletableFuture} with the Response
	 * @throws OpenemsNamedException on parse error
	 */
	public static CompletableFuture<? extends JsonrpcResponseSuccess> handleEdgeRequest(//
			final TimeOfUseTariffMoroccoMetadata metadata, //
			final TimeOfUseTariffMoroccoRequest request, //
			final String edgeId //
	) throws OpenemsNamedException {
		return metadata.getMoroccoTariff(edgeId, request);
	}

	private TimeOfUseTariffMoroccoHandler() {
	}

}
