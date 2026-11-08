package org.gielinor.profilesync;

import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HunterRumourSnapshotTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void whistleObservationCapturesTheCurrentHunterAndRumour()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertTrue(tracker.observe(
			ChatMessageType.GAMEMESSAGE,
			"Your current rumour target is Pyre fox, assigned by Guild Hunter Teco.",
			false,
			100L
		));

		Map<String, Object> snapshot = tracker.snapshot();
		Map<String, Object> current = (Map<String, Object>) snapshot.get("current");
		assertEquals("active", snapshot.get("status"));
		assertEquals("teco", current.get("hunterKey"));
		assertEquals("expert", current.get("hunterTier"));
		assertEquals("Pyre fox", current.get("rumour"));
		assertEquals(false, current.get("complete"));
		assertNull(snapshot.get("completionCount"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void realWhistleMessageRetainsTheRumourWhenTheHunterIsNotNamed()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertTrue(tracker.observe(
			ChatMessageType.GAMEMESSAGE,
			"Your current rumour target is a grey chinchompa.",
			false,
			150L
		));

		Map<String, Object> snapshot = tracker.snapshot();
		Map<String, Object> current = (Map<String, Object>) snapshot.get("current");
		assertEquals("active", snapshot.get("status"));
		assertEquals("Grey chinchompa", current.get("rumour"));
		assertFalse(current.containsKey("hunterKey"));
		assertEquals(0, ((List<?>) snapshot.get("assignments")).size());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void unattributedWhistleObservationRestoresAndCanComplete()
	{
		HunterRumourSnapshot original = new HunterRumourSnapshot();
		original.observe(ChatMessageType.GAMEMESSAGE,
			"Your current rumour target is a grey chinchompa.", false, 175L);

		HunterRumourSnapshot restored = new HunterRumourSnapshot();
		restored.restore(original.snapshot());
		assertTrue(restored.observe(ChatMessageType.GAMEMESSAGE,
			"You find a rare piece of the creature! You should take it back to the Hunter Guild.", false, 200L));

		Map<String, Object> snapshot = restored.snapshot();
		assertEquals("complete", snapshot.get("status"));
		assertEquals(true, ((Map<String, Object>) snapshot.get("current")).get("complete"));
		assertFalse(((Map<String, Object>) snapshot.get("current")).containsKey("hunterKey"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void dialogueCanRetainMultipleObservedAssignmentsWithoutInventingTheOthers()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertFalse(tracker.observe(
			ChatMessageType.DIALOG,
			"Guild Hunter Ornus|I've heard reports of a Sabre-toothed kyatt.",
			false,
			100L
		));
		assertTrue(tracker.observe(
			ChatMessageType.DIALOG,
			"Guild Hunter Ornus|I've heard reports of a Sabre-toothed kyatt.",
			true,
			200L
		));

		Map<String, Object> snapshot = tracker.snapshot();
		List<Map<String, Object>> assignments = (List<Map<String, Object>>) snapshot.get("assignments");
		assertEquals(1, assignments.size());
		assertEquals("Ornus", assignments.get(0).get("hunterName"));
		assertEquals(true, snapshot.get("unobservedAssignmentsAreUnknown"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void completionAndTurnInHaveDistinctObservedStates()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		tracker.observe(ChatMessageType.GAMEMESSAGE,
			"Your current rumour target is Moonlight moth, assigned by Guild Hunter Wolf.", false, 100L);
		tracker.observe(ChatMessageType.GAMEMESSAGE,
			"You find a rare piece of the creature! You should take it back to the Hunter Guild.", false, 200L);
		Map<String, Object> complete = tracker.snapshot();
		assertEquals("complete", complete.get("status"));
		assertEquals(true, ((Map<String, Object>) complete.get("current")).get("complete"));

		tracker.observe(ChatMessageType.DIALOG,
			"Guild Hunter Wolf|Here's your reward. Would you like another rumour?", true, 300L);
		Map<String, Object> none = tracker.snapshot();
		assertEquals("none", none.get("status"));
		assertNull(none.get("current"));
	}

	@Test
	public void restoredObservationIsExplicitlyCached()
	{
		HunterRumourSnapshot original = new HunterRumourSnapshot();
		original.observe(ChatMessageType.GAMEMESSAGE,
			"Your current rumour target is Herbiboar, assigned by Guild Hunter Wolf.", false, 400L);

		HunterRumourSnapshot restored = new HunterRumourSnapshot();
		restored.restore(original.snapshot());
		Map<String, Object> snapshot = restored.snapshot();
		assertEquals(true, snapshot.get("loaded"));
		assertEquals(true, snapshot.get("fromCache"));
		assertEquals("active", snapshot.get("status"));
	}
}
