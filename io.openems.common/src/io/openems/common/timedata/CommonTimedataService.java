package io.openems.common.timedata;

import java.time.Period;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.SortedMap;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.request.QueryHistoricTimeseriesDataRequest;
import io.openems.common.types.ChannelAddress;

public interface CommonTimedataService {

	/**
	 * Calculates the time {@link Resolution} for the period.
	 *
	 * @param fromDate the From-Date
	 * @param toDate   the To-Date
	 * @return the resolution
	 */
	public static Resolution calculateResolution(ZonedDateTime fromDate, ZonedDateTime toDate) {
		var days = Period.between(fromDate.toLocalDate(), toDate.toLocalDate()).getDays();
		if (days <= 1) {
			return new Resolution(5, ChronoUnit.MINUTES);
		} else if (days == 2) {
			return new Resolution(10, ChronoUnit.MINUTES);
		} else if (days == 3) {
			return new Resolution(15, ChronoUnit.MINUTES);
		} else if (days == 4) {
			return new Resolution(20, ChronoUnit.MINUTES);
		} else if (days <= 6) {
			return new Resolution(30, ChronoUnit.MINUTES);
		} else if (days <= 12) {
			return new Resolution(1, ChronoUnit.HOURS);
		} else if (days <= 24) {
			return new Resolution(2, ChronoUnit.HOURS);
		} else if (days <= 48) {
			return new Resolution(4, ChronoUnit.HOURS);
		} else if (days <= 96) {
			return new Resolution(8, ChronoUnit.HOURS);
		} else if (days <= 144) {
			return new Resolution(12, ChronoUnit.HOURS);
		} else {
			return new Resolution(1, ChronoUnit.DAYS);
		}
	}

	/**
	 * Queries historic data. The 'resolution' of the query is calculated
	 * dynamically according to the length of the period.
	 *
	 * @param edgeId  the Edge-ID
	 * @param request the {@link QueryHistoricTimeseriesDataRequest}
	 * @return the query result; possibly null
	 */
	public default SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			QueryHistoricTimeseriesDataRequest request) throws OpenemsNamedException {
		// calculate resolution based on the length of the period
		var resolution = request.getResolution() //
				.orElse(CommonTimedataService.calculateResolution(request.getFromDate(), request.getToDate()));

		return this.queryHistoricData(edgeId, request.getFromDate(), request.getToDate(), request.getChannels(),
				resolution);
	}

	/**
	 * Queries historic data.
	 *
	 * @param edgeId     the Edge-ID; or null query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException;

	/**
	 * Queries historic energy.
	 *
	 * @param edgeId   the Edge-ID; or null query all
	 * @param fromDate the From-Date
	 * @param toDate   the To-Date
	 * @param channels the Channels
	 * @return the query result; possibly null
	 */
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException;

	/**
	 * Queries historic data and aggregates all values within the given time range
	 * into a single scalar per channel using the specified aggregation function.
	 *
	 * <p>
	 * Unlike {@link #queryHistoricData}, which returns a time-series, this method
	 * pushes the aggregation (mean/sum/min/max/median) down to the data layer and
	 * returns one value per channel.
	 *
	 * @param edgeId              the Edge-ID; or null to query all
	 * @param fromDate            the From-Date
	 * @param toDate              the To-Date
	 * @param channels            the Channels
	 * @param aggregationFunction the aggregation function name (e.g. "mean",
	 *                            "sum", "min", "max", "median")
	 * @return the query result as a map from channel to aggregated value; possibly
	 *         null
	 */
	public default SortedMap<ChannelAddress, JsonElement> queryHistoricDataAggregated(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, String aggregationFunction)
			throws OpenemsNamedException {
		throw new io.openems.common.exceptions.OpenemsException(
				"queryHistoricDataAggregated is not supported by this implementation");
	}

	/**
	 * Queries historic energy per period.
	 *
	 * <p>
	 * This is for use-cases where you want to get the energy for each period (with
	 * {@link Resolution}) per Channel, e.g. to visualize energy in a histogram
	 * chart. For each period the energy is calculated by subtracting first value of
	 * the period from the last value of the period.
	 *
	 * @param edgeId     the Edge-ID; or null query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException;

	/**
	 * Queries historic energy cost per period.
	 *
	 * <p>
	 * This method fetches energy cost at a forced 1-Hour resolution to accurately applying
	 * varying Time-of-Use tariffs, and then sums the hourly costs into the requested resolution bucket.
	 *
	 * @param edgeId     the Edge-ID; or null query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the requested {@link Resolution}
	 * @return the query result; possibly null
	 * @throws OpenemsNamedException on error
	 */
	public default SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyCostPerPeriod(
			String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels,
			Resolution resolution) throws OpenemsNamedException {

		// 0. If requested resolution is finer than 1 hour, the cost channel delta
		// IS already correct at that granularity — treat like a plain energy channel.
		var requestedDuration = resolution.getUnit().getDuration().multipliedBy(resolution.getValue());
		if (requestedDuration.compareTo(java.time.Duration.ofHours(1)) < 0) {
			return this.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution);
		}

		// 1. Force the fetch to be perfectly exactly 1 Hour resolution for accurate ToU Tariffs
		var hourlyResolution = new Resolution(1, ChronoUnit.HOURS);
		var hourlyData = this.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, hourlyResolution);

		if (hourlyData == null) {
			return null;
		}

		// 2. If the requested resolution is exactly 1 Hour, no further aggregation is needed.
		if (resolution.getValue() == 1 && resolution.getUnit() == ChronoUnit.HOURS) {
			return hourlyData;
		}

		// 3. Aggregate 1-Hour cost deltas into the requested resolution buckets
		SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> aggregatedData = new java.util.TreeMap<>();

		ZonedDateTime currentBucketStart = fromDate;
		ZonedDateTime nextBucketStart = currentBucketStart.plus(resolution.getValue(), resolution.getUnit());

		SortedMap<ChannelAddress, Double> currentBucketSums = new java.util.TreeMap<>();
		SortedMap<ChannelAddress, Boolean> currentBucketHasData = new java.util.TreeMap<>();
		for (ChannelAddress channel : channels) {
			currentBucketSums.put(channel, 0.0);
			currentBucketHasData.put(channel, false);
		}

		for (java.util.Map.Entry<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> entry : hourlyData.entrySet()) {
			ZonedDateTime timestamp = entry.getKey();

			// Move to the next bucket if we've crossed the boundary
			while (!timestamp.isBefore(nextBucketStart)) {
				SortedMap<ChannelAddress, JsonElement> bucketResult = new java.util.TreeMap<>();
				for (java.util.Map.Entry<ChannelAddress, Double> sumEntry : currentBucketSums.entrySet()) {
					if (!currentBucketHasData.get(sumEntry.getKey())) {
						bucketResult.put(sumEntry.getKey(), com.google.gson.JsonNull.INSTANCE);
					} else {
						double roundedVal = Math.round(sumEntry.getValue() * 10000.0) / 10000.0;
						bucketResult.put(sumEntry.getKey(), new com.google.gson.JsonPrimitive(roundedVal));
					}
					currentBucketSums.put(sumEntry.getKey(), 0.0);
					currentBucketHasData.put(sumEntry.getKey(), false);
				}
				aggregatedData.put(currentBucketStart, bucketResult);

				currentBucketStart = nextBucketStart;
				nextBucketStart = currentBucketStart.plus(resolution.getValue(), resolution.getUnit());
			}

			// Add the 1-hour values to the current bucket
			for (java.util.Map.Entry<ChannelAddress, JsonElement> channelEntry : entry.getValue().entrySet()) {
				ChannelAddress channel = channelEntry.getKey();
				JsonElement value = channelEntry.getValue();
				if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					double hourlyCost = value.getAsDouble();
					currentBucketSums.put(channel, currentBucketSums.get(channel) + hourlyCost);
					currentBucketHasData.put(channel, true);
				}
			}
		}

		// Add the final bucket
		if (currentBucketStart.isBefore(toDate)) {
			SortedMap<ChannelAddress, JsonElement> bucketResult = new java.util.TreeMap<>();
			for (java.util.Map.Entry<ChannelAddress, Double> sumEntry : currentBucketSums.entrySet()) {
				if (!currentBucketHasData.get(sumEntry.getKey())) {
					bucketResult.put(sumEntry.getKey(), com.google.gson.JsonNull.INSTANCE);
				} else {
					double roundedVal = Math.round(sumEntry.getValue() * 10000.0) / 10000.0;
					bucketResult.put(sumEntry.getKey(), new com.google.gson.JsonPrimitive(roundedVal));
				}
			}
			aggregatedData.put(currentBucketStart, bucketResult);
		}

		return aggregatedData;
	}

	/**
	 * Queries historic energy emissions per period.
	 *
	 * <p>
	 * This method fetches the emissions channel at a forced 1-Day resolution to accurately
	 * reflect daily CO2e factors from Odoo, and then sums the daily emissions into the
	 * requested resolution bucket.
	 *
	 * @param edgeId     the Edge-ID; or null to query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the requested {@link Resolution}
	 * @return the query result; possibly null
	 * @throws OpenemsNamedException on error
	 */
	public default SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyEmissionsPerPeriod(
			String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels,
			Resolution resolution) throws OpenemsNamedException {

		// 0. If requested resolution is finer than 1 day, the emissions channel delta
		// IS already correct at that granularity — treat like a plain energy channel.
		var requestedDuration = resolution.getUnit().getDuration().multipliedBy(resolution.getValue());
		if (requestedDuration.compareTo(java.time.Duration.ofDays(1)) < 0) {
			return this.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution);
		}

		// 1. Force the fetch to be exactly 1 Day resolution —
		//    CO2e factors are configured daily, so hourly deltas would be meaningless.
		var dailyResolution = new Resolution(1, ChronoUnit.DAYS);
		var dailyData = this.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, dailyResolution);

		if (dailyData == null) {
			return null;
		}

		// 2. If the requested resolution is exactly 1 Day, no further aggregation is needed.
		if (resolution.getValue() == 1 && resolution.getUnit() == ChronoUnit.DAYS) {
			return dailyData;
		}

		// 3. Aggregate 1-Day emission deltas into the requested resolution buckets
		SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> aggregatedData = new java.util.TreeMap<>();

		ZonedDateTime currentBucketStart = fromDate;
		ZonedDateTime nextBucketStart = currentBucketStart.plus(resolution.getValue(), resolution.getUnit());

		SortedMap<ChannelAddress, Double> currentBucketSums = new java.util.TreeMap<>();
		SortedMap<ChannelAddress, Boolean> currentBucketHasData = new java.util.TreeMap<>();
		for (ChannelAddress channel : channels) {
			currentBucketSums.put(channel, 0.0);
			currentBucketHasData.put(channel, false);
		}

		for (java.util.Map.Entry<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> entry : dailyData.entrySet()) {
			ZonedDateTime timestamp = entry.getKey();

			// Move to the next bucket if we've crossed the boundary
			while (!timestamp.isBefore(nextBucketStart)) {
				SortedMap<ChannelAddress, JsonElement> bucketResult = new java.util.TreeMap<>();
				for (java.util.Map.Entry<ChannelAddress, Double> sumEntry : currentBucketSums.entrySet()) {
					if (!currentBucketHasData.get(sumEntry.getKey())) {
						bucketResult.put(sumEntry.getKey(), com.google.gson.JsonNull.INSTANCE);
					} else {
						double roundedVal = Math.round(sumEntry.getValue() * 10000.0) / 10000.0;
						bucketResult.put(sumEntry.getKey(), new com.google.gson.JsonPrimitive(roundedVal));
					}
					currentBucketSums.put(sumEntry.getKey(), 0.0);
					currentBucketHasData.put(sumEntry.getKey(), false);
				}
				aggregatedData.put(currentBucketStart, bucketResult);

				currentBucketStart = nextBucketStart;
				nextBucketStart = currentBucketStart.plus(resolution.getValue(), resolution.getUnit());
			}

			// Add the 1-day values to the current bucket
			for (java.util.Map.Entry<ChannelAddress, JsonElement> channelEntry : entry.getValue().entrySet()) {
				ChannelAddress channel = channelEntry.getKey();
				JsonElement value = channelEntry.getValue();
				if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					double dailyEmissions = value.getAsDouble();
					currentBucketSums.put(channel, currentBucketSums.get(channel) + dailyEmissions);
					currentBucketHasData.put(channel, true);
				}
			}
		}

		// Add the final bucket
		if (currentBucketStart.isBefore(toDate)) {
			SortedMap<ChannelAddress, JsonElement> bucketResult = new java.util.TreeMap<>();
			for (java.util.Map.Entry<ChannelAddress, Double> sumEntry : currentBucketSums.entrySet()) {
				if (!currentBucketHasData.get(sumEntry.getKey())) {
					bucketResult.put(sumEntry.getKey(), com.google.gson.JsonNull.INSTANCE);
				} else {
					double roundedVal = Math.round(sumEntry.getValue() * 10000.0) / 10000.0;
					bucketResult.put(sumEntry.getKey(), new com.google.gson.JsonPrimitive(roundedVal));
				}
			}
			aggregatedData.put(currentBucketStart, bucketResult);
		}

		return aggregatedData;
	}
}
