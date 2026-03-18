package io.openems.backend.common.jsonrpc.response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;

/**
 * Represents a JSON-RPC Response for 'aggregateAndGetEdgesChannelsValues'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "globalConsumptionEnergy": 9674000,
 *     "globalMeanActivePower": 878,
 *     "globalEnergyCost": 142.5
 *   }
 * }
 * </pre>
 */
public class AggregateAndGetEdgesChannelsValuesResponse extends JsonrpcResponseSuccess {

	private final Map<String, JsonElement> results = new LinkedHashMap<>();

	public AggregateAndGetEdgesChannelsValuesResponse(UUID id) {
		super(id);
	}

	/**
	 * Adds a result for the given aggregation key with optional movement percentage.
	 *
	 * @param key                the aggregation key
	 * @param value              the aggregated value (number or null)
	 * @param movementPercentage the calculated movement percentage (number or null)
	 */
	public void addResult(String key, Number value, Double movementPercentage) {
		var obj = new JsonObject();
		obj.add("value", value == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value));
		obj.add("movementPercentage", movementPercentage == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(movementPercentage));
		
		this.results.put(key, obj);
	}

	@Override
	public JsonObject getResult() {
		var result = new JsonObject();
		for (var entry : this.results.entrySet()) {
			result.add(entry.getKey(), entry.getValue());
		}
		return result;
	}
}
