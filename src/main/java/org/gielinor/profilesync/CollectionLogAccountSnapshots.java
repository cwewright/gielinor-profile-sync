package org.gielinor.profilesync;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps partial Collection Log observations scoped to the RuneScape account
 * that produced them. Switching accounts must never discard or cross-pollinate
 * observations.
 */
final class CollectionLogAccountSnapshots
{
	private final Map<String, Object> snapshotsByAccount = new LinkedHashMap<>();
	private CollectionLogSnapshot active = new CollectionLogSnapshot();
	private String activeAccount = "";

	synchronized CollectionLogSnapshot activate(String rsn)
	{
		String account = accountKey(rsn);
		if (account.equals(activeAccount))
		{
			return active;
		}
		preserveActive();
		activeAccount = account;
		active = new CollectionLogSnapshot();
		active.restore(snapshotsByAccount.get(account));
		return active;
	}

	synchronized CollectionLogSnapshot current()
	{
		return active;
	}

	synchronized void merge(String rsn, Object rawSnapshot)
	{
		String account = accountKey(rsn);
		CollectionLogSnapshot merged = new CollectionLogSnapshot();
		merged.restore(rawSnapshot);
		merged.restore(snapshotsByAccount.get(account));
		if (account.equals(activeAccount))
		{
			merged.restore(active.toMap());
			active = merged;
		}
		snapshotsByAccount.put(account, merged.toMap());
	}

	synchronized void deactivate()
	{
		preserveActive();
		activeAccount = "";
		active = new CollectionLogSnapshot();
	}

	static Object mergeForExport(Object storedSnapshot, Object currentSnapshot)
	{
		CollectionLogSnapshot merged = new CollectionLogSnapshot();
		merged.restore(storedSnapshot);
		merged.restore(currentSnapshot);
		return merged.toMap();
	}

	private void preserveActive()
	{
		if (!activeAccount.isEmpty())
		{
			snapshotsByAccount.put(activeAccount, active.toMap());
		}
	}

	private static String accountKey(String rsn)
	{
		return rsn == null ? "" : rsn.trim().toLowerCase(Locale.ROOT);
	}
}
