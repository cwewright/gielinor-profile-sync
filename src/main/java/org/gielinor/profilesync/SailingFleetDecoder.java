package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/** Resolves Sailing signals through the selected vessel's ordered game-DB option lists. */
final class SailingFleetDecoder
{
	interface DatabaseReader
	{
		List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value);
		List<Integer> tableRows(int table);
		Object[] field(int row, int column, int tupleIndex);
	}

	private final DatabaseReader database;

	SailingFleetDecoder(DatabaseReader database)
	{
		this.database = database;
	}

	static SailingFleetDecoder forClient(Client client)
	{
		return new SailingFleetDecoder(new DatabaseReader()
		{
			public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
			{
				return client.getDBRowsByValue(table, column, tupleIndex, value);
			}

			public List<Integer> tableRows(int table)
			{
				return client.getDBTableRows(table);
			}

			public Object[] field(int row, int column, int tupleIndex)
			{
				return client.getDBTableField(row, column, tupleIndex);
			}
		});
	}

	Map<String, Object> boatType(int rawValue)
	{
		Map<String, Object> result = baseResult();
		if (rawValue <= 0)
		{
			result.put("status", "not-configured");
			return result;
		}
		try
		{
			List<Integer> rows = matchingBoatRows(rawValue);
			for (int nameColumn : new int[] {
				DBTableID.SailingBoat.COL_DISPLAYNAME,
				DBTableID.SailingBoat.COL_INLINE_NAME,
				DBTableID.SailingBoat.COL_NAME
			})
			{
				Map<String, Object> decoded = decodeNamedRows(baseResult(), rows, nameColumn);
				if (!"unresolved".equals(decoded.get("status")))
				{
					return decoded;
				}
			}
			result.put("status", "unresolved");
			return result;
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
			return result;
		}
	}

	Map<String, Object> boatName(int prefix, int descriptor, int noun)
	{
		Map<String, Object> result = baseResult();
		List<Integer> rawParts = new ArrayList<>();
		rawParts.add(prefix);
		rawParts.add(descriptor);
		rawParts.add(noun);
		result.put("rawParts", rawParts);
		try
		{
			String prefixName = nameOption(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_PREFIX_OPTIONS, prefix);
			String descriptorName = nameOption(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_DESCRIPTOR_OPTIONS, descriptor);
			String nounName = nameOption(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_NOUN_OPTIONS, noun);
			if (prefixName == null || descriptorName == null || nounName == null)
			{
				result.put("status", "unresolved");
				return result;
			}

			String name = safeName((prefixName + " " + descriptorName + " " + nounName).replaceAll("\\s+", " "));
			if (name == null)
			{
				result.put("status", "unresolved");
				return result;
			}
			result.put("status", "resolved");
			result.put("name", name);
			return result;
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
			return result;
		}
	}

	Map<String, Object> keel(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_KEEL_OPTION, DBTableID.SailingBoatKeel.COL_NAME, rawValue);
	}

	Map<String, Object> hull(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_HULL_OPTION, DBTableID.SailingBoatHull.COL_NAME, rawValue);
	}

	Map<String, Object> sail(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_SAIL_OPTION, DBTableID.SailingBoatSail.COL_NAME, rawValue);
	}

	Map<String, Object> steering(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_STEERING_OPTION, DBTableID.SailingBoatSteering.COL_NAME, rawValue);
	}

	Map<String, Object> flag(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_FLAG_OPTION, DBTableID.SailingBoatFlag.COL_NAME, rawValue);
	}

	Map<String, Object> brazier(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_BRAZIER_OPTION, DBTableID.SailingBoatBrazier.COL_NAME, rawValue);
	}

	Map<String, Object> trim(int boatType, int rawValue)
	{
		return decodeBoatOption(boatType, DBTableID.SailingBoat.COL_TRIM_OPTION, DBTableID.SailingBoatTrim.COL_NAME, rawValue);
	}

	Map<String, Object> facility(int boatType, int hotspotIndex, int rawValue)
	{
		Map<String, Object> result = baseResult();
		if (rawValue <= 0)
		{
			result.put("status", "not-configured");
			return result;
		}
		try
		{
			Integer boatRow = uniqueBoatRow(boatType);
			if (boatRow == null)
			{
				result.put("status", "unresolved");
				return result;
			}
			Integer hotspotRow = rowAt(database.field(boatRow, DBTableID.SailingBoat.COL_HOTSPOT, 3), hotspotIndex);
			if (hotspotRow == null)
			{
				result.put("status", "unresolved");
				return result;
			}
			Object[] options = database.field(hotspotRow, DBTableID.SailingBoatHotspot.COL_OPTION, 0);
			return decodeSelectedRow(result, options, rawValue, DBTableID.SailingBoatFacility.COL_NAME);
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
			return result;
		}
	}

	Map<String, Object> facilityAcrossHotspots(int boatType, int rawValue)
	{
		Map<String, Object> result = baseResult();
		if (rawValue <= 0)
		{
			result.put("status", "not-configured");
			return result;
		}
		try
		{
			Integer boatRow = uniqueBoatRow(boatType);
			if (boatRow == null)
			{
				result.put("status", "unresolved");
				return result;
			}
			List<Integer> selectedRows = new ArrayList<>();
			for (Object hotspot : database.field(boatRow, DBTableID.SailingBoat.COL_HOTSPOT, 3))
			{
				if (hotspot instanceof Number)
				{
					Object[] options = database.field(((Number) hotspot).intValue(), DBTableID.SailingBoatHotspot.COL_OPTION, 0);
					Integer selected = rowAt(options, rawValue - 1);
					if (selected != null)
					{
						selectedRows.add(selected);
					}
				}
			}
			return decodeNamedRows(result, selectedRows, DBTableID.SailingBoatFacility.COL_NAME);
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
			return result;
		}
	}

	private Map<String, Object> decodeBoatOption(int boatType, int optionColumn, int nameColumn, int rawValue)
	{
		Map<String, Object> result = baseResult();
		if (rawValue <= 0)
		{
			result.put("status", "not-configured");
			return result;
		}
		try
		{
			Integer boatRow = uniqueBoatRow(boatType);
			if (boatRow == null)
			{
				result.put("status", "unresolved");
				return result;
			}
			return decodeSelectedRow(result, database.field(boatRow, optionColumn, 0), rawValue, nameColumn);
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
			return result;
		}
	}

	private String nameOption(int row, int rawValue)
	{
		Object[] options = database.field(row, DBTableID.SailingBoatNameOptions.COL_OPTION, 0);
		if (rawValue < 0 || rawValue >= options.length || !(options[rawValue] instanceof String))
		{
			return null;
		}
		return safeName((String) options[rawValue]);
	}

	private Map<String, Object> decodeSelectedRow(Map<String, Object> result, Object[] options, int rawValue, int nameColumn)
	{
		Integer selectedRow = rowAt(options, rawValue - 1);
		if (selectedRow == null)
		{
			result.put("status", "unresolved");
			return result;
		}
		return decodeNamedRows(result, Collections.singletonList(selectedRow), nameColumn);
	}

	private Map<String, Object> decodeNamedRows(Map<String, Object> result, List<Integer> rows, int nameColumn)
	{
		Set<String> names = new LinkedHashSet<>();
		for (Integer row : rows)
		{
			for (Object value : database.field(row, nameColumn, 0))
			{
				if (value instanceof String)
				{
					String name = safeName((String) value);
					if (name != null)
					{
						names.add(name);
					}
				}
			}
		}

		if (names.size() == 1)
		{
			result.put("status", "resolved");
			result.put("name", names.iterator().next());
		}
		else if (names.size() > 1)
		{
			result.put("status", "ambiguous");
			result.put("candidateNames", new ArrayList<>(names).subList(0, Math.min(8, names.size())));
		}
		else
		{
			result.put("status", "unresolved");
		}
		return result;
	}

	private Map<String, Object> baseResult()
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("source", "runelite-game-db-option-list");
		return result;
	}

	private Integer uniqueBoatRow(int boatType)
	{
		List<Integer> rows = matchingBoatRows(boatType);
		return rows.size() == 1 ? rows.get(0) : null;
	}

	private List<Integer> matchingBoatRows(int boatType)
	{
		try
		{
			List<Integer> indexed = database.rowsByValue(DBTableID.SailingBoat.ID,
				DBTableID.SailingBoat.COL_TYPE_ID, 0, boatType);
			if (indexed != null && !indexed.isEmpty())
			{
				return indexed;
			}
		}
		catch (RuntimeException ignored)
		{
			// The boat type column is not indexed in every client cache.
		}

		List<Integer> matches = new ArrayList<>();
		for (Integer row : database.tableRows(DBTableID.SailingBoat.ID))
		{
			Object[] values = database.field(row, DBTableID.SailingBoat.COL_TYPE_ID, 0);
			if (values.length > 0 && values[0] instanceof Number && ((Number) values[0]).intValue() == boatType)
			{
				matches.add(row);
			}
		}
		return matches;
	}

	private Integer rowAt(Object[] rows, int index)
	{
		return rows != null && index >= 0 && index < rows.length && rows[index] instanceof Number
			? ((Number) rows[index]).intValue() : null;
	}

	private String safeName(String raw)
	{
		String value = raw.replaceAll("<[^>]*>", "").trim();
		return value.isEmpty() || value.length() > 80 || !value.matches("[A-Za-z0-9 '&(),+.-]+") ? null : value;
	}
}
