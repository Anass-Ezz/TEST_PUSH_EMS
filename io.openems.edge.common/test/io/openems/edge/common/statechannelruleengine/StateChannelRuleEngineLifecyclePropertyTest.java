package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertFalse;

import java.util.Random;
import java.util.Set;

import org.junit.Test;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;

/**
 * Property 4: Channel Lifecycle — No Leaks
 *
 * <p>After calling {@code applyRules(provider, json)} followed by
 * {@code applyRules(provider, "[]")}, every previously created dynamic channel
 * is no longer registered on the component.
 *
 * <p>Validates: Requirements 3.3, 3.5
 */
public class StateChannelRuleEngineLifecyclePropertyTest {

	private static final int SAMPLE_SIZE = 100;
	private static final long SEED = 77771L;

	// -------------------------------------------------------------------------
	// Dummy component
	// -------------------------------------------------------------------------

	private enum TestChannelId implements io.openems.edge.common.channel.ChannelId {
		ACTIVE_POWER(Doc.of(OpenemsType.INTEGER)), //
		REACTIVE_POWER(Doc.of(OpenemsType.INTEGER)), //
		FREQUENCY(Doc.of(OpenemsType.FLOAT));

		private final Doc doc;

		TestChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	private static class DummyProvider extends AbstractDummyOpenemsComponent<DummyProvider>
			implements StateChannelRuleEngineProvider, OpenemsComponent {

		DummyProvider() {
			super("dummy0", OpenemsComponent.ChannelId.values(), TestChannelId.values());
		}

		@Override
		public io.openems.edge.common.channel.ChannelId[] getEligibleChannelIds() {
			return TestChannelId.values();
		}

		@Override
		public io.openems.edge.common.channel.Channel<?> addDynamicChannel(
				io.openems.edge.common.channel.ChannelId channelId) {
			return this.addChannel(channelId);
		}

		@Override
		public void removeDynamicChannel(io.openems.edge.common.channel.Channel<?> channel) {
			this.removeChannel(channel);
		}

		@Override
		protected DummyProvider self() {
			return this;
		}
	}

	// -------------------------------------------------------------------------
	// Property test
	// -------------------------------------------------------------------------

	/**
	 * Property 4: after applyRules then applyRules("[]"), no dynamic channels
	 * remain registered on the component.
	 */
	@Test
	public void property4_noChannelLeaksAfterClear() {
		var rng = new Random(SEED);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			var provider = new DummyProvider();
			int ruleCount = 1 + rng.nextInt(4); // 1–4 rules
			var json = buildJson(rng, ruleCount);

			// Apply rules
			StateChannelRuleEngineSupport.applyRules(provider, json);

			// Capture the names that were created
			Set<String> createdNames = Set.copyOf(
					StateChannelRuleEngineSupport.dynamicChannelNames.getOrDefault(provider, Set.of()));

			// Clear all rules
			StateChannelRuleEngineSupport.applyRules(provider, "[]");

			// Assert none of the previously created channels are still registered
			var channelIds = provider.channels().stream()
					.map(ch -> ch.channelId().name())
					.collect(java.util.stream.Collectors.toSet());

			for (String name : createdNames) {
				assertFalse("Channel [" + name + "] should have been removed but is still registered",
						channelIds.contains(name));
			}
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String buildJson(Random rng, int count) {
		var sb = new StringBuilder("[");
		String[] channels = { "ACTIVE_POWER", "REACTIVE_POWER", "FREQUENCY" };
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(",");
			}
			String ch = channels[rng.nextInt(channels.length)];
			String name = "RULE_" + (i + 1);
			String cond = rng.nextBoolean() ? "GREATER_THAN" : "LESS_THAN";
			double threshold = rng.nextDouble() * 500;
			String level = pickLevel(rng);
			sb.append("{\"channelId\":\"").append(ch)
					.append("\",\"condition\":\"").append(cond)
					.append("\",\"threshold\":").append(threshold)
					.append(",\"name\":\"").append(name)
					.append("\",\"level\":\"").append(level)
					.append("\",\"description\":\"desc\"}");
		}
		sb.append("]");
		return sb.toString();
	}

	private static String pickLevel(Random rng) {
		return switch (rng.nextInt(3)) {
		case 0 -> "INFO";
		case 1 -> "WARNING";
		default -> "FAULT";
		};
	}
}
