package org.gielinor.profilesync;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionLogAccountSnapshotsTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void preservesIndependentObservationsAcrossAccountRoundTrip()
	{
		CollectionLogAccountSnapshots accounts = new CollectionLogAccountSnapshots();
		accounts.activate("Sailor A").observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", true, 1)
		), 1_000L);
		accounts.activate("Sailor B").observePage("Barrows Chests", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(4708, "Ahrim's hood", false, 0)
		), 2_000L);

		Map<String, Object> restoredA = accounts.activate("sailor a").toMap();
		Map<String, Object> pagesA = (Map<String, Object>) restoredA.get("pages");
		assertTrue(pagesA.containsKey("abyssal-sire"));
		assertFalse(pagesA.containsKey("barrows-chests"));

		Map<String, Object> restoredB = accounts.activate("SAILOR B").toMap();
		Map<String, Object> pagesB = (Map<String, Object>) restoredB.get("pages");
		assertTrue(pagesB.containsKey("barrows-chests"));
		assertFalse(pagesB.containsKey("abyssal-sire"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void exportMergeRetainsStoredPagesAndNewerCurrentState()
	{
		CollectionLogSnapshot stored = new CollectionLogSnapshot();
		stored.observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", true, 1)
		), 1_000L);
		CollectionLogSnapshot current = new CollectionLogSnapshot();
		current.observePage("Barrows Chests", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(4708, "Ahrim's hood", false, 0)
		), 2_000L);

		Map<String, Object> merged = (Map<String, Object>) CollectionLogAccountSnapshots.mergeForExport(stored.toMap(), current.toMap());
		Map<String, Object> pages = (Map<String, Object>) merged.get("pages");
		assertEquals(2, pages.size());
		assertTrue(pages.containsKey("abyssal-sire"));
		assertTrue(pages.containsKey("barrows-chests"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void activatingObservationAccountBeforeFirstTickCannotLeakPreviousAccount()
	{
		CollectionLogAccountSnapshots accounts = new CollectionLogAccountSnapshots();
		accounts.activate("Sailor A").observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", true, 1)
		), 1_000L);

		accounts.activate("Sailor B").observePage("Barrows Chests", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(4708, "Ahrim's hood", true, 1)
		), 2_000L);

		Map<String, Object> pagesA = (Map<String, Object>) accounts.activate("Sailor A").toMap().get("pages");
		Map<String, Object> pagesB = (Map<String, Object>) accounts.activate("Sailor B").toMap().get("pages");
		assertTrue(pagesA.containsKey("abyssal-sire"));
		assertFalse(pagesA.containsKey("barrows-chests"));
		assertTrue(pagesB.containsKey("barrows-chests"));
		assertFalse(pagesB.containsKey("abyssal-sire"));
	}
}
