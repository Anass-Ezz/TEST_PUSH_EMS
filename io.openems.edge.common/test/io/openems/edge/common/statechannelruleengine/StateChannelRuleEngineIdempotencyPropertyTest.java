package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;

/**
 * Property 9: Idempotency of applyRules
 *
 * <p>Calling {@code applyRules(provider, json)} twice with the same JSON
 * produces the same set of dynamic channel names as calling it once.
 *
 * <p>Validates: Requirement 3.2
 */
public class StateChannelRuleEngineIdempotencyPropertyTest {

	private static final int SAMPLE_SIZE = 100;
	private static final long SEED = 99991L;

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
	 * Property 9: applying the same JSON twice yields the same channel name set.
	 */
	@Test
	public void property9_applyRulesTwiceYieldsSameChannels() {
		var rng = new Random(SEED);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			var provider = new DummyProvider();
			int ruleCount = rng.nextInt(5); // 0–4 rules
			var json = buildJson(rng, ruleCount);

			// First application
			StateChannelRuleEngineSupport.applyRules(provider, json);
			Set<String> afterFirst = dynamicChannelNames(provider);

			// Second application with same JSON
			StateChannelRuleEngineSupport.applyRules(provider, json);
			Set<String> afterSecond = dynamicChannelNames(provider);

			assertEquals("Idempotency violated for JSON: " + json, afterFirst, afterSecond);

			// Cleanup
			StateChannelRuleEngineSupport.applyRules(provider, "[]");
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static Set<String> dynamicChannelNames(DummyProvider provider) {
		var names = StateChannelRuleEngineSupport.dynamicChannelNames.get(provider);
		if (names == null) {
			return Set.of();
		}
		return Set.copyOf(names);
	}

	private static String buildJson(Random rng, int count) {
		if (count == 0) {
			return "[]";
		}
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
