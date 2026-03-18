package io.openems.edge.meter.socomec.digiware.iac;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerDoc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.socomec.digiware.SocomecIacMeter;

/**
 * Socomec Digiware IAC Multi-Load Meter.
 *
 * <p>
 * Supports I-60 (6 load tables), I-30 and I-35 (3 load tables).
 * Each instance represents one load selected via configuration.
 */
public interface MeterSocomecDigiwareIac extends SocomecIacMeter, ElectricityMeter, OpenemsComponent, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Neutral Current In.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * </ul>
		 */
		CURRENT_IN(new IntegerDoc() //
				.unit(Unit.MILLIAMPERE)),

		/**
		 * Nominal Apparent Power (Snom).
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE}
		 * </ul>
		 */
		SNOM(new IntegerDoc() //
				.unit(Unit.VOLT_AMPERE)),

		/**
		 * Total Lagging Reactive Power.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		TOTAL_LAGGING_REACTIVE_POWER(new IntegerDoc() //
				.unit(Unit.VOLT_AMPERE_REACTIVE)),

		/**
		 * Total Leading Reactive Power.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		TOTAL_LEADING_REACTIVE_POWER(new IntegerDoc() //
				.unit(Unit.VOLT_AMPERE_REACTIVE)),

		/**
		 * Total Power Factor (/1000).
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Range: -1000 to 1000 (divide by 1000 for actual value)
		 * </ul>
		 */
		TOTAL_POWER_FACTOR(Doc.of(OpenemsType.INTEGER)),

		/**
		 * Power Factor Type.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Range: 0=undefined, 1=leading, 2=lagging
		 * </ul>
		 */
		POWER_FACTOR_TYPE(Doc.of(OpenemsType.INTEGER));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}
