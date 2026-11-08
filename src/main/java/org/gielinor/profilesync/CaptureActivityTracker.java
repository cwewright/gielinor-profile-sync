package org.gielinor.profilesync;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

final class CaptureActivityTracker
{
	private final Map<Skill, Integer> observedXp = new EnumMap<>(Skill.class);
	private Skill recentSkill;
	private long recentSkillAt;

	void observe(Skill skill, int xp, long observedAt)
	{
		Integer previousXp = observedXp.put(skill, xp);
		if (previousXp != null && xp > previousXp)
		{
			recentSkill = skill;
			recentSkillAt = observedAt;
		}
	}

	String recentSkillTag(long now, long maximumAgeMillis)
	{
		if (recentSkill == null || recentSkillAt <= 0 || now - recentSkillAt > maximumAgeMillis)
		{
			return null;
		}
		return recentSkill.getName();
	}

	void reset()
	{
		observedXp.clear();
		recentSkill = null;
		recentSkillAt = 0;
	}

	static String coarseLocationTag(WorldPoint worldPoint)
	{
		return worldPoint == null ? null : "region-" + worldPoint.getRegionID();
	}
}
