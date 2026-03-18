package io.openems.backend.common.metadata;

import java.util.concurrent.CompletableFuture;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.jsonrpc.request.Co2eScope2Request;
import io.openems.common.jsonrpc.response.Co2eScope2Response;

@ProviderType
public interface Co2eScope2Metadata {

	/**
	 * Retrieves the Co2e Scope 2 configuration.
	 *
	 * @param edgeId  the Edge-ID
	 * @param request the {@link Co2eScope2Request}
	 * @return a future with the {@link Co2eScope2Response}
	 */
	public CompletableFuture<Co2eScope2Response> getCo2eScope2(String edgeId,
			Co2eScope2Request request);

}
