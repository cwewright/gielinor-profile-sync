package org.gielinor.profilesync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GielinorProfileSyncPlugin.CONFIG_GROUP)
public interface GielinorProfileSyncConfig extends Config
{
	@Range(min = 10, max = 1000)
	@ConfigItem(
		keyName = "exportIntervalTicks",
		name = "Export interval",
		description = "Game ticks between local snapshots. 100 ticks is about one minute."
	)
	default int exportIntervalTicks()
	{
		return 100;
	}
}
