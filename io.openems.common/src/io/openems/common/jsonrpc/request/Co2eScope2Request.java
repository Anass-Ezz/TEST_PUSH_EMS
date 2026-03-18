package io.openems.common.jsonrpc.request;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;

/**
 * Represents a JSON-RPC Request for 'co2eScope2'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "co2eScope2",
 *   "params": {}
 * }
 * </pre>
 */
public class Co2eScope2Request extends JsonrpcRequest {

	public static final String METHOD = "co2eScope2";

	/**
	 * Create {@link Co2eScope2Request} from a template {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link Co2eScope2Request}
	 * @throws OpenemsNamedException on parse error
	 */
	public static Co2eScope2Request from(JsonrpcRequest r) throws OpenemsException {
		return new Co2eScope2Request(r);
	}

	public Co2eScope2Request() {
		super(Co2eScope2Request.METHOD);
	}

	private Co2eScope2Request(JsonrpcRequest request) {
		super(request, Co2eScope2Request.METHOD);
	}

	@Override
	public JsonObject getParams() {
		return new JsonObject();
	}

}
