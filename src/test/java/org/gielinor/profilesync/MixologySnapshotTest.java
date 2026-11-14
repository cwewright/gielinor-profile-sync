package org.gielinor.profilesync;

import java.util.Map;
import java.util.Arrays;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MixologySnapshotTest
{
	@Test
	public void exportsPointsSeparatelyFromStoredPaste()
	{
		Map<Integer, Integer> points = Map.of(VarPlayerID.MIXOLOGY_MOX_POINTS, 726,
			VarPlayerID.MIXOLOGY_AGA_POINTS, 960, VarPlayerID.MIXOLOGY_LYE_POINTS, 771);
		Map<Integer, Integer> paste = Map.of(VarbitID.MM_AVAILABLE_MOX, 100,
			VarbitID.MM_AVAILABLE_AGA, 200, VarbitID.MM_AVAILABLE_LYE, 300);
		Map<String, Object> snapshot = MixologySnapshot.build(points::get, paste::get, 1000);
		assertEquals(true, snapshot.get("loaded"));
		assertEquals(Map.of("mox", 726, "aga", 960, "lye", 771), snapshot.get("rewardPoints"));
		assertEquals(Map.of("mox", 100, "aga", 200, "lye", 300), snapshot.get("hopperPaste"));
		assertEquals("1970-01-01T00:00:01Z", snapshot.get("observedAt"));
	}

	@Test
	public void preservesRealZeroBalancesAndDoesNotReuseAnotherSnapshot()
	{
		MixologySnapshot.build(id -> 100, id -> 200, 1000);
		Map<String, Object> empty = MixologySnapshot.build(id -> 0, id -> 0, 2000);
		assertEquals(true, empty.get("loaded"));
		assertEquals(Map.of("mox", 0, "aga", 0, "lye", 0), empty.get("hopperPaste"));
		assertEquals(Map.of("mox", 0, "aga", 0, "lye", 0), empty.get("rewardPoints"));
	}

	@Test
	public void unavailableReadsNeverInventEmptyBalances()
	{
		for (Map<String, Object> snapshot : Arrays.asList(
			MixologySnapshot.build(id -> -1, id -> 0, 1000),
			MixologySnapshot.build(id -> 726, id -> { throw new IllegalStateException(); }, 1000)))
		{
			assertEquals(false, snapshot.get("loaded"));
			assertFalse(snapshot.containsKey("rewardPoints"));
			assertFalse(snapshot.containsKey("hopperPaste"));
			assertFalse(snapshot.containsKey("observedAt"));
		}
	}
}
