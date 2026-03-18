package io.openems.edge.meter.cluster;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.meter.api.ClusteredMeter;
import io.openems.edge.meter.api.ElectricityMeter;

public interface ClusteredMeterDevice extends ClusteredMeter, ElectricityMeter {
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
}
