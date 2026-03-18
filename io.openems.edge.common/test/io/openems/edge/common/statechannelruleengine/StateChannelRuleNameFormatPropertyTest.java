package io.openems.edge.common.statechannelruleengine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;
import java.util.regex.Pattern;

import org.junit.Test;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statechannelruleengine.StateChannelRule.Condition;

/**
 * Property 7: Name Format Invariant
 *
 * <p>For any {@link StateChannelRule}, {@code validate()} must succeed if and
 * only if the {@code name} field matches the regex {@code [A-Z][A-Z0-9_]*}.
 *
 * <p>Validates: Requirements 2.7, 7.2
 */
public class StateChannelRuleNameFormatPropertyTest {

	private static final Pattern VALID_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
	private static final int SAMPLE_SIZE = 500;
	private static final long SEED = 42L;

	/**
	 * Property 7: Name Format Invariant — valid names always pass validate().
	 *
	 * <p>For any string matching {@code [A-Z][A-Z0-9_]*}, {@code validate()} must
	 * not throw an exception.
	 */
	@Test
	public void property7_validNameAlwaysPassesValidate() {
		var rng = new Random(SEED);
		for (int i = 0; i < SAMPLE_SIZE; i++) {
			var name = generateValidName(rng);
			assertTrue("Generated name should match pattern: " + name, VALID_NAME.matcher(name).matches());
			var rule = new StateChannelRule("CH", Condition.GREATER_THAN, 0.0, name, Level.WARNING, "");
			try {
				rule.validate();
				// expected: no exception
			} catch (OpenemsNamedException e) {
				fail("validate() threw for valid name [" + name + "]: " + e.getMessage());
			}
		}
	}

	/**
	 * Property 7: Name Format Invariant — invalid names always fail validate().
	 *
	 * <p>For any string NOT matching {@code [A-Z][A-Z0-9_]*}, {@code validate()}
	 * must throw {@link OpenemsNamedException}.
	 */
	@Test
	public void property7_invalidNameAlwaysFailsValidate() {
		var rng = new Random(SEED + 1);
		for (int i = 0; i < SAMPLE_SIZE; i++) {
			var name = generateInvalidName(rng);
			assertFalse("Generated name should NOT match pattern: " + name, VALID_NAME.matcher(name).matches());
			var rule = new StateChannelRule("CH", Condition.GREATER_THAN, 0.0, name, Level.WARNING, "");
			try {
				rule.validate();
				fail("validate() should have thrown for invalid name [" + name + "]");
			} catch (OpenemsNamedException e) {
				// expected
			}
		}
	}

	/**
	 * Property 7: Name Format Invariant — null name always fails validate().
	 */
	@Test
	public void property7_nullNameAlwaysFailsValidate() {
		var rule = new StateChannelRule("CH", Condition.GREATER_THAN, 0.0, null, Level.WARNING, "");
		try {
			rule.validate();
			fail("validate() should have thrown for null name");
		} catch (OpenemsNamedException e) {
			// expected
		}
	}

	/**
	 * Property 7: Name Format Invariant — empty string always fails validate().
	 */
	@Test
	public void property7_emptyNameAlwaysFailsValidate() {
		var rule = new StateChannelRule("CH", Condition.GREATER_THAN, 0.0, "", Level.WARNING, "");
		try {
			rule.validate();
			fail("validate() should have thrown for empty name");
		} catch (OpenemsNamedException e) {
			// expected
		}
	}

	// -------------------------------------------------------------------------
	// Generators
	// -------------------------------------------------------------------------

	/**
	 * Generates a random string matching {@code [A-Z][A-Z0-9_]*} (length 1–20).
	 */
	private static String generateValidName(Random rng) {
		var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		var bodyChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
		int length = 1 + rng.nextInt(20);
		var sb = new StringBuilder(length);
		sb.append(chars.charAt(rng.nextInt(chars.length())));
		for (int i = 1; i < length; i++) {
			sb.append(bodyChars.charAt(rng.nextInt(bodyChars.length())));
		}
		return sb.toString();
	}

	/**
	 * Generates a random string that does NOT match {@code [A-Z][A-Z0-9_]*}.
	 * Strategies: starts with lowercase, digit, underscore, or contains spaces /
	 * special characters.
	 */
	private static String generateInvalidName(Random rng) {
		int strategy = rng.nextInt(5);
		return switch (strategy) {
		case 0 -> generateLowercaseStart(rng); // starts with lowercase letter
		case 1 -> generateDigitStart(rng); // starts with digit
		case 2 -> generateUnderscoreStart(rng); // starts with underscore
		case 3 -> generateWithSpecialChars(rng); // contains special chars
		default -> generateWithSpaces(rng); // contains spaces
		};
	}

	private static String generateLowercaseStart(Random rng) {
		var lower = "abcdefghijklmnopqrstuvwxyz";
		var body = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
		int length = 1 + rng.nextInt(10);
		var sb = new StringBuilder();
		sb.append(lower.charAt(rng.nextInt(lower.length())));
		for (int i = 1; i < length; i++) {
			sb.append(body.charAt(rng.nextInt(body.length())));
		}
		return sb.toString();
	}

	private static String generateDigitStart(Random rng) {
		return rng.nextInt(10) + "VALID_SUFFIX";
	}

	private static String generateUnderscoreStart(Random rng) {
		var body = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
		int length = 1 + rng.nextInt(10);
		var sb = new StringBuilder("_");
		for (int i = 0; i < length; i++) {
			sb.append(body.charAt(rng.nextInt(body.length())));
		}
		return sb.toString();
	}

	private static String generateWithSpecialChars(Random rng) {
		var specials = "!@#$%^&*()-+=[]{}|;':\",./<>?";
		var base = "VALID";
		int pos = rng.nextInt(base.length() + 1);
		char special = specials.charAt(rng.nextInt(specials.length()));
		return base.substring(0, pos) + special + base.substring(pos);
	}

	private static String generateWithSpaces(Random rng) {
		var base = "VALID";
		int pos = 1 + rng.nextInt(base.length());
		return base.substring(0, pos) + " " + base.substring(pos);
	}
}
