package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.statechannelruleengine.StateChannelRule.Condition;

/**
 * Property 8: Parser Round-Trip
 *
 * <p>Generate valid {@link StateChannelRule} objects, serialize to JSON, parse
 * back; assert equality of all fields including {@code threshold} precision.
 *
 * <p>Validates: Requirements 8.1, 8.3
 */
public class StateChannelRuleParserRoundTripPropertyTest {

	private static final int SAMPLE_SIZE = 200;
	private static final long SEED = 77777L;

	private enum TestChannelId implements ChannelId {
		ACTIVE_POWER(Doc.of(io.openems.common.types.OpenemsType.INTEGER)), //
		REACTIVE_POWER(Doc.of(io.openems.common.types.OpenemsType.INTEGER)), //
		FREQUENCY(Doc.of(io.openems.common.types.OpenemsType.FLOAT)), //
		VOLTAGE_L1(Doc.of(io.openems.common.types.OpenemsType.FLOAT)), //
		CURRENT_L1(Doc.of(io.openems.common.types.OpenemsType.FLOAT));

		private final Doc doc;

		TestChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	private static final ChannelId[] ELIGIBLE = TestChannelId.values();
	private static final String[] ELIGIBLE_NAMES = { "ACTIVE_POWER", "REACTIVE_POWER", "FREQUENCY", "VOLTAGE_L1",
			"CURRENT_L1" };
	private static final Condition[] CONDITIONS = Condition.values();
	private static final Level[] LEVELS = { Level.INFO, Level.WARNING, Level.FAULT };

	/**
	 * Property 8: A single valid rule survives a serialize → parse round-trip with
	 * all fields equal.
	 */
	@Test
	public void property8_singleRuleRoundTrip() {
		var rng = new Random(SEED);

		for (int i = 0; i < SAMPLE_SIZE; i++) {
			StateChannelRule original = generateValidRule(rng, "RULE_" + i);
			String json = serializeToJsonArray(List.of(original));

			List<StateChannelRule> parsed;
			try {
				parsed = StateChannelRuleParser.parse(json, ELIGIBLE);
			} catch (OpenemsNamedException e) {
				fail("parse() threw for valid serialized rule: " + e.getMessage() + " | JSON: " + json);
				return;
			}

			assertEquals("Expected exactly 1 rule after round-trip", 1, parsed.size());
			assertRulesEqual(original, parsed.get(0));
		}
	}

	/**
	 * Property 8: Multiple valid rules survive a serialize → parse round-trip with
	 * all fields equal and order preserved.
	 */
	@Test
	public void property8_multipleRulesRoundTrip() {
		var rng = new Random(SEED + 1);

		for (int sample = 0; sample < 50; sample++) {
			int count = 1 + rng.nextInt(10);
			var originals = new java.util.ArrayList<StateChannelRule>(count);
			for (int i = 0; i < count; i++) {
				originals.add(generateValidRule(rng, "RULE_" + sample + "_" + i));
			}

			String json = serializeToJsonArray(originals);

			List<StateChannelRule> parsed;
			try {
				parsed = StateChannelRuleParser.parse(json, ELIGIBLE);
			} catch (OpenemsNamedException e) {
				fail("parse() threw for valid serialized rules: " + e.getMessage());
				return;
			}

			assertEquals("Rule count must match after round-trip", originals.size(), parsed.size());
			for (int i = 0; i < originals.size(); i++) {
				assertRulesEqual(originals.get(i), parsed.get(i));
			}
		}
	}

	/**
	 * Property 8: Threshold precision is preserved exactly (IEEE 754 double
	 * round-trip).
	 */
	@Test
	public void property8_thresholdPrecisionPreserved() {
		var rng = new Random(SEED + 2);

		for (int i = 0; i < SAMPLE_SIZE; i++) {
			// Use a precise double value
			double threshold = rng.nextDouble() * 1e9 - 5e8; // range [-5e8, 5e8]
			var rule = new StateChannelRule("ACTIVE_POWER", Condition.GREATER_THAN, threshold, "RULE_" + i,
					Level.WARNING, "desc");

			String json = serializeToJsonArray(List.of(rule));
			List<StateChannelRule> parsed;
			try {
				parsed = StateChannelRuleParser.parse(json, ELIGIBLE);
			} catch (OpenemsNamedException e) {
				fail("parse() threw: " + e.getMessage());
				return;
			}

			assertEquals("Threshold must be preserved exactly", threshold, parsed.get(0).threshold(), 0.0);
		}
	}

	// -------------------------------------------------------------------------
	// Serializer
	// -------------------------------------------------------------------------

	/**
	 * Serializes a list of {@link StateChannelRule}s to a JSON array string using
	 * Gson.
	 */
	static String serializeToJsonArray(List<StateChannelRule> rules) {
		var array = new JsonArray();
		for (StateChannelRule rule : rules) {
			array.add(serializeRule(rule));
		}
		return array.toString();
	}

	/**
	 * Serializes a single {@link StateChannelRule} to a Gson {@link JsonObject}.
	 */
	static JsonObject serializeRule(StateChannelRule rule) {
		var obj = new JsonObject();
		obj.addProperty("channelId", rule.channelId());
		obj.addProperty("condition", rule.condition().name());
		obj.addProperty("threshold", rule.threshold());
		obj.addProperty("name", rule.name());
		obj.addProperty("level", rule.level().name());
		obj.addProperty("description", rule.description());
		return obj;
	}

	// -------------------------------------------------------------------------
	// Generators
	// -------------------------------------------------------------------------

	private static StateChannelRule generateValidRule(Random rng, String name) {
		String channelId = ELIGIBLE_NAMES[rng.nextInt(ELIGIBLE_NAMES.length)];
		Condition condition = CONDITIONS[rng.nextInt(CONDITIONS.length)];
		double threshold = rng.nextDouble() * 10000 - 5000;
		Level level = LEVELS[rng.nextInt(LEVELS.length)];
		String description = "Description for " + name;
		return new StateChannelRule(channelId, condition, threshold, name, level, description);
	}

	// -------------------------------------------------------------------------
	// Assertions
	// -------------------------------------------------------------------------

	private static void assertRulesEqual(StateChannelRule expected, StateChannelRule actual) {
		assertEquals("channelId mismatch", expected.channelId(), actual.channelId());
		assertEquals("condition mismatch", expected.condition(), actual.condition());
		assertEquals("threshold mismatch", expected.threshold(), actual.threshold(), 0.0);
		assertEquals("name mismatch", expected.name(), actual.name());
		assertEquals("level mismatch", expected.level(), actual.level());
		assertEquals("description mismatch", expected.description(), actual.description());
	}
}
