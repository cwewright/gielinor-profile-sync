package org.gielinor.profilesync;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reward currency and hopper contents are distinct from banked herbs or paste items. */
final class MixologySnapshot
{
	private MixologySnapshot()
	{
	}

	static Map<String, Object> build(IntUnaryOperator varp, IntUnaryOperator varbit, long now)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schemaVersion", 1);
		result.put("source", "RuneLite Mastering Mixology reward and hopper variables");
		try
		{
			Map<String, Integer> points = balances(varp, VarPlayerID.MIXOLOGY_MOX_POINTS,
				VarPlayerID.MIXOLOGY_AGA_POINTS, VarPlayerID.MIXOLOGY_LYE_POINTS);
			Map<String, Integer> hopper = balances(varbit, VarbitID.MM_AVAILABLE_MOX,
				VarbitID.MM_AVAILABLE_AGA, VarbitID.MM_AVAILABLE_LYE);
			result.put("loaded", true);
			result.put("observedAt", Instant.ofEpochMilli(now).toString());
			result.put("rewardPoints", points);
			result.put("hopperPaste", hopper);
		}
		catch (RuntimeException unavailable)
		{
			result.put("loaded", false);
		}
		return result;
	}

	private static Map<String, Integer> balances(IntUnaryOperator read, int mox, int aga, int lye)
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		result.put("mox", read.applyAsInt(mox));
		result.put("aga", read.applyAsInt(aga));
		result.put("lye", read.applyAsInt(lye));
		if (result.values().stream().anyMatch(value -> value < 0))
		{
			throw new IllegalStateException("Mixology balances unavailable");
		}
		return result;
	}
}
