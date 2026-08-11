package org.gielinor.profilesync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(GielinorProfileSyncPlugin.CONFIG_GROUP)
public interface GielinorProfileSyncConfig extends Config
{
	@ConfigSection(
		name = "Character history captures",
		description = "Save privacy-minded scene and character records locally.",
		position = 1
	)
	String characterCaptures = "characterCaptures";

	@Range(min = 10, max = 1000)
	@ConfigItem(
		keyName = "exportIntervalTicks",
		name = "Export interval",
		description = "Game ticks between local snapshots. 100 ticks is about one minute.",
		position = 0
	)
	default int exportIntervalTicks()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "captureHotkey",
		name = "Capture scene",
		description = "Save the next rendered safe frame and its character context locally.",
		section = characterCaptures,
		position = 0
	)
	default Keybind captureHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showFramingGuide",
		name = "Show framing guide",
		description = "Show the exact UI-free area that will be saved and highlight your character.",
		section = characterCaptures,
		position = 1
	)
	default boolean showFramingGuide()
	{
		return true;
	}

	@Range(min = 5, max = 300)
	@ConfigItem(
		keyName = "recentBankSeconds",
		name = "Bank context window",
		description = "Seconds after closing a bank that a capture may be tagged as a bank scene.",
		section = characterCaptures,
		position = 2
	)
	default int recentBankSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "automaticCaptures",
		name = "Automatic history captures",
		description = "After explicit opt-in, save a scene about once per active hour when recent skill activity can label it.",
		section = characterCaptures,
		position = 3
	)
	default boolean automaticCaptures()
	{
		return false;
	}

	@Range(min = 15, max = 240)
	@ConfigItem(
		keyName = "automaticCaptureMinutes",
		name = "Automatic capture interval",
		description = "Minutes of logged-in play between automatic skill-history scenes.",
		section = characterCaptures,
		position = 4
	)
	default int automaticCaptureMinutes()
	{
		return 60;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "skillContextMinutes",
		name = "Skill context window",
		description = "How recently XP must have changed for an automatic scene to receive that skill tag.",
		section = characterCaptures,
		position = 5
	)
	default int skillContextMinutes()
	{
		return 10;
	}

	@Range(min = 30, max = 500)
	@ConfigItem(
		keyName = "captureRetention",
		name = "Local capture limit",
		description = "Maximum pending bundles; pruning preserves two bank scenes and the newest scene for every tagged skill.",
		section = characterCaptures,
		position = 6
	)
	default int captureRetention()
	{
		return 100;
	}
}
