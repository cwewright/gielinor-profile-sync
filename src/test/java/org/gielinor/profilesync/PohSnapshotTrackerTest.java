package org.gielinor.profilesync;

import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PohSnapshotTrackerTest
{
	@Test
	public void requiresOwnHouseBuildingModeBeforeClaimingFurniture()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", "Enter");
		tracker.observeForTest(new Object(), 1001, "Oak larder", "Search", "Remove");

		Map<String, Object> visitorView = tracker.snapshot(100L, false, 3, 2, 5, null);
		assertFalse((Boolean) visitorView.get("loaded"));

		Map<String, Object> ownHouse = tracker.snapshot(200L, true, 3, 2, 5, null);
		assertTrue((Boolean) ownHouse.get("loaded"));
		assertFalse((Boolean) ownHouse.get("fromCache"));
		assertEquals("building-mode", ownHouse.get("ownershipEvidence"));
		assertEquals(1, ownHouse.get("furnitureCount"));
		assertEquals("Oak larder", furniture(ownHouse).get(0).get("name"));
		assertFalse(ownHouse.containsKey("coordinates"));
	}

	@Test
	public void retainsTheLastInspectionWithoutTurningUnknownObjectsIntoAbsence()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", "Enter");
		tracker.observeForTest(new Object(), 1002, "Fancy rejuvenation pool", "Drink", "Remove");
		tracker.observeForTest(new Object(), 1003, "Wall", "Examine");
		tracker.snapshot(300L, true, 1, 4, 7, null);
		tracker.clearScene();

		Map<String, Object> cached = tracker.snapshot(400L, false, 0, 0, 0, null);
		assertTrue((Boolean) cached.get("loaded"));
		assertTrue((Boolean) cached.get("fromCache"));
		assertEquals(300L, cached.get("lastSeenTimestamp"));
		assertEquals(1, cached.get("furnitureCount"));
	}

	@Test
	public void restoresTheLastInspectionAcrossPluginRestarts()
	{
		PohSnapshotTracker original = new PohSnapshotTracker();
		original.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", "Enter");
		original.observeForTest(new Object(), 1004, "Basic jewellery box", "Teleport", "Remove");
		Map<String, Object> inspection = original.snapshot(500L, true, 2, 1, 4, null);

		PohSnapshotTracker restarted = new PohSnapshotTracker();
		restarted.restore(inspection);
		Map<String, Object> cached = restarted.snapshot(600L, false, 0, 0, 0, null);
		assertTrue((Boolean) cached.get("loaded"));
		assertTrue((Boolean) cached.get("fromCache"));
		assertEquals(500L, cached.get("lastSeenTimestamp"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void exportsPortalRelativeRoomsAndFurnitureTilesWithoutWorldCoordinates()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", 40, 40, 0, "Enter");
		tracker.observeForTest(new Object(), 1005, "Oak larder", 49, 43, 0, "Search", "Remove");
		int[][][] chunks = emptyChunks();
		chunks[0][5][5] = packedChunk(0, 400, 500, 1);
		chunks[0][6][5] = packedChunk(0, 401, 500, 2);

		Map<String, Object> inspection = tracker.snapshot(700L, true, 3, 2, 5, chunks);
		List<Map<String, Object>> rooms = (List<Map<String, Object>>) inspection.get("rooms");
		List<Map<String, Object>> furniture = furniture(inspection);

		assertEquals(2, inspection.get("schemaVersion"));
		assertEquals(true, inspection.get("layoutLoaded"));
		assertEquals(2, rooms.size());
		assertEquals("0:0:0", rooms.get(0).get("id"));
		assertEquals("0:1:0", rooms.get(1).get("id"));
		assertEquals("0:1:0", furniture.get(0).get("roomId"));
		assertEquals(1, furniture.get(0).get("tileX"));
		assertEquals(3, furniture.get(0).get("tileY"));
		assertFalse(inspection.containsKey("coordinates"));
		assertEquals(false, inspection.get("exactCoordinatesIncluded"));
	}

	private static int[][][] emptyChunks()
	{
		int[][][] chunks = new int[4][13][13];
		for (int plane = 0; plane < chunks.length; plane++)
		{
			for (int x = 0; x < chunks[plane].length; x++)
			{
				java.util.Arrays.fill(chunks[plane][x], -1);
			}
		}
		return chunks;
	}

	private static int packedChunk(int plane, int chunkX, int chunkY, int rotation)
	{
		return plane << 24 | chunkX << 14 | chunkY << 3 | rotation << 1;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> furniture(Map<String, Object> snapshot)
	{
		return (List<Map<String, Object>>) snapshot.get("furniture");
	}
}
