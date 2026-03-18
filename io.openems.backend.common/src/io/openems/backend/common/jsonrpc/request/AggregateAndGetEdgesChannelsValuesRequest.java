package io.openems.backend.common.jsonrpc.request;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.types.AggregationType;
import io.openems.common.types.ChannelType;
import io.openems.common.types.EdgeChannelAddress;
import io.openems.common.types.Period;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Request for 'aggregateAndGetEdgesChannelsValues'.
 *
 * <p>
 * Supports two levels of aggregation: period aggregation (reducing all values
 * of a channel within a period into one value) and cross-channel aggregation
 * (combining the period-aggregated values of multiple channels into a single
 * result).
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "aggregateAndGetEdgesChannelsValues",
 *   "params": {
 *     "timezone": "Europe/Paris",
 *     "aggregations": [
 *       {
 *         "key": "globalConsumptionEnergy",
 *         "channels": ["edge0/meter0/ActiveConsumptionEnergy", ...],
 *         "channelType": "energy", // one of: normal, energy, cost, emissions
 *         "channelsAggregationType": "sum",
 *         "periodAggregation": {
 *           "period": "Last 24 Hours",
 *           "periodAggregationType": "mean"
 *         }
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
public class AggregateAndGetEdgesChannelsValuesRequest extends JsonrpcRequest {

	public static final String METHOD = "aggregateAndGetEdgesChannelsValues";

	/**
	 * Defines a single aggregation within the request.
	 *
	 * @param key                      unique identifier for the result
	 * @param channels                 channel addresses in edgeId/componentId/channelId format
	 * @param channelType              one of: normal, energy, cost, emissions
	 * @param channelsAggregationType  how to combine channels (nullable if single channel)
	 * @param period                   the time period (Day, Week, Month); defaults to DAY
	 * @param periodAggregationType    aggregation over time (nullable; only for normal)
	 */
	public record AggregationDefinition(//
			String key, //
			List<EdgeChannelAddress> channels, //
			ChannelType channelType, //
			AggregationType channelsAggregationType, //
			Period period, //
			AggregationType periodAggregationType, //
			boolean hasPeriodAggregation //
	) {
	}

	/**
	 * Create {@link AggregateAndGetEdgesChannelsValuesRequest} from a template
	 * {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link AggregateAndGetEdgesChannelsValuesRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static AggregateAndGetEdgesChannelsValuesRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		var p = r.getParams();

		// Parse timezone
		var timezoneStr = JsonUtils.getAsString(p, "timezone");
		var timezone = TimeZone.getTimeZone(timezoneStr).toZoneId();

		// Parse aggregations
		var jAggregations = JsonUtils.getAsJsonArray(p, "aggregations");
		var aggregations = new ArrayList<AggregationDefinition>();
		var keys = new HashSet<String>();

		for (JsonElement jAgg : jAggregations) {
			var aggObj = JsonUtils.getAsJsonObject(jAgg);

			// Key
			var key = JsonUtils.getAsString(aggObj, "key");
			if (!keys.add(key)) {
				throw new OpenemsException("Duplicate aggregation key: '" + key + "'");
			}

			// Channels
			var jChannels = JsonUtils.getAsJsonArray(aggObj, "channels");
			var channels = new ArrayList<EdgeChannelAddress>();
			for (JsonElement jChannel : jChannels) {
				channels.add(EdgeChannelAddress.fromString(JsonUtils.getAsString(jChannel)));
			}
			if (channels.isEmpty()) {
				throw new OpenemsException("Aggregation '" + key + "' must have at least one channel");
			}

			// Channel type
			var channelType = ChannelType.fromString(JsonUtils.getAsString(aggObj, "channelType"));

			// Channels aggregation type (optional)
			AggregationType channelsAggregationType = null;
			var channelsAggTypeOpt = JsonUtils.getAsOptionalString(aggObj, "channelsAggregationType");
			if (channelsAggTypeOpt.isPresent()) {
				channelsAggregationType = AggregationType.fromString(channelsAggTypeOpt.get());
			}

			// Period aggregation (optional object)
			var period = Period.LAST_24_HOURS; // default
			AggregationType periodAggregationType = null;
			var hasPeriodAggregation = false;
			var periodAggOpt = JsonUtils.getAsOptionalJsonObject(aggObj, "periodAggregation");
			if (periodAggOpt.isPresent()) {
				hasPeriodAggregation = true;
				var periodAggObj = periodAggOpt.get();
				period = Period.fromString(JsonUtils.getAsString(periodAggObj, "period"));

				var periodAggTypeOpt = JsonUtils.getAsOptionalString(periodAggObj, "periodAggregationType");
				if (periodAggTypeOpt.isPresent()) {
					periodAggregationType = AggregationType.fromString(periodAggTypeOpt.get());
				}
			}

			aggregations.add(new AggregationDefinition(//
					key, channels, channelType, channelsAggregationType, //
					period, periodAggregationType, hasPeriodAggregation));
		}

		return new AggregateAndGetEdgesChannelsValuesRequest(r, timezone, aggregations);
	}

	private final ZoneId timezone;
	private final List<AggregationDefinition> aggregations;

	private AggregateAndGetEdgesChannelsValuesRequest(JsonrpcRequest request, ZoneId timezone,
			List<AggregationDefinition> aggregations) {
		super(request, AggregateAndGetEdgesChannelsValuesRequest.METHOD);
		this.timezone = timezone;
		this.aggregations = aggregations;
	}

	/**
	 * Gets the timezone.
	 *
	 * @return the {@link ZoneId}
	 */
	public ZoneId getTimezone() {
		return this.timezone;
	}

	/**
	 * Gets the list of aggregation definitions.
	 *
	 * @return the list of {@link AggregationDefinition}
	 */
	public List<AggregationDefinition> getAggregations() {
		return this.aggregations;
	}

	@Override
	public JsonObject getParams() {
		var aggregationsArray = new JsonArray();
		for (var agg : this.aggregations) {
			var aggObj = new JsonObject();
			aggObj.addProperty("key", agg.key());

			var channelsArray = new JsonArray();
			for (var ch : agg.channels()) {
				channelsArray.add(ch.toString());
			}
			aggObj.add("channels", channelsArray);
			aggObj.addProperty("channelType", agg.channelType().name().toLowerCase());

			if (agg.channelsAggregationType() != null) {
				aggObj.addProperty("channelsAggregationType", agg.channelsAggregationType().name().toLowerCase());
			}

			var periodAggObj = new JsonObject();
			periodAggObj.addProperty("period", agg.period().name());
			if (agg.periodAggregationType() != null) {
				periodAggObj.addProperty("periodAggregationType", agg.periodAggregationType().name().toLowerCase());
			}
			aggObj.add("periodAggregation", periodAggObj);

			aggregationsArray.add(aggObj);
		}
		return JsonUtils.buildJsonObject() //
				.addProperty("timezone", this.timezone.getId()) //
				.add("aggregations", aggregationsArray) //
				.build();
	}
}
