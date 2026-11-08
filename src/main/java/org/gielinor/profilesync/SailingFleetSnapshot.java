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
	private static final int[][] NAME = {
		{VarbitID.SAILING_BOAT_1_NAME_1, VarbitID.SAILING_BOAT_1_NAME_2, VarbitID.SAILING_BOAT_1_NAME_3},
		{VarbitID.SAILING_BOAT_2_NAME_1, VarbitID.SAILING_BOAT_2_NAME_2, VarbitID.SAILING_BOAT_2_NAME_3},
		{VarbitID.SAILING_BOAT_3_NAME_1, VarbitID.SAILING_BOAT_3_NAME_2, VarbitID.SAILING_BOAT_3_NAME_3},
		{VarbitID.SAILING_BOAT_4_NAME_1, VarbitID.SAILING_BOAT_4_NAME_2, VarbitID.SAILING_BOAT_4_NAME_3},
		{VarbitID.SAILING_BOAT_5_NAME_1, VarbitID.SAILING_BOAT_5_NAME_2, VarbitID.SAILING_BOAT_5_NAME_3}
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
		return build(timestamp, varbitReader, cargoReader, null);
	}

	static Map<String, Object> build(
		long timestamp,
		IntUnaryOperator varbitReader,
		IntFunction<Map<String, Object>> cargoReader,
		SailingFleetDecoder decoder)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schemaVersion", 3);
		result.put("source", "RuneLite persistent Sailing gamevals, game DB labels, and cargo containers");
		result.put("sourceVersion", "runelite-1.12.35-gamevals");
		result.put("fleetFromCache", false);
		result.put("fleetLastSeenTimestamp", timestamp);
		result.put("unknownFields", Arrays.asList("crewAssignments"));
		Integer activeSlotSignal = safeRead(varbitReader, VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED);
		Integer playerOnPersonalBoat = safeRead(varbitReader, VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT);
		Integer boardedBoat = safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT);
		Integer boardedBoatType = safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT_TYPE);
		Integer storedBoardedBoatType = safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT_TYPE_STORED);
		List<Integer> boardedNameParts = Arrays.asList(
			safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT_NAME_1),
			safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT_NAME_2),
			safeRead(varbitReader, VarbitID.SAILING_BOARDED_BOAT_NAME_3)
		);
		Map<String, Object> activeSignals = new LinkedHashMap<>();
		activeSignals.put("lastPersonalBoatBoarded", activeSlotSignal);
		activeSignals.put("playerOnPersonalBoat", playerOnPersonalBoat);
		activeSignals.put("boardedBoat", boardedBoat);
		activeSignals.put("boardedBoatType", boardedBoatType);
		activeSignals.put("storedBoardedBoatType", storedBoardedBoatType);
		activeSignals.put("boardedNameParts", boardedNameParts);
		result.put("activeBoatSignals", activeSignals);

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
				int typeId = varbitReader.applyAsInt(TYPE[boatIndex]);
				boat.put("typeId", typeId);
				List<Integer> nameParts = Arrays.asList(
					varbitReader.applyAsInt(NAME[boatIndex][0]),
					varbitReader.applyAsInt(NAME[boatIndex][1]),
					varbitReader.applyAsInt(NAME[boatIndex][2])
				);
				boat.put("nameParts", nameParts);
				if (decoder != null)
				{
					boat.put("decodedType", decoder.boatType(typeId));
					boat.put("decodedName", decoder.boatName(nameParts.get(0), nameParts.get(1), nameParts.get(2)));
				}
				else
				{
					Map<String, Object> unresolvedName = new LinkedHashMap<>();
					unresolvedName.put("status", "unresolved");
					unresolvedName.put("rawParts", nameParts);
					boat.put("decodedName", unresolvedName);
				}

				Map<String, Object> components = new LinkedHashMap<>();
				int keelId = varbitReader.applyAsInt(KEEL[boatIndex]);
				int hullId = varbitReader.applyAsInt(HULL[boatIndex]);
				int sailId = varbitReader.applyAsInt(SAIL[boatIndex]);
				int steeringId = varbitReader.applyAsInt(STEERING[boatIndex]);
				int teleportFocusId = varbitReader.applyAsInt(TELEPORT_FOCUS[boatIndex]);
				int flagId = varbitReader.applyAsInt(FLAG[boatIndex]);
				int brazierId = varbitReader.applyAsInt(BRAZIER[boatIndex]);
				int trimId = varbitReader.applyAsInt(TRIM[boatIndex]);
				components.put("keelId", keelId);
				components.put("hullId", hullId);
				components.put("sailId", sailId);
				components.put("steeringId", steeringId);
				components.put("teleportFocusId", teleportFocusId);
				components.put("flagId", flagId);
				components.put("brazierId", brazierId);
				components.put("trimId", trimId);
				boat.put("components", components);
				if (decoder != null)
				{
					Map<String, Object> decoded = new LinkedHashMap<>();
					decoded.put("keel", decoder.keel(typeId, keelId));
					decoded.put("hull", decoder.hull(typeId, hullId));
					decoded.put("sail", decoder.sail(typeId, sailId));
					decoded.put("steering", decoder.steering(typeId, steeringId));
					decoded.put("teleportFocus", decoder.facilityAcrossHotspots(typeId, teleportFocusId));
					decoded.put("flag", decoder.flag(typeId, flagId));
					decoded.put("brazier", decoder.brazier(typeId, brazierId));
					decoded.put("trim", decoder.trim(typeId, trimId));
					boat.put("decodedComponents", decoded);
				}

				Map<String, Object> condition = new LinkedHashMap<>();
				condition.put("storedHitpoints", varbitReader.applyAsInt(STORED_HP[boatIndex]));
				condition.put("storedMaxHitpoints", varbitReader.applyAsInt(STORED_MAX_HP[boatIndex]));
				boat.put("condition", condition);

				List<Map<String, Object>> facilities = new ArrayList<>(FACILITY_SLOT_COUNT);
				for (int facilityIndex = 0; facilityIndex < FACILITY_SLOT_COUNT; facilityIndex++)
				{
					Map<String, Object> facility = new LinkedHashMap<>();
					facility.put("slot", facilityIndex);
					int componentId = varbitReader.applyAsInt(FACILITY[boatIndex][facilityIndex]);
					facility.put("componentId", componentId);
					facility.put("extraData", varbitReader.applyAsInt(FACILITY_EXTRA[boatIndex][facilityIndex]));
					if (decoder != null)
					{
						facility.put("decoded", decoder.facility(typeId, facilityIndex, componentId));
					}
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
		result.put("activeBoat", correlateActiveBoat(
			boats,
			activeSlotSignal,
			playerOnPersonalBoat,
			boardedBoatType,
			storedBoardedBoatType,
			boardedNameParts
		));
		Map<String, Object> privacy = new LinkedHashMap<>();
		privacy.put("exactCoordinatesIncluded", false);
		privacy.put("worldNumberIncluded", false);
		privacy.put("nearbyPlayersIncluded", false);
		result.put("privacy", privacy);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> correlateActiveBoat(
		List<Map<String, Object>> boats,
		Integer slotSignal,
		Integer playerOnPersonalBoat,
		Integer boardedType,
		Integer storedBoardedType,
		List<Integer> boardedNameParts)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("status", "unresolved");
		if (playerOnPersonalBoat == null || playerOnPersonalBoat != 1)
		{
			result.put("reason", "not-aboard-personal-boat");
			return result;
		}
		if (slotSignal == null || slotSignal < 1 || slotSignal > BOAT_COUNT)
		{
			result.put("reason", "owned-slot-signal-unavailable");
			return result;
		}

		Map<String, Object> selected = null;
		for (Map<String, Object> boat : boats)
		{
			if (Integer.valueOf(slotSignal).equals(boat.get("slot")))
			{
				selected = boat;
				break;
			}
		}
		if (selected == null)
		{
			result.put("reason", "owned-slot-not-exported");
			return result;
		}

		int selectedType = selected.get("typeId") instanceof Number
			? ((Number) selected.get("typeId")).intValue() : 0;
		boolean liveTypeMatches = boardedType != null && boardedType > 0 && boardedType == selectedType;
		boolean storedTypeMatches = storedBoardedType != null && storedBoardedType > 0 && storedBoardedType == selectedType;
		if (!liveTypeMatches && !storedTypeMatches)
		{
			result.put("reason", "boat-type-signals-do-not-match-slot");
			return result;
		}

		Object rawNameParts = selected.get("nameParts");
		if (!(rawNameParts instanceof List) || boardedNameParts.contains(null)
			|| !rawNameParts.equals(boardedNameParts))
		{
			result.put("reason", "boat-name-signals-do-not-match-slot");
			return result;
		}

		result.put("status", "confirmed");
		result.put("id", selected.get("id"));
		result.put("slot", selected.get("slot"));
		result.put("typeId", selected.get("typeId"));
		result.put("decodedType", selected.get("decodedType"));
		result.put("decodedName", selected.get("decodedName"));
		result.put("correlation", Arrays.asList(
			"owned-slot-signal",
			liveTypeMatches ? "live-boat-type" : "stored-boat-type",
			"three-part-boat-name"
		));
		return result;
	}

	private static Integer safeRead(IntUnaryOperator reader, int varbit)
	{
		try
		{
			return reader.applyAsInt(varbit);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
