package org.gielinor.profilesync;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import net.runelite.api.gameval.VarbitID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SailingFleetSnapshotTest
{
	@Test
	public void exportsMultipleOwnedBoatsAsObservedRawState()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
		values.put(VarbitID.SAILING_BOAT_1_TYPE, 2);
		values.put(VarbitID.SAILING_BOAT_1_HULL, 14);
		values.put(VarbitID.SAILING_BOAT_1_STORED_HP, 83);
		values.put(VarbitID.SAILING_BOAT_1_STORED_MAXHP, 100);
		values.put(VarbitID.SAILING_BOAT_1_HOTSPOT_3, 27);
		values.put(VarbitID.SAILING_BOAT_1_HOTSPOT_3_EXTRA_DATA, 4);
		values.put(VarbitID.SAILING_BOAT_4_OWNED, 1);
		values.put(VarbitID.SAILING_BOAT_4_TYPE, 5);

		Map<String, Object> loadedCargo = new LinkedHashMap<>();
		loadedCargo.put("loaded", true);
		loadedCargo.put("fromCache", false);
		loadedCargo.put("lastSeenTimestamp", 1234L);
		Map<String, Object> cargoItems = new LinkedHashMap<>();
		cargoItems.put("0", item("Repair kit", 9));
		loadedCargo.put("items", cargoItems);

		Map<String, Object> result = SailingFleetSnapshot.build(
			1234L,
			id -> values.getOrDefault(id, 0),
			boatIndex -> boatIndex == 0 ? loadedCargo : unloadedCargo()
		);

		assertEquals(true, result.get("fleetLoaded"));
		assertEquals(false, result.get("fleetFromCache"));
		assertEquals(1234L, result.get("fleetLastSeenTimestamp"));
		List<Map<String, Object>> boats = boats(result);
		assertEquals(2, boats.size());
		assertEquals("boat-slot-1", boats.get(0).get("id"));
		assertEquals(1, boats.get(0).get("slot"));
		assertEquals(2, boats.get(0).get("typeId"));
		assertEquals(14, map(boats.get(0).get("components")).get("hullId"));
		assertEquals(83, map(boats.get(0).get("condition")).get("storedHitpoints"));
		assertEquals(100, map(boats.get(0).get("condition")).get("storedMaxHitpoints"));
		List<Map<String, Object>> facilities = list(boats.get(0).get("facilities"));
		assertEquals(13, facilities.size());
		assertEquals(27, facilities.get(3).get("componentId"));
		assertEquals(4, facilities.get(3).get("extraData"));
		assertTrue((Boolean) map(boats.get(0).get("cargo")).get("loaded"));
		assertFalse((Boolean) map(boats.get(1).get("cargo")).get("loaded"));
	}

	@Test
	public void keepsUnknownCargoLocalToTheBoat()
	{
		Map<String, Object> result = SailingFleetSnapshot.build(
			2000L,
			id -> id == VarbitID.SAILING_BOAT_1_OWNED ? 1 : 0,
			boatIndex -> { throw new IllegalStateException("container unavailable"); }
		);

		assertEquals(true, result.get("fleetLoaded"));
		Map<String, Object> cargo = map(boats(result).get(0).get("cargo"));
		assertEquals(false, cargo.get("loaded"));
		assertEquals(false, cargo.get("fromCache"));
		assertEquals(0L, cargo.get("lastSeenTimestamp"));
	}

	@Test
	public void marksTheFleetUnavailableWhenPersistentSignalsCannotBeRead()
	{
		Map<String, Object> result = SailingFleetSnapshot.build(
			3000L,
			id -> { throw new IllegalStateException("varbits unavailable"); },
			boatIndex -> unloadedCargo()
		);

		assertEquals(false, result.get("fleetLoaded"));
		assertEquals(0L, result.get("fleetLastSeenTimestamp"));
		assertTrue(boats(result).isEmpty());
	}

	private static Map<String, Object> item(String name, int quantity)
	{
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("name", name);
		item.put("quantity", quantity);
		return item;
	}

	private static Map<String, Object> unloadedCargo()
	{
		Map<String, Object> cargo = new LinkedHashMap<>();
		cargo.put("loaded", false);
		cargo.put("fromCache", false);
		cargo.put("lastSeenTimestamp", 0L);
		cargo.put("items", new LinkedHashMap<>());
		return cargo;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value)
	{
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> list(Object value)
	{
		return (List<Map<String, Object>>) value;
	}

	private static List<Map<String, Object>> boats(Map<String, Object> fleet)
	{
		return list(fleet.get("boats"));
	}
}
