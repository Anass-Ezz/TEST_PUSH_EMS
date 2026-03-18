package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.Collectors;

import org.junit.Test;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;

/**
 * Unit tests for {@link StateChannelRuleEngineSupport}.
 */
public class StateChannelRuleEngineSupportTest {

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
	// Tests
	// -------------------------------------------------------------------------

	/**
	 * Test: channels are created and registered after applyRules() with valid JSON.
	 */
	@Test
	public void test_channelsCreatedAfterApplyRules() {
		var provider = new DummyProvider();
		String json = "[{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\","
				+ "\"threshold\":1000.0,\"name\":\"HIGH_POWER\",\"level\":\"WARNING\","
				+ "\"description\":\"Power is high\"}]";

		StateChannelRuleEngineSupport.applyRules(provider, json);

		// Channel should be registered (name in UPPER_CAMEL = "HighPower")
		Channel<?> ch = provider._channel("HighPower");
		assertNotNull("Dynamic channel HIGH_POWER should be registered", ch);
		assertTrue("Dynamic channel should be a StateChannel", ch instanceof StateChannel);
	}

	/**
	 * Test: previous channels are removed on second applyRules() call.
	 */
	@Test
	public void test_previousChannelsRemovedOnSecondApply() {
		var provider = new DummyProvider();
		String json1 = "[{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\","
				+ "\"threshold\":1000.0,\"name\":\"HIGH_POWER\",\"level\":\"WARNING\","
				+ "\"description\":\"\"}]";
		String json2 = "[{\"channelId\":\"REACTIVE_POWER\",\"condition\":\"LESS_THAN\","
				+ "\"threshold\":0.0,\"name\":\"LOW_REACTIVE\",\"level\":\"INFO\","
				+ "\"description\":\"\"}]";

		StateChannelRuleEngineSupport.applyRules(provider, json1);
		assertNotNull("HIGH_POWER should exist after first apply", provider._channel("HighPower"));

		StateChannelRuleEngineSupport.applyRules(provider, json2);
		assertNull("HIGH_POWER should be removed after second apply", provider._channel("HighPower"));
		assertNotNull("LOW_REACTIVE should exist after second apply", provider._channel("LowReactive"));
	}

	/**
	 * Test: applyRules(provider, "[]") removes all dynamic channels.
	 */
	@Test
	public void test_emptyJsonRemovesAllDynamicChannels() {
		var provider = new DummyProvider();
		String json = "[{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\","
				+ "\"threshold\":500.0,\"name\":\"OVERLOAD\",\"level\":\"FAULT\","
				+ "\"description\":\"\"}]";

		StateChannelRuleEngineSupport.applyRules(provider, json);
		assertNotNull("OVERLOAD should exist", provider._channel("Overload"));

		StateChannelRuleEngineSupport.applyRules(provider, "[]");
		assertNull("OVERLOAD should be removed after clearing", provider._channel("Overload"));
	}

	/**
	 * Test: malformed JSON leaves zero dynamic channels registered.
	 */
	@Test
	public void test_malformedJsonLeavesNoChannels() {
		var provider = new DummyProvider();

		StateChannelRuleEngineSupport.applyRules(provider, "not valid json {{{");

		var names = StateChannelRuleEngineSupport.dynamicChannelNames.get(provider);
		assertTrue("No dynamic channels should be registered after malformed JSON",
				names == null || names.isEmpty());
	}

	/**
	 * Test: onSetNextValue listener sets state channel to false when source value
	 * is undefined (null).
	 */
	@Test
	public void test_undefinedSourceValueSetsFalse() {
		var provider = new DummyProvider();
		String json = "[{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\","
				+ "\"threshold\":100.0,\"name\":\"HIGH_POWER\",\"level\":\"WARNING\","
				+ "\"description\":\"\"}]";

		StateChannelRuleEngineSupport.applyRules(provider, json);

		// Set source to a value first so the listener fires
		Channel<Integer> source = provider.channel(TestChannelId.ACTIVE_POWER);
		source.setNextValue(200); // triggers true

		StateChannel stateChannel = (StateChannel) provider._channel("HighPower");
		assertTrue("State should be true when value > threshold", stateChannel.getNextValue().orElse(false));

		// Now set to null (undefined)
		source.setNextValue(null);
		assertFalse("State should be false when source value is undefined",
				stateChannel.getNextValue().orElse(true));
	}

	/**
	 * Test: two rules on different channels both work independently.
	 */
	@Test
	public void test_multipleRulesWorkIndependently() {
		var provider = new DummyProvider();
		String json = "["
				+ "{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\","
				+ "\"threshold\":1000.0,\"name\":\"HIGH_POWER\",\"level\":\"WARNING\",\"description\":\"\"},"
				+ "{\"channelId\":\"FREQUENCY\",\"condition\":\"LESS_THAN\","
				+ "\"threshold\":49.5,\"name\":\"LOW_FREQ\",\"level\":\"FAULT\",\"description\":\"\"}"
				+ "]";

		StateChannelRuleEngineSupport.applyRules(provider, json);

		Channel<Integer> power = provider.channel(TestChannelId.ACTIVE_POWER);
		Channel<Float> freq = provider.channel(TestChannelId.FREQUENCY);

		power.setNextValue(1500); // > 1000 → true
		freq.setNextValue(50.0f); // not < 49.5 → false

		StateChannel highPower = (StateChannel) provider._channel("HighPower");
		StateChannel lowFreq = (StateChannel) provider._channel("LowFreq");

		assertTrue("HIGH_POWER should be true", highPower.getNextValue().orElse(false));
		assertFalse("LOW_FREQ should be false", lowFreq.getNextValue().orElse(true));
	}
}
