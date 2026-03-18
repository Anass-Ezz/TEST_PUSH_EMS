package io.openems.backend.core.jsonrpcrequesthandler;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.backend.common.jsonrpc.request.AggregateAndGetEdgesChannelsValuesRequest;
import io.openems.backend.common.jsonrpc.request.AggregateAndGetEdgesChannelsValuesRequest.AggregationDefinition;
import io.openems.backend.common.jsonrpc.response.AggregateAndGetEdgesChannelsValuesResponse;
import io.openems.backend.common.metadata.User;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.session.Role;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.ChannelType;
import io.openems.common.types.EdgeChannelAddress;

/**
 * Handles {@link AggregateAndGetEdgesChannelsValuesRequest} by grouping
 * channels by edge, checking permissions, and delegating to the appropriate
 * timedata queries.
 *
 * <p>
 * This handler follows the same pattern as {@link EdgeRpcRequestHandler}, with
 * a reference to its parent {@link CoreJsonRpcRequestHandlerImpl}.
 */
public class ChannelAggregationHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ChannelAggregationHandler.class);

	private final CoreJsonRpcRequestHandlerImpl parent;

	protected ChannelAggregationHandler(CoreJsonRpcRequestHandlerImpl parent) {
		this.parent = parent;
	}

	/**
	 * Handles an {@link AggregateAndGetEdgesChannelsValuesRequest}.
	 *
	 * @param user      the {@link User}
	 * @param messageId the JSON-RPC Message-ID
	 * @param request   the {@link AggregateAndGetEdgesChannelsValuesRequest}
	 * @return the JSON-RPC Success Response Future
	 * @throws OpenemsNamedException on error
	 */
	protected CompletableFuture<? extends JsonrpcResponseSuccess> handleRequest(User user, UUID messageId,
			AggregateAndGetEdgesChannelsValuesRequest request) throws OpenemsNamedException {

		var response = new AggregateAndGetEdgesChannelsValuesResponse(messageId);
		var timezone = request.getTimezone();
		var now = ZonedDateTime.now(timezone);

		LOG.info("[ChannelAggregation] Received request with {} aggregation(s), timezone={}",
				request.getAggregations().size(), timezone);

		for (var aggregation : request.getAggregations()) {
			this.processAggregation(user, aggregation, now, response);
		}

		return CompletableFuture.completedFuture(response);
	}

	/**
	 * Processes a single {@link AggregationDefinition}.
	 *
	 * @param user        the user
	 * @param aggregation the aggregation definition
	 * @param now         the current time
	 * @param response    the response
	 * @throws OpenemsNamedException on error
	 */
	private void processAggregation(User user, AggregationDefinition aggregation, ZonedDateTime now,
			AggregateAndGetEdgesChannelsValuesResponse response) throws OpenemsNamedException {

		var key = aggregation.key();
		var channelType = aggregation.channelType();
		var period = aggregation.period();

		// 1. Group channels by edgeId
		var channelsByEdge = groupByEdge(aggregation.channels());

		LOG.info("[ChannelAggregation] Processing key='{}', channelType={}, period={}, edges={}, totalChannels={}",
				key, channelType, period, channelsByEdge.keySet(), aggregation.channels().size());

		// 2. Check permissions per edge
		for (var edgeId : channelsByEdge.keySet()) {
			if (user.getRole(edgeId).isEmpty()) {
				this.parent.metadata.getEdgeMetadataForUser(user, edgeId);
			}
			user.assertEdgeRoleIsAtLeast(AggregateAndGetEdgesChannelsValuesRequest.METHOD, edgeId, Role.GUEST);

			LOG.info("[ChannelAggregation]   Edge '{}': permission OK, channels={}", edgeId,
					channelsByEdge.get(edgeId));
		}

		this.validateAggregation(aggregation);

		Number currentValue;
		Double movementPercentage = null;

		if (!aggregation.hasPeriodAggregation() && channelType == ChannelType.NORMAL) {
			LOG.info("[ChannelAggregation]   No period configuration. Fetching live values.");
			currentValue = this.queryLiveValues(key, aggregation, channelsByEdge);
		} else {
			// 3. Query the CURRENT period data
			var fromDate = period.computeFromDate(now);
			var toDate = now;

			LOG.info("[ChannelAggregation]   Current Period={}, fromDate={}, toDate={}", period, fromDate, toDate);

			currentValue = this.queryAggregationPeriod(key, aggregation, channelsByEdge, fromDate, toDate);

			// 4. Query the PREVIOUS period data (if requested) and build response
			if (aggregation.hasPeriodAggregation() && currentValue != null && period.supportsMovementPercentage()) {
				var prevFromDate = period.computePreviousPeriodFromDate(fromDate);
				var prevToDate = period.computePreviousPeriodToDate(fromDate);

				LOG.info("[ChannelAggregation]   Previous Period={}, prevFromDate={}, prevToDate={}", period, prevFromDate, prevToDate);
				
				Number prevValue = this.queryAggregationPeriod(key, aggregation, channelsByEdge, prevFromDate, prevToDate);

				if (prevValue != null && prevValue.doubleValue() != 0.0) {
					movementPercentage = ((currentValue.doubleValue() - prevValue.doubleValue()) / Math.abs(prevValue.doubleValue())) * 100.0;
				}
			}
		}

		response.addResult(aggregation.key(), currentValue, movementPercentage);
	}

	/**
	 * Queries current live values from EdgeManager.
	 * 
	 * @param key            the key
	 * @param aggregation    the aggregation
	 * @param channelsByEdge the channels by edge
	 * @return the aggregated value
	 */
	private Number queryLiveValues(String key, AggregationDefinition aggregation,
			Map<String, Set<ChannelAddress>> channelsByEdge) {

		java.util.Map<EdgeChannelAddress, Number> channelScalars = new java.util.LinkedHashMap<>();

		for (var entry : channelsByEdge.entrySet()) {
			var edgeId = entry.getKey();
			var channels = entry.getValue();

			var data = this.parent.edgeManager.getChannelValues(edgeId, channels);
			for (var channel : channels) {
				var val = data.get(channel);
				if (val != null && val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
					channelScalars.put(new EdgeChannelAddress(edgeId, channel.getComponentId(), channel.getChannelId()), val.getAsNumber());
				} else {
					LOG.warn("[ChannelAggregation]   Live value for {}/{} is missing or not a number: {}", edgeId, channel, val);
					channelScalars = null;
					break;
				}
			}
			if (channelScalars == null) {
				break;
			}
		}

		return this.applyCrossChannelAggregation(key, aggregation, channelScalars);
	}

	/**
	 * Queries the aggregated value for a given period.
	 * 
	 * @param key            the aggregation key
	 * @param aggregation    the {@link AggregationDefinition}
	 * @param channelsByEdge a map of edge IDs to their respective channels
	 * @param fromDate       the start date
	 * @param toDate         the end date
	 * @return the aggregated value as a Number
	 * @throws OpenemsNamedException on error
	 */
	private Number queryAggregationPeriod(String key, AggregationDefinition aggregation,
			Map<String, Set<ChannelAddress>> channelsByEdge, ZonedDateTime fromDate, ZonedDateTime toDate) throws OpenemsNamedException {
		return switch (aggregation.channelType()) {
		case NORMAL -> this.queryNormalChannels(key, aggregation, channelsByEdge, fromDate, toDate);
		case ENERGY -> this.queryEnergyChannels(key, aggregation, channelsByEdge, fromDate, toDate);
		case COST -> this.queryCostChannels(key, aggregation, channelsByEdge, fromDate, toDate);
		case EMISSIONS -> this.queryEmissionsChannels(key, aggregation, channelsByEdge, fromDate, toDate);
		};
	}

	/**
	 * Validates channelType-specific constraints.
	 *
	 * @throws OpenemsNamedException if validation fails
	 */
	private void validateAggregation(AggregationDefinition aggregation) throws OpenemsNamedException {
		// ENERGY, COST and EMISSIONS must NOT have periodAggregationType
		if ((aggregation.channelType() == ChannelType.ENERGY || aggregation.channelType() == ChannelType.COST
				|| aggregation.channelType() == ChannelType.EMISSIONS)
				&& aggregation.periodAggregationType() != null) {
			throw new OpenemsException("periodAggregationType must not be set for channelType '"
					+ aggregation.channelType().name().toLowerCase() + "' in aggregation '" + aggregation.key() + "'");
		}
	}

	/**
	 * Queries data for NORMAL channels using
	 * {@code queryHistoricDataAggregated()} — delegates period aggregation to
	 * InfluxDB.
	 */
	private Number queryNormalChannels(String key, AggregationDefinition aggregation,
			Map<String, Set<ChannelAddress>> channelsByEdge, ZonedDateTime fromDate, ZonedDateTime toDate) throws OpenemsNamedException {

		var aggFunction = aggregation.periodAggregationType() != null
				? aggregation.periodAggregationType().name().toLowerCase()
				: "mean";

		LOG.info("[ChannelAggregation]   NORMAL: aggregationFunction={}", aggFunction);

		java.util.Map<EdgeChannelAddress, Number> channelScalars = new java.util.LinkedHashMap<>();

		for (var entry : channelsByEdge.entrySet()) {
			var edgeId = entry.getKey();
			var channels = entry.getValue();

			var data = this.parent.timedataManager.queryHistoricDataAggregated(edgeId, fromDate, toDate, channels,
					aggFunction);
			if (data == null) {
				LOG.warn("[ChannelAggregation]   NORMAL edge='{}': queryHistoricDataAggregated returned null", edgeId);
				channelScalars = null;
				break;
			}

			for (var channel : channels) {
				var val = data.get(channel);
				if (val != null && val.isJsonPrimitive()) {
					channelScalars.put(new EdgeChannelAddress(edgeId, channel.getComponentId(), channel.getChannelId()), val.getAsNumber());
				} else {
					channelScalars = null;
					break;
				}
			}
			if (channelScalars == null) break;
		}

		return applyCrossChannelAggregation(key, aggregation, channelScalars);
	}

	/**
	 * Queries data for ENERGY channels using
	 * {@code queryHistoricEnergy()} and logs the delta values.
	 */
	private Number queryEnergyChannels(String key, AggregationDefinition aggregation, Map<String, Set<ChannelAddress>> channelsByEdge,
			ZonedDateTime fromDate, ZonedDateTime toDate) throws OpenemsNamedException {

		java.util.Map<EdgeChannelAddress, Number> channelScalars = new java.util.LinkedHashMap<>();

		for (var entry : channelsByEdge.entrySet()) {
			var edgeId = entry.getKey();
			var channels = entry.getValue();

			var data = this.parent.timedataManager.queryHistoricEnergy(edgeId, fromDate, toDate, channels);
			if (data == null) {
				LOG.warn("[ChannelAggregation]   ENERGY edge='{}': queryHistoricEnergy returned null", edgeId);
				channelScalars = null;
				break;
			}

			for (var channel : channels) {
				var val = data.get(channel);
				if (val != null && val.isJsonPrimitive()) {
					channelScalars.put(new EdgeChannelAddress(edgeId, channel.getComponentId(), channel.getChannelId()), val.getAsNumber());
				} else {
					channelScalars = null;
					break;
				}
			}
			if (channelScalars == null) {
				break;
			}
		}

		return applyCrossChannelAggregation(key, aggregation, channelScalars);
	}

	/**
	 * Queries data for COST channels using
	 * {@code queryHistoricEnergyCostPerPeriod()} at 1-hour resolution
	 * and logs the hourly buckets.
	 */
	private Number queryCostChannels(String key, AggregationDefinition aggregation, Map<String, Set<ChannelAddress>> channelsByEdge,
			ZonedDateTime fromDate, ZonedDateTime toDate) throws OpenemsNamedException {

		var hourlyResolution = new Resolution(1, ChronoUnit.HOURS);
		java.util.Map<EdgeChannelAddress, Number> channelScalars = new java.util.LinkedHashMap<>();

		for (var entry : channelsByEdge.entrySet()) {
			var edgeId = entry.getKey();
			var channels = entry.getValue();

			var data = this.parent.timedataManager.queryHistoricEnergyCostPerPeriod(edgeId, fromDate, toDate,
					channels, hourlyResolution);
			if (data == null) {
				LOG.warn("[ChannelAggregation]   COST edge='{}': queryHistoricEnergyCostPerPeriod returned null",
						edgeId);
				channelScalars = null;
				break;
			}

			for (var channel : channels) {
				double sum = 0.0;
				boolean hasData = false;
				for (var timestampEntry : data.entrySet()) {
					var val = timestampEntry.getValue().get(channel);
					if (val != null && val.isJsonPrimitive()) {
						sum += val.getAsDouble();
						hasData = true;
					}
				}
				if (hasData) {
					channelScalars.put(new EdgeChannelAddress(edgeId, channel.getComponentId(), channel.getChannelId()), sum);
				} else {
					channelScalars = null;
					break;
				}
			}
			if (channelScalars == null) {
				break;
			}
		}

		return applyCrossChannelAggregation(key, aggregation, channelScalars);
	}

	/**
	 * Queries data for EMISSIONS channels using
	 * {@code queryHistoricEnergyEmissionsPerPeriod()} at 1-day resolution
	 * and sums the daily emission buckets.
	 * 
	 * @param key            the key
	 * @param aggregation    the aggregation
	 * @param channelsByEdge the channels by edge
	 * @param fromDate       the from date
	 * @param toDate         the to date
	 * @return the aggregated value
	 * @throws OpenemsNamedException on error
	 */
	private Number queryEmissionsChannels(String key, AggregationDefinition aggregation,
			Map<String, Set<ChannelAddress>> channelsByEdge, ZonedDateTime fromDate, ZonedDateTime toDate) throws OpenemsNamedException {

		var dailyResolution = new Resolution(1, ChronoUnit.DAYS);
		java.util.Map<EdgeChannelAddress, Number> channelScalars = new java.util.LinkedHashMap<>();

		for (var entry : channelsByEdge.entrySet()) {
			var edgeId = entry.getKey();
			var channels = entry.getValue();

			var data = this.parent.timedataManager.queryHistoricEnergyEmissionsPerPeriod(edgeId, fromDate, toDate,
					channels, dailyResolution);
			if (data == null) {
				LOG.warn("[ChannelAggregation]   EMISSIONS edge='{}': queryHistoricEnergyEmissionsPerPeriod returned null",
						edgeId);
				channelScalars = null;
				break;
			}

			for (var channel : channels) {
				double sum = 0.0;
				boolean hasData = false;
				for (var timestampEntry : data.entrySet()) {
					var val = timestampEntry.getValue().get(channel);
					if (val != null && val.isJsonPrimitive()) {
						sum += val.getAsDouble();
						hasData = true;
					}
				}
				if (hasData) {
					channelScalars.put(new EdgeChannelAddress(edgeId, channel.getComponentId(), channel.getChannelId()), sum);
				} else {
					channelScalars = null;
					break;
				}
			}
			if (channelScalars == null) {
				break;
			}
		}

		return this.applyCrossChannelAggregation(key, aggregation, channelScalars);
	}

	private Number applyCrossChannelAggregation(String key, AggregationDefinition aggregation,
			java.util.Map<EdgeChannelAddress, Number> channelScalars) {

		if (channelScalars == null || channelScalars.isEmpty()) {
			LOG.info("[ChannelAggregation]   Result: key='{}' -> null (missing data for one or more channels)", key);
			return null;
		}
		
		var values = new java.util.ArrayList<Number>(channelScalars.values());
		if (values.size() == 1 || aggregation.channelsAggregationType() == null) {
			LOG.info("[ChannelAggregation]   Result: key='{}' -> {} (Single channel)", key, values.get(0));
			return values.get(0);
		} else {
			var result = aggregation.channelsAggregationType().apply(values);
			LOG.info("[ChannelAggregation]   Result: key='{}' -> {} (Aggregated {} channels using {})", 
					key, result, values.size(), aggregation.channelsAggregationType());
			return result;
		}
	}

	/**
	 * Formats a map of channel values for logging.
	 * 
	 * @param values the values to format
	 * @return the formatted string
	 */
	private static String formatChannelValues(Map<ChannelAddress, JsonElement> values) {
		var sb = new StringBuilder("{");
		var first = true;
		for (var e : values.entrySet()) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(e.getKey()).append("=").append(e.getValue());
			first = false;
		}
		return sb.append("}").toString();
	}

	/**
	 * Groups a list of {@link EdgeChannelAddress} by their edge ID.
	 *
	 * @param channels the channel addresses
	 * @return a map from edgeId to sets of {@link ChannelAddress}
	 */
	private static Map<String, Set<ChannelAddress>> groupByEdge(List<EdgeChannelAddress> channels) {
		var result = new HashMap<String, Set<ChannelAddress>>();
		for (var edgeChannel : channels) {
			result.computeIfAbsent(edgeChannel.getEdgeId(), k -> new HashSet<>())
					.add(edgeChannel.getChannelAddress());
		}
		return result;
	}
}
