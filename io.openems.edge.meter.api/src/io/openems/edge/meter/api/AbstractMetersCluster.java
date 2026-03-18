package io.openems.edge.meter.api;

import java.util.ArrayList;
import java.util.List;


import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;

public abstract class AbstractMetersCluster extends AbstractOpenemsComponent
		implements ClusteredMeter, EventHandler {

	private final Logger log = LoggerFactory.getLogger(AbstractMetersCluster.class);

	private String[] meterIds = new String[0];
	private ComponentManager componentManager;

	protected AbstractMetersCluster(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);
	}

	protected void activate(ComponentContext context, String id, String alias, boolean enabled, String[] meterIds,
			ComponentManager componentManager) {
		super.activate(context, id, alias, enabled);
		this.meterIds = meterIds != null ? meterIds : new String[0];
		this.componentManager = componentManager;
	}

	@Override
	protected void modified(ComponentContext context, String id, String alias, boolean enabled) {
		super.modified(context, id, alias, enabled);
	}

	protected void modified(ComponentContext context, String id, String alias, boolean enabled, String[] meterIds,
			ComponentManager componentManager) {
		super.modified(context, id, alias, enabled);
		this.meterIds = meterIds != null ? meterIds : new String[0];
		this.componentManager = componentManager;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.calculateAggregation();
			break;
		}
	}

	private void calculateAggregation() {
		// 1. Global Component Check
		List<ElectricityMeter> meters = new ArrayList<>();
		for (String meterId : this.meterIds) {
			try {
				ElectricityMeter meter = this.componentManager.getComponent(meterId);
				if (meter == null) {
					this.channel(OpenemsComponent.ChannelId.STATE).setNextValue(Level.FAULT);
					this.logError(this.log, "Meter " + meterId + " is missing. Aborting aggregation.");
					return;
				}
				meters.add(meter);
			} catch (OpenemsNamedException e) {
				this.channel(OpenemsComponent.ChannelId.STATE).setNextValue(Level.FAULT);
				this.logError(this.log, "Meter " + meterId + " is missing. Aborting aggregation.");
				return;
			}
		}

		// 2. Global State Check
		for (ElectricityMeter meter : meters) {
			if (meter instanceof OpenemsComponent) {
				OpenemsComponent component = (OpenemsComponent) meter;
				Channel<Integer> stateChannel = component.channel(OpenemsComponent.ChannelId.STATE);
				Level state = Level.fromValue(stateChannel.value().orElse(Level.OK.getValue())).orElse(Level.OK);
				if (state != Level.OK) {
					this.channel(OpenemsComponent.ChannelId.STATE).setNextValue(Level.WARNING);
					this.logWarn(this.log, "Meter " + component.id() + " is in " + state.name() + " state. Aborting aggregation.");
					return;
				}
			}
		}

		// 3. Per-Channel Aggregation
		for (ClusteredMeter.DefaultChannelId def : ClusteredMeter.DefaultChannelId.values()) {
			io.openems.edge.common.channel.ChannelId channelId = def.getTargetChannelId();
			AggregationType type = def.getAggregationType();
			List<Object> values = new ArrayList<>();
			boolean channelValid = true;

			for (ElectricityMeter meter : meters) {
				if (meter instanceof OpenemsComponent) {
					OpenemsComponent component = (OpenemsComponent) meter;
					try {
						// Check if the channel exists on the target component
						io.openems.edge.common.channel.Channel<?> channel = component.channel(channelId.id());
						Object value = channel.value().get();
						if (value != null) {
							values.add(value);
						} else {
							// Value is null -> Skip this channel entirely
							channelValid = false;
							break;
						}
					} catch (IllegalArgumentException e) {
						// Channel not found -> Skip this channel entirely
						channelValid = false;
						break;
					}
				}
			}

			// Calculate Result
			Object result = null;
			if (channelValid && !values.isEmpty()) {
				switch (type) {
				case SUM:
					result = this.sum(values);
					break;
				case AVERAGE:
					result = this.average(values);
					break;
				case MIN:
					result = this.min(values);
					break;
				case MAX:
					result = this.max(values);
					break;
				}
			}

			// Set Value
			this.channel(channelId).setNextValue(result);
		}
		
		// If we reached here, everything is OK
		this.channel(OpenemsComponent.ChannelId.STATE).setNextValue(Level.OK);
	}

	private Double sum(List<Object> values) {
		double sum = 0;
		for (Object v : values) {
			if (v instanceof Number) {
				sum += ((Number) v).doubleValue();
			}
		}
		return sum;
	}

	private Double average(List<Object> values) {
		if (values.isEmpty()) {
			return null;
		}
		Double sum = this.sum(values);
		return sum / values.size();
	}

	private Double min(List<Object> values) {
		if (values.isEmpty()) return null;
		double min = Double.MAX_VALUE;
		boolean found = false;
		for (Object v : values) {
			if (v instanceof Number) {
				min = Math.min(min, ((Number) v).doubleValue());
				found = true;
			}
		}
		return found ? min : null;
	}

    private Double max(List<Object> values) {
		if (values.isEmpty()) return null;
		double max = Double.MIN_VALUE;
		boolean found = false;
		for (Object v : values) {
			if (v instanceof Number) {
				max = Math.max(max, ((Number) v).doubleValue());
				found = true;
			}
		}
		return found ? max : null;
	}
}
