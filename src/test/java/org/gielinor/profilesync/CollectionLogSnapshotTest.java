package org.gielinor.profilesync;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionLogSnapshotTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void recordsOnlyExplicitlyObservedPageSlots()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		long observedAt = Instant.parse("2026-08-20T12:30:00Z").toEpochMilli();
		assertTrue(snapshot.observePage("Bosses", "Abyssal Sire", Arrays.asList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", true, 1),
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", false, 0)
		), observedAt));

		Map<String, Object> exported = snapshot.toMap();
		assertEquals("partial", exported.get("status"));
		assertEquals("observed-pages-only", exported.get("coverage"));
		assertEquals(1, exported.get("observedPageCount"));
		assertEquals(2, exported.get("observedSlotCount"));
		assertEquals(1, exported.get("obtainedObservedSlotCount"));

		Map<String, Object> pages = (Map<String, Object>) exported.get("pages");
		Map<String, Object> page = (Map<String, Object>) pages.get("abyssal-sire");
		assertEquals("Bosses", page.get("category"));
		assertEquals("2026-08-20T12:30:00Z", page.get("observedAt"));
		assertEquals(1, page.get("obtainedSlotCount"));
		assertEquals(1, page.get("missingSlotCount"));
		Map<String, Object> entries = (Map<String, Object>) page.get("entries");
		assertEquals(true, ((Map<String, Object>) entries.get("13262")).get("obtained"));
		assertEquals(false, ((Map<String, Object>) entries.get("7979")).get("obtained"));
		assertFalse(pages.containsKey("unvisited-page"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void newerPageObservationReplacesOlderState()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		snapshot.observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", false, 0)
		), 1_000L);
		snapshot.observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", true, 2)
		), 2_000L);
		snapshot.observePage("Abyssal Sire", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", false, 0)
		), 1_500L);

		Map<String, Object> pages = (Map<String, Object>) snapshot.toMap().get("pages");
		Map<String, Object> page = (Map<String, Object>) pages.get("abyssal-sire");
		Map<String, Object> entries = (Map<String, Object>) page.get("entries");
		Map<String, Object> item = (Map<String, Object>) entries.get("7979");
		assertEquals(true, item.get("obtained"));
		assertEquals(2, item.get("quantity"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void refusesTransientProgressRegression()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		snapshot.observePage("Bosses", "Abyssal Sire", Arrays.asList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", true, 1),
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", false, 0)
		), 1_000L);
		assertFalse(snapshot.observePage("Bosses", "Abyssal Sire", Arrays.asList(
			new CollectionLogSnapshot.ItemObservation(13262, "Abyssal orphan", false, 0),
			new CollectionLogSnapshot.ItemObservation(7979, "Abyssal head", false, 0)
		), 2_000L));

		Map<String, Object> pages = (Map<String, Object>) snapshot.toMap().get("pages");
		Map<String, Object> page = (Map<String, Object>) pages.get("abyssal-sire");
		assertEquals(1, page.get("obtainedSlotCount"));
	}

	@Test
	public void recordsAuthoritativeUniqueCountsSeparatelyFromObservedSlots()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		snapshot.observeUniqueCounts(425, 1_699, Instant.parse("2026-08-20T12:30:00Z").toEpochMilli());
		Map<String, Object> exported = snapshot.toMap();
		assertEquals(425, exported.get("uniqueObtainedCount"));
		assertEquals(1_699, exported.get("uniqueItemCount"));
		assertEquals("2026-08-20T12:30:00Z", exported.get("uniqueCountsObservedAt"));
	}

	@Test
	public void roundTripsStoredObservationsAndKeepsUnknownWhenEmpty()
	{
		CollectionLogSnapshot empty = new CollectionLogSnapshot();
		assertEquals("unavailable", empty.toMap().get("status"));
		assertEquals(0, empty.toMap().get("observedPageCount"));

		CollectionLogSnapshot original = new CollectionLogSnapshot();
		original.observePage("Barrows Chests", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(4708, "Ahrim's hood", true, 3)
		), Instant.parse("2026-08-20T12:30:00Z").toEpochMilli());
		CollectionLogSnapshot restored = new CollectionLogSnapshot();
		restored.restore(original.toMap());
		assertEquals(original.toMap(), restored.toMap());
	}

	@Test
	public void rejectsPagesWithoutValidSlots()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		assertFalse(snapshot.observePage("", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(1, "Item", true, 1)
		), 1L));
		assertFalse(snapshot.observePage("Overview", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(-1, "", false, 0)
		), 1L));
		assertEquals(0, snapshot.toMap().get("observedPageCount"));
	}

	@Test
	public void rejectsObservationsWithoutRealTimestamps()
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		assertFalse(snapshot.observePage("Overview", Collections.singletonList(
			new CollectionLogSnapshot.ItemObservation(1, "Item", true, 1)
		), 0L));
		snapshot.observeUniqueCounts(1, 10, 0L);
		assertEquals(0, snapshot.toMap().get("observedPageCount"));
		assertFalse(snapshot.toMap().containsKey("uniqueCountsObservedAt"));
	}
}
