package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Retains a privacy-safe inspection of the player's own house. An inspection is
 * authoritative only while building mode is active and the POH exit portal is
 * present; this avoids mistaking another player's house for the account's POH.
 */
final class PohSnapshotTracker
{
	private final Map<Object, Furniture> sceneFurniture = new IdentityHashMap<>();
	private boolean exitPortalObserved;
	private int exitPortalSceneX;
	private int exitPortalSceneY;
	private int exitPortalPlane;
	private volatile Map<String, Object> lastInspection;

	void observe(TileObject object, ObjectComposition composition)
	{
		if (object == null)
		{
			return;
		}
		if (object.getId() == ObjectID.POH_EXIT_PORTAL)
		{
			exitPortalObserved = true;
			LocalPoint location = object.getLocalLocation();
			exitPortalSceneX = location.getSceneX();
			exitPortalSceneY = location.getSceneY();
			exitPortalPlane = object.getPlane();
		}
		LocalPoint location = object.getLocalLocation();
		Furniture furniture = Furniture.from(
			object.getId(),
			composition,
			location.getSceneX(),
			location.getSceneY(),
			object.getPlane()
		);
		if (furniture != null)
		{
			sceneFurniture.put(object, furniture);
		}
	}

	void observeForTest(Object key, int objectId, String name, String... actions)
	{
		observeForTest(key, objectId, name, 0, 0, 0, actions);
	}

	void observeForTest(Object key, int objectId, String name, int sceneX, int sceneY, int plane, String... actions)
	{
		if (objectId == ObjectID.POH_EXIT_PORTAL)
		{
			exitPortalObserved = true;
			exitPortalSceneX = sceneX;
			exitPortalSceneY = sceneY;
			exitPortalPlane = plane;
		}
		Furniture furniture = Furniture.from(objectId, name, sceneX, sceneY, plane, actions);
		if (furniture != null)
		{
			sceneFurniture.put(key, furniture);
		}
	}

	void forget(TileObject object)
	{
		if (object != null)
		{
			sceneFurniture.remove(object);
			if (object.getId() == ObjectID.POH_EXIT_PORTAL)
			{
				exitPortalObserved = false;
				exitPortalSceneX = 0;
				exitPortalSceneY = 0;
				exitPortalPlane = 0;
			}
		}
	}

	void clearScene()
	{
		sceneFurniture.clear();
		exitPortalObserved = false;
		exitPortalSceneX = 0;
		exitPortalSceneY = 0;
		exitPortalPlane = 0;
	}

	void resetAccount()
	{
		lastInspection = null;
	}

	void restore(Object value)
	{
		if (lastInspection != null)
		{
			return;
		}
		if (!(value instanceof Map))
		{
			return;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> candidate = (Map<String, Object>) value;
		if (Boolean.TRUE.equals(candidate.get("loaded")) && "building-mode".equals(candidate.get("ownershipEvidence"))
			&& candidate.get("furniture") instanceof List)
		{
			lastInspection = new LinkedHashMap<>(candidate);
		}
	}

	Map<String, Object> snapshot(
		long timestamp,
		boolean buildingMode,
		int houseLocation,
		int houseStyle,
		int houseSize,
		int[][][] instanceTemplateChunks
	)
	{
		if (buildingMode && exitPortalObserved)
		{
			List<Furniture> observed = new ArrayList<>(sceneFurniture.values());
			List<Room> rooms = buildRooms(observed, instanceTemplateChunks);
			Map<String, Room> roomBySceneChunk = new LinkedHashMap<>();
			for (Room room : rooms)
			{
				roomBySceneChunk.put(room.sceneKey(), room);
			}
			observed.sort(Comparator
				.comparingInt((Furniture value) -> value.plane)
				.thenComparingInt(value -> value.sceneY)
				.thenComparingInt(value -> value.sceneX)
				.thenComparing(value -> value.name)
				.thenComparingInt(value -> value.objectId));
			List<Map<String, Object>> furniture = new ArrayList<>();
			for (Furniture value : observed)
			{
				Room room = roomBySceneChunk.get(Room.sceneKey(value.plane, value.sceneX / 8, value.sceneY / 8));
				Map<String, Object> mapped = value.toMap(room);
				furniture.add(mapped);
				if (room != null)
				{
					room.furniture.add(mapped);
				}
			}
			List<Map<String, Object>> mappedRooms = new ArrayList<>();
			for (Room room : rooms)
			{
				mappedRooms.add(room.toMap());
			}

			Map<String, Object> inspection = new LinkedHashMap<>();
			inspection.put("schemaVersion", 2);
			inspection.put("loaded", true);
			inspection.put("fromCache", false);
			inspection.put("lastSeenTimestamp", timestamp);
			inspection.put("ownershipEvidence", "building-mode");
			inspection.put("houseLocationId", houseLocation);
			inspection.put("houseStyleId", houseStyle);
			inspection.put("houseSize", houseSize);
			inspection.put("furniture", furniture);
			inspection.put("furnitureCount", furniture.size());
			inspection.put("layoutLoaded", !rooms.isEmpty());
			inspection.put("layoutCoordinateSystem", "exit-portal-relative-room-grid");
			inspection.put("roomCount", mappedRooms.size());
			inspection.put("rooms", mappedRooms);
			inspection.put("exactCoordinatesIncluded", false);
			inspection.put("templateReferencesIncluded", true);
			inspection.put("nearbyPlayersIncluded", false);
			lastInspection = inspection;
			return inspection;
		}

		Map<String, Object> retained = lastInspection;
		if (retained != null)
		{
			Map<String, Object> cached = new LinkedHashMap<>(retained);
			cached.put("fromCache", true);
			return cached;
		}

		Map<String, Object> unavailable = new LinkedHashMap<>();
		unavailable.put("schemaVersion", 2);
		unavailable.put("loaded", false);
		unavailable.put("fromCache", false);
		unavailable.put("lastSeenTimestamp", 0L);
		unavailable.put("ownershipEvidence", "unknown");
		unavailable.put("furniture", new ArrayList<>());
		unavailable.put("furnitureCount", 0);
		unavailable.put("layoutLoaded", false);
		unavailable.put("layoutCoordinateSystem", "exit-portal-relative-room-grid");
		unavailable.put("roomCount", 0);
		unavailable.put("rooms", new ArrayList<>());
		unavailable.put("exactCoordinatesIncluded", false);
		unavailable.put("templateReferencesIncluded", false);
		unavailable.put("nearbyPlayersIncluded", false);
		return unavailable;
	}

	private List<Room> buildRooms(List<Furniture> furniture, int[][][] instanceTemplateChunks)
	{
		Map<String, Room> rooms = new LinkedHashMap<>();
		int anchorChunkX = Math.floorDiv(exitPortalSceneX, 8);
		int anchorChunkY = Math.floorDiv(exitPortalSceneY, 8);
		if (instanceTemplateChunks != null)
		{
			for (int plane = 0; plane < instanceTemplateChunks.length; plane++)
			{
				int[][] planeChunks = instanceTemplateChunks[plane];
				if (planeChunks == null)
				{
					continue;
				}
				for (int chunkX = 0; chunkX < planeChunks.length; chunkX++)
				{
					if (planeChunks[chunkX] == null)
					{
						continue;
					}
					for (int chunkY = 0; chunkY < planeChunks[chunkX].length; chunkY++)
					{
						int template = planeChunks[chunkX][chunkY];
						if (template == -1)
						{
							continue;
						}
						Room room = new Room(plane, chunkX, chunkY, chunkX - anchorChunkX, chunkY - anchorChunkY, plane - exitPortalPlane, template);
						rooms.put(room.sceneKey(), room);
					}
				}
			}
		}
		for (Furniture value : furniture)
		{
			int chunkX = Math.floorDiv(value.sceneX, 8);
			int chunkY = Math.floorDiv(value.sceneY, 8);
			String sceneKey = Room.sceneKey(value.plane, chunkX, chunkY);
			rooms.computeIfAbsent(sceneKey, ignored -> new Room(
				value.plane,
				chunkX,
				chunkY,
				chunkX - anchorChunkX,
				chunkY - anchorChunkY,
				value.plane - exitPortalPlane,
				-1
			));
		}
		List<Room> output = new ArrayList<>(rooms.values());
		output.sort(Comparator
			.comparingInt((Room value) -> value.relativePlane)
			.thenComparingInt(value -> value.relativeY)
			.thenComparingInt(value -> value.relativeX));
		return output.size() > 128 ? new ArrayList<>(output.subList(0, 128)) : output;
	}

	private static final class Furniture
	{
		private final int objectId;
		private final String name;
		private final List<String> actions;
		private final int sceneX;
		private final int sceneY;
		private final int plane;

		private Furniture(int objectId, String name, List<String> actions, int sceneX, int sceneY, int plane)
		{
			this.objectId = objectId;
			this.name = name;
			this.actions = actions;
			this.sceneX = sceneX;
			this.sceneY = sceneY;
			this.plane = plane;
		}

		private static Furniture from(int objectId, ObjectComposition composition, int sceneX, int sceneY, int plane)
		{
			if (composition == null)
			{
				return null;
			}
			ObjectComposition transformed = composition.getImpostorIds() == null ? composition : composition.getImpostor();
			if (transformed != null)
			{
				composition = transformed;
			}
			return from(objectId, composition.getName(), sceneX, sceneY, plane, composition.getActions());
		}

		private static Furniture from(int objectId, String name, int sceneX, int sceneY, int plane, String[] rawActions)
		{
			if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name))
			{
				return null;
			}
			List<String> actions = new ArrayList<>();
			boolean removable = false;
			if (rawActions != null)
			{
				for (String action : rawActions)
				{
					if (action != null && !action.trim().isEmpty())
					{
						actions.add(action);
						removable |= "remove".equalsIgnoreCase(action);
					}
				}
			}
			return removable ? new Furniture(objectId, name, actions, sceneX, sceneY, plane) : null;
		}

		private Map<String, Object> toMap(Room room)
		{
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("objectId", objectId);
			result.put("name", name);
			result.put("actions", actions);
			result.put("roomId", room == null ? null : room.id());
			result.put("tileX", Math.floorMod(sceneX, 8));
			result.put("tileY", Math.floorMod(sceneY, 8));
			result.put("plane", room == null ? plane : room.relativePlane);
			return result;
		}
	}

	private static final class Room
	{
		private final int plane;
		private final int sceneChunkX;
		private final int sceneChunkY;
		private final int relativeX;
		private final int relativeY;
		private final int relativePlane;
		private final int template;
		private final List<Map<String, Object>> furniture = new ArrayList<>();

		private Room(int plane, int sceneChunkX, int sceneChunkY, int relativeX, int relativeY, int relativePlane, int template)
		{
			this.plane = plane;
			this.sceneChunkX = sceneChunkX;
			this.sceneChunkY = sceneChunkY;
			this.relativeX = relativeX;
			this.relativeY = relativeY;
			this.relativePlane = relativePlane;
			this.template = template;
		}

		private String id()
		{
			return relativePlane + ":" + relativeX + ":" + relativeY;
		}

		private String sceneKey()
		{
			return sceneKey(plane, sceneChunkX, sceneChunkY);
		}

		private static String sceneKey(int plane, int chunkX, int chunkY)
		{
			return plane + ":" + chunkX + ":" + chunkY;
		}

		private Map<String, Object> toMap()
		{
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("id", id());
			value.put("x", relativeX);
			value.put("y", relativeY);
			value.put("plane", relativePlane);
			value.put("identityKnown", false);
			value.put("roomType", null);
			if (template >= 0)
			{
				int rotation = template >> 1 & 0x3;
				int templateChunkY = template >> 3 & 0x7FF;
				int templateChunkX = template >> 14 & 0x3FF;
				int templatePlane = template >> 24 & 0x3;
				int templateRegionId = (templateChunkX >> 3) << 8 | templateChunkY >> 3;
				int templateChunkIndex = (templateChunkX & 0x7) << 3 | templateChunkY & 0x7;
				value.put("rotation", rotation);
				value.put("templatePlane", templatePlane);
				value.put("templateRegionId", templateRegionId);
				value.put("templateChunkIndex", templateChunkIndex);
			}
			value.put("furniture", furniture);
			value.put("furnitureCount", furniture.size());
			return value;
		}
	}
}
