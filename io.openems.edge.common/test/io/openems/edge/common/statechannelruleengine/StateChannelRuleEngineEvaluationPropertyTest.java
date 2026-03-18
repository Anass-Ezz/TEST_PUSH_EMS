package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;

/**
 * Property 3: Evaluation Correctness
 *
 * <p>For arbitrary finite doubles {@code v} and {@code t}:
 * <ul>
 * <li>With {@code GREATER_THAN}: state channel is {@code true} iff {@code v > t}</li>
 * <li>With {@code LESS_THAN}: state channel is {@code true} iff {@code v < t}</li>
 * </ul>
 *
 * <p>Validates: Requirements 4.1, 4.2
 */
public class StateChannelRuleEngineEvaluationPropertyTest {

	private static final int SAMPLE_SIZE = 200;
	private static final long SEED = 55551L;

	// -------------------------------------------------------------------------
	// Dummy component
	// -------------------------------------------------------------------------

	private enum TestChannelId implements io.openems.edge.common.channel.ChannelId {
		SOURCE(Doc.of(OpenemsType.DOUBLE));

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
	// Property tests
	// -------------------------------------------------------------------------

	/**
	 * Property 3a: GREATER_THAN — state is true iff v > threshold.
	 */
	@Test
	public void property3a_greaterThanEvaluationCorrectness() {
		var rng = new Random(SEED);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			double threshold = (rng.nextDouble() - 0.5) * 2000;
			double value = (rng.nextDouble() - 0.5) * 2000;
			boolean expected = value > threshold;

			var provider = new DummyProvider();
			String json = buildJson("SOURCE", "GREATER_THAN", threshold, "MYRULE", "WARNING");
			StateChannelRuleEngineSupport.applyRules(provider, json);

			// Trigger the listener by setting the source channel value
			Channel<Double> source = provider.channel(TestChannelId.SOURCE);
			source.setNextValue(value);

			// Read the state channel
			StateChannel stateChannel = (StateChannel) provider._channel("MyRule");
			boolean actual = stateChannel.getNextValue().orElse(false);

			assertEquals("GREATER_THAN: v=" + value + " t=" + threshold + " expected=" + expected,
					expected, actual);

			StateChannelRuleEngineSupport.applyRules(provider, "[]");
		}
	}

	/**
	 * Property 3b: LESS_THAN — state is true iff v < threshold.
	 */
	@Test
	public void property3b_lessThanEvaluationCorrectness() {
		var rng = new Random(SEED + 1);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			double threshold = (rng.nextDouble() - 0.5) * 2000;
			double value = (rng.nextDouble() - 0.5) * 2000;
			boolean expected = value < threshold;

			var provider = new DummyProvider();
			String json = buildJson("SOURCE", "LESS_THAN", threshold, "MYRULE", "FAULT");
			StateChannelRuleEngineSupport.applyRules(provider, json);

			Channel<Double> source = provider.channel(TestChannelId.SOURCE);
			source.setNextValue(value);

			StateChannel stateChannel = (StateChannel) provider._channel("MyRule");
			boolean actual = stateChannel.getNextValue().orElse(false);

			assertEquals("LESS_THAN: v=" + value + " t=" + threshold + " expected=" + expected,
					expected, actual);

			StateChannelRuleEngineSupport.applyRules(provider, "[]");
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String buildJson(String channelId, String condition, double threshold, String name, String level) {
		return "[{\"channelId\":\"" + channelId + "\",\"condition\":\"" + condition + "\",\"threshold\":" + threshold
				+ ",\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"description\":\"test\"}]";
	}
}
