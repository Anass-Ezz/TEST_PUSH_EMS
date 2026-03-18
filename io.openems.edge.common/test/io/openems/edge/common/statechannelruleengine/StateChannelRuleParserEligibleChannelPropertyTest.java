package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.Doc;

/**
 * Property 6: Eligible-Channel Enforcement
 *
 * <p>For any generated JSON array, every rule in the parse result has a
 * {@code channelId} present in the eligible set.
 *
 * <p>Validates: Requirements 2.1, 7.1
 */
public class StateChannelRuleParserEligibleChannelPropertyTest {

	private static final int SAMPLE_SIZE = 200;
	private static final long SEED = 12345L;

	/** Eligible channel IDs used in tests. */
	private enum TestChannelId implements ChannelId {
		ACTIVE_POWER(Doc.of(io.openems.common.types.OpenemsType.INTEGER)), //
		REACTIVE_POWER(Doc.of(io.openems.common.types.OpenemsType.INTEGER)), //
		FREQUENCY(Doc.of(io.openems.common.types.OpenemsType.FLOAT));

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
	private static final Set<String> ELIGIBLE_NAMES = Set.of("ACTIVE_POWER", "REACTIVE_POWER", "FREQUENCY");

	/** All channel names including ineligible ones for mixing into generated JSON. */
	private static final String[] ALL_CHANNEL_NAMES = { "ACTIVE_POWER", "REACTIVE_POWER", "FREQUENCY",
			"INELIGIBLE_CHANNEL", "UNKNOWN_CH", "VOLTAGE_L1", "CURRENT_L1" };

	/**
	 * Property 6: Every rule returned by parse() has a channelId in the eligible
	 * set.
	 */
	@Test
	public void property6_allReturnedRulesHaveEligibleChannelId() {
		var rng = new Random(SEED);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			int arraySize = rng.nextInt(10); // 0–9 entries
			var json = buildJsonArray(rng, arraySize);

			List<StateChannelRule> rules;
			try {
				rules = StateChannelRuleParser.parse(json, ELIGIBLE);
			} catch (OpenemsNamedException e) {
				fail("parse() threw OpenemsNamedException for valid JSON: " + e.getMessage());
				return;
			}

			for (StateChannelRule rule : rules) {
				assertTrue(
						"Rule channelId [" + rule.channelId() + "] must be in eligible set, but was not. JSON: " + json,
						ELIGIBLE_NAMES.contains(rule.channelId()));
			}
		}
	}

	/**
	 * Property 6: Mix of eligible and ineligible entries — only eligible ones
	 * appear in result.
	 */
	@Test
	public void property6_ineligibleEntriesAreAlwaysFiltered() {
		var rng = new Random(SEED + 1);

		for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
			// Build array with at least one ineligible entry
			int eligibleCount = 1 + rng.nextInt(3);
			int ineligibleCount = 1 + rng.nextInt(3);
			var json = buildMixedJsonArray(rng, eligibleCount, ineligibleCount);

			List<StateChannelRule> rules;
			try {
				rules = StateChannelRuleParser.parse(json, ELIGIBLE);
			} catch (OpenemsNamedException e) {
				fail("parse() threw for valid JSON: " + e.getMessage());
				return;
			}

			for (StateChannelRule rule : rules) {
				assertTrue("Ineligible channelId [" + rule.channelId() + "] must not appear in result",
						ELIGIBLE_NAMES.contains(rule.channelId()));
			}
		}
	}

	// -------------------------------------------------------------------------
	// JSON builders
	// -------------------------------------------------------------------------

	/**
	 * Builds a JSON array with {@code size} entries using random channel names
	 * (mix of eligible and ineligible).
	 */
	private static String buildJsonArray(Random rng, int size) {
		var sb = new StringBuilder("[");
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(",");
			}
			String channelName = ALL_CHANNEL_NAMES[rng.nextInt(ALL_CHANNEL_NAMES.length)];
			String name = "RULE_" + (i + 1);
			String condition = rng.nextBoolean() ? "GREATER_THAN" : "LESS_THAN";
			double threshold = rng.nextDouble() * 1000;
			String level = pickLevel(rng);
			sb.append(buildEntry(channelName, condition, threshold, name, level, "desc"));
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Builds a JSON array with a guaranteed mix of eligible and ineligible entries.
	 */
	private static String buildMixedJsonArray(Random rng, int eligibleCount, int ineligibleCount) {
		var sb = new StringBuilder("[");
		boolean first = true;
		int nameIdx = 0;

		String[] eligibleNames = { "ACTIVE_POWER", "REACTIVE_POWER", "FREQUENCY" };
		String[] ineligibleNames = { "INELIGIBLE_CHANNEL", "UNKNOWN_CH", "VOLTAGE_L1" };

		for (int i = 0; i < eligibleCount; i++) {
			if (!first) {
				sb.append(",");
			}
			first = false;
			String ch = eligibleNames[rng.nextInt(eligibleNames.length)];
			String name = "ELIGIBLE_" + (nameIdx++);
			sb.append(buildEntry(ch, "GREATER_THAN", 100.0, name, "WARNING", ""));
		}
		for (int i = 0; i < ineligibleCount; i++) {
			if (!first) {
				sb.append(",");
			}
			first = false;
			String ch = ineligibleNames[rng.nextInt(ineligibleNames.length)];
			String name = "INELIGIBLE_" + (nameIdx++);
			sb.append(buildEntry(ch, "GREATER_THAN", 100.0, name, "WARNING", ""));
		}
		sb.append("]");
		return sb.toString();
	}

	private static String buildEntry(String channelId, String condition, double threshold, String name, String level,
			String description) {
		return "{\"channelId\":\"" + channelId + "\",\"condition\":\"" + condition + "\",\"threshold\":" + threshold
				+ ",\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"description\":\"" + description + "\"}";
	}

	private static String pickLevel(Random rng) {
		return switch (rng.nextInt(3)) {
		case 0 -> "INFO";
		case 1 -> "WARNING";
		default -> "FAULT";
		};
	}
}
