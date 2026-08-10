package org.gielinor.profilesync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GielinorProfileSyncPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GielinorProfileSyncPlugin.class);
		RuneLite.main(args);
	}
}
