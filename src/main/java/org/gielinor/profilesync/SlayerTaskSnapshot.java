package org.gielinor.profilesync;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads only the server-backed assignment state RuneLite uses for its Slayer counter. */
final class SlayerTaskSnapshot
{
	private static final int BOSS_TASK_ID = 98;
	private static final int KRYSTILIA_SLAYER_MASTER = 7;
	private static final int MAX_GAME_LABEL_LENGTH = 120;

	interface Source
	{
		int varp(int id);
		int varbit(int id);
		List<Integer> rowsByValue(int tableId, int columnId, int tupleIndex, Object value);
		Object[] field(int rowId, int columnId, int tupleIndex);
	}

	private SlayerTaskSnapshot()
	{
	}

	static Map<String, Object> build(Client client, long observedAt)
	{
		return build(new Source()
		{
			@Override
			public int varp(int id)
			{
				return client.getVarpValue(id);
			}

			@Override
			public int varbit(int id)
			{
				return client.getVarbitValue(id);
			}

			@Override
			public List<Integer> rowsByValue(int tableId, int columnId, int tupleIndex, Object value)
			{
				return client.getDBRowsByValue(tableId, columnId, tupleIndex, value);
			}

			@Override
			public Object[] field(int rowId, int columnId, int tupleIndex)
			{
				return client.getDBTableField(rowId, columnId, tupleIndex);
			}
		}, observedAt);
	}

	static Map<String, Object> build(Source source, long observedAt)
	{
		Map<String, Object> result = base(false, observedAt);
		try
		{
			int remaining = source.varp(VarPlayerID.SLAYER_COUNT);
			int original = source.varp(VarPlayerID.SLAYER_COUNT_ORIGINAL);
			int taskId = source.varp(VarPlayerID.SLAYER_TARGET);
			int areaId = source.varp(VarPlayerID.SLAYER_AREA);
			int masterId = source.varbit(VarbitID.SLAYER_MASTER);

			result = base(true, observedAt);
			result.put("status", remaining > 0 ? "live" : "none");
			result.put("points", nonNegative(source.varbit(VarbitID.SLAYER_POINTS)));
			result.put("streak", nonNegative(source.varbit(
				masterId == KRYSTILIA_SLAYER_MASTER
					? VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED
					: VarbitID.SLAYER_TASKS_COMPLETED)));

			if (remaining <= 0)
			{
				result.put("task", null);
				return result;
			}

			Map<String, Object> task = new LinkedHashMap<>();
			task.put("taskId", nonNegative(taskId));
			task.put("remaining", nonNegative(remaining));
			task.put("original", original > 0 ? original : null);
			task.put("assignedById", nonNegative(masterId));
			task.put("requiredLocationId", areaId > 0 ? areaId : null);

			Integer taskRow = resolveTaskRow(source, taskId);
			task.put("monster", taskRow == null
				? null
				: safeLabel(first(source.field(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))));
			task.put("requiredLocation", resolveArea(source, areaId));
			task.put("identityKnown", task.get("monster") != null);
			result.put("task", task);
			return result;
		}
		catch (RuntimeException exception)
		{
			result.put("status", "unavailable");
			result.put("task", null);
			return result;
		}
	}

	private static Map<String, Object> base(boolean loaded, long observedAt)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schemaVersion", 1);
		result.put("source", "RuneLite server-backed Slayer assignment variables");
		result.put("sourceVersion", "runelite-1.12.35-task-vars");
		result.put("taskLoaded", loaded);
		result.put("taskFromCache", false);
		result.put("taskLastSeenTimestamp", loaded && observedAt > 0
			? Instant.ofEpochMilli(observedAt).toString()
			: null);
		result.put("status", loaded ? "none" : "unavailable");
		result.put("points", null);
		result.put("streak", null);
		result.put("task", null);
		return result;
	}

	private static Integer resolveTaskRow(Source source, int taskId)
	{
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> rows = source.rowsByValue(
				DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0,
				source.varbit(VarbitID.SLAYER_TARGET_BOSSID));
			if (rows == null || rows.isEmpty())
			{
				return null;
			}
			Object value = first(source.field(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0));
			return value instanceof Integer ? (Integer) value : null;
		}

		List<Integer> rows = source.rowsByValue(
			DBTableID.SlayerTask.ID,
			DBTableID.SlayerTask.COL_ID,
			0,
			taskId);
		return rows == null || rows.isEmpty() ? null : rows.get(0);
	}

	private static String resolveArea(Source source, int areaId)
	{
		if (areaId <= 0)
		{
			return null;
		}
		List<Integer> rows = source.rowsByValue(
			DBTableID.SlayerArea.ID,
			DBTableID.SlayerArea.COL_AREA_ID,
			0,
			areaId);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}
		return safeLabel(first(source.field(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)));
	}

	private static Object first(Object[] values)
	{
		return values == null || values.length == 0 ? null : values[0];
	}

	private static Integer nonNegative(int value)
	{
		return value >= 0 ? value : null;
	}

	private static String safeLabel(Object value)
	{
		if (!(value instanceof String))
		{
			return null;
		}
		String normalized = ((String) value)
			.replaceAll("<[^>]*>", "")
			.replaceAll("[\\p{Cntrl}<>]", "")
			.replaceAll("\\s+", " ")
			.trim();
		if (normalized.isEmpty())
		{
			return null;
		}
		return normalized.length() <= MAX_GAME_LABEL_LENGTH
			? normalized
			: normalized.substring(0, MAX_GAME_LABEL_LENGTH);
	}
}
