package io.openems.common.types;

import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

/**
 * Represents a Channel Address that includes the Edge-ID:
 * {@code edgeId/componentId/channelId}.
 *
 * <p>
 * This is used for cross-edge queries where the edge is encoded in the channel
 * address string rather than passed separately.
 */
public class EdgeChannelAddress {

	private final String edgeId;
	private final ChannelAddress channelAddress;
	private final String toString;

	public EdgeChannelAddress(String edgeId, String componentId, String channelId) {
		this(edgeId, new ChannelAddress(componentId, channelId),
				new StringBuilder(edgeId).append("/").append(componentId).append("/").append(channelId).toString());
	}

	private EdgeChannelAddress(String edgeId, ChannelAddress channelAddress, String toString) {
		this.edgeId = edgeId;
		this.channelAddress = channelAddress;
		this.toString = toString;
	}

	/**
	 * Gets the Edge-ID.
	 *
	 * @return the Edge-ID
	 */
	public String getEdgeId() {
		return this.edgeId;
	}

	/**
	 * Gets the wrapped {@link ChannelAddress} (componentId/channelId).
	 *
	 * @return the {@link ChannelAddress}
	 */
	public ChannelAddress getChannelAddress() {
		return this.channelAddress;
	}

	@Override
	public String toString() {
		return this.toString;
	}

	/**
	 * Parses a string "edgeId/componentId/channelId" to an
	 * {@link EdgeChannelAddress}.
	 *
	 * @param address the address as a String
	 * @return the {@link EdgeChannelAddress}
	 * @throws OpenemsNamedException on parse error
	 */
	public static EdgeChannelAddress fromString(String address) throws OpenemsNamedException {
		try {
			var parts = address.split("/", 3);
			if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
				throw new IllegalArgumentException("Expected format edgeId/componentId/channelId");
			}
			var edgeId = parts[0];
			var channelAddress = new ChannelAddress(parts[1], parts[2]);
			return new EdgeChannelAddress(edgeId, channelAddress, address);
		} catch (Exception e) {
			throw OpenemsError.COMMON_NO_VALID_CHANNEL_ADDRESS.exception(address);
		}
	}

	@Override
	public int hashCode() {
		return this.toString.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || this.getClass() != obj.getClass()) {
			return false;
		}
		var other = (EdgeChannelAddress) obj;
		return this.toString.equals(other.toString);
	}
}
