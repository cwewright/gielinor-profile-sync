package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads only persistent, account-scoped Sailing state exposed by RuneLite
 * gamevals. Values that RuneLite does not decode into a public API type stay as
 * raw numeric identifiers so the exporter never invents game knowledge.
 */
final class SailingFleetSnapshot
{
	private static final int BOAT_COUNT = 5;
	private static final int FACILITY_SLOT_COUNT = 13;

	private static final int[] OWNED = {
		VarbitID.SAILING_BOAT_1_OWNED,
		VarbitID.SAILING_BOAT_2_OWNED,
		VarbitID.SAILING_BOAT_3_OWNED,
		VarbitID.SAILING_BOAT_4_OWNED,
		VarbitID.SAILING_BOAT_5_OWNED
	};
	private static final int[] TYPE = {
		VarbitID.SAILING_BOAT_1_TYPE,
		VarbitID.SAILING_BOAT_2_TYPE,
		VarbitID.SAILING_BOAT_3_TYPE,
		VarbitID.SAILING_BOAT_4_TYPE,
		VarbitID.SAILING_BOAT_5_TYPE
	};
	private static final int[] KEEL = {
		VarbitID.SAILING_BOAT_1_KEEL,
		VarbitID.SAILING_BOAT_2_KEEL,
		VarbitID.SAILING_BOAT_3_KEEL,
		VarbitID.SAILING_BOAT_4_KEEL,
		VarbitID.SAILING_BOAT_5_KEEL
	};
	private static final int[] HULL = {
		VarbitID.SAILING_BOAT_1_HULL,
		VarbitID.SAILING_BOAT_2_HULL,
		VarbitID.SAILING_BOAT_3_HULL,
		VarbitID.SAILING_BOAT_4_HULL,
		VarbitID.SAILING_BOAT_5_HULL
	};
	private static final int[] SAIL = {
		VarbitID.SAILING_BOAT_1_SAIL,
		VarbitID.SAILING_BOAT_2_SAIL,
		VarbitID.SAILING_BOAT_3_SAIL,
		VarbitID.SAILING_BOAT_4_SAIL,
		VarbitID.SAILING_BOAT_5_SAIL
	};
	private static final int[] STEERING = {
		VarbitID.SAILING_BOAT_1_STEERING,
		VarbitID.SAILING_BOAT_2_STEERING,
		VarbitID.SAILING_BOAT_3_STEERING,
		VarbitID.SAILING_BOAT_4_STEERING,
		VarbitID.SAILING_BOAT_5_STEERING
	};
	private static final int[] TELEPORT_FOCUS = {
		VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_3_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_4_TELEPORT_FOCUS,
		VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS
	};
	private static final int[] FLAG = {
		VarbitID.SAILING_BOAT_1_FLAG,
		VarbitID.SAILING_BOAT_2_FLAG,
		VarbitID.SAILING_BOAT_3_FLAG,
		VarbitID.SAILING_BOAT_4_FLAG,
		VarbitID.SAILING_BOAT_5_FLAG
	};
	private static final int[] BRAZIER = {
		VarbitID.SAILING_BOAT_1_BRAZIER,
		VarbitID.SAILING_BOAT_2_BRAZIER,
		VarbitID.SAILING_BOAT_3_BRAZIER,
		VarbitID.SAILING_BOAT_4_BRAZIER,
		VarbitID.SAILING_BOAT_5_BRAZIER
	};
	private static final int[] TRIM = {
		VarbitID.SAILING_BOAT_1_TRIM,
		VarbitID.SAILING_BOAT_2_TRIM,
		VarbitID.SAILING_BOAT_3_TRIM,
		VarbitID.SAILING_BOAT_4_TRIM,
		VarbitID.SAILING_BOAT_5_TRIM
	};
	private static final int[] STORED_HP = {
		VarbitID.SAILING_BOAT_1_STORED_HP,
		VarbitID.SAILING_BOAT_2_STORED_HP,
		VarbitID.SAILING_BOAT_3_STORED_HP,
		VarbitID.SAILING_BOAT_4_STORED_HP,
		VarbitID.SAILING_BOAT_5_STORED_HP
	};
	private static final int[] STORED_MAX_HP = {
		VarbitID.SAILING_BOAT_1_STORED_MAXHP,
		VarbitID.SAILING_BOAT_2_STORED_MAXHP,
		VarbitID.SAILING_BOAT_3_STORED_MAXHP,
		VarbitID.SAILING_BOAT_4_STORED_MAXHP,
		VarbitID.SAILING_BOAT_5_STORED_MAXHP
	};

	private static final int[][] FACILITY = {
		{
			VarbitID.SAILING_BOAT_1_HOTSPOT_0, VarbitID.SAILING_BOAT_1_HOTSPOT_1,
			VarbitID.SAILING_BOAT_1_HOTSPOT_2, VarbitID.SAILING_BOAT_1_HOTSPOT_3,
			VarbitID.SAILING_BOAT_1_HOTSPOT_4, VarbitID.SAILING_BOAT_1_HOTSPOT_5,
			VarbitID.SAILING_BOAT_1_HOTSPOT_6, VarbitID.SAILING_BOAT_1_HOTSPOT_7,
			VarbitID.SAILING_BOAT_1_HOTSPOT_8, VarbitID.SAILING_BOAT_1_HOTSPOT_9,
			VarbitID.SAILING_BOAT_1_HOTSPOT_10, VarbitID.SAILING_BOAT_1_HOTSPOT_11,
			VarbitID.SAILING_BOAT_1_HOTSPOT_12
		},
		{
			VarbitID.SAILING_BOAT_2_HOTSPOT_0, VarbitID.SAILING_BOAT_2_HOTSPOT_1,
			VarbitID.SAILING_BOAT_2_HOTSPOT_2, VarbitID.SAILING_BOAT_2_HOTSPOT_3,
			VarbitID.SAILING_BOAT_2_HOTSPOT_4, VarbitID.SAILING_BOAT_2_HOTSPOT_5,
			VarbitID.SAILING_BOAT_2_HOTSPOT_6, VarbitID.SAILING_BOAT_2_HOTSPOT_7,
			VarbitID.SAILING_BOAT_2_HOTSPOT_8, VarbitID.SAILING_BOAT_2_HOTSPOT_9,
			VarbitID.SAILING_BOAT_2_HOTSPOT_10, VarbitID.SAILING_BOAT_2_HOTSPOT_11,
			VarbitID.SAILING_BOAT_2_HOTSPOT_12
		},
		{
			VarbitID.SAILING_BOAT_3_HOTSPOT_0, VarbitID.SAILING_BOAT_3_HOTSPOT_1,
			VarbitID.SAILING_BOAT_3_HOTSPOT_2, VarbitID.SAILING_BOAT_3_HOTSPOT_3,
			VarbitID.SAILING_BOAT_3_HOTSPOT_4, VarbitID.SAILING_BOAT_3_HOTSPOT_5,
			VarbitID.SAILING_BOAT_3_HOTSPOT_6, VarbitID.SAILING_BOAT_3_HOTSPOT_7,
			VarbitID.SAILING_BOAT_3_HOTSPOT_8, VarbitID.SAILING_BOAT_3_HOTSPOT_9,
			VarbitID.SAILING_BOAT_3_HOTSPOT_10, VarbitID.SAILING_BOAT_3_HOTSPOT_11,
			VarbitID.SAILING_BOAT_3_HOTSPOT_12
		},
		{
			VarbitID.SAILING_BOAT_4_HOTSPOT_0, VarbitID.SAILING_BOAT_4_HOTSPOT_1,
			VarbitID.SAILING_BOAT_4_HOTSPOT_2, VarbitID.SAILING_BOAT_4_HOTSPOT_3,
			VarbitID.SAILING_BOAT_4_HOTSPOT_4, VarbitID.SAILING_BOAT_4_HOTSPOT_5,
			VarbitID.SAILING_BOAT_4_HOTSPOT_6, VarbitID.SAILING_BOAT_4_HOTSPOT_7,
			VarbitID.SAILING_BOAT_4_HOTSPOT_8, VarbitID.SAILING_BOAT_4_HOTSPOT_9,
			VarbitID.SAILING_BOAT_4_HOTSPOT_10, VarbitID.SAILING_BOAT_4_HOTSPOT_11,
			VarbitID.SAILING_BOAT_4_HOTSPOT_12
		},
		{
			VarbitID.SAILING_BOAT_5_HOTSPOT_0, VarbitID.SAILING_BOAT_5_HOTSPOT_1,
			VarbitID.SAILING_BOAT_5_HOTSPOT_2, VarbitID.SAILING_BOAT_5_HOTSPOT_3,
			VarbitID.SAILING_BOAT_5_HOTSPOT_4, VarbitID.SAILING_BOAT_5_HOTSPOT_5,
			VarbitID.SAILING_BOAT_5_HOTSPOT_6, VarbitID.SAILING_BOAT_5_HOTSPOT_7,
			VarbitID.SAILING_BOAT_5_HOTSPOT_8, VarbitID.SAILING_BOAT_5_HOTSPOT_9,
			VarbitID.SAILING_BOAT_5_HOTSPOT_10, VarbitID.SAILING_BOAT_5_HOTSPOT_11,
			VarbitID.SAILING_BOAT_5_HOTSPOT_12
		}
	};

	private static final int[][] FACILITY_EXTRA = {
		{
			VarbitID.SAILING_BOAT_1_HOTSPOT_0_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_1_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_2_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_3_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_4_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_5_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_6_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_7_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_8_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_9_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_10_EXTRA_DATA, VarbitID.SAILING_BOAT_1_HOTSPOT_11_EXTRA_DATA,
			VarbitID.SAILING_BOAT_1_HOTSPOT_12_EXTRA_DATA
		},
		{
			VarbitID.SAILING_BOAT_2_HOTSPOT_0_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_1_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_2_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_3_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_4_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_5_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_6_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_7_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_8_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_9_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_10_EXTRA_DATA, VarbitID.SAILING_BOAT_2_HOTSPOT_11_EXTRA_DATA,
			VarbitID.SAILING_BOAT_2_HOTSPOT_12_EXTRA_DATA
		},
		{
			VarbitID.SAILING_BOAT_3_HOTSPOT_0_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_1_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_2_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_3_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_4_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_5_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_6_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_7_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_8_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_9_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_10_EXTRA_DATA, VarbitID.SAILING_BOAT_3_HOTSPOT_11_EXTRA_DATA,
			VarbitID.SAILING_BOAT_3_HOTSPOT_12_EXTRA_DATA
		},
		{
			VarbitID.SAILING_BOAT_4_HOTSPOT_0_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_1_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_2_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_3_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_4_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_5_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_6_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_7_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_8_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_9_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_10_EXTRA_DATA, VarbitID.SAILING_BOAT_4_HOTSPOT_11_EXTRA_DATA,
			VarbitID.SAILING_BOAT_4_HOTSPOT_12_EXTRA_DATA
		},
		{
			VarbitID.SAILING_BOAT_5_HOTSPOT_0_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_1_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_2_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_3_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_4_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_5_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_6_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_7_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_8_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_9_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_10_EXTRA_DATA, VarbitID.SAILING_BOAT_5_HOTSPOT_11_EXTRA_DATA,
			VarbitID.SAILING_BOAT_5_HOTSPOT_12_EXTRA_DATA
		}
	};

	private SailingFleetSnapshot()
	{
	}

	static Map<String, Object> build(long timestamp, IntUnaryOperator varbitReader, IntFunction<Map<String, Object>> cargoReader)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schemaVersion", 1);
		result.put("source", "RuneLite persistent Sailing boat gamevals and cargo containers");
		result.put("sourceVersion", "runelite-1.12.35-gamevals");
		result.put("fleetFromCache", false);
		result.put("fleetLastSeenTimestamp", timestamp);
		result.put("unknownFields", Arrays.asList(
			"decodedBoatTypeNames",
			"decodedComponentNames",
			"activeBoat",
			"crewAssignments"
		));

		List<Map<String, Object>> boats = new ArrayList<>();
		try
		{
			for (int boatIndex = 0; boatIndex < BOAT_COUNT; boatIndex++)
			{
				int ownershipValue = varbitReader.applyAsInt(OWNED[boatIndex]);
				if (ownershipValue <= 0)
				{
					continue;
				}

				Map<String, Object> boat = new LinkedHashMap<>();
				boat.put("id", "boat-slot-" + (boatIndex + 1));
				boat.put("slot", boatIndex + 1);
				boat.put("owned", true);
				boat.put("ownershipValue", ownershipValue);
				boat.put("typeId", varbitReader.applyAsInt(TYPE[boatIndex]));

				Map<String, Object> components = new LinkedHashMap<>();
				components.put("keelId", varbitReader.applyAsInt(KEEL[boatIndex]));
				components.put("hullId", varbitReader.applyAsInt(HULL[boatIndex]));
				components.put("sailId", varbitReader.applyAsInt(SAIL[boatIndex]));
				components.put("steeringId", varbitReader.applyAsInt(STEERING[boatIndex]));
				components.put("teleportFocusId", varbitReader.applyAsInt(TELEPORT_FOCUS[boatIndex]));
				components.put("flagId", varbitReader.applyAsInt(FLAG[boatIndex]));
				components.put("brazierId", varbitReader.applyAsInt(BRAZIER[boatIndex]));
				components.put("trimId", varbitReader.applyAsInt(TRIM[boatIndex]));
				boat.put("components", components);

				Map<String, Object> condition = new LinkedHashMap<>();
				condition.put("storedHitpoints", varbitReader.applyAsInt(STORED_HP[boatIndex]));
				condition.put("storedMaxHitpoints", varbitReader.applyAsInt(STORED_MAX_HP[boatIndex]));
				boat.put("condition", condition);

				List<Map<String, Object>> facilities = new ArrayList<>(FACILITY_SLOT_COUNT);
				for (int facilityIndex = 0; facilityIndex < FACILITY_SLOT_COUNT; facilityIndex++)
				{
					Map<String, Object> facility = new LinkedHashMap<>();
					facility.put("slot", facilityIndex);
					facility.put("componentId", varbitReader.applyAsInt(FACILITY[boatIndex][facilityIndex]));
					facility.put("extraData", varbitReader.applyAsInt(FACILITY_EXTRA[boatIndex][facilityIndex]));
					facilities.add(facility);
				}
				boat.put("facilities", facilities);

				Map<String, Object> cargo;
				try
				{
					cargo = cargoReader.apply(boatIndex);
				}
				catch (RuntimeException e)
				{
					cargo = null;
				}
				if (cargo == null)
				{
					cargo = new LinkedHashMap<>();
					cargo.put("loaded", false);
					cargo.put("fromCache", false);
					cargo.put("lastSeenTimestamp", 0L);
					cargo.put("items", new LinkedHashMap<>());
				}
				boat.put("cargo", cargo);
				boats.add(boat);
			}
			result.put("fleetLoaded", true);
		}
		catch (RuntimeException e)
		{
			boats.clear();
			result.put("fleetLoaded", false);
			result.put("fleetLastSeenTimestamp", 0L);
		}

		result.put("boats", boats);
		Map<String, Object> privacy = new LinkedHashMap<>();
		privacy.put("exactCoordinatesIncluded", false);
		privacy.put("worldNumberIncluded", false);
		privacy.put("nearbyPlayersIncluded", false);
		result.put("privacy", privacy);
		return result;
	}
}
