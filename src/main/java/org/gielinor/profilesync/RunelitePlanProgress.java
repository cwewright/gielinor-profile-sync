package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Checklist notes only. Progress never changes exported bank, inventory, or game state. */
final class RunelitePlanProgress
{
	private final Gson gson;
	private final Function<String, String> read;
	private final BiConsumer<String, String> write;

	RunelitePlanProgress(Gson gson, Function<String, String> read, BiConsumer<String, String> write)
	{
		this.gson = gson;
		this.read = read;
		this.write = write;
	}

	State read(String account, String revision)
	{
		try
		{
			String json = read.apply(key(account));
			if (json != null && json.length() <= 16384)
			{
				State state = gson.fromJson(json, State.class);
				if (state != null && revision.equals(state.revision) && state.checked != null && state.manual != null
					&& state.checked.size() <= 80 && state.manual.size() <= 80)
				{
					return state;
				}
			}
		}
		catch (RuntimeException ignored) { /* Invalid notes never prevent the plan from loading. */ }
		return new State(revision, Collections.emptySet(), Collections.emptySet());
	}

	void write(String account, String revision, Set<String> checked, Set<String> manual)
	{
		write.accept(key(account), gson.toJson(new State(revision, checked, manual)));
	}

	private static String key(String account)
	{
		return "planProgress_" + account.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
	}

	static final class State
	{
		String revision;
		Set<String> checked;
		Set<String> manual;
		State(String revision, Set<String> checked, Set<String> manual)
		{
			this.revision = revision;
			this.checked = new HashSet<>(checked);
			this.manual = new HashSet<>(manual);
		}
	}
}
