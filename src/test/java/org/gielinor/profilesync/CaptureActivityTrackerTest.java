package org.gielinor.profilesync;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CaptureActivityTrackerTest
{
	@Test
	public void tagsOnlyRealRecentXpChanges()
	{
		CaptureActivityTracker tracker = new CaptureActivityTracker();
		tracker.observe(Skill.ATTACK, 1_000, 1_000L);
		assertNull(tracker.recentSkillTag(1_100L, 5_000L));

		tracker.observe(Skill.ATTACK, 1_010, 2_000L);
		assertEquals("Attack", tracker.recentSkillTag(3_000L, 5_000L));
		assertNull(tracker.recentSkillTag(8_001L, 5_000L));
	}

	@Test
	public void resetPreventsSkillTagsCrossingAccountSessions()
	{
		CaptureActivityTracker tracker = new CaptureActivityTracker();
		tracker.observe(Skill.FISHING, 2_000, 1_000L);
		tracker.observe(Skill.FISHING, 2_100, 2_000L);
		tracker.reset();
		assertNull(tracker.recentSkillTag(2_001L, 5_000L));
	}

	@Test
	public void locationTagKeepsOnlyTheCoarseMapRegion()
	{
		assertEquals("region-12850", CaptureActivityTracker.coarseLocationTag(new WorldPoint(3200, 3200, 0)));
		assertNull(CaptureActivityTracker.coarseLocationTag(null));
	}
}
