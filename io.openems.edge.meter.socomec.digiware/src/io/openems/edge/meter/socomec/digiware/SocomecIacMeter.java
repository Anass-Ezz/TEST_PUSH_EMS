package io.openems.edge.meter.socomec.digiware;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;

public interface SocomecIacMeter extends ElectricityMeter, OpenemsComponent, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		NO_SOCOMEC_METER(Doc.of(Level.FAULT) //
				.text("This is not a Socomec meter")), //
		UNKNOWN_SOCOMEC_METER(Doc.of(Level.FAULT) //
				.text("Unable to identify Socomec meter")), //
		UNKNOWN_LOAD_TABLE(Doc.of(Level.FAULT) //
				.text("Load table doesn't exist for this Identified meter")), //
		LOAD_TABLE_NOT_ENABLED(Doc.of(Level.FAULT) //
				.text("Load table isn't enabled for this Identified meter")),
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
}
