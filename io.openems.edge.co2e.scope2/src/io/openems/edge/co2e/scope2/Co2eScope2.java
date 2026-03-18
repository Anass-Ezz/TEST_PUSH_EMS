package io.openems.edge.co2e.scope2;

import org.osgi.annotation.versioning.ProviderType;
import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.DoubleReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Calculates and provides the dynamically active Scope 2
 * CO2 Emissions factor in [kg CO2e / kWh].
 */
@ProviderType
public interface Co2eScope2 extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * The active, instantaneous CO2e factor synced from Odoo.
		 *
		 * <ul>
		 * <li>Interface: Co2eScope2
		 * <li>Type: Double
		 * <li>Unit: NONE (Technically [kg CO2e / kWh])
		 * </ul>
		 */
		FACTOR(Doc.of(io.openems.common.types.OpenemsType.DOUBLE)),

		/**
		 * The state of the connection between the Backend proxy and the Edge.
		 *
		 * <ul>
		 * <li>Interface: Co2eScope2
		 * <li>Type: State
		 * <li>Level: FAULT
		 * </ul>
		 */
		COMMUNICATION_FAILED(Doc.of(Level.FAULT));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
	
	public default DoubleReadChannel getFactorChannel() {
		return this.channel(ChannelId.FACTOR);
	}

	public default Value<Double> getFactor() {
		return this.getFactorChannel().value();
	}

	/**
	 * Gets the strictly active real-time emission factor [kg CO2e / kWh].
	 *
	 * @return the factor, or null if uninitialized/disconnected.
	 */
	public Double getNowFactor();

}
