package io.openems.edge.meter.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.edge.common.channel.Doc;

@ProviderType
public interface ClusteredMeter extends ElectricityMeter {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public enum AggregationType {
		/**
		 * Sums up the values of all meters.
		 */
		SUM,
		/**
		 * Calculates the average value of all meters.
		 */
		AVERAGE,
		/**
		 * Takes the minimum value of all meters.
		 */
		MIN,
		/**
		 * Takes the maximum value of all meters.
		 */
		MAX;
	}

	public enum DefaultChannelId implements io.openems.edge.common.channel.ChannelId {
		// Power (Sum)
		ACTIVE_POWER(ElectricityMeter.ChannelId.ACTIVE_POWER, AggregationType.SUM), //
		REACTIVE_POWER(ElectricityMeter.ChannelId.REACTIVE_POWER, AggregationType.SUM), //
		ACTIVE_PRODUCTION_ENERGY(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, AggregationType.SUM), //
		ACTIVE_CONSUMPTION_ENERGY(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, AggregationType.SUM), //

		// Phases Power (Sum)
		ACTIVE_POWER_L1(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, AggregationType.SUM), //
		ACTIVE_POWER_L2(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, AggregationType.SUM), //
		ACTIVE_POWER_L3(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, AggregationType.SUM), //
		REACTIVE_POWER_L1(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, AggregationType.SUM), //
		REACTIVE_POWER_L2(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, AggregationType.SUM), //
		REACTIVE_POWER_L3(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, AggregationType.SUM), //

		// Current (Sum)
		CURRENT(ElectricityMeter.ChannelId.CURRENT, AggregationType.SUM), //
		CURRENT_L1(ElectricityMeter.ChannelId.CURRENT_L1, AggregationType.SUM), //
		CURRENT_L2(ElectricityMeter.ChannelId.CURRENT_L2, AggregationType.SUM), //
		CURRENT_L3(ElectricityMeter.ChannelId.CURRENT_L3, AggregationType.SUM), //

		// Voltage (Average)
		VOLTAGE(ElectricityMeter.ChannelId.VOLTAGE, AggregationType.AVERAGE), //
		VOLTAGE_L1(ElectricityMeter.ChannelId.VOLTAGE_L1, AggregationType.AVERAGE), //
		VOLTAGE_L2(ElectricityMeter.ChannelId.VOLTAGE_L2, AggregationType.AVERAGE), //
		VOLTAGE_L3(ElectricityMeter.ChannelId.VOLTAGE_L3, AggregationType.AVERAGE), //

		// Frequency (Average)
		FREQUENCY(ElectricityMeter.ChannelId.FREQUENCY, AggregationType.AVERAGE), //

		// Cost and Emissions (Sum)
		ACTIVE_CONSUMPTION_ENERGY_COST(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_COST, AggregationType.SUM), //
		ACTIVE_CONSUMPTION_ENERGY_EMISSIONS(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_EMISSIONS, AggregationType.SUM);

		private final io.openems.edge.common.channel.ChannelId channelId;
		private final AggregationType aggregationType;

		private DefaultChannelId(io.openems.edge.common.channel.ChannelId channelId, AggregationType aggregationType) {
			this.channelId = channelId;
			this.aggregationType = aggregationType;
		}

		@Override
		public Doc doc() {
			return this.channelId.doc();
		}
		
		public io.openems.edge.common.channel.ChannelId getTargetChannelId() {
			return this.channelId;
		}

		public AggregationType getAggregationType() {
			return this.aggregationType;
		}
	}
}
