package io.openems.common.types;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;

/**
 * Defines the aggregation function to apply.
 *
 * <p>
 * Used for both period aggregation (reducing time-series to a scalar) and
 * cross-channel aggregation (combining per-channel scalars into one result).
 */
public enum AggregationType {

	SUM, MEAN, MIN, MAX, MEDIAN;

	/**
	 * Parses a string to an {@link AggregationType}.
	 *
	 * @param value the string value
	 * @return the {@link AggregationType}
	 * @throws OpenemsNamedException if the value is invalid
	 */
	public static AggregationType fromString(String value) throws OpenemsNamedException {
		return switch (value.toLowerCase()) {
		case "sum" -> SUM;
		case "mean" -> MEAN;
		case "min" -> MIN;
		case "max" -> MAX;
		case "median" -> MEDIAN;
		default -> throw new OpenemsException(
				"Invalid aggregationType: '" + value + "'. Must be one of: sum, mean, min, max, median");
		};
	}

	/**
	 * Applies this aggregation function to a list of numbers.
	 * 
	 * @param values the list of values
	 * @return the aggregated result; 0 if the list is empty
	 */
	public Number apply(java.util.List<Number> values) {
		if (values == null || values.isEmpty()) {
			return 0;
		}
		return switch (this) {
		case SUM -> values.stream().mapToDouble(Number::doubleValue).sum();
		case MEAN -> values.stream().mapToDouble(Number::doubleValue).average().orElse(0.0);
		case MIN -> values.stream().mapToDouble(Number::doubleValue).min().orElse(0.0);
		case MAX -> values.stream().mapToDouble(Number::doubleValue).max().orElse(0.0);
		case MEDIAN -> computeMedian(values);
		};
	}

	private static Number computeMedian(java.util.List<Number> values) {
		var sorted = values.stream().mapToDouble(Number::doubleValue).sorted().toArray();
		if (sorted.length % 2 == 0) {
			return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
		}
		return sorted[sorted.length / 2];
	}
}
