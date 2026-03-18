package io.openems.edge.common.statechannelruleengine;

import java.util.regex.Pattern;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

/**
 * Represents a single threshold-based state channel rule.
 *
 * <p>Each rule maps a source channel to a threshold condition, severity level,
 * name, and description. When the source channel value crosses the threshold,
 * the corresponding dynamic {@code StateChannel} is set to {@code true}.
 *
 * @param channelId   the ID of the source channel (must be in eligible set)
 * @param condition   the comparison condition ({@code GREATER_THAN} or {@code LESS_THAN})
 * @param threshold   the threshold value (must be a finite IEEE 754 double)
 * @param name        the name for the dynamic StateChannel (must match {@code [A-Z][A-Z0-9_]*})
 * @param level       the severity level ({@code INFO}, {@code WARNING}, or {@code FAULT})
 * @param description optional human-readable description for the StateChannel
 */
public record StateChannelRule(
		String channelId,
		Condition condition,
		double threshold,
		String name,
		Level level,
		String description) {

	/** Pattern that valid rule names must match. */
	private static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

	/**
	 * The comparison condition for a threshold rule.
	 */
	public enum Condition {
		/** The source value must be strictly greater than the threshold. */
		GREATER_THAN,
		/** The source value must be strictly less than the threshold. */
		LESS_THAN
	}

	/**
	 * Validates this rule's fields.
	 *
	 * @throws OpenemsNamedException if:
	 *                               <ul>
	 *                               <li>{@code name} does not match
	 *                               {@code [A-Z][A-Z0-9_]*}</li>
	 *                               <li>{@code threshold} is non-finite (NaN or
	 *                               Infinity)</li>
	 *                               <li>{@code condition} is null</li>
	 *                               <li>{@code level} is null</li>
	 *                               </ul>
	 */
	public void validate() throws OpenemsNamedException {
		if (this.condition == null) {
			throw OpenemsError.GENERIC.exception("StateChannelRule condition must not be null");
		}
		if (this.level == null) {
			throw OpenemsError.GENERIC.exception("StateChannelRule level must not be null");
		}
		if (!Double.isFinite(this.threshold)) {
			throw OpenemsError.GENERIC
					.exception("StateChannelRule threshold must be a finite number, got: " + this.threshold);
		}
		if (this.name == null || !NAME_PATTERN.matcher(this.name).matches()) {
			throw OpenemsError.GENERIC.exception(
					"StateChannelRule name [" + this.name + "] does not match pattern [A-Z][A-Z0-9_]*");
		}
	}
}
