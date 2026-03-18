package io.openems.backend.core.jsonrpcrequesthandler;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import io.openems.backend.common.jsonrpc.request.AggregateAndQueryEdgesChannelsHistoryRequest;
import io.openems.backend.common.jsonrpc.request.AggregateAndQueryEdgesChannelsHistoryRequest.AggregationHistoryDefinition;
import io.openems.backend.common.jsonrpc.response.AggregateAndQueryEdgesChannelsHistoryResponse;
import io.openems.backend.common.metadata.User;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.session.Role;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.EdgeChannelAddress;

/**
 * Handles {@link AggregateAndQueryEdgesChannelsHistoryRequest} by grouping
 * channels by edge, checking permissions, and delegating to the appropriate
 * timedata queries to build a history chart response.
 */
public class ChannelHistoryAggregationHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ChannelHistoryAggregationHandler.class);

	private final CoreJsonRpcRequestHandlerImpl parent;

	protected ChannelHistoryAggregationHandler(CoreJsonRpcRequestHandlerImpl parent) {
		this.parent = parent;
	}

	/**
	 * Handles an {@link AggregateAndQueryEdgesChannelsHistoryRequest}.
	 *
	 * @param user         the {@link User}
	 * @param messageId    the JSON-RPC Message-ID
	 * @param request      the {@link AggregateAndQueryEdgesChannelsHistoryRequest}
	 * @return the JSON-RPC Success Response Future
	 * @throws OpenemsNamedException on error
	 */
	protected CompletableFuture<? extends JsonrpcResponseSuccess> handleRequest(User user, UUID messageId,
			AggregateAndQueryEdgesChannelsHistoryRequest request) throws OpenemsNamedException {

		var timezone = request.getTimezone();
		var fromDate = request.getFromDate();
		var toDate = request.getToDate();

		// We need a resolution. If not provided, calculate it based on the period.
		var resolution = request.getResolution().orElse(
				io.openems.common.timedata.CommonTimedataService.calculateResolution(fromDate, toDate));

		LOG.info("[ChannelHistoryAggregation] Received request: {} aggregation(s), from={}, to={}, res={}{}, timezone={}",
				request.getAggregations().size(), fromDate, toDate, resolution.getValue(), resolution.getUnit(), timezone);

		SortedMap<ZonedDateTime, SortedMap<String, JsonElement>> finalTable = new TreeMap<>();

		// Pre-fill the timestamps to guarantee parallel arrays across all keys
		ZonedDateTime current = fromDate;
		while (current.isBefore(toDate)) {
			finalTable.put(current, new TreeMap<>());
			current = current.plus(resolution.getValue(), resolution.getUnit());
		}

		for (var aggregation : request.getAggregations()) {
			this.processAggregation(user, aggregation, fromDate, toDate, resolution, finalTable);
		}

		var response = new AggregateAndQueryEdgesChannelsHistoryResponse(messageId, finalTable);
		return CompletableFuture.completedFuture(response);
	}

	/**
	 * Processes a single {@link AggregationHistoryDefinition}.
	 * 
	 * @param user        the user
	 * @param aggregation the aggregation definition
	 * @param fromDate    the from date
	 * @param toDate      the to date
	 * @param resolution  the resolution
	 * @param finalTable  the final table
	 * @throws OpenemsNamedException on error
	 */
	private void processAggregation(User user, AggregationHistoryDefinition aggregation, ZonedDateTime fromDate,
			ZonedDateTime toDate, Resolution resolution,
			SortedMap<ZonedDateTime, SortedMap<String, JsonElement>> finalTable) throws OpenemsNamedException {

		var key = aggregation.key();
		var channelType = aggregation.channelType();

		// 1. Group channels by edgeId
		var channelsByEdge = groupByEdge(aggregation.channels());

		LOG.info("[ChannelHistoryAggregation] Processing key='{}', channelType={}, edges={}, totalChannels={}",
				key, channelType, channelsByEdge.keySet(), aggregation.channels().size());

		// 2. Check permissions per edge
		for (var edgeId : channelsByEdge.keySet()) {
			if (user.getRole(edgeId).isEmpty()) {
				this.parent.metadata.getEdgeMetadataForUser(user, edgeId);
			}
			user.assertEdgeRoleIsAtLeast(AggregateAndQueryEdgesChannelsHistoryRequest.METHOD, edgeId, Role.GUEST);

			LOG.info("[ChannelHistoryAggregation]   Edge '{}': permission OK, channels={}", edgeId,
					channelsByEdge.get(edgeId));
		}

		// 3. Query data across all edges and merge into a single map
		SortedMap<ZonedDateTime, Map<EdgeChannelAddress, JsonElement>> combinedData = new TreeMap<>();
		
		switch (channelType) {
		case NORMAL -> {
			for (var entry : channelsByEdge.entrySet()) {
				var edgeId = entry.getKey();
				var data = this.parent.timedataManager.queryHistoricData(edgeId, fromDate, toDate, entry.getValue(),
						resolution);
				mergeData(edgeId, data, combinedData);
			}
		}
		case ENERGY -> {
			for (var entry : channelsByEdge.entrySet()) {
				var edgeId = entry.getKey();
				var data = this.parent.timedataManager.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, entry.getValue(),
						resolution);
				mergeData(edgeId, data, combinedData);
			}
		}
		case COST -> {
			for (var entry : channelsByEdge.entrySet()) {
				var edgeId = entry.getKey();
				var data = this.parent.timedataManager.queryHistoricEnergyCostPerPeriod(edgeId, fromDate, toDate, entry.getValue(),
						resolution);
				mergeData(edgeId, data, combinedData);
			}
		}
		case EMISSIONS -> {
			for (var entry : channelsByEdge.entrySet()) {
				var edgeId = entry.getKey();
				var data = this.parent.timedataManager.queryHistoricEnergyEmissionsPerPeriod(edgeId, fromDate, toDate,
						entry.getValue(), resolution);
				mergeData(edgeId, data, combinedData);
			}
		}
		}

		// 4. Apply Cross-Channel Aggregation per timestamp bucket
		for (var timestamp : finalTable.keySet()) {
			var timestampMap = combinedData.get(timestamp);
			
			if (timestampMap == null || timestampMap.isEmpty()) {
				finalTable.get(timestamp).put(key, JsonNull.INSTANCE);
				continue;
			}
			
			java.util.List<Number> valuesToAggregate = new java.util.ArrayList<>();
			boolean missingData = false;
			
			for (var channel : aggregation.channels()) {
				var val = timestampMap.get(channel);
				if (val != null && val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
					valuesToAggregate.add(val.getAsNumber());
				} else {
					missingData = true;
					break;
				}
			}
			
			if (missingData || valuesToAggregate.isEmpty()) {
				finalTable.get(timestamp).put(key, JsonNull.INSTANCE);
			} else {
				if (valuesToAggregate.size() == 1 || aggregation.channelsAggregationType() == null) {
					finalTable.get(timestamp).put(key, new JsonPrimitive(valuesToAggregate.get(0)));
				} else {
					var result = aggregation.channelsAggregationType().apply(valuesToAggregate);
					finalTable.get(timestamp).put(key, new JsonPrimitive(result));
				}
			}
		}
	}

	private static void mergeData(String edgeId, SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> data,
			SortedMap<ZonedDateTime, Map<EdgeChannelAddress, JsonElement>> combinedData) {
		if (data == null) {
			return;
		}
		for (var rowEntry : data.entrySet()) {
			var timestampMap = combinedData.computeIfAbsent(rowEntry.getKey(), k -> new HashMap<>());
			for (var colEntry : rowEntry.getValue().entrySet()) {
				timestampMap.put(new EdgeChannelAddress(edgeId, colEntry.getKey().getComponentId(), colEntry.getKey().getChannelId()),
						colEntry.getValue());
			}
		}
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
