package io.openems.backend.common.jsonrpc.response;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;

/**
 * Represents a JSON-RPC Response for 'aggregateAndQueryEdgesChannelsHistory'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "timestamps": [
 *       '2011-12-03T10:15:30Z',...
 *     ],
 *     "data": {
 *       "globalConsumptionEnergy": [
 *         value1, value2,...
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
public class AggregateAndQueryEdgesChannelsHistoryResponse extends JsonrpcResponseSuccess {

	private final SortedMap<ZonedDateTime, SortedMap<String, JsonElement>> table;

	public AggregateAndQueryEdgesChannelsHistoryResponse(UUID id,
			SortedMap<ZonedDateTime, SortedMap<String, JsonElement>> table) {
		super(id);
		this.table = table;
	}

	@Override
	public JsonObject getResult() {
		var result = new JsonObject();

		var timestamps = new JsonArray();
		for (ZonedDateTime timestamp : this.table.keySet()) {
			timestamps.add(timestamp.format(DateTimeFormatter.ISO_INSTANT));
		}
		result.add("timestamps", timestamps);

		var data = new JsonObject();
		for (Entry<ZonedDateTime, SortedMap<String, JsonElement>> rowEntry : this.table.entrySet()) {
			for (Entry<String, JsonElement> colEntry : rowEntry.getValue().entrySet()) {
				var key = colEntry.getKey();
				var value = colEntry.getValue();
				
				var keyValuesElement = data.get(key);
				JsonArray keyValues;
				if (keyValuesElement != null) {
					keyValues = keyValuesElement.getAsJsonArray();
				} else {
					keyValues = new JsonArray();
				}
				keyValues.add(value);
				data.add(key, keyValues);
			}
		}
		result.add("data", data);

		return result;
	}

}
