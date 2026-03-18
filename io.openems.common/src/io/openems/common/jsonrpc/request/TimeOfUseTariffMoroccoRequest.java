package io.openems.common.jsonrpc.request;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;

/**
 * Represents a JSON-RPC Request for 'timeOfUseTariffMorocco'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "timeOfUseTariffMorocco",
 *   "params": {}
 * }
 * </pre>
 */
public class TimeOfUseTariffMoroccoRequest extends JsonrpcRequest {

	public static final String METHOD = "timeOfUseTariffMorocco";

	/**
	 * Create {@link TimeOfUseTariffMoroccoRequest} from a template {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link TimeOfUseTariffMoroccoRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static TimeOfUseTariffMoroccoRequest from(JsonrpcRequest r) throws OpenemsException {
		return new TimeOfUseTariffMoroccoRequest(r);
	}

	public TimeOfUseTariffMoroccoRequest() {
		super(TimeOfUseTariffMoroccoRequest.METHOD);
	}

	private TimeOfUseTariffMoroccoRequest(JsonrpcRequest request) {
		super(request, TimeOfUseTariffMoroccoRequest.METHOD);
	}

	@Override
	public JsonObject getParams() {
		return new JsonObject();
	}

}
