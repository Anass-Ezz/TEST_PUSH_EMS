package io.openems.edge.common.statechannelruleengine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonObject;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.statechannelruleengine.StateChannelRule.Condition;

/**
 * Parses a JSON array string into a validated list of {@link StateChannelRule}
 * objects.
 *
 * <p>Invalid entries (unknown channelId, bad enum values, failed validation,
 * duplicate names) are silently skipped. Structurally malformed JSON causes an
 * {@link OpenemsNamedException} to be thrown.
 *
 * <p>A maximum of 50 valid rules are accepted per call.
 */
public final class StateChannelRuleParser {

	/** Maximum number of valid rules accepted per parse call. */
	private static final int MAX_RULES = 50;

	private StateChannelRuleParser() {
	}

	/**
	 * Parses a JSON array string into a list of validated
	 * {@link StateChannelRule}s.
	 *
	 * @param json     JSON array string
	 * @param eligible the eligible {@link ChannelId}s declared by the provider
	 * @return ordered list of valid rules (invalid entries are skipped)
	 * @throws OpenemsNamedException if the JSON is structurally malformed
	 */
	public static List<StateChannelRule> parse(String json, ChannelId[] eligible) throws OpenemsNamedException {
		Set<String> eligibleNames = Stream.of(eligible) //
				.map(ChannelId::name) //
				.collect(Collectors.toSet());

		var array = JsonUtils.getAsJsonArray(JsonUtils.parse(json));
		var result = new ArrayList<StateChannelRule>();
		var seenNames = new HashSet<String>();

		for (var element : array) {
			if (result.size() >= MAX_RULES) {
				break;
			}

			if (!element.isJsonObject()) {
				continue; // skip non-object array elements silently
			}
			JsonObject obj = element.getAsJsonObject();
			String channelId = JsonUtils.getAsString(obj, "channelId");
			String condStr = JsonUtils.getAsString(obj, "condition");
			double threshold = JsonUtils.getAsDouble(obj, "threshold");
			String name = JsonUtils.getAsString(obj, "name");
			String levelStr = JsonUtils.getAsString(obj, "level");
			String description = JsonUtils.getAsOptionalString(obj, "description").orElse("");

			// Skip entries with unknown channelId
			if (!eligibleNames.contains(channelId)) {
				continue;
			}

			// Skip entries with duplicate name (keep first occurrence)
			if (!seenNames.add(name)) {
				continue;
			}

			// Parse enums — skip entry on IllegalArgumentException
			Condition condition;
			Level level;
			try {
				condition = Condition.valueOf(condStr);
				level = Level.valueOf(levelStr);
			} catch (IllegalArgumentException e) {
				seenNames.remove(name); // undo the name reservation
				continue;
			}

			var rule = new StateChannelRule(channelId, condition, threshold, name, level, description);

			// Validate — skip entry if invalid
			try {
				rule.validate();
			} catch (OpenemsNamedException e) {
				seenNames.remove(name); // undo the name reservation
				continue;
			}

			result.add(rule);
		}

		return result;
	}
}
