package io.openems.edge.meter.socomec.digiware;

import io.openems.common.types.OptionsEnum;

/**
 * Load selection enum for Socomec Digiware I60 meter.
 * 
 * <p>
 * The I60 meter has 6 CT terminals that can measure up to 6 loads.
 * Each load has a complete 3-phase register map with voltages, currents, power, etc.
 */
public enum LoadSelection implements OptionsEnum {
	UNDEFINED(-1, "Undefined", 0),
	LOAD_1(1, "Load 1", 18432), // 0x4800
	LOAD_2(2, "Load 2", 20480), // 0x5000
	LOAD_3(3, "Load 3", 22528), // 0x5800
	LOAD_4(4, "Load 4", 24576), // 0x6000
	LOAD_5(5, "Load 5", 26624), // 0x6800
	LOAD_6(6, "Load 6", 28672); // 0x7000

	private final int value;
	private final String name;
	private final int baseAddress;

	private LoadSelection(int value, String name, int baseAddress) {
		this.value = value;
		this.name = name;
		this.baseAddress = baseAddress;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}

	/**
	 * Gets the Modbus base address for this load.
	 * 
	 * @return the base register address
	 */
	public int getBaseAddress() {
		return this.baseAddress;
	}

	/**
	 * Register offsets from base address (same for all loads).
	 */
	public static class Offset {
		public static final int LOAD_STATUS = 0; // +0
		public static final int DATE_OF_LAST_INSTANCE = 1; // +1
		public static final int INTEGRATION_TIME = 3; // +3
		public static final int FREQUENCY = 10; // +10
		public static final int VOLTAGE_V1 = 12; // +12
		public static final int VOLTAGE_V2 = 14; // +14
		public static final int VOLTAGE_V3 = 16; // +16
		public static final int VOLTAGE_U12 = 20; // +20
		public static final int VOLTAGE_U23 = 22; // +22
		public static final int VOLTAGE_U31 = 24; // +24
		public static final int CURRENT_I1 = 26; // +26
		public static final int CURRENT_I2 = 28; // +28
		public static final int CURRENT_I3 = 30; // +30
		public static final int CURRENT_IN = 32; // +32
		public static final int SNOM = 42; // +42
		public static final int TOTAL_ACTIVE_POWER = 44; // +44
		public static final int TOTAL_REACTIVE_POWER = 46; // +46
		public static final int TOTAL_LAGGING_REACTIVE_POWER = 48; // +48
		public static final int TOTAL_LEADING_REACTIVE_POWER = 50; // +50
		public static final int TOTAL_APPARENT_POWER = 52; // +52
		public static final int TOTAL_POWER_FACTOR = 54; // +54
		public static final int POWER_FACTOR_TYPE = 55; // +55
	}

	/**
	 * Gets the energy register base address for this load.
	 * Energy registers are in a different address space than instantaneous values.
	 * 
	 * @return the energy register base address
	 */
	public int getEnergyBaseAddress() {
		switch (this) {
		case LOAD_1:
			return 19841; // 0x4D81
		case LOAD_2:
			return 21889; // 0x5581
		case LOAD_3:
			return 23937; // 0x5D81
		case LOAD_4:
			return 25985; // 0x6581
		case LOAD_5:
			return 28033; // 0x6D81
		case LOAD_6:
			return 30081; // 0x7581
		default:
			return 0;
		}
	}

	/**
	 * Energy register offsets from energy base address.
	 */
	public static class EnergyOffset {
		public static final int TOTAL_POSITIVE_ACTIVE_ENERGY = 0; // +0 (Ea+) - U32 in kWh
		public static final int TOTAL_RESIDUAL_POSITIVE_ACTIVE_ENERGY = 2; // +2 (rEa+) - U16 in Wh x10^-1
		public static final int TOTAL_NEGATIVE_ACTIVE_ENERGY = 3; // +3 (Ea-) - U32 in kWh
		public static final int TOTAL_RESIDUAL_NEGATIVE_ACTIVE_ENERGY = 5; // +5 (rEa-) - U16 in Wh x10^-1
	}
}
