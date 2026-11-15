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
		assertEquals(2, ownHouse.get("furnitureCount"));
		assertTrue(furniture(ownHouse).stream().anyMatch(item -> "Oak larder".equals(item.get("name"))));
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
		assertEquals(2, cached.get("furnitureCount"));
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
		List<Map<String, Object>> furniture = furniture(inspection).stream().filter(item -> "built".equals(item.get("state"))).collect(java.util.stream.Collectors.toList());

		assertEquals(3, inspection.get("schemaVersion"));
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

	@Test
	@SuppressWarnings("unchecked")
	public void ignoresUnoccupiedInstanceChunksAndLabelsHighConfidenceRooms()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", 40, 40, 0, "Enter");
		tracker.observeForTest(new Object(), 1006, "Oak larder", 49, 43, 0, "Search", "Remove");
		tracker.observeForTest(new Object(), 1007, "Teak cape rack", 57, 43, 0, "Search", "Remove");
		int[][][] chunks = emptyChunks();
		for (int x = 0; x < 13; x++)
		{
			for (int y = 0; y < 8; y++)
			{
				chunks[0][x][y] = packedChunk(0, 400 + x, 500 + y, 0);
			}
		}

		chunks[0][6][5] = packedChunk(0, 29 * 8 + 2, 110 * 8 + 7, 0);
		chunks[0][7][5] = packedChunk(0, 29 * 8 + 6, 110 * 8 + 1, 0);

		Map<String, Object> inspection = tracker.snapshot(800L, true, 3, 2, 5, chunks);
		List<Map<String, Object>> rooms = (List<Map<String, Object>>) inspection.get("rooms");

		assertEquals(3, rooms.size());
		assertEquals(3, inspection.get("roomCount"));
		assertEquals("Kitchen", rooms.get(1).get("roomType"));
		assertEquals(true, rooms.get(1).get("identityKnown"));
		assertEquals("Costume room", rooms.get(2).get("roomType"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void includesEmptyKnownRoomsAndSeparatesHotspotsFromInstalledFurniture()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", 40, 40, 0, "Enter");
		tracker.observeForTest(new Object(), ObjectID.POH_BED_2, "Bed", 49, 43, 0, "Remove");
		tracker.observeForTest(new Object(), 15261, "Wardrobe space", 50, 43, 0, "Build");
		int[][][] chunks = emptyChunks();
		chunks[0][5][5] = packedChunk(1, 30 * 8, 110 * 8 + 1, 0);
		chunks[0][6][5] = packedChunk(1, 30 * 8 + 6, 110 * 8 + 7, 2);
		chunks[1][5][5] = packedChunk(1, 30 * 8 + 7, 110 * 8 + 6, 1);
		// Grass padding must not become a room.
		chunks[0][7][5] = packedChunk(1, 30 * 8, 110 * 8, 0);
		Map<String, Object> value = tracker.snapshot(900L, true, 3, 5, 5, chunks);
		List<Map<String, Object>> rooms = (List<Map<String, Object>>) value.get("rooms");
		assertEquals(3, rooms.size());
		assertEquals("Garden", rooms.get(0).get("roomType"));
		assertEquals("Bedroom", rooms.get(1).get("roomType"));
		assertEquals("Quest hall", rooms.get(2).get("roomType"));
		assertEquals("template", rooms.get(2).get("identitySource"));
		assertTrue(((List<?>) rooms.get(2).get("furniture")).isEmpty());
		assertEquals(1, ((List<?>) rooms.get(1).get("hotspots")).size());
		assertEquals(2, value.get("furnitureCount"));
		assertTrue(furniture(value).stream().anyMatch(item -> "Oak bed".equals(item.get("displayName"))));
	}

	@Test
	public void templateDecodingIsBoundedAndIndependentOfFurnitureAndRotation()
	{
		for (int regionX = 29; regionX <= 31; regionX++)
		{
			for (int plane = 0; plane < 4; plane++)
			{
				for (int rotation = 0; rotation < 4; rotation++)
				{
					assertEquals("Parlour", PohCatalogue.roomType(packedChunk(plane, regionX * 8, 110 * 8 + 7, rotation)));
					assertEquals("League hall", PohCatalogue.roomType(packedChunk(plane, regionX * 8 + 5, 111 * 8, rotation)));
				}
			}
		}
		assertEquals(null, PohCatalogue.roomType(-1));
		assertEquals(null, PohCatalogue.roomType(packedChunk(0, 28 * 8, 110 * 8 + 7, 0)));
		assertEquals(null, PohCatalogue.roomType(packedChunk(0, 30 * 8, 109 * 8 + 7, 0)));
		assertEquals(null, PohCatalogue.roomType(packedChunk(0, 30 * 8, 111 * 8 + 7, 0)));
	}

	@Test
	public void accountResetCannotReusePreviousHouseEvidence()
	{
		PohSnapshotTracker tracker = new PohSnapshotTracker();
		tracker.observeForTest(new Object(), ObjectID.POH_EXIT_PORTAL, "Portal", "Enter");
		tracker.snapshot(1000L, true, 1, 1, 5, null);
		tracker.resetAccount();
		assertFalse((Boolean) tracker.snapshot(1100L, true, 1, 1, 5, null).get("loaded"));
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
