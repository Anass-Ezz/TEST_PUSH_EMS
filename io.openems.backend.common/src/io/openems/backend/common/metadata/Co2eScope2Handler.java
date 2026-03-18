package io.openems.backend.common.metadata;

import java.util.concurrent.CompletableFuture;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.jsonrpc.request.Co2eScope2Request;

public final class Co2eScope2Handler {

	/**
	 * Handles a request from the edge to get the Co2e Scope 2 configuration.
	 * 
	 * @param metadata the {@link Co2eScope2Metadata}
	 * @param request  the {@link Co2eScope2Request}
	 * @param edgeId   the edge id
	 * @return the {@link CompletableFuture} with the Response
	 * @throws OpenemsNamedException on parse error
	 */
	public static CompletableFuture<? extends JsonrpcResponseSuccess> handleEdgeRequest(//
			final Co2eScope2Metadata metadata, //
			final Co2eScope2Request request, //
			final String edgeId //
	) throws OpenemsNamedException {
		return metadata.getCo2eScope2(edgeId, request);
	}

	private Co2eScope2Handler() {
	}

}
