package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/**
 * Resolves persistent Sailing customisation signals through the game DB that
 * supplied them. Raw values remain in the export; a failed or ambiguous lookup
 * never becomes a guessed component name.
 */
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
			@Override
			public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
			{
				return client.getDBRowsByValue(table, column, tupleIndex, value);
			}

			@Override
			public List<Integer> tableRows(int table)
			{
				return client.getDBTableRows(table);
			}

			@Override
			public Object[] field(int row, int column, int tupleIndex)
			{
				return client.getDBTableField(row, column, tupleIndex);
			}
		});
	}

	Map<String, Object> boatType(int rawValue)
	{
		return decode(
			DBTableID.SailingBoat.ID,
			DBTableID.SailingBoat.COL_TYPE_ID,
			DBTableID.SailingBoat.COL_DISPLAYNAME,
			rawValue
		);
	}

	Map<String, Object> keel(int rawValue)
	{
		return decode(DBTableID.SailingBoatKeel.ID, DBTableID.SailingBoatKeel.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatKeel.COL_NAME, rawValue);
	}

	Map<String, Object> hull(int rawValue)
	{
		return decode(DBTableID.SailingBoatHull.ID, DBTableID.SailingBoatHull.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatHull.COL_NAME, rawValue);
	}

	Map<String, Object> sail(int rawValue)
	{
		return decode(DBTableID.SailingBoatSail.ID, DBTableID.SailingBoatSail.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatSail.COL_NAME, rawValue);
	}

	Map<String, Object> steering(int rawValue)
	{
		return decode(DBTableID.SailingBoatSteering.ID, DBTableID.SailingBoatSteering.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatSteering.COL_NAME, rawValue);
	}

	Map<String, Object> flag(int rawValue)
	{
		return decode(DBTableID.SailingBoatFlag.ID, DBTableID.SailingBoatFlag.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatFlag.COL_NAME, rawValue);
	}

	Map<String, Object> brazier(int rawValue)
	{
		return decode(DBTableID.SailingBoatBrazier.ID, DBTableID.SailingBoatBrazier.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatBrazier.COL_NAME, rawValue);
	}

	Map<String, Object> trim(int rawValue)
	{
		return decode(DBTableID.SailingBoatTrim.ID, DBTableID.SailingBoatTrim.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatTrim.COL_NAME, rawValue);
	}

	Map<String, Object> facility(int rawValue)
	{
		return decode(DBTableID.SailingBoatFacility.ID, DBTableID.SailingBoatFacility.COL_FACILITY_CUSTOMISATION_ORDER,
			DBTableID.SailingBoatFacility.COL_NAME, rawValue);
	}

	private Map<String, Object> decode(int table, int valueColumn, int nameColumn, int rawValue)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("source", "runelite-game-db");
		if (rawValue <= 0)
		{
			result.put("status", "not-configured");
			return result;
		}

		try
		{
			List<Integer> rows = matchingRows(table, valueColumn, rawValue);
			Set<String> names = new LinkedHashSet<>();
			for (Integer row : rows)
			{
				Object[] values = database.field(row, nameColumn, 0);
				if (values.length > 0 && values[0] instanceof String)
				{
					String name = safeName((String) values[0]);
					if (name != null)
					{
						names.add(name);
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
		}
		catch (RuntimeException e)
		{
			result.put("status", "unavailable");
		}
		return result;
	}

	private List<Integer> matchingRows(int table, int column, int rawValue)
	{
		try
		{
			List<Integer> indexed = database.rowsByValue(table, column, 0, rawValue);
			if (indexed != null && !indexed.isEmpty())
			{
				return indexed;
			}
		}
		catch (RuntimeException ignored)
		{
			// Some DB columns are not indexed. A bounded table scan is the safe fallback.
		}

		List<Integer> matches = new ArrayList<>();
		for (Integer row : database.tableRows(table))
		{
			Object[] values = database.field(row, column, 0);
			if (values.length > 0 && values[0] instanceof Number && ((Number) values[0]).intValue() == rawValue)
			{
				matches.add(row);
			}
		}
		return matches;
	}

	private String safeName(String raw)
	{
		String value = raw.replaceAll("<[^>]*>", "").trim();
		if (value.isEmpty() || value.length() > 80 || !value.matches("[A-Za-z0-9 '&(),+.-]+"))
		{
			return null;
		}
		return value;
	}
}
