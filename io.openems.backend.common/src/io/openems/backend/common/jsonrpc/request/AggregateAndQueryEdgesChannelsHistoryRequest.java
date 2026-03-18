package io.openems.backend.common.jsonrpc.request;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.AggregationType;
import io.openems.common.types.ChannelType;
import io.openems.common.types.EdgeChannelAddress;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Request for 'aggregateAndQueryEdgesChannelsHistory'.
 *
 * <p>
 * Supports cross-channel aggregation over historical time-series data. The structure
 * builds time arrays based on the requested resolution and calculates the
 * aggregated values for each timestamp step.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "aggregateAndQueryEdgesChannelsHistory",
 *   "params": {
 *     "timezone": "Africa/Casablanca",
 *     "fromDate": "YYYY-MM-DD",
 *     "toDate": "YYYY-MM-DD",
 *     "resolution"?: {
 *       "value": Number,
 *       "unit": {@link ChronoUnit}
 *     },
 *     "aggregations": [
 *       {
 *         "key": "globalConsumptionEnergy",
 *         "channels": ["edge0/meter0/ActiveConsumptionEnergy", ...],
 *         "channelType": "energy", // one of: normal, energy, cost, emissions
 *         "channelsAggregationType": "sum"
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
public class AggregateAndQueryEdgesChannelsHistoryRequest extends JsonrpcRequest {

	public static final String METHOD = "aggregateAndQueryEdgesChannelsHistory";
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

	/**
	 * Defines a single aggregation within the request.
	 *
	 * @param key                      unique identifier for the result
	 * @param channels                 channel addresses in edgeId/componentId/channelId format
	 * @param channelType              one of: normal, energy, cost, emissions
	 * @param channelsAggregationType  how to combine channels (nullable if single channel)
	 */
	public record AggregationHistoryDefinition(//
			String key, //
			List<EdgeChannelAddress> channels, //
			ChannelType channelType, //
			AggregationType channelsAggregationType //
	) {
	}

	/**
	 * Create {@link AggregateAndQueryEdgesChannelsHistoryRequest} from a template
	 * {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link AggregateAndQueryEdgesChannelsHistoryRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static AggregateAndQueryEdgesChannelsHistoryRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		var p = r.getParams();

		// Parse timezone
		var jTimezone = JsonUtils.getAsPrimitive(p, "timezone");
		final ZoneId timezone;
		if (jTimezone.isNumber()) {
			timezone = ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(JsonUtils.getAsInt(jTimezone) * -1));
		} else {
			timezone = TimeZone.getTimeZone(JsonUtils.getAsString(p, "timezone")).toZoneId();
		}

		// Parse fromDate/toDate
		var fromDate = JsonUtils.getAsZonedDateWithZeroTime(p, "fromDate", timezone);
		var toDate = JsonUtils.getAsZonedDateWithZeroTime(p, "toDate", timezone).plusDays(1);

		// Parse resolution
		var jResolutionOpt = JsonUtils.getOptionalSubElement(p, "resolution");
		final Optional<Resolution> resolution;
		if (jResolutionOpt.isPresent()) {
			var jResolution = jResolutionOpt.get();
			if (jResolution.isJsonPrimitive()) {
				resolution = Optional.of(new Resolution(JsonUtils.getAsInt(jResolution), ChronoUnit.SECONDS));
			} else {
				var value = JsonUtils.getAsInt(jResolution, "value");
				var unit = JsonUtils.getAsString(jResolution, "unit");
				resolution = Optional.of(new Resolution(value, unit));
			}
		} else {
			resolution = Optional.empty();
		}

		// Parse aggregations
		var jAggregations = JsonUtils.getAsJsonArray(p, "aggregations");
		var aggregations = new ArrayList<AggregationHistoryDefinition>();
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

			aggregations.add(new AggregationHistoryDefinition(//
					key, channels, channelType, channelsAggregationType));
		}

		return new AggregateAndQueryEdgesChannelsHistoryRequest(r, timezone, fromDate, toDate, resolution,
				aggregations);
	}

	private final ZoneId timezone;
	private final ZonedDateTime fromDate;
	private final ZonedDateTime toDate;
	private final Optional<Resolution> resolution;
	private final List<AggregationHistoryDefinition> aggregations;

	private AggregateAndQueryEdgesChannelsHistoryRequest(JsonrpcRequest request, ZoneId timezone,
			ZonedDateTime fromDate, ZonedDateTime toDate, Optional<Resolution> resolution,
			List<AggregationHistoryDefinition> aggregations) {
		super(request, AggregateAndQueryEdgesChannelsHistoryRequest.METHOD);
		this.timezone = timezone;
		this.fromDate = fromDate;
		this.toDate = toDate;
		this.resolution = resolution;
		this.aggregations = aggregations;
	}

	public ZoneId getTimezone() {
		return this.timezone;
	}

	public ZonedDateTime getFromDate() {
		return this.fromDate;
	}

	public ZonedDateTime getToDate() {
		return this.toDate;
	}

	public Optional<Resolution> getResolution() {
		return this.resolution;
	}

	public List<AggregationHistoryDefinition> getAggregations() {
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

			aggregationsArray.add(aggObj);
		}

		var params = JsonUtils.buildJsonObject() //
				.addProperty("timezone", this.timezone.getId()) //
				.addProperty("fromDate", AggregateAndQueryEdgesChannelsHistoryRequest.FORMAT.format(this.fromDate)) //
				.addProperty("toDate", AggregateAndQueryEdgesChannelsHistoryRequest.FORMAT.format(this.toDate)) //
				.add("aggregations", aggregationsArray);

		this.resolution.ifPresent(resolution -> {
			params.add("resolution", JsonUtils.buildJsonObject() //
					.addProperty("unit", resolution.getUnit().name()) //
					.addProperty("value", resolution.getValue()) //
					.build());
		});

		return params.build();
	}
}
