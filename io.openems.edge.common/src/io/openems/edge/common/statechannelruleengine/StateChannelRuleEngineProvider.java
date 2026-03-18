package io.openems.edge.common.statechannelruleengine;

import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;

/**
 * Implemented by any {@link io.openems.edge.common.component.OpenemsComponent} that exposes
 * channels eligible for threshold-based dynamic StateChannel generation.
 *
 * <p>Usage in {@code activate()} / {@code modified()}:
 *
 * <pre>
 *   StateChannelRuleEngineProvider.applyRules(this, config.stateChannelRules());
 * </pre>
 *
 * <p>Usage in {@code deactivate()} (pass {@code "[]"} to remove all dynamic channels):
 *
 * <pre>
 *   StateChannelRuleEngineProvider.applyRules(this, "[]");
 *   super.deactivate();
 * </pre>
 */
public interface StateChannelRuleEngineProvider {

	/**
	 * Returns the ChannelIds that may be used as inputs for state channel rules.
	 * Typically a subset of the component's {@code ChannelId} enum values. All
	 * returned channels must be numeric (INTEGER, LONG, FLOAT, or DOUBLE).
	 *
	 * @return array of eligible ChannelIds (must be numeric channels)
	 */
	ChannelId[] getEligibleChannelIds();

	/**
	 * Adds a dynamic channel to this component. Implemented by the host component
	 * (which extends {@link io.openems.edge.common.component.AbstractOpenemsComponent})
	 * by delegating to its protected {@code addChannel(ChannelId)} method.
	 *
	 * @param channelId the ChannelId to add
	 * @return the newly created Channel
	 */
	Channel<?> addDynamicChannel(ChannelId channelId);

	/**
	 * Removes a dynamic channel from this component. Implemented by the host
	 * component by delegating to its protected {@code removeChannel(Channel<?>)}
	 * method.
	 *
	 * @param channel the Channel to remove
	 */
	void removeDynamicChannel(Channel<?> channel);

	/**
	 * Parses the JSON rules string, removes previously created dynamic channels and
	 * their {@code onSetNextValue} listeners, then creates new StateChannels and
	 * registers event-driven listeners on each source channel.
	 *
	 * <p>Call this in {@code activate()}, {@code modified()}, and
	 * {@code deactivate()} (with {@code "[]"} on deactivate).
	 *
	 * @param provider  the component implementing this interface (pass {@code this})
	 * @param rulesJson JSON array string from OSGi config
	 */
	static void applyRules(StateChannelRuleEngineProvider provider, String rulesJson) {
		StateChannelRuleEngineSupport.applyRules(provider, rulesJson);
	}
}
