package io.openems.common.jsonrpc.response;

import java.util.UUID;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Response for 'co2eScope2'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "factor": Double
 *   }
 * }
 * </pre>
 */
public class Co2eScope2Response extends JsonrpcResponseSuccess {

	private final Double factor;

	public Co2eScope2Response(UUID id, Double factor) {
		super(id);
		this.factor = factor;
	}

	public static Co2eScope2Response from(JsonrpcResponseSuccess response) throws OpenemsNamedException {
		var result = response.getResult();
		var factor = JsonUtils.getAsDouble(result, "factor");
		return new Co2eScope2Response(response.getId(), factor);
	}

	public Double getFactor() {
		return factor;
	}

	@Override
	public JsonObject getResult() {
		var result = new JsonObject();
		result.addProperty("factor", this.factor);
		return result;
	}

}
