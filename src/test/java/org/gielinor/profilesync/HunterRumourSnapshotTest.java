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
	public void exportsExplicitCompletionTotalsIncludingLegacyColoursAndCommas()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertTrue(tracker.observe(ChatMessageType.GAMEMESSAGE,
			"You have completed @red@1,234</col> rumours for the Hunter Guild.", false, 100L));
		assertEquals(1234, tracker.snapshot().get("completionCount"));
		assertEquals(true, tracker.snapshot().get("loaded"));
		assertNull(tracker.snapshot().get("current"));
		assertEquals("unavailable", tracker.snapshot().get("status"));
		assertTrue(tracker.observe(ChatMessageType.GAMEMESSAGE,
			"You have completed <col=ff0000>1</col> rumour for the Hunter Guild.", false, 200L));
		assertEquals(1, tracker.snapshot().get("completionCount"));
		assertTrue(tracker.observe(ChatMessageType.GAMEMESSAGE,
			"You have completed 0 rumours for the Hunter Guild.", false, 300L));
		assertEquals(0, tracker.snapshot().get("completionCount"));
	}

	@Test
	public void repeatedTotalsAreNotAddedAndOrdinaryTurnInsDoNotGuessCounts()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		String total = "You have completed 42 rumours for the Hunter Guild.";
		tracker.observe(ChatMessageType.GAMEMESSAGE, total, false, 100L);
		tracker.observe(ChatMessageType.GAMEMESSAGE, total, false, 200L);
		tracker.observe(ChatMessageType.DIALOG, "Guild Hunter Teco|Here's your reward. Would you like another rumour?", true, 300L);
		assertEquals(42, tracker.snapshot().get("completionCount"));
		assertEquals("none", tracker.snapshot().get("status"));
		tracker.observe(ChatMessageType.GAMEMESSAGE, "You have completed 43 rumours for the Hunter Guild.", false, 400L);
		assertEquals(43, tracker.snapshot().get("completionCount"));
	}

	@Test
	public void retainsTotalsAcrossRestartButClearsThemForAnotherAccount()
	{
		HunterRumourSnapshot original = new HunterRumourSnapshot();
		original.observe(ChatMessageType.GAMEMESSAGE, "You have completed 42 rumours for the Hunter Guild.", false, 100L);
		HunterRumourSnapshot restored = new HunterRumourSnapshot();
		restored.restore(original.snapshot());
		assertEquals(42, restored.snapshot().get("completionCount"));
		assertEquals(true, restored.snapshot().get("fromCache"));
		restored.observe(ChatMessageType.GAMEMESSAGE, "Your current rumour target is a grey chinchompa.", false, 200L);
		assertEquals(42, restored.snapshot().get("completionCount"));
		restored.resetAccount();
		assertNull(restored.snapshot().get("completionCount"));
	}

	@Test
	public void restoresTotalsWhenTheCurrentAssignmentWasAlreadyObserved()
	{
		HunterRumourSnapshot saved = new HunterRumourSnapshot();
		saved.observe(ChatMessageType.GAMEMESSAGE, "You have completed 41 rumours for the Hunter Guild.", false, 100L);
		HunterRumourSnapshot current = new HunterRumourSnapshot();
		current.observe(ChatMessageType.GAMEMESSAGE, "Your current rumour target is a grey chinchompa.", false, 200L);
		current.restore(saved.snapshot());
		assertEquals(41, current.snapshot().get("completionCount"));
		current.observe(ChatMessageType.GAMEMESSAGE, "You have completed 42 rumours for the Hunter Guild.", false, 300L);
		current.restore(saved.snapshot());
		assertEquals(42, current.snapshot().get("completionCount"));
	}

	@Test
	public void rejectsOtherChatTypesMalformedCountsAndUnrelatedTotals()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertFalse(tracker.observe(ChatMessageType.PUBLICCHAT, "You have completed 99 rumours for the Hunter Guild.", false, 100L));
		for (String count : new String[]{"-1", "1.5", "1,23", "99999999999999999999999"})
		{
			assertFalse(tracker.observe(ChatMessageType.GAMEMESSAGE, "You have completed " + count + " rumours for the Hunter Guild.", false, 100L));
		}
		assertFalse(tracker.observe(ChatMessageType.GAMEMESSAGE, "You have completed 99 tasks for the Slayer Guild.", false, 100L));
		assertNull(tracker.snapshot().get("completionCount"));
	}

	@Test
	public void oldExportsAndInvalidSavedTotalsRemainUnknown()
	{
		HunterRumourSnapshot original = new HunterRumourSnapshot();
		original.observe(ChatMessageType.GAMEMESSAGE, "Your current rumour target is a grey chinchompa.", false, 100L);
		for (Object count : new Object[]{null, -1, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648L, "42"})
		{
			Map<String, Object> saved = original.snapshot();
			saved.put("completionCount", count);
			HunterRumourSnapshot restored = new HunterRumourSnapshot();
			restored.restore(saved);
			assertNull(restored.snapshot().get("completionCount"));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void recognizesRankedHunterNamesInCurrentGuildDialogue()
	{
		HunterRumourSnapshot tracker = new HunterRumourSnapshot();
		assertTrue(tracker.observe(
			ChatMessageType.DIALOG,
			"Guild Hunter Teco (Expert)|You're hunting for me at the moment. This rumour<br>"
				+ "was about a sabre-toothed kyatt. You'll need to bring<br>back one of its teeth.",
			true,
			100L
		));

		Map<String, Object> snapshot = tracker.snapshot();
		Map<String, Object> current = (Map<String, Object>) snapshot.get("current");
		assertEquals("active", snapshot.get("status"));
		assertEquals("teco", current.get("hunterKey"));
		assertEquals("expert", current.get("hunterTier"));
		assertEquals("Sabre-toothed kyatt", current.get("rumour"));
		assertEquals(false, current.get("complete"));
	}

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
