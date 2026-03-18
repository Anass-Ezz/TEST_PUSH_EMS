package io.openems.edge.common.statechannelruleengine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.statechannelruleengine.StateChannelRule.Condition;

/**
 * Internal support class for {@link StateChannelRuleEngineProvider}.
 *
 * <p>Manages the lifecycle of dynamically created {@link StateChannel}s and
 * their {@code onSetNextValue} listeners for each provider.
 */
/* package-private */ final class StateChannelRuleEngineSupport {

	private static final Logger LOG = LoggerFactory.getLogger(StateChannelRuleEngineSupport.class);

	/** Cleanup runnables (remove listener + remove channel) per provider. */
	static final WeakHashMap<StateChannelRuleEngineProvider, List<Runnable>> activeCleanups = new WeakHashMap<>();

	/** Names of dynamically created channels per provider. */
	static final WeakHashMap<StateChannelRuleEngineProvider, Set<String>> dynamicChannelNames = new WeakHashMap<>();

	private StateChannelRuleEngineSupport() {
	}

	/**
	 * Applies the given JSON rules to the provider.
	 *
	 * @param provider  the component implementing {@link StateChannelRuleEngineProvider}
	 * @param rulesJson JSON array string from OSGi config
	 */
	static void applyRules(StateChannelRuleEngineProvider provider, String rulesJson) {
		// Step 1 & 2: run existing cleanups (removes listeners and channels)
		runCleanups(provider);

		// Step 3: early exit for empty/null config
		if (rulesJson == null || rulesJson.isBlank() || "[]".equals(rulesJson.strip())) {
			return;
		}

		// Step 4: parse rules
		List<StateChannelRule> rules;
		try {
			rules = StateChannelRuleParser.parse(rulesJson, provider.getEligibleChannelIds());
		} catch (OpenemsNamedException e) {
			LOG.warn("[StateChannelRuleEngine] Failed to parse rules JSON: " + e.getMessage());
			return;
		}

		// Step 5: create channels and register listeners
		var cleanups = new ArrayList<Runnable>();
		var names = new HashSet<String>();

		for (StateChannelRule rule : rules) {
			// Create dynamic ChannelId
			var doc = Doc.of(rule.level()).text(rule.description());
			var channelId = new ChannelId.ChannelIdImpl(rule.name(), doc);

			// Add the channel via the provider interface (avoids protected-access issue)
			Channel<?> addedChannel = provider.addDynamicChannel(channelId);
			if (!(addedChannel instanceof StateChannel stateChannel)) {
				// Should not happen since Doc.of(Level) creates a StateChannelDoc
				LOG.warn("[StateChannelRuleEngine] Created channel is not a StateChannel: " + rule.name());
				provider.removeDynamicChannel(addedChannel);
				continue;
			}

			names.add(rule.name());

			// Get source channel — channelId() is UPPER_UNDERSCORE, convert to camelCase
			Channel<Object> sourceChannel;
			try {
				sourceChannel = ((io.openems.edge.common.component.OpenemsComponent) provider)
						.channel(io.openems.edge.common.channel.ChannelId.channelIdUpperToCamel(rule.channelId()));
			} catch (Exception e) {
				LOG.warn("[StateChannelRuleEngine] Source channel not found: " + rule.channelId());
				provider.removeDynamicChannel(stateChannel);
				names.remove(rule.name());
				continue;
			}

			// Register onSetNextValue listener
			Consumer<Value<Object>> listener = value -> {
				var opt = value.asOptional();
				if (opt.isEmpty()) {
					stateChannel.setNextValue(false);
					return;
				}
				double v;
				try {
					v = ((Number) opt.get()).doubleValue();
				} catch (ClassCastException ex) {
					stateChannel.setNextValue(false);
					return;
				}
				boolean result = rule.condition() == Condition.GREATER_THAN
						? v > rule.threshold()
						: v < rule.threshold();
				stateChannel.setNextValue(result);
			};

			sourceChannel.onSetNextValue(listener);

			// Store cleanup: remove listener then remove channel
			final var capturedSource = sourceChannel;
			final var capturedState = stateChannel;
			cleanups.add(() -> {
				capturedSource.removeOnSetNextValueCallback(listener);
				provider.removeDynamicChannel(capturedState);
			});
		}

		activeCleanups.put(provider, cleanups);
		dynamicChannelNames.put(provider, names);
	}

	/**
	 * Runs all cleanup runnables for the given provider and clears stored state.
	 */
	private static void runCleanups(StateChannelRuleEngineProvider provider) {
		var cleanups = activeCleanups.remove(provider);
		if (cleanups != null) {
			for (Runnable r : cleanups) {
				r.run();
			}
		}
		dynamicChannelNames.remove(provider);
	}
}
