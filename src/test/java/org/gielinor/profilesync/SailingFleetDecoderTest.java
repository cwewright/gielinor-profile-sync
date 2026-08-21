package org.gielinor.profilesync;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import net.runelite.api.gameval.DBTableID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SailingFleetDecoderTest
{
	@Test
	public void resolvesBoatAndComponentNamesFromRuneLiteTables()
	{
		FakeDatabase database = new FakeDatabase();
		database.add(DBTableID.SailingBoat.ID, 100, DBTableID.SailingBoat.COL_TYPE_ID, 2,
			DBTableID.SailingBoat.COL_DISPLAYNAME, "<col=ffffff>Skiff</col>");
		database.add(DBTableID.SailingBoatHull.ID, 200,
			DBTableID.SailingBoatHull.COL_FACILITY_CUSTOMISATION_ORDER, 3,
			DBTableID.SailingBoatHull.COL_NAME, "Teak hull");

		SailingFleetDecoder decoder = new SailingFleetDecoder(database);
		assertEquals("resolved", decoder.boatType(2).get("status"));
		assertEquals("Skiff", decoder.boatType(2).get("name"));
		assertEquals("Teak hull", decoder.hull(3).get("name"));
	}

	@Test
	public void preservesAmbiguityInsteadOfPickingAName()
	{
		FakeDatabase database = new FakeDatabase();
		database.add(DBTableID.SailingBoatFacility.ID, 300,
			DBTableID.SailingBoatFacility.COL_FACILITY_CUSTOMISATION_ORDER, 4,
			DBTableID.SailingBoatFacility.COL_NAME, "Bronze cannon");
		database.add(DBTableID.SailingBoatFacility.ID, 301,
			DBTableID.SailingBoatFacility.COL_FACILITY_CUSTOMISATION_ORDER, 4,
			DBTableID.SailingBoatFacility.COL_NAME, "Salvaging hook");

		Map<String, Object> decoded = new SailingFleetDecoder(database).facility(4);
		assertEquals("ambiguous", decoded.get("status"));
		assertFalse(decoded.containsKey("name"));
		assertEquals(Arrays.asList("Bronze cannon", "Salvaging hook"), decoded.get("candidateNames"));
	}

	@Test
	public void fallsBackToScanningWhenAColumnIsNotIndexed()
	{
		FakeDatabase database = new FakeDatabase();
		database.throwIndexedLookup = true;
		database.add(DBTableID.SailingBoatSteering.ID, 400,
			DBTableID.SailingBoatSteering.COL_FACILITY_CUSTOMISATION_ORDER, 2,
			DBTableID.SailingBoatSteering.COL_NAME, "Iron helm");

		assertEquals("Iron helm", new SailingFleetDecoder(database).steering(2).get("name"));
	}

	@Test
	public void treatsZeroAsNotConfiguredAndUnsafeNamesAsUnresolved()
	{
		FakeDatabase database = new FakeDatabase();
		database.add(DBTableID.SailingBoatTrim.ID, 500,
			DBTableID.SailingBoatTrim.COL_FACILITY_CUSTOMISATION_ORDER, 7,
			DBTableID.SailingBoatTrim.COL_NAME, "C:\\Users\\name\\token.txt");
		SailingFleetDecoder decoder = new SailingFleetDecoder(database);

		assertEquals("not-configured", decoder.trim(0).get("status"));
		assertEquals("unresolved", decoder.trim(7).get("status"));
	}

	private static final class FakeDatabase implements SailingFleetDecoder.DatabaseReader
	{
		private final Map<Integer, List<Integer>> tables = new HashMap<>();
		private final Map<String, Object> fields = new HashMap<>();
		private boolean throwIndexedLookup;

		private void add(int table, int row, int valueColumn, int value, int nameColumn, String name)
		{
			tables.computeIfAbsent(table, ignored -> new java.util.ArrayList<>()).add(row);
			fields.put(key(row, valueColumn), value);
			fields.put(key(row, nameColumn), name);
		}

		@Override
		public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
		{
			if (throwIndexedLookup)
			{
				throw new IllegalStateException("not indexed");
			}
			List<Integer> matches = new java.util.ArrayList<>();
			for (Integer row : tableRows(table))
			{
				if (value.equals(fields.get(key(row, column))))
				{
					matches.add(row);
				}
			}
			return matches;
		}

		@Override
		public List<Integer> tableRows(int table)
		{
			return tables.getOrDefault(table, Collections.emptyList());
		}

		@Override
		public Object[] field(int row, int column, int tupleIndex)
		{
			Object value = fields.get(key(row, column));
			return value == null ? new Object[0] : new Object[]{value};
		}

		private String key(int row, int column)
		{
			return row + ":" + column;
		}
	}
}
