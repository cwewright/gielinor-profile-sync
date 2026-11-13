package org.gielinor.profilesync;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Called on the client thread; no network or file access. */
final class RuneliteOrdersLive
{
	private RuneliteOrdersLive() { }

	static List<RuneliteOrders.Card> cards(Client client, Map<String, Object> hunter, RuneliteOrders orders, String rsn)
	{
		List<RuneliteOrders.Card> cards = tasks(SlayerTaskSnapshot.build(client, 0), hunter);
		cards.addAll(suggestions(orders, rsn, name ->
		{
			for (Quest quest : Quest.values())
			{
				if (quest.getName().equalsIgnoreCase(name)) { return quest.getState(client) == QuestState.FINISHED; }
			}
			return false;
		}, name -> client.getSkillExperience(Skill.valueOf(name.toUpperCase(Locale.ROOT))),
			name -> client.getRealSkillLevel(Skill.valueOf(name.toUpperCase(Locale.ROOT))), Instant.now()));
		return cards;
	}

	static List<RuneliteOrders.Card> suggestions(RuneliteOrders orders, String rsn, Predicate<String> finished,
		ToIntFunction<String> experience, ToIntFunction<String> level, Instant now)
	{
		List<RuneliteOrders.Card> cards = new ArrayList<>();
		if (orders == null || !orders.belongsTo(rsn) || !orders.expiresAt.isAfter(now)) { return cards; }
		for (RuneliteOrders.Card card : orders.cards)
		{
			if ("quest".equals(card.kind))
			{
				if (!finished.test(card.questName)) { cards.add(card); }
			}
			else
			{
				try
				{
					int remaining = Math.max(0, card.targetXp - experience.applyAsInt(card.skill));
					cards.add(card.withSummary(remaining == 0 ? "Milestone reached! Sync for your next ideas."
						: "Lowest skill at sync · level " + level.applyAsInt(card.skill) + " · " + String.format(Locale.US, "%,d", remaining) + " XP to your milestone"));
				}
				catch (IllegalArgumentException e) { /* Unknown skills never acquire guessed progress. */ }
			}
		}
		return cards;
	}

	static List<RuneliteOrders.Card> tasks(Map<String, Object> slayer, Map<String, Object> hunter)
	{
		List<RuneliteOrders.Card> cards = new ArrayList<>();
		Object taskObject = slayer.get("task");
		if (Boolean.TRUE.equals(slayer.get("taskLoaded")) && taskObject instanceof Map)
		{
			Map<?, ?> task = (Map<?, ?>) taskObject;
			Object monster = task.get("monster"), remaining = task.get("remaining");
			if (monster instanceof String && remaining instanceof Number && ((Number) remaining).intValue() > 0)
			{
				String area = task.get("requiredLocation") instanceof String ? "Required area: " + task.get("requiredLocation") : "No named required area observed.";
				cards.add(new RuneliteOrders.Card("slayer:" + monster, "task", "Slayer · " + monster, remaining + " remaining", Collections.singletonList(area), wiki((String) monster), null, null, 0));
			}
		}
		Object currentObject = hunter.get("current");
		if (Boolean.TRUE.equals(hunter.get("loaded")) && currentObject instanceof Map)
		{
			Map<?, ?> current = (Map<?, ?>) currentObject;
			Object rumour = current.get("rumour");
			if (Boolean.TRUE.equals(current.get("active")) && rumour instanceof String)
			{
				String name = current.get("hunterName") instanceof String ? current.get("hunterName").toString() : "Assigning hunter not observed";
				String status = Boolean.TRUE.equals(current.get("complete")) ? "Rare piece found · return to your hunter" : "Rare piece not recorded yet";
				String freshness = Boolean.TRUE.equals(hunter.get("fromCache")) ? "Retained assignment; check your whistle after changing rumours." : "Observed through Guild dialogue or your whistle.";
				cards.add(new RuneliteOrders.Card("hunter:" + rumour, "task", "Hunter · " + rumour, status, List.of(name, freshness), wiki((String) rumour), null, null, 0));
			}
		}
		return cards;
	}

	private static String wiki(String name) { return "https://oldschool.runescape.wiki/w/" + URLEncoder.encode(name.replace(' ', '_'), StandardCharsets.UTF_8); }
}
