package io.openems.backend.edgewebsocket;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.backend.common.metadata.AppCenterHandler;
import io.openems.backend.common.metadata.AppCenterMetadata;
import io.openems.backend.oauthregistry.OAuthRegistry;
import io.openems.backend.oauthregistry.OAuthRegistryRequestHandler;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.jsonrpc.request.AppCenterRequest;
import io.openems.common.jsonrpc.request.OAuthRegistryRequest;
import io.openems.common.jsonrpc.request.TimeOfUseTariffMoroccoRequest;
import io.openems.common.jsonrpc.request.Co2eScope2Request;
import io.openems.backend.common.metadata.TimeOfUseTariffMoroccoHandler;
import io.openems.backend.common.metadata.TimeOfUseTariffMoroccoMetadata;
import io.openems.backend.common.metadata.Co2eScope2Handler;
import io.openems.backend.common.metadata.Co2eScope2Metadata;

public class OnRequest implements io.openems.common.websocket.OnRequest {

	private final Logger log = LoggerFactory.getLogger(OnRequest.class);
	private final Supplier<AppCenterMetadata.EdgeData> appCenterMetadata;
	private final Supplier<OAuthRegistry> oAuthRegistry;
	private final Supplier<TimeOfUseTariffMoroccoMetadata> timeOfUseTariffMoroccoMetadata;
	private final Supplier<Co2eScope2Metadata> co2eScope2Metadata;
	private final BiConsumer<Logger, String> logWarn;

	public OnRequest(//
			Supplier<AppCenterMetadata.EdgeData> appCenterMetadata, //
			Supplier<OAuthRegistry> oAuthBackend, //
			Supplier<TimeOfUseTariffMoroccoMetadata> timeOfUseTariffMoroccoMetadata, //
			Supplier<Co2eScope2Metadata> co2eScope2Metadata, //
			BiConsumer<Logger, String> logWarn) {
		this.appCenterMetadata = appCenterMetadata;
		this.oAuthRegistry = oAuthBackend;
		this.timeOfUseTariffMoroccoMetadata = timeOfUseTariffMoroccoMetadata;
		this.co2eScope2Metadata = co2eScope2Metadata;
		this.logWarn = logWarn;
	}

	@Override
	public CompletableFuture<? extends JsonrpcResponseSuccess> apply(WebSocket ws, JsonrpcRequest request)
			throws OpenemsException, OpenemsNamedException {

		final WsData wsData = ws.getAttachment();
		final var edgeId = wsData.getEdgeId().get();

		var resultFuture = switch (request.getMethod()) {
		case AppCenterRequest.METHOD -> AppCenterHandler.handleEdgeRequest(this.appCenterMetadata.get(), //
				AppCenterRequest.from(request), edgeId);
		case TimeOfUseTariffMoroccoRequest.METHOD -> TimeOfUseTariffMoroccoHandler.handleEdgeRequest( //
				this.timeOfUseTariffMoroccoMetadata.get(), TimeOfUseTariffMoroccoRequest.from(request), edgeId);
		case Co2eScope2Request.METHOD -> Co2eScope2Handler.handleEdgeRequest( //
				this.co2eScope2Metadata.get(), Co2eScope2Request.from(request), edgeId);
		case OAuthRegistryRequest.METHOD ->
			OAuthRegistryRequestHandler.handleRequest(this.oAuthRegistry.get(), OAuthRegistryRequest.from(request));
		default -> null;
		};

		if (resultFuture != null) {
			return resultFuture;
		}
		this.logWarn.accept(this.log, "Unhandled Request: " + request);
		throw OpenemsError.JSONRPC_UNHANDLED_METHOD.exception(request.getMethod());
	}

}
