package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
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
		}
		Furniture furniture = Furniture.from(object.getId(), composition);
		if (furniture != null)
		{
			sceneFurniture.put(object, furniture);
		}
	}

	void observeForTest(Object key, int objectId, String name, String... actions)
	{
		if (objectId == ObjectID.POH_EXIT_PORTAL)
		{
			exitPortalObserved = true;
		}
		Furniture furniture = Furniture.from(objectId, name, actions);
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
			}
		}
	}

	void clearScene()
	{
		sceneFurniture.clear();
		exitPortalObserved = false;
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

	Map<String, Object> snapshot(long timestamp, boolean buildingMode, int houseLocation, int houseStyle, int houseSize)
	{
		if (buildingMode && exitPortalObserved)
		{
			List<Furniture> observed = new ArrayList<>(sceneFurniture.values());
			observed.sort(Comparator.comparing((Furniture value) -> value.name).thenComparingInt(value -> value.objectId));
			List<Map<String, Object>> furniture = new ArrayList<>();
			for (Furniture value : observed)
			{
				furniture.add(value.toMap());
			}

			Map<String, Object> inspection = new LinkedHashMap<>();
			inspection.put("schemaVersion", 1);
			inspection.put("loaded", true);
			inspection.put("fromCache", false);
			inspection.put("lastSeenTimestamp", timestamp);
			inspection.put("ownershipEvidence", "building-mode");
			inspection.put("houseLocationId", houseLocation);
			inspection.put("houseStyleId", houseStyle);
			inspection.put("houseSize", houseSize);
			inspection.put("furniture", furniture);
			inspection.put("furnitureCount", furniture.size());
			inspection.put("exactCoordinatesIncluded", false);
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
		unavailable.put("schemaVersion", 1);
		unavailable.put("loaded", false);
		unavailable.put("fromCache", false);
		unavailable.put("lastSeenTimestamp", 0L);
		unavailable.put("ownershipEvidence", "unknown");
		unavailable.put("furniture", new ArrayList<>());
		unavailable.put("furnitureCount", 0);
		unavailable.put("exactCoordinatesIncluded", false);
		unavailable.put("nearbyPlayersIncluded", false);
		return unavailable;
	}

	private static final class Furniture
	{
		private final int objectId;
		private final String name;
		private final List<String> actions;

		private Furniture(int objectId, String name, List<String> actions)
		{
			this.objectId = objectId;
			this.name = name;
			this.actions = actions;
		}

		private static Furniture from(int objectId, ObjectComposition composition)
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
			return from(objectId, composition.getName(), composition.getActions());
		}

		private static Furniture from(int objectId, String name, String[] rawActions)
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
			return removable ? new Furniture(objectId, name, actions) : null;
		}

		private Map<String, Object> toMap()
		{
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("objectId", objectId);
			result.put("name", name);
			result.put("actions", actions);
			return result;
		}
	}
}
