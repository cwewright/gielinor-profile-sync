package org.gielinor.profilesync;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class SlayerTaskSnapshotTest
{
	private static final long OBSERVED_AT = 1_786_089_600_000L;

	@Test
	@SuppressWarnings("unchecked")
	public void exportsAProvableLocationBoundAssignment()
	{
		FakeSource source = new FakeSource();
		source.varps.put(VarPlayerID.SLAYER_COUNT, 87);
		source.varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 143);
		source.varps.put(VarPlayerID.SLAYER_TARGET, 42);
		source.varps.put(VarPlayerID.SLAYER_AREA, 12);
		source.varbits.put(VarbitID.SLAYER_MASTER, 9);
		source.varbits.put(VarbitID.SLAYER_POINTS, 325);
		source.varbits.put(VarbitID.SLAYER_TASKS_COMPLETED, 64);
		source.taskRows.put(42, 4200);
		source.fields.put(key(4200, DBTableID.SlayerTask.COL_NAME_UPPERCASE), new Object[]{"Jellies"});
		source.areaRows.put(12, 1200);
		source.fields.put(key(1200, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER), new Object[]{"Karuulm Slayer Dungeon"});

		Map<String, Object> snapshot = SlayerTaskSnapshot.build(source, OBSERVED_AT);
		Map<String, Object> task = (Map<String, Object>) snapshot.get("task");

		assertEquals(true, snapshot.get("taskLoaded"));
		assertEquals(false, snapshot.get("taskFromCache"));
		assertEquals(Instant.ofEpochMilli(OBSERVED_AT).toString(), snapshot.get("taskLastSeenTimestamp"));
		assertEquals("live", snapshot.get("status"));
		assertEquals(325, snapshot.get("points"));
		assertEquals(64, snapshot.get("streak"));
		assertEquals("Jellies", task.get("monster"));
		assertEquals(87, task.get("remaining"));
		assertEquals(143, task.get("original"));
		assertEquals(9, task.get("assignedById"));
		assertEquals("Karuulm Slayer Dungeon", task.get("requiredLocation"));
		assertEquals(true, task.get("identityKnown"));
	}

	@Test
	public void noAssignmentIsKnownWithoutInventingAMonster()
	{
		Map<String, Object> snapshot = SlayerTaskSnapshot.build(new FakeSource(), OBSERVED_AT);
		assertEquals(true, snapshot.get("taskLoaded"));
		assertEquals("none", snapshot.get("status"));
		assertNull(snapshot.get("task"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void unknownDatabaseRowsKeepTheLiveCountButNotAnInventedIdentity()
	{
		FakeSource source = new FakeSource();
		source.varps.put(VarPlayerID.SLAYER_COUNT, 10);
		source.varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 10);
		source.varps.put(VarPlayerID.SLAYER_TARGET, 999);
		Map<String, Object> task = (Map<String, Object>) SlayerTaskSnapshot.build(source, OBSERVED_AT).get("task");
		assertNull(task.get("monster"));
		assertEquals(false, task.get("identityKnown"));
	}

	@Test
	public void unavailableVarsRemainExplicitlyUnavailable()
	{
		FakeSource source = new FakeSource();
		source.fail = true;
		Map<String, Object> snapshot = SlayerTaskSnapshot.build(source, OBSERVED_AT);
		assertEquals(false, snapshot.get("taskLoaded"));
		assertEquals("unavailable", snapshot.get("status"));
		assertNull(snapshot.get("taskLastSeenTimestamp"));
		assertNull(snapshot.get("task"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void bossAssignmentsResolveThroughTheOfficialSubtable()
	{
		FakeSource source = new FakeSource();
		source.varps.put(VarPlayerID.SLAYER_COUNT, 25);
		source.varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 35);
		source.varps.put(VarPlayerID.SLAYER_TARGET, 98);
		source.varbits.put(VarbitID.SLAYER_TARGET_BOSSID, 7);
		source.bossRows.put(7, 7000);
		source.fields.put(key(7000, DBTableID.SlayerTaskSublist.COL_TASK), new Object[]{7100});
		source.fields.put(key(7100, DBTableID.SlayerTask.COL_NAME_UPPERCASE), new Object[]{"Vorkath"});
		Map<String, Object> task = (Map<String, Object>) SlayerTaskSnapshot.build(source, OBSERVED_AT).get("task");
		assertEquals("Vorkath", task.get("monster"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void stripsMarkupAndControlCharactersFromGameLabels()
	{
		FakeSource source = new FakeSource();
		source.varps.put(VarPlayerID.SLAYER_COUNT, 1);
		source.varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 1);
		source.varps.put(VarPlayerID.SLAYER_TARGET, 1);
		source.taskRows.put(1, 100);
		source.fields.put(key(100, DBTableID.SlayerTask.COL_NAME_UPPERCASE), new Object[]{"<col=red>Jelly\n"});
		Map<String, Object> task = (Map<String, Object>) SlayerTaskSnapshot.build(source, OBSERVED_AT).get("task");
		assertEquals("Jelly", task.get("monster"));
		assertFalse(((String) task.get("monster")).contains("<"));
	}

	private static String key(int row, int column)
	{
		return row + ":" + column;
	}

	private static final class FakeSource implements SlayerTaskSnapshot.Source
	{
		private final Map<Integer, Integer> varps = new HashMap<>();
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final Map<Integer, Integer> taskRows = new HashMap<>();
		private final Map<Integer, Integer> bossRows = new HashMap<>();
		private final Map<Integer, Integer> areaRows = new HashMap<>();
		private final Map<String, Object[]> fields = new HashMap<>();
		private boolean fail;

		@Override
		public int varp(int id)
		{
			if (fail)
			{
				throw new IllegalStateException("not logged in");
			}
			return varps.getOrDefault(id, 0);
		}

		@Override
		public int varbit(int id)
		{
			return varbits.getOrDefault(id, 0);
		}

		@Override
		public List<Integer> rowsByValue(int tableId, int columnId, int tupleIndex, Object value)
		{
			Map<Integer, Integer> rows = tableId == DBTableID.SlayerTask.ID
				? taskRows
				: tableId == DBTableID.SlayerTaskSublist.ID ? bossRows : areaRows;
			Integer row = rows.get(value);
			return row == null ? Collections.emptyList() : Collections.singletonList(row);
		}

		@Override
		public Object[] field(int rowId, int columnId, int tupleIndex)
		{
			return fields.get(key(rowId, columnId));
		}
	}
}
