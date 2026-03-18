package io.openems.common.jsonrpc.response;

import java.util.UUID;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Response for 'timeOfUseTariffMorocco'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "pricePeak": Double,
 *     "priceStandard": Double,
 *     "priceOffPeak": Double
 *   }
 * }
 * </pre>
 */
public class TimeOfUseTariffMoroccoResponse extends JsonrpcResponseSuccess {

	private final Double pricePeak;
	private final Double priceStandard;
	private final Double priceOffPeak;

	public TimeOfUseTariffMoroccoResponse(UUID id, Double pricePeak, Double priceStandard, Double priceOffPeak) {
		super(id);
		this.pricePeak = pricePeak;
		this.priceStandard = priceStandard;
		this.priceOffPeak = priceOffPeak;
	}

	public static TimeOfUseTariffMoroccoResponse from(JsonrpcResponseSuccess response) throws OpenemsNamedException {
		var result = response.getResult();
		var pricePeak = JsonUtils.getAsDouble(result, "pricePeak");
		var priceStandard = JsonUtils.getAsDouble(result, "priceStandard");
		var priceOffPeak = JsonUtils.getAsDouble(result, "priceOffPeak");
		return new TimeOfUseTariffMoroccoResponse(response.getId(), pricePeak, priceStandard, priceOffPeak);
	}

	public Double getPricePeak() {
		return pricePeak;
	}

	public Double getPriceStandard() {
		return priceStandard;
	}

	public Double getPriceOffPeak() {
		return priceOffPeak;
	}

	@Override
	public JsonObject getResult() {
		var result = new JsonObject();
		result.addProperty("pricePeak", this.pricePeak);
		result.addProperty("priceStandard", this.priceStandard);
		result.addProperty("priceOffPeak", this.priceOffPeak);
		return result;
	}

}
