package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.statechannelruleengine.StateChannelRule.Condition;

/**
 * Unit tests for {@link StateChannelRuleParser}.
 *
 * <p>Validates: Requirements 2.1–2.10, 7.1–7.5, 8.2
 */
public class StateChannelRuleParserTest {

	// -------------------------------------------------------------------------
	// Test ChannelId enum
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Test 1: Valid array returns correct rules in order
	// -------------------------------------------------------------------------

	@Test
	public void validArrayReturnsCorrectRulesInOrder() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":5000.0,
				   "name":"HIGH_POWER","level":"WARNING","description":"Power too high"},
				  {"channelId":"FREQUENCY","condition":"LESS_THAN","threshold":49.5,
				   "name":"LOW_FREQ","level":"FAULT","description":"Frequency low"}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(2, rules.size());

		var r0 = rules.get(0);
		assertEquals("ACTIVE_POWER", r0.channelId());
		assertEquals(Condition.GREATER_THAN, r0.condition());
		assertEquals(5000.0, r0.threshold(), 0.0);
		assertEquals("HIGH_POWER", r0.name());
		assertEquals(Level.WARNING, r0.level());
		assertEquals("Power too high", r0.description());

		var r1 = rules.get(1);
		assertEquals("FREQUENCY", r1.channelId());
		assertEquals(Condition.LESS_THAN, r1.condition());
		assertEquals(49.5, r1.threshold(), 0.0);
		assertEquals("LOW_FREQ", r1.name());
		assertEquals(Level.FAULT, r1.level());
		assertEquals("Frequency low", r1.description());
	}

	// -------------------------------------------------------------------------
	// Test 2: Unknown channelId entries are skipped, valid ones returned
	// -------------------------------------------------------------------------

	@Test
	public void unknownChannelIdEntriesAreSkipped() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"UNKNOWN_CHANNEL","condition":"GREATER_THAN","threshold":100.0,
				   "name":"UNKNOWN_RULE","level":"WARNING","description":""},
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":1000.0,
				   "name":"VALID_RULE","level":"INFO","description":"Valid"}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("VALID_RULE", rules.get(0).name());
		assertEquals("ACTIVE_POWER", rules.get(0).channelId());
	}

	// -------------------------------------------------------------------------
	// Test 3: Malformed JSON throws OpenemsNamedException
	// -------------------------------------------------------------------------

	@Test
	public void malformedJsonThrowsOpenemsNamedException() {
		assertThrows(OpenemsNamedException.class, () -> StateChannelRuleParser.parse("not json", ELIGIBLE));
		assertThrows(OpenemsNamedException.class, () -> StateChannelRuleParser.parse("{}", ELIGIBLE));
		assertThrows(OpenemsNamedException.class, () -> StateChannelRuleParser.parse("[{unclosed", ELIGIBLE));
	}

	// -------------------------------------------------------------------------
	// Test 4: Duplicate name keeps first, drops second
	// -------------------------------------------------------------------------

	@Test
	public void duplicateNameKeepsFirstDropsSecond() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":1000.0,
				   "name":"DUPLICATE_NAME","level":"WARNING","description":"First"},
				  {"channelId":"REACTIVE_POWER","condition":"LESS_THAN","threshold":500.0,
				   "name":"DUPLICATE_NAME","level":"FAULT","description":"Second"}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("DUPLICATE_NAME", rules.get(0).name());
		assertEquals("ACTIVE_POWER", rules.get(0).channelId());
		assertEquals("First", rules.get(0).description());
	}

	// -------------------------------------------------------------------------
	// Test 5: 51-rule array returns exactly 50 rules
	// -------------------------------------------------------------------------

	@Test
	public void fiftyOneRulesReturnsExactlyFifty() throws OpenemsNamedException {
		var sb = new StringBuilder("[");
		for (int i = 0; i < 51; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\",\"threshold\":");
			sb.append(i * 10.0);
			sb.append(",\"name\":\"RULE_");
			sb.append(i);
			sb.append("\",\"level\":\"WARNING\",\"description\":\"\"}");
		}
		sb.append("]");

		List<StateChannelRule> rules = StateChannelRuleParser.parse(sb.toString(), ELIGIBLE);

		assertEquals(50, rules.size());
	}

	// -------------------------------------------------------------------------
	// Test 6: Missing optional description defaults to empty string
	// -------------------------------------------------------------------------

	@Test
	public void missingDescriptionDefaultsToEmptyString() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"NO_DESC_RULE","level":"INFO"}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("", rules.get(0).description());
	}

	// -------------------------------------------------------------------------
	// Additional edge cases
	// -------------------------------------------------------------------------

	@Test
	public void emptyArrayReturnsEmptyList() throws OpenemsNamedException {
		List<StateChannelRule> rules = StateChannelRuleParser.parse("[]", ELIGIBLE);
		assertEquals(0, rules.size());
	}

	@Test
	public void invalidConditionStringIsSkipped() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"INVALID_COND","threshold":100.0,
				   "name":"BAD_COND","level":"WARNING","description":""},
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"GOOD_RULE","level":"WARNING","description":""}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("GOOD_RULE", rules.get(0).name());
	}

	@Test
	public void invalidLevelStringIsSkipped() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"BAD_LEVEL","level":"CRITICAL","description":""},
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"GOOD_RULE","level":"FAULT","description":""}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("GOOD_RULE", rules.get(0).name());
	}

	@Test
	public void invalidNamePatternIsSkipped() throws OpenemsNamedException {
		var json = """
				[
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"invalid_name","level":"WARNING","description":""},
				  {"channelId":"ACTIVE_POWER","condition":"GREATER_THAN","threshold":100.0,
				   "name":"VALID_NAME","level":"WARNING","description":""}
				]
				""";

		List<StateChannelRule> rules = StateChannelRuleParser.parse(json, ELIGIBLE);

		assertEquals(1, rules.size());
		assertEquals("VALID_NAME", rules.get(0).name());
	}
}
