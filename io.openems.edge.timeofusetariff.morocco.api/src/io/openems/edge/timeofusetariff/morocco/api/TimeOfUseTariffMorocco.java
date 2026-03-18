package io.openems.edge.timeofusetariff.morocco.api;

import io.openems.edge.common.channel.DoubleReadChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.channel.Doc;

public interface TimeOfUseTariffMorocco extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		PRICE_PEAK(Doc.of(io.openems.common.types.OpenemsType.DOUBLE) //
				.text("Price during Peak Hours (Heures de pointe)")), //
		PRICE_STANDARD(Doc.of(io.openems.common.types.OpenemsType.DOUBLE) //
				.text("Price during Standard Hours (Heures pleines)")), //
		PRICE_OFF_PEAK(Doc.of(io.openems.common.types.OpenemsType.DOUBLE) //
				.text("Price during Off-Peak Hours (Heures creuses)"));

		private final io.openems.edge.common.channel.Doc doc;

		private ChannelId(io.openems.edge.common.channel.Doc doc) {
			this.doc = doc;
		}

		@Override
		public io.openems.edge.common.channel.Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the current price based on the time of day and season.
	 *
	 * @return the current price in Double format
	 */
	
	public default DoubleReadChannel getPricePeakChannel() {
		return this.channel(ChannelId.PRICE_PEAK);
	}
	
	public default DoubleReadChannel getPriceStandardChannel() {
		return this.channel(ChannelId.PRICE_STANDARD);
	}
	
	public default DoubleReadChannel getPriceOffPeakChannel() {
		return this.channel(ChannelId.PRICE_OFF_PEAK);
	}
	
	
	public Double getNowPrice();

}
