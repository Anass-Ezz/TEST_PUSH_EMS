package io.openems.shared.influxdb.proxy;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.influxdb.query.dsl.Flux;
import com.influxdb.query.dsl.functions.restriction.Restrictions;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.shared.influxdb.InfluxConnector.InfluxConnection;

/**
 * Implements queries using Flux.
 */
public class FluxProxy extends QueryProxy {

	private static final Logger LOG = LoggerFactory.getLogger(FluxProxy.class);

	public FluxProxy(String tag) {
		super(tag);
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(//
			InfluxConnection influxConnection, //
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels //
	) throws OpenemsNamedException {
		var query = this.buildHistoricEnergyQuery(bucket, measurement, influxEdgeId, fromDate, toDate, channels);
		var queryResult = this.executeQuery(influxConnection, query);
		return convertHistoricEnergyResult(query, queryResult);
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricDataAggregated(//
			InfluxConnection influxConnection, //
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			String aggregationFunction //
	) throws OpenemsNamedException {
		var query = this.buildHistoricDataAggregatedQuery(bucket, measurement, influxEdgeId, fromDate, toDate,
				channels, aggregationFunction);
		var queryResult = this.executeQuery(influxConnection, query);
		// Reuse the same result converter as energy — both return flat channel→value
		return convertHistoricEnergyResult(query, queryResult);
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergySingleValueInDay(InfluxConnection influxConnection,
			String bucket, String measurement, Optional<Integer> influxEdgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(//
			InfluxConnection influxConnection, //
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			Resolution resolution //
	) throws OpenemsNamedException {
		var query = this.buildHistoricDataQuery(bucket, measurement, influxEdgeId, fromDate, toDate, channels,
				resolution);
		var queryResult = this.executeQuery(influxConnection, query);
		return convertHistoricDataQueryResult(queryResult, fromDate, resolution);
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(//
			InfluxConnection influxConnection, //
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			Resolution resolution //
	) throws OpenemsNamedException {
		var query = this.buildHistoricEnergyPerPeriodQuery(bucket, measurement, influxEdgeId, fromDate, toDate,
				channels, resolution);
		var queryResult = this.executeQuery(influxConnection, query);
		return convertHistoricDataQueryResult(queryResult, fromDate, resolution);
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryRawHistoricEnergyPerPeriodSingleValueInDay(
			InfluxConnection influxConnection, String bucket, String measurement, Optional<Integer> influxEdgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryFirstValueBefore(String bucket,
			InfluxConnection influxConnection, String measurement, Optional<Integer> influxEdgeId, ZonedDateTime date,
			Set<ChannelAddress> channels) throws OpenemsNamedException {
		final var query = this.buildFetchFirstValueBefore(bucket, measurement, influxEdgeId, date, channels);
		final var queryResult = this.executeQuery(influxConnection, query);
		return convertFirstValueBeforeQueryResult(queryResult, channels);
	}

	@Override
	protected String buildHistoricDataQuery(//
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			Resolution resolution //
	) {
		// remove 5 minutes to prevent shifted timeline
		var fromInstant = fromDate.toInstant().minus(5, ChronoUnit.MINUTES).atZone(fromDate.getZone());

		// Ensure range is not empty
		if (!toDate.isAfter(fromInstant)) {
			fromInstant = toDate.minus(1, ChronoUnit.MILLIS);
		}

		// prepare query
		var builder = new StringBuilder() //
				.append("from(bucket: \"").append(bucket).append("\")\n") //
				.append("  |> range(start: ").append(fromInstant.toInstant()).append(", stop: ").append(toDate.toInstant()).append(")\n") //
				.append("  |> filter(fn: (r) => r._measurement == \"").append(measurement).append("\")\n");

		if (influxEdgeId.isPresent()) {
			builder.append("  |> filter(fn: (r) => r.").append(this.tag).append(" == \"").append(influxEdgeId.get()).append("\")\n");
		}

		builder.append("  |> filter(fn: (r) => ").append(this.toChannelFilter(channels)).append(")\n") //
				.append("  |> aggregateWindow(every: ").append(toFluxDuration(resolution)).append(", fn: mean)");

		return builder.toString();
	}

	@Override
	protected String buildHistoricEnergyQuery(//
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels //
	) {
		// Ensure range is not empty (at least 1ms)
		if (!toDate.isAfter(fromDate)) {
			fromDate = toDate.minus(1, ChronoUnit.MILLIS);
		}

		// prepare query
		var builder = new StringBuilder() //
				.append("data = from(bucket: \"").append(bucket).append("\")\n") //
				.append("  |> range(start: ").append(fromDate.toInstant()).append(", stop: ").append(toDate.toInstant()).append(")\n") //
				.append("  |> filter(fn: (r) => r._measurement == \"").append(measurement).append("\")\n");

		if (influxEdgeId.isPresent()) {
			builder.append("  |> filter(fn: (r) => r.").append(this.tag).append(" == \"").append(influxEdgeId.get()).append("\")\n");
		}

		builder //
				.append("  |> filter(fn: (r) => ").append(this.toChannelFilter(channels)).append(")\n") //
				.append("first = data |> first()\n") //
				.append("last = data |> last()\n") //
				.append("union(tables: [first, last])\n") //
				.append("  |> difference()");
		return builder.toString();
	}

	@Override
	protected String buildHistoricDataAggregatedQuery(//
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			String aggregationFunction //
	) {
		// Ensure range is not empty (at least 1ms)
		if (!toDate.isAfter(fromDate)) {
			fromDate = toDate.minus(1, ChronoUnit.MILLIS);
		}

		// Build a Flux query that aggregates all raw values within the range into a
		// single scalar per channel using the specified function.
		var builder = new StringBuilder() //
				.append("from(bucket: \"").append(bucket).append("\")\n") //
				.append("  |> range(start: ").append(fromDate.toInstant()).append(", stop: ").append(toDate.toInstant()).append(")\n") //
				.append("  |> filter(fn: (r) => r._measurement == \"").append(measurement).append("\")\n");

		if (influxEdgeId.isPresent()) {
			builder.append("  |> filter(fn: (r) => r.").append(this.tag).append(" == \"").append(influxEdgeId.get()).append("\")\n");
		}

		builder.append("  |> filter(fn: (r) => ").append(this.toChannelFilter(channels)).append(")\n") //
				.append("  |> ").append(aggregationFunction).append("()");

		return builder.toString();
	}

	@Override
	protected String buildHistoricEnergyQuerySingleValueInDay(String bucket, String measurement,
			Optional<Integer> influxEdgeId, ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String buildHistoricEnergyPerPeriodQuery(//
			String bucket, //
			String measurement, //
			Optional<Integer> influxEdgeId, //
			ZonedDateTime fromDate, //
			ZonedDateTime toDate, //
			Set<ChannelAddress> channels, //
			Resolution resolution //
	) {
		if (resolution.getUnit().equals(ChronoUnit.MONTHS)) {
			fromDate = fromDate.with(TemporalAdjusters.firstDayOfMonth());
			if (!toDate.equals(toDate.with(TemporalAdjusters.firstDayOfMonth()))) {
				toDate = toDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1);
			}
		}

		// Shift the fromDate back by one resolution step to get the value IMMEDIATELY BEFORE the start.
		// This ensures `difference()` has a previous point to calculate the delta for the first requested bucket.
		var queryFromDate = fromDate.minus(resolution.getValue(), resolution.getUnit());

		// Ensure range is not empty
		if (!toDate.isAfter(queryFromDate)) {
			queryFromDate = toDate.minus(1, ChronoUnit.MILLIS);
		}

		// prepare query
		var builder = new StringBuilder() //
				.append("from(bucket: \"").append(bucket).append("\")\n") //
				.append("  |> range(start: ").append(queryFromDate.toInstant()).append(", stop: ").append(toDate.toInstant()).append(")\n") //
				.append("  |> filter(fn: (r) => r._measurement == \"").append(measurement).append("\")\n");

		if (influxEdgeId.isPresent()) {
			builder.append("  |> filter(fn: (r) => r.").append(this.tag).append(" == \"").append(influxEdgeId.get()).append("\")\n");
		}

		builder.append("  |> filter(fn: (r) => ").append(this.toChannelFilter(channels)).append(")\n") //
				.append("  |> aggregateWindow(every: ").append(toFluxDuration(resolution)).append(", fn: last)\n") //
				.append("  |> difference(nonNegative: true)");

		return builder.toString();
	}

	@Override
	protected String buildHistoricEnergyPerPeriodQuerySingleValueInDay(String bucket, String measurement,
			Optional<Integer> influxEdgeId, ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels,
			Resolution resolution) throws OpenemsException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String buildFetchFirstValueBefore(String bucket, String measurement, Optional<Integer> influxEdgeId,
			ZonedDateTime date, Set<ChannelAddress> channels) {
		// Calculates actual system date -100 days
		ZonedDateTime hundredDaysAgo = date.minusDays(100);

		var builder = new StringBuilder() //
				.append("from(bucket: \"").append(bucket).append("\")\n") //
				.append("  |> range(start: ").append(hundredDaysAgo.toInstant()).append(", stop: ").append(date.toInstant()).append(")\n") //
				.append("  |> filter(fn: (r) => r._measurement == \"").append(measurement).append("\")\n");

		if (influxEdgeId.isPresent()) {
			builder.append("  |> filter(fn: (r) => r.").append(this.tag).append(" == \"").append(influxEdgeId.get()).append("\")\n");
		}

		builder.append("  |> filter(fn: (r) => ").append(this.toChannelFilter(channels)).append(")\n") //
				.append("  |> last()");

		return builder.toString();
	}

	/**
	 * Converts given {@link Set} of {@link ChannelAddress} to a Flux filter string.
	 *
	 * @param channels {@link Set} of {@link ChannelAddress}
	 * @return the filter string (e.g. 'r._field == "foo" or r._field == "bar"')
	 */
	private String toChannelFilter(Set<ChannelAddress> channels) {
		return channels.stream() //
				.map(c -> "r._field == \"" + c.toString() + "\"") //
				.collect(java.util.stream.Collectors.joining(" or "));
	}

	/**
	 * Converts a {@link Resolution} to a Flux duration string (e.g. "1h", "30d").
	 *
	 * @param resolution the {@link Resolution}
	 * @return the Flux duration string
	 */
	private static String toFluxDuration(Resolution resolution) {
		var unit = switch (resolution.getUnit()) {
		case NANOS -> "ns";
		case MICROS -> "us";
		case MILLIS -> "ms";
		case SECONDS -> "s";
		case MINUTES -> "m";
		case HOURS -> "h";
		case DAYS -> "d";
		case WEEKS -> "w";
		case MONTHS -> "mo";
		case YEARS -> "y";
		default -> resolution.getUnit().name().toLowerCase();
		};
		return resolution.getValue() + unit;
	}

	/**
	 * Converts given {@link Set} of {@link ChannelAddress} to {@link Restrictions}
	 * separated by or.
	 *
	 * @deprecated use {@link #toChannelFilter(Set)} for string building.
	 * @param channels {@link Set} of {@link ChannelAddress}
	 * @return {@link Restrictions} separated by or
	 */
	@Deprecated
	private static Restrictions toChannelAddressFieldList(Set<ChannelAddress> channels) {
		var restrictions = channels.stream() //
				.map(channel -> Restrictions.field().equal(channel.toString())) //
				.toArray(restriction -> new Restrictions[restriction]);

		return Restrictions.or(restrictions);
	}

	/**
	 * Execute given query.
	 * 
	 * @param influxConnection a Influx-Connection
	 * @param query            to execute
	 * @return Result from database as {@link List} of {@link FluxTable}
	 * @throws OpenemsException on error
	 */
	private List<FluxTable> executeQuery(InfluxConnection influxConnection, String query) throws OpenemsException {
		this.assertQueryLimit();

		// Parse result
		List<FluxTable> queryResult;
		try {
			queryResult = influxConnection.client.getQueryApi().query(query);
		} catch (RuntimeException e) {
			this.queryLimit.increase();
			LOG.error("InfluxDB query runtime error. Query: " + query + ", Error: " + e.getMessage());
			throw new OpenemsException(e.getMessage());
		}
		this.queryLimit.decrease();
		return queryResult;
	}

	/**
	 * Converts the QueryResult of a Historic-Data query to a properly typed Table.
	 *
	 * @param queryResult the Query-Result
	 * @param fromDate    start date from query
	 * @param resolution  {@link Resolution} to revert InfluxDB offset
	 * @return the historic data as Map
	 * @throws OpenemsException on error
	 */
	private static SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> convertHistoricDataQueryResult(
			List<FluxTable> queryResult, ZonedDateTime fromDate, Resolution resolution) throws OpenemsNamedException {
		SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> table = new TreeMap<>();

		for (FluxTable fluxTable : queryResult) {
			for (FluxRecord record : fluxTable.getRecords()) {
				var timestamp = ZonedDateTime.ofInstant(record.getTime(), fromDate.getZone());

				// ignore first timestamp is before from date
				if (timestamp.isBefore(fromDate)) {
					continue;
				}
				timestamp = resolution.revertInfluxDbOffset(timestamp);

				var valueObj = record.getValue();
				var value = switch (valueObj) {
				case null //
					-> JsonNull.INSTANCE;
				case Number n //
					-> new JsonPrimitive(n);
				default //
					-> new JsonPrimitive(valueObj.toString());
				};

				var channelAddresss = ChannelAddress.fromString(record.getField());

				var row = table.get(timestamp);
				if (row == null) {
					row = new TreeMap<>();
				}
				row.put(channelAddresss, value);

				table.put(timestamp, row);
			}
		}

		return table;
	}

	/**
	 * Converts the QueryResult of a Historic-Energy query to a properly typed Map.
	 *
	 * @param query       was executed
	 * @param queryResult the Query-Result
	 * @return the historic energy as Map
	 * @throws OpenemsException on error
	 */
	private static SortedMap<ChannelAddress, JsonElement> convertHistoricEnergyResult(String query,
			List<FluxTable> queryResult) throws OpenemsNamedException {
		SortedMap<ChannelAddress, JsonElement> map = new TreeMap<>();

		for (FluxTable fluxTable : queryResult) {
			for (FluxRecord record : fluxTable.getRecords()) {

				var valueObj = record.getValue();
				var value = switch (valueObj) {
				case null //
					-> JsonNull.INSTANCE;
				case Number number -> {
					if (number.intValue() < 0) {
						// do not consider negative values
						LOG.warn("Got negative Energy value [" + number + "] for query: " + query);
						yield JsonNull.INSTANCE;
					} else {
						yield new JsonPrimitive(number);
					}
				}
				default //
					-> new JsonPrimitive(valueObj.toString());
				};

				var channelAddresss = ChannelAddress.fromString(record.getField());

				map.put(channelAddresss, value);
			}
		}

		// Check if all values are null
		var areAllValuesNull = true;
		for (JsonElement value : map.values()) {
			if (!value.isJsonNull()) {
				areAllValuesNull = false;
				break;
			}
		}
		if (areAllValuesNull) {
			throw new OpenemsException("Energy values are not available for query: " + query);
		}

		return map;
	}

	/**
	 * Converts the QueryResult of a Last-Data query to a properly typed Table.
	 *
	 * @param queryResult the Query-Result
	 * @param channels    the ChannelAddress
	 * @return the latest data as Map
	 * @throws OpenemsNamedException on error
	 */
	private static SortedMap<ChannelAddress, JsonElement> convertFirstValueBeforeQueryResult(
			List<FluxTable> queryResult, Set<ChannelAddress> channels) throws OpenemsNamedException {

		SortedMap<ChannelAddress, JsonElement> latestValues = new TreeMap<>();

		for (FluxTable fluxTable : queryResult) {
			for (FluxRecord record : fluxTable.getRecords()) {

				var valueObj = record.getValue();
				var value = switch (valueObj) {
				case null //
					-> JsonNull.INSTANCE;
				case Number number //
					-> new JsonPrimitive(number);
				default //
					-> new JsonPrimitive(valueObj.toString());
				};

				var channelAddresss = ChannelAddress.fromString(record.getField());
				latestValues.put(channelAddresss, value);

			}
		}

		return latestValues;
	}

}