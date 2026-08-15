package org.gielinor.profilesync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts a separate RuneLite development client with Gielinor Profile Sync
 * loaded as a built-in external plug-in.
 */
public final class GielinorProfileSyncPreview
{
	private GielinorProfileSyncPreview()
	{
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GielinorProfileSyncPlugin.class);
		RuneLite.main(args);
	}
}
