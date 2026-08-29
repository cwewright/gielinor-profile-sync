package org.gielinor.profilesync;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AchievementDiarySnapshotTest
{
	private static final List<String> REGION_KEYS = Arrays.asList(
		"ardougne", "desert", "falador", "fremennik", "kandarin", "karamja",
		"kourend_kebos", "lumbridge_draynor", "morytania", "varrock",
		"western_provinces", "wilderness"
	);

	@Test
	@SuppressWarnings("unchecked")
	public void exportsAllTwelveRegionsAndTheVerifiedCatalogueTotals()
	{
		Map<String, Object> snapshot = AchievementDiarySnapshot.build(varbitId -> 0);
		Map<String, Object> regions = (Map<String, Object>) snapshot.get("regions");

		assertEquals(2, snapshot.get("schemaVersion"));
		assertEquals("runelite-1.12.35-varbits", snapshot.get("sourceVersion"));
		assertEquals(REGION_KEYS, Arrays.asList(regions.keySet().toArray(new String[0])));
		assertEquals(48, snapshot.get("totalTierCount"));
		assertEquals(0, snapshot.get("completedTierCount"));
		assertEquals(false, snapshot.get("allComplete"));

		Map<String, int[]> expectedTotals = new LinkedHashMap<>();
		expectedTotals.put("ardougne", new int[]{10, 12, 12, 8});
		expectedTotals.put("desert", new int[]{11, 12, 10, 6});
		expectedTotals.put("falador", new int[]{11, 14, 11, 6});
		expectedTotals.put("fremennik", new int[]{10, 9, 9, 6});
		expectedTotals.put("kandarin", new int[]{11, 14, 11, 7});
		expectedTotals.put("karamja", new int[]{10, 19, 10, 5});
		expectedTotals.put("kourend_kebos", new int[]{12, 13, 10, 8});
		expectedTotals.put("lumbridge_draynor", new int[]{12, 12, 11, 6});
		expectedTotals.put("morytania", new int[]{11, 11, 10, 6});
		expectedTotals.put("varrock", new int[]{14, 13, 10, 5});
		expectedTotals.put("western_provinces", new int[]{11, 13, 13, 7});
		expectedTotals.put("wilderness", new int[]{12, 11, 10, 7});

		int taskTotal = 0;
		for (Map.Entry<String, Object> regionEntry : regions.entrySet())
		{
			Map<String, Object> region = (Map<String, Object>) regionEntry.getValue();
			int[] actualTotals = new int[4];
			int tierIndex = 0;
			for (String tierName : Arrays.asList("easy", "medium", "hard", "elite"))
			{
				Map<String, Object> tier = (Map<String, Object>) region.get(tierName);
				actualTotals[tierIndex++] = (Integer) tier.get("totalTaskCount");
				taskTotal += (Integer) tier.get("totalTaskCount");
				assertEquals("available", tier.get("signalStatus"));
			}
			assertArrayEquals(expectedTotals.get(regionEntry.getKey()), actualTotals);
		}
		assertEquals(492, taskTotal);
	}

	@Test
	public void exportsPartialTaskProgressWithoutClaimingCompletion()
	{
		Map<Integer, Integer> signals = signals(
			VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 0,
			VarbitID.ARDOUGNE_EASY_COUNT, 3,
			VarbitID.ARDOUGNE_EASY_REWARD, 0);
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id -> signals.getOrDefault(id, 0)), "ardougne", "easy");

		assertEquals(false, tier.get("tierComplete"));
		assertEquals(false, tier.get("rewardClaimed"));
		assertEquals(3, tier.get("completedTaskCount"));
		assertEquals(10, tier.get("totalTaskCount"));
	}

	@Test
	public void completionSignalWinsOverAStaleCountWithoutClaimingTheReward()
	{
		Map<Integer, Integer> signals = signals(
			VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 1,
			VarbitID.ARDOUGNE_EASY_COUNT, 4,
			VarbitID.ARDOUGNE_EASY_REWARD, 0);
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id -> signals.getOrDefault(id, 0)), "ardougne", "easy");

		assertEquals(true, tier.get("complete"));
		assertEquals(true, tier.get("tierComplete"));
		assertEquals(false, tier.get("rewardClaimed"));
		assertEquals(10, tier.get("completedTaskCount"));
	}

	@Test
	public void claimedRewardIsMonotonicPositiveCompletionEvidence()
	{
		Map<Integer, Integer> signals = signals(
			VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 0,
			VarbitID.ARDOUGNE_EASY_COUNT, 2,
			VarbitID.ARDOUGNE_EASY_REWARD, 1);
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id -> signals.getOrDefault(id, 0)), "ardougne", "easy");

		assertEquals(true, tier.get("tierComplete"));
		assertEquals(true, tier.get("rewardClaimed"));
		assertEquals(10, tier.get("completedTaskCount"));
		assertEquals(1, tier.get("value"));
	}

	@Test
	public void zeroSignalsAreKnownZeroRatherThanUnavailable()
	{
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id -> 0), "desert", "easy");

		assertEquals(false, tier.get("tierComplete"));
		assertEquals(false, tier.get("rewardClaimed"));
		assertEquals(0, tier.get("completedTaskCount"));
		assertEquals("available", tier.get("signalStatus"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void unavailableSignalsRemainExplicitlyUnknownAtEveryAggregate()
	{
		Map<String, Object> snapshot = AchievementDiarySnapshot.build(id -> null);
		Map<String, Object> tier = tier(snapshot, "morytania", "medium");
		Map<String, Object> regions = (Map<String, Object>) snapshot.get("regions");
		Map<String, Object> morytania = (Map<String, Object>) regions.get("morytania");

		assertNull(tier.get("complete"));
		assertNull(tier.get("value"));
		assertNull(tier.get("rewardClaimed"));
		assertNull(tier.get("tierComplete"));
		assertNull(tier.get("completedTaskCount"));
		assertEquals(11, tier.get("totalTaskCount"));
		assertEquals("unavailable", tier.get("signalStatus"));
		assertNull(morytania.get("completedTierCount"));
		assertNull(morytania.get("allComplete"));
		assertNull(snapshot.get("completedTierCount"));
		assertNull(snapshot.get("completionPercent"));
		assertNull(snapshot.get("allComplete"));
	}

	@Test
	public void partialSignalAvailabilityDoesNotFabricateTheMissingFields()
	{
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id ->
			id == VarbitID.FALADOR_MED_COUNT ? 5 : null), "falador", "medium");

		assertEquals(false, tier.get("tierComplete"));
		assertNull(tier.get("rewardClaimed"));
		assertEquals(5, tier.get("completedTaskCount"));
		assertEquals("partial", tier.get("signalStatus"));
	}

	@Test
	public void mapsKaramjasAtjunSignalsSeparatelyFromItsCountSignals()
	{
		Map<Integer, Integer> signals = signals(
			VarbitID.ATJUN_MED_DONE, 1,
			VarbitID.KARAMJA_MED_COUNT, 19,
			VarbitID.ATJUN_MED_REWARD, 0);
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(id -> signals.getOrDefault(id, 0)), "karamja", "medium");

		assertEquals(true, tier.get("tierComplete"));
		assertEquals(false, tier.get("rewardClaimed"));
		assertEquals(19, tier.get("completedTaskCount"));
		assertEquals(19, tier.get("totalTaskCount"));
	}

	@Test
	public void rejectsOutOfRangeCountsInsteadOfClampingThem()
	{
		Map<Integer, Integer> signals = signals(
			VarbitID.VARROCK_DIARY_ELITE_COMPLETE, 0,
			VarbitID.VARROCK_ELITE_COUNT, 99,
			VarbitID.VARROCK_ELITE_REWARD, 0);
		Map<String, Object> tier = tier(AchievementDiarySnapshot.build(
			id -> signals.containsKey(id) ? signals.get(id) : null), "varrock", "elite");

		assertEquals(false, tier.get("tierComplete"));
		assertNull(tier.get("completedTaskCount"));
		assertEquals("partial", tier.get("signalStatus"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> tier(Map<String, Object> snapshot, String regionName, String tierName)
	{
		Map<String, Object> regions = (Map<String, Object>) snapshot.get("regions");
		Map<String, Object> region = (Map<String, Object>) regions.get(regionName);
		return (Map<String, Object>) region.get(tierName);
	}

	private static Map<Integer, Integer> signals(int... values)
	{
		Map<Integer, Integer> signals = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index += 2)
		{
			signals.put(values[index], values[index + 1]);
		}
		return signals;
	}
}
