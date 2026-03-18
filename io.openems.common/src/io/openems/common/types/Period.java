package io.openems.common.types;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;

/**
 * Defines the time period for aggregation.
 *
 * <p>
 * The period is a rolling window relative to the current time rather than 
 * aligning to a calendar boundary (e.g. "Last 24 Hours").
 */
public enum Period {
	LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS, TODAY, THIS_MONTH;

	/**
	 * Parses a string to a {@link Period}.
	 *
	 * @param value the string value (case-sensitive)
	 * @return the {@link Period}
	 * @throws OpenemsNamedException if the value is invalid
	 */
	public static Period fromString(String value) throws OpenemsNamedException {
		try {
			return Period.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new OpenemsException("Invalid period: '" + value + "'. Must be one of: "
					+ java.util.stream.Stream.of(Period.values()).map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
		}
	}

	/**
	 * Computes the start of the current period relative to the given time.
	 *
	 * @param now the current time (already in the correct timezone)
	 * @return the start of the period as {@link ZonedDateTime}
	 */
	public ZonedDateTime computeFromDate(ZonedDateTime now) {
		return switch (this) {
		case LAST_24_HOURS -> now.minus(24, ChronoUnit.HOURS);
		case LAST_7_DAYS -> now.minus(7, ChronoUnit.DAYS);
		case LAST_30_DAYS -> now.minus(30, ChronoUnit.DAYS);
		case TODAY -> now.truncatedTo(ChronoUnit.DAYS);
		case THIS_MONTH -> now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
		};
	}

	/**
	 * Returns true if this period supports movement percentage calculation.
	 *
	 * @return true if supported
	 */
	public boolean supportsMovementPercentage() {
		return switch (this) {
		case LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS -> true;
		case TODAY, THIS_MONTH -> false;
		};
	}

	/**
	 * Computes the start of the matching predecessor period relative to the main
	 * period's fromDate.
	 *
	 * @param fromDate the fromDate of the main period
	 * @return the start of the previous period as {@link ZonedDateTime}
	 */
	public ZonedDateTime computePreviousPeriodFromDate(ZonedDateTime fromDate) {
		return switch (this) {
		case LAST_24_HOURS -> fromDate.minus(24, ChronoUnit.HOURS);
		case LAST_7_DAYS -> fromDate.minus(7, ChronoUnit.DAYS);
		case LAST_30_DAYS -> fromDate.minus(30, ChronoUnit.DAYS);
		default -> fromDate; // Should not be called if !supportsMovementPercentage()
		};
	}

	/**
	 * Computes the end of the matching predecessor period. This is exactly
	 * equal to the fromDate of the main period.
	 *
	 * @param fromDate the fromDate of the main period
	 * @return the end of the previous period as {@link ZonedDateTime}
	 */
	public ZonedDateTime computePreviousPeriodToDate(ZonedDateTime fromDate) {
		return fromDate; // The end of the previous period touches the start of the current one
	}
}
