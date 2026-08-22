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

		Map<String, Object> visitorView = tracker.snapshot(100L, false, 3, 2, 5);
		assertFalse((Boolean) visitorView.get("loaded"));

		Map<String, Object> ownHouse = tracker.snapshot(200L, true, 3, 2, 5);
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
		tracker.snapshot(300L, true, 1, 4, 7);
		tracker.clearScene();

		Map<String, Object> cached = tracker.snapshot(400L, false, 0, 0, 0);
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
		Map<String, Object> inspection = original.snapshot(500L, true, 2, 1, 4);

		PohSnapshotTracker restarted = new PohSnapshotTracker();
		restarted.restore(inspection);
		Map<String, Object> cached = restarted.snapshot(600L, false, 0, 0, 0);
		assertTrue((Boolean) cached.get("loaded"));
		assertTrue((Boolean) cached.get("fromCache"));
		assertEquals(500L, cached.get("lastSeenTimestamp"));
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> furniture(Map<String, Object> snapshot)
	{
		return (List<Map<String, Object>>) snapshot.get("furniture");
	}
}
