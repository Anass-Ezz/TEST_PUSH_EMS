package io.openems.common.types;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;

/**
 * Defines the type of a channel for aggregation purposes.
 *
 * <ul>
 * <li>{@link #NORMAL} — a regular instantaneous measurement (e.g.
 * ActivePower)</li>
 * <li>{@link #ENERGY} — a cumulative counter (e.g.
 * ActiveConsumptionEnergy)</li>
 * <li>{@link #COST} — a cost channel derived from energy</li>
 * </ul>
 */
public enum ChannelType {

	NORMAL, ENERGY, COST, EMISSIONS;

	/**
	 * Parses a string to a {@link ChannelType}.
	 *
	 * @param value the string value
	 * @return the {@link ChannelType}
	 * @throws OpenemsNamedException if the value is invalid
	 */
	public static ChannelType fromString(String value) throws OpenemsNamedException {
		return switch (value.toLowerCase()) {
		case "normal" -> NORMAL;
		case "energy" -> ENERGY;
		case "cost" -> COST;
		case "emissions" -> EMISSIONS;
		default -> throw new OpenemsException("Invalid channelType: '" + value + "'. Must be one of: normal, energy, cost, emissions");
		};
	}
}
