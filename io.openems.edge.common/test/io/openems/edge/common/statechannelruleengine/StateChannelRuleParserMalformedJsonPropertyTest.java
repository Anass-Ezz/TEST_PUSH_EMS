package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.Doc;

/**
 * Property 10: Malformed JSON Produces No Channels
 *
 * <p>Passing structurally invalid JSON strings to {@code parse()} always throws
 * {@link OpenemsNamedException}.
 *
 * <p>Validates: Requirements 6.1, 6.4
 */
public class StateChannelRuleParserMalformedJsonPropertyTest {

	private static final int RANDOM_SAMPLE_SIZE = 200;
	private static final long SEED = 99999L;

	private enum TestChannelId implements ChannelId {
		ACTIVE_POWER(Doc.of(io.openems.common.types.OpenemsType.INTEGER));

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

	/**
	 * Fixed set of known-malformed JSON strings — these are structurally invalid
	 * (not parseable as a JSON array).
	 */
	private static final String[] KNOWN_MALFORMED = { //
			"{}", // object, not array
			"null", // null literal
			"true", // boolean
			"42", // number
			"\"hello\"", // string
			"{\"key\":\"value\"}", // object
			"[{\"unclosed\":true}", // unclosed array
			"[{\"key\":}]", // invalid value
			"[{\"key\":\"val\",}]", // trailing comma
			"not json at all", // plain text
			"", // empty string
			"   ", // whitespace only
			"[", // incomplete array
			"{", // incomplete object
			"[{\"channelId\":\"ACTIVE_POWER\",\"condition\":\"GREATER_THAN\"", // truncated
	};

	/**
	 * Property 10: All known-malformed JSON strings throw OpenemsNamedException.
	 */
	@Test
	public void property10_knownMalformedJsonAlwaysThrows() {
		for (String malformed : KNOWN_MALFORMED) {
			assertThrowsOpenemsNamedException(malformed);
		}
	}

	/**
	 * Property 10: Randomly generated non-JSON strings always throw
	 * OpenemsNamedException.
	 */
	@Test
	public void property10_randomNonJsonStringsAlwaysThrow() {
		var rng = new Random(SEED);
		for (int i = 0; i < RANDOM_SAMPLE_SIZE; i++) {
			String nonJson = generateNonJsonString(rng);
			assertThrowsOpenemsNamedException(nonJson);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static void assertThrowsOpenemsNamedException(String json) {
		try {
			StateChannelRuleParser.parse(json, ELIGIBLE);
			fail("Expected OpenemsNamedException for malformed JSON: [" + json + "]");
		} catch (OpenemsNamedException e) {
			// expected — property holds
		}
	}

	/**
	 * Generates a random string that is not valid JSON (no leading '[' or '{').
	 */
	private static String generateNonJsonString(Random rng) {
		int strategy = rng.nextInt(4);
		return switch (strategy) {
		case 0 -> generateRandomAlphanumeric(rng, 1 + rng.nextInt(20));
		case 1 -> generateRandomPunctuation(rng, 1 + rng.nextInt(10));
		case 2 -> generatePartialJson(rng);
		default -> generateMixedGarbage(rng, 1 + rng.nextInt(15));
		};
	}

	private static String generateRandomAlphanumeric(Random rng, int length) {
		var chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
		var sb = new StringBuilder(length);
		// Ensure it doesn't start with '[' or '{' to guarantee it's not a JSON array/object
		sb.append((char) ('a' + rng.nextInt(26)));
		for (int i = 1; i < length; i++) {
			sb.append(chars.charAt(rng.nextInt(chars.length())));
		}
		return sb.toString();
	}

	private static String generateRandomPunctuation(Random rng, int length) {
		var chars = "!@#$%^&*()-+=|;':\",./<>?~`";
		var sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(rng.nextInt(chars.length())));
		}
		return sb.toString();
	}

	private static String generatePartialJson(Random rng) {
		// Truncated JSON objects/arrays that are not valid
		String[] partials = { "[{\"key\":", "[{\"key\":\"val\"", "{\"a\":1", "[{\"a\":1},{" };
		return partials[rng.nextInt(partials.length)];
	}

	private static String generateMixedGarbage(Random rng, int length) {
		var chars = "abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=";
		var sb = new StringBuilder(length);
		// Start with a letter to avoid accidentally forming valid JSON
		sb.append((char) ('a' + rng.nextInt(26)));
		for (int i = 1; i < length; i++) {
			sb.append(chars.charAt(rng.nextInt(chars.length())));
		}
		return sb.toString();
	}
}
