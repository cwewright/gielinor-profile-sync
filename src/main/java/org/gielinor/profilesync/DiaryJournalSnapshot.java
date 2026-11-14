package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.util.Text;

/** Observes journal text only; it never opens interfaces or guesses task identities from counts. */
final class DiaryJournalSnapshot
{
	private static final List<String> REGIONS = Arrays.asList("ardougne", "desert", "falador", "fremennik",
		"kandarin", "karamja", "kourend_kebos", "lumbridge_draynor", "morytania", "varrock",
		"western_provinces", "wilderness");
	private final Map<String, Object> journals = new LinkedHashMap<>();

	void resetAccount()
	{
		journals.clear();
	}

	void observe(String windowTitle, List<String> rows, long now)
	{
		if (!plain(windowTitle).startsWith("Achievement Diary") || rows.isEmpty() || rows.size() > 250)
		{
			return;
		}
		String title = plain(rows.get(0)).toLowerCase(Locale.ROOT);
		String region = REGIONS.stream().filter(key -> title.startsWith(key.split("_")[0])).findFirst().orElse(null);
		if (region == null)
		{
			return;
		}
		Map<String, Object> tiers = new LinkedHashMap<>();
		List<Map<String, Object>> lines = null;
		for (String row : rows)
		{
			String text = plain(row);
			String tier = text.toLowerCase(Locale.ROOT);
			if (Arrays.asList("easy", "medium", "hard", "elite").contains(tier))
			{
				lines = new ArrayList<>();
				tiers.put(tier, lines);
			}
			else if (lines != null && !text.isEmpty() && text.length() <= 1000)
			{
				Map<String, Object> line = new LinkedHashMap<>();
				line.put("text", text);
				// Requirements can contain their own <str>. Only a strike before the task text counts.
				line.put("complete", row.replaceAll("<col=[^>]*>", "").trim().startsWith("<str>"));
				lines.add(line);
			}
		}
		if (!tiers.isEmpty())
		{
			Map<String, Object> journal = new LinkedHashMap<>();
			journal.put("observedAt", now);
			journal.put("tiers", tiers);
			journals.put(region, journal);
		}
	}

	void restore(Object saved)
	{
		if (!(saved instanceof Map))
		{
			return;
		}
		Map<?, ?> source = (Map<?, ?>) saved;
		for (String region : REGIONS)
		{
			Object journal = source.get(region);
			if (journal instanceof Map && !journals.containsKey(region))
			{
				journals.put(region, journal);
			}
		}
	}

	Map<String, Object> snapshot()
	{
		return new LinkedHashMap<>(journals);
	}

	private static String plain(String text)
	{
		return text == null ? "" : Text.removeTags(text.replace("<br>", " ")).replace('\u00a0', ' ').trim();
	}
}
