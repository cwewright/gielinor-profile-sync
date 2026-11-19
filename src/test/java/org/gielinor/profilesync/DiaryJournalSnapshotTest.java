package org.gielinor.profilesync;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiaryJournalSnapshotTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void capturesTaskStrikesButNotStruckRequirements()
	{
		DiaryJournalSnapshot journal = new DiaryJournalSnapshot();
		journal.observe("Achievement Diary - Kourend & Kebos", Arrays.asList(
			"Kourend & Kebos Tasks", "<col=ffff00>Easy</col>",
			"<str>Create a Strength potion in the Lovakengj Pub.</str><col=ffffff>(12 Herblore)",
			"Fish Trout from the River Molch.<str>(20 Fishing)</str>", "Medium", "Travel somewhere."), 100);
		Map<?, ?> saved = (Map<?, ?>) journal.snapshot().get("kourend_kebos");
		Map<?, ?> tiers = (Map<?, ?>) saved.get("tiers");
		List<Map<?, ?>> easy = (List<Map<?, ?>>) tiers.get("easy");
		assertEquals(true, easy.get(0).get("complete"));
		assertEquals(false, easy.get(1).get("complete"));
		assertEquals("Create a Strength potion in the Lovakengj Pub.(12 Herblore)", easy.get(0).get("text"));
		assertTrue(tiers.containsKey("medium"));
	}

	@Test
	public void retainsObservationsAndClearsForAnotherAccount()
	{
		DiaryJournalSnapshot journal = new DiaryJournalSnapshot();
		journal.observe("Achievement Diary", Arrays.asList("Ardougne area tasks", "Easy", "<str>Old task"), 100);
		Map<String, Object> saved = journal.snapshot();
		journal.resetAccount();
		assertTrue(journal.snapshot().isEmpty());
		journal.restore(saved);
		journal.observe("Achievement Diary", Arrays.asList("Ardougne area tasks", "Easy", "New task"), 200);
		journal.restore(saved);
		assertEquals(200L, ((Map<?, ?>) journal.snapshot().get("ardougne")).get("observedAt"));
		journal.observe("A quest", Arrays.asList("Ardougne area tasks", "Easy", "<str>Wrong task"), 300);
		assertEquals(200L, ((Map<?, ?>) journal.snapshot().get("ardougne")).get("observedAt"));
	}
}
