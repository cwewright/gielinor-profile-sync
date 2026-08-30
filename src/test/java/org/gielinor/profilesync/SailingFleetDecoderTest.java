package org.gielinor.profilesync;

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
	public void resolvesBoatAndComponentNamesThroughTheBoatsOrderedOptions()
	{
		FakeDatabase database = new FakeDatabase();
		database.addBoat(100, 2, "<col=ffffff>Skiff</col>");
		database.set(100, DBTableID.SailingBoat.COL_HULL_OPTION, 0, 200, 201, 202);
		database.set(200, DBTableID.SailingBoatHull.COL_NAME, 0, "Wooden hull");
		database.set(201, DBTableID.SailingBoatHull.COL_NAME, 0, "Oak hull");
		database.set(202, DBTableID.SailingBoatHull.COL_NAME, 0, "Teak hull");

		SailingFleetDecoder decoder = new SailingFleetDecoder(database);
		assertEquals("Skiff", decoder.boatType(2).get("name"));
		assertEquals("Teak hull", decoder.hull(2, 3).get("name"));
		assertEquals("runelite-game-db-option-list", decoder.hull(2, 3).get("source"));
	}

	@Test
	public void ignoresUnrelatedRowsWithTheSameCustomisationOrder()
	{
		FakeDatabase database = new FakeDatabase();
		database.addBoat(100, 2, "Skiff");
		database.set(100, DBTableID.SailingBoat.COL_STEERING_OPTION, 0, 400, 401);
		database.set(400, DBTableID.SailingBoatSteering.COL_NAME, 0, "Bronze helm");
		database.set(401, DBTableID.SailingBoatSteering.COL_NAME, 0, "Iron helm");
		database.addTableRow(DBTableID.SailingBoatSteering.ID, 499);
		database.set(499, DBTableID.SailingBoatSteering.COL_FACILITY_CUSTOMISATION_ORDER, 0, 2);
		database.set(499, DBTableID.SailingBoatSteering.COL_NAME, 0, "Wrong vessel option");

		assertEquals("Iron helm", new SailingFleetDecoder(database).steering(2, 2).get("name"));
	}

	@Test
	public void fallsBackToTheTrustedInlineBoatNameWhenDisplayNameIsEmpty()
	{
		FakeDatabase database = new FakeDatabase();
		database.addBoat(100, 2, null);
		database.set(100, DBTableID.SailingBoat.COL_INLINE_NAME, 0, "<col=ffffff>Skiff</col>");

		assertEquals("resolved", new SailingFleetDecoder(database).boatType(2).get("status"));
		assertEquals("Skiff", new SailingFleetDecoder(database).boatType(2).get("name"));
	}

	@Test
	public void resolvesTheThreePartCustomBoatNameFromOrderedGameOptions()
	{
		FakeDatabase database = new FakeDatabase();
		database.set(
			DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_PREFIX_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION,
			0,
			"The", "Captain's"
		);
		database.set(
			DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_DESCRIPTOR_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION,
			0,
			"Salty", "Wayward"
		);
		database.set(
			DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_NOUN_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION,
			0,
			"Herring", "Voyager", "Lantern"
		);

		Map<String, Object> decoded = new SailingFleetDecoder(database).boatName(0, 1, 2);
		assertEquals("resolved", decoded.get("status"));
		assertEquals("The Wayward Lantern", decoded.get("name"));
		assertEquals(java.util.Arrays.asList(0, 1, 2), decoded.get("rawParts"));
	}

	@Test
	public void leavesOutOfRangeOrUnsafeBoatNamesUnresolved()
	{
		FakeDatabase database = new FakeDatabase();
		database.set(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_PREFIX_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION, 0, "C:\\Users\\captain");
		database.set(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_DESCRIPTOR_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION, 0, "Salty");
		database.set(DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_NOUN_OPTIONS,
			DBTableID.SailingBoatNameOptions.COL_OPTION, 0, "Voyager");

		SailingFleetDecoder decoder = new SailingFleetDecoder(database);
		assertEquals("unresolved", decoder.boatName(0, 0, 0).get("status"));
		assertEquals("unresolved", decoder.boatName(7, 0, 0).get("status"));
	}

	@Test
	public void traversesBoatHotspotAndItsOrderedFacilityOptions()
	{
		FakeDatabase database = new FakeDatabase();
		database.addBoat(100, 2, "Skiff");
		database.set(100, DBTableID.SailingBoat.COL_HOTSPOT, 3, 500, 501);
		database.set(500, DBTableID.SailingBoatHotspot.COL_OPTION, 0, 603);
		database.set(501, DBTableID.SailingBoatHotspot.COL_OPTION, 0, 600, 601, 602);
		database.set(603, DBTableID.SailingBoatFacility.COL_NAME, 0, "Teleport focus");
		database.set(600, DBTableID.SailingBoatFacility.COL_NAME, 0, "Cargo hold");
		database.set(601, DBTableID.SailingBoatFacility.COL_NAME, 0, "Salvaging hook");
		database.set(602, DBTableID.SailingBoatFacility.COL_NAME, 0, "Cannon");

		assertEquals("Salvaging hook", new SailingFleetDecoder(database).facility(2, 1, 2).get("name"));
		assertEquals("ambiguous", new SailingFleetDecoder(database).facilityAcrossHotspots(2, 1).get("status"));
	}

	@Test
	public void fallsBackToScanningOnlyForTheBoatTypeLookup()
	{
		FakeDatabase database = new FakeDatabase();
		database.throwIndexedLookup = true;
		database.addBoat(100, 2, "Skiff");
		database.set(100, DBTableID.SailingBoat.COL_KEEL_OPTION, 0, 700);
		database.set(700, DBTableID.SailingBoatKeel.COL_NAME, 0, "Bronze keel");

		assertEquals("Bronze keel", new SailingFleetDecoder(database).keel(2, 1).get("name"));
	}

	@Test
	public void preservesZeroBoundsAmbiguityAndUnsafeNames()
	{
		FakeDatabase database = new FakeDatabase();
		database.addBoat(100, 2, "Skiff");
		database.addBoat(101, 2, "Sloop");
		database.set(100, DBTableID.SailingBoat.COL_TRIM_OPTION, 0, 800);
		database.set(800, DBTableID.SailingBoatTrim.COL_NAME, 0, "C:\\Users\\name\\token.txt");
		SailingFleetDecoder decoder = new SailingFleetDecoder(database);

		assertEquals("not-configured", decoder.trim(2, 0).get("status"));
		assertEquals("unresolved", decoder.trim(2, 2).get("status"));
		assertEquals("ambiguous", decoder.boatType(2).get("status"));
		assertFalse(decoder.boatType(2).containsKey("name"));
	}

	private static final class FakeDatabase implements SailingFleetDecoder.DatabaseReader
	{
		private final Map<Integer, List<Integer>> tables = new HashMap<>();
		private final Map<String, Object[]> fields = new HashMap<>();
		private boolean throwIndexedLookup;

		private void addBoat(int row, int type, String name)
		{
			addTableRow(DBTableID.SailingBoat.ID, row);
			set(row, DBTableID.SailingBoat.COL_TYPE_ID, 0, type);
			if (name != null)
			{
				set(row, DBTableID.SailingBoat.COL_DISPLAYNAME, 0, name);
			}
		}

		private void addTableRow(int table, int row)
		{
			tables.computeIfAbsent(table, ignored -> new java.util.ArrayList<>()).add(row);
		}

		private void set(int row, int column, int tupleIndex, Object... values)
		{
			fields.put(key(row, column, tupleIndex), values);
		}

		public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
		{
			if (throwIndexedLookup)
			{
				throw new IllegalStateException("not indexed");
			}
			List<Integer> matches = new java.util.ArrayList<>();
			for (Integer row : tableRows(table))
			{
				Object[] rowValues = field(row, column, tupleIndex);
				if (rowValues.length > 0 && value.equals(rowValues[0]))
				{
					matches.add(row);
				}
			}
			return matches;
		}

		public List<Integer> tableRows(int table)
		{
			return tables.getOrDefault(table, Collections.emptyList());
		}

		public Object[] field(int row, int column, int tupleIndex)
		{
			return fields.getOrDefault(key(row, column, tupleIndex), new Object[0]);
		}

		private String key(int row, int column, int tupleIndex)
		{
			return row + ":" + column + ":" + tupleIndex;
		}
	}
}
