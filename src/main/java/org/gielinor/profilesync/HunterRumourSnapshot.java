package org.gielinor.profilesync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.NpcID;

/**
 * Retains Hunter Rumour evidence observed in the game's own dialogue and
 * Quetzal whistle messages. RuneLite does not expose a complete server-backed
 * assignment variable, so unobserved masters and completion totals stay
 * unknown instead of being inferred from inventory or Hunter XP.
 */
final class HunterRumourSnapshot
{
	private static final String COVERAGE = "observed-dialogue-and-whistle";
	private static final String SOURCE = "RuneLite Hunter Guild dialogue and Quetzal whistle observations";
	private static final String RARE_PIECE_MESSAGE = "you find a rare piece of the creature! you should take it back to the hunter guild.";
	private static final List<HunterDefinition> HUNTERS = Arrays.asList(
		new HunterDefinition("wolf", NpcID.HG_WOLF, "Wolf", "Guild Hunter Wolf", "master"),
		new HunterDefinition("teco", NpcID.HG_TECO, "Teco", "Guild Hunter Teco", "expert"),
		new HunterDefinition("aco", NpcID.HG_ACO, "Aco", "Guild Hunter Aco", "expert"),
		new HunterDefinition("cervus", NpcID.HG_CERVUS, "Cervus", "Guild Hunter Cervus", "adept"),
		new HunterDefinition("ornus", NpcID.HG_ORNUS, "Ornus", "Guild Hunter Ornus", "adept"),
		new HunterDefinition("gilman", NpcID.HG_GILMAN, "Gilman", "Huntmaster Gilman", "novice")
	);
	private static final List<RumourDefinition> RUMOURS = rumourDefinitions();

	private final Map<String, Assignment> assignments = new LinkedHashMap<>();
	private String activeHunterKey;
	private boolean currentComplete;
	private boolean knownNoAssignment;
	private long lastSeenTimestamp;
	private boolean restored;

	boolean observe(ChatMessageType type, String rawMessage, boolean inHunterBurrows, long observedAt)
	{
		String message = normalized(rawMessage);
		if (message.isEmpty())
		{
			return false;
		}

		if (type == ChatMessageType.GAMEMESSAGE)
		{
			if (message.contains("your current rumour target is"))
			{
				HunterDefinition hunter = referencedHunter(message);
				RumourDefinition rumour = referencedRumour(message);
				return hunter != null && rumour != null && activate(hunter, rumour, "quetzal-whistle", observedAt);
			}
			if (message.equals(RARE_PIECE_MESSAGE) && activeHunterKey != null && !currentComplete)
			{
				currentComplete = true;
				markObserved(observedAt);
				return true;
			}
			return false;
		}

		if (type != ChatMessageType.DIALOG || !inHunterBurrows)
		{
			return false;
		}

		HunterDefinition speaker = speakingHunter(message);
		if (speaker == null)
		{
			return false;
		}
		String dialogue = message.substring(message.indexOf('|') + 1).trim();
		if (dialogue.contains("would you like another rumour?")
			|| dialogue.contains("here's your reward.")
			|| dialogue.contains("another one done?"))
		{
			if (activeHunterKey != null)
			{
				assignments.remove(activeHunterKey);
			}
			activeHunterKey = null;
			currentComplete = false;
			knownNoAssignment = true;
			markObserved(observedAt);
			return true;
		}
		if (dialogue.contains("stopped off for a bit of hunting first"))
		{
			return false;
		}

		RumourDefinition rumour = referencedRumour(dialogue);
		if (rumour == null)
		{
			return false;
		}
		HunterDefinition referenced = referencedHunter(dialogue);
		HunterDefinition assignmentHunter = referenced == null ? speaker : referenced;
		boolean noviceReassignmentOffer = dialogue.contains("would you prefer that one, or a new one entirely");
		assignments.put(assignmentHunter.key, new Assignment(assignmentHunter, rumour, "hunter-dialogue", observedAt));
		if (!noviceReassignmentOffer)
		{
			activeHunterKey = assignmentHunter.key;
			currentComplete = false;
			knownNoAssignment = false;
		}
		markObserved(observedAt);
		return true;
	}

	void restore(Object value)
	{
		if (lastSeenTimestamp > 0 || !(value instanceof Map))
		{
			return;
		}
		Map<?, ?> candidate = (Map<?, ?>) value;
		if (!Boolean.TRUE.equals(candidate.get("loaded")) || !(candidate.get("assignments") instanceof List))
		{
			return;
		}
		for (Object rawAssignment : (List<?>) candidate.get("assignments"))
		{
			if (!(rawAssignment instanceof Map))
			{
				continue;
			}
			Map<?, ?> entry = (Map<?, ?>) rawAssignment;
			HunterDefinition hunter = hunterByKey(string(entry.get("hunterKey")));
			RumourDefinition rumour = rumourByName(string(entry.get("rumour")));
			if (hunter == null || rumour == null)
			{
				continue;
			}
			long observedAt = number(entry.get("observedAt"));
			assignments.put(hunter.key, new Assignment(hunter, rumour, string(entry.get("observationSource")), observedAt));
		}
		Object rawCurrent = candidate.get("current");
		if (rawCurrent instanceof Map)
		{
			activeHunterKey = string(((Map<?, ?>) rawCurrent).get("hunterKey"));
			currentComplete = Boolean.TRUE.equals(((Map<?, ?>) rawCurrent).get("complete"));
		}
		knownNoAssignment = "none".equals(candidate.get("status"));
		lastSeenTimestamp = number(candidate.get("lastSeenTimestamp"));
		if (lastSeenTimestamp <= 0)
		{
			lastSeenTimestamp = isoMillis(string(candidate.get("lastSeenTimestampIso")));
		}
		restored = lastSeenTimestamp > 0;
	}

	void resetAccount()
	{
		assignments.clear();
		activeHunterKey = null;
		currentComplete = false;
		knownNoAssignment = false;
		lastSeenTimestamp = 0L;
		restored = false;
	}

	Map<String, Object> snapshot()
	{
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("schemaVersion", 1);
		output.put("source", SOURCE);
		output.put("sourceVersion", "hunter-rumours-observation-v1");
		output.put("coverage", COVERAGE);
		output.put("loaded", lastSeenTimestamp > 0);
		output.put("fromCache", restored && lastSeenTimestamp > 0);
		output.put("lastSeenTimestamp", lastSeenTimestamp);
		output.put("lastSeenTimestampIso", lastSeenTimestamp > 0 ? Instant.ofEpochMilli(lastSeenTimestamp).toString() : null);
		output.put("status", status());

		List<Map<String, Object>> observedAssignments = new ArrayList<>();
		for (Assignment assignment : assignments.values())
		{
			observedAssignments.add(assignment.toMap(
				assignment.hunter.key.equals(activeHunterKey),
				assignment.hunter.key.equals(activeHunterKey) && currentComplete
			));
		}
		output.put("assignments", observedAssignments);
		Assignment current = activeHunterKey == null ? null : assignments.get(activeHunterKey);
		output.put("current", current == null ? null : current.toMap(true, currentComplete));
		output.put("completionCount", null);
		output.put("unobservedAssignmentsAreUnknown", true);
		return output;
	}

	private boolean activate(HunterDefinition hunter, RumourDefinition rumour, String source, long observedAt)
	{
		Assignment previous = assignments.get(hunter.key);
		boolean changed = previous == null || !previous.rumour.name.equals(rumour.name)
			|| !hunter.key.equals(activeHunterKey) || currentComplete;
		assignments.put(hunter.key, new Assignment(hunter, rumour, source, observedAt));
		activeHunterKey = hunter.key;
		currentComplete = false;
		knownNoAssignment = false;
		markObserved(observedAt);
		return changed || observedAt > 0;
	}

	private void markObserved(long observedAt)
	{
		lastSeenTimestamp = Math.max(1L, observedAt);
		restored = false;
	}

	private String status()
	{
		if (lastSeenTimestamp <= 0)
		{
			return "unavailable";
		}
		if (activeHunterKey != null)
		{
			return currentComplete ? "complete" : "active";
		}
		return knownNoAssignment ? "none" : "unavailable";
	}

	private static HunterDefinition speakingHunter(String message)
	{
		for (HunterDefinition hunter : HUNTERS)
		{
			if (message.startsWith(hunter.speakerName.toLowerCase(Locale.ROOT) + "|"))
			{
				return hunter;
			}
		}
		return null;
	}

	private static HunterDefinition referencedHunter(String message)
	{
		for (HunterDefinition hunter : HUNTERS)
		{
			if (message.contains(hunter.name.toLowerCase(Locale.ROOT)))
			{
				return hunter;
			}
		}
		return null;
	}

	private static RumourDefinition referencedRumour(String message)
	{
		for (RumourDefinition rumour : RUMOURS)
		{
			for (String phrase : rumour.phrases)
			{
				if (message.contains(phrase))
				{
					return rumour;
				}
			}
		}
		return null;
	}

	private static HunterDefinition hunterByKey(String key)
	{
		for (HunterDefinition hunter : HUNTERS)
		{
			if (hunter.key.equals(key))
			{
				return hunter;
			}
		}
		return null;
	}

	private static RumourDefinition rumourByName(String name)
	{
		for (RumourDefinition rumour : RUMOURS)
		{
			if (rumour.name.equalsIgnoreCase(name))
			{
				return rumour;
			}
		}
		return null;
	}

	private static List<RumourDefinition> rumourDefinitions()
	{
		List<RumourDefinition> values = new ArrayList<>(Arrays.asList(
			new RumourDefinition("Barb-tailed kebbit"), new RumourDefinition("Black warlock"),
			new RumourDefinition("Carnivorous chinchompa", "red chinchompa"), new RumourDefinition("Dashing kebbit"),
			new RumourDefinition("Dark kebbit"), new RumourDefinition("Embertailed jerboa"),
			new RumourDefinition("Grey chinchompa"), new RumourDefinition("Herbiboar"),
			new RumourDefinition("Horned graahk"), new RumourDefinition("Moonlight antelope"),
			new RumourDefinition("Moonlight moth"), new RumourDefinition("Orange salamander"),
			new RumourDefinition("Prickly kebbit"), new RumourDefinition("Pyre fox"),
			new RumourDefinition("Razor-backed kebbit"), new RumourDefinition("Red salamander"),
			new RumourDefinition("Sabre-toothed kebbit"), new RumourDefinition("Sabre-toothed kyatt"),
			new RumourDefinition("Sapphire glacialis"), new RumourDefinition("Snowy knight"),
			new RumourDefinition("Spined larupia"), new RumourDefinition("Spotted kebbit"),
			new RumourDefinition("Sunlight antelope"), new RumourDefinition("Sunlight moth"),
			new RumourDefinition("Swamp lizard"), new RumourDefinition("Tecu salamander"),
			new RumourDefinition("Tropical wagtail"), new RumourDefinition("Wild kebbit"),
			new RumourDefinition("Wyrmscraig goat")
		));
		values.sort(Comparator.comparingInt((RumourDefinition value) -> value.longestPhrase()).reversed());
		return values;
	}

	private static String normalized(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]*>", "")
			.replace('’', '\'')
			.replaceAll("\\s+", " ")
			.trim()
			.toLowerCase(Locale.ROOT);
	}

	private static String string(Object value)
	{
		return value instanceof String ? (String) value : "";
	}

	private static long number(Object value)
	{
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

	private static long isoMillis(String value)
	{
		try
		{
			return value.isEmpty() ? 0L : Instant.parse(value).toEpochMilli();
		}
		catch (RuntimeException exception)
		{
			return 0L;
		}
	}

	private static final class HunterDefinition
	{
		private final String key;
		private final int npcId;
		private final String name;
		private final String speakerName;
		private final String tier;

		private HunterDefinition(String key, int npcId, String name, String speakerName, String tier)
		{
			this.key = key;
			this.npcId = npcId;
			this.name = name;
			this.speakerName = speakerName;
			this.tier = tier;
		}
	}

	private static final class RumourDefinition
	{
		private final String name;
		private final List<String> phrases;

		private RumourDefinition(String name, String... aliases)
		{
			this.name = name;
			this.phrases = new ArrayList<>();
			this.phrases.add(name.toLowerCase(Locale.ROOT));
			for (String alias : aliases)
			{
				this.phrases.add(alias.toLowerCase(Locale.ROOT));
			}
		}

		private int longestPhrase()
		{
			return phrases.stream().mapToInt(String::length).max().orElse(0);
		}
	}

	private static final class Assignment
	{
		private final HunterDefinition hunter;
		private final RumourDefinition rumour;
		private final String observationSource;
		private final long observedAt;

		private Assignment(HunterDefinition hunter, RumourDefinition rumour, String observationSource, long observedAt)
		{
			this.hunter = hunter;
			this.rumour = rumour;
			this.observationSource = observationSource == null || observationSource.isEmpty() ? "retained-observation" : observationSource;
			this.observedAt = observedAt;
		}

		private Map<String, Object> toMap(boolean active, boolean complete)
		{
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("hunterKey", hunter.key);
			value.put("hunterNpcId", hunter.npcId);
			value.put("hunterName", hunter.name);
			value.put("hunterTier", hunter.tier);
			value.put("rumour", rumour.name);
			value.put("active", active);
			value.put("complete", complete);
			value.put("observationSource", observationSource);
			value.put("observedAt", observedAt);
			return value;
		}
	}
}
