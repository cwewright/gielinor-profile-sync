package org.gielinor.profilesync;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

final class AchievementDiarySnapshot
{
	static final int SCHEMA_VERSION = 2;
	static final String SOURCE_VERSION = "runelite-1.12.35-varbits";
	private static final String SOURCE = "RuneLite achievement diary completion, count, and reward varbits";
	private static final int TIERS_PER_REGION = 4;

	private static final List<RegionDefinition> REGIONS = Arrays.asList(
		region("ardougne", "Ardougne",
			tier(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_EASY_COUNT, VarbitID.ARDOUGNE_EASY_REWARD, 10),
			tier(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_MED_COUNT, VarbitID.ARDOUGNE_MEDIUM_REWARD, 12),
			tier(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_HARD_COUNT, VarbitID.ARDOUGNE_HARD_REWARD, 12),
			tier(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE, VarbitID.ARDOUGNE_ELITE_COUNT, VarbitID.ARDOUGNE_ELITE_REWARD, 8)),
		region("desert", "Desert",
			tier(VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_EASY_COUNT, VarbitID.DESERT_EASY_REWARD, 11),
			tier(VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_MED_COUNT, VarbitID.DESERT_MEDIUM_REWARD, 12),
			tier(VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_HARD_COUNT, VarbitID.DESERT_HARD_REWARD, 10),
			tier(VarbitID.DESERT_DIARY_ELITE_COMPLETE, VarbitID.DESERT_ELITE_COUNT, VarbitID.DESERT_ELITE_REWARD, 6)),
		region("falador", "Falador",
			tier(VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_EASY_COUNT, VarbitID.FALADOR_EASY_REWARD, 11),
			tier(VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_MED_COUNT, VarbitID.FALADOR_MEDIUM_REWARD, 14),
			tier(VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_HARD_COUNT, VarbitID.FALADOR_HARD_REWARD, 11),
			tier(VarbitID.FALADOR_DIARY_ELITE_COMPLETE, VarbitID.FALADOR_ELITE_COUNT, VarbitID.FALADOR_ELITE_REWARD, 6)),
		region("fremennik", "Fremennik",
			tier(VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_EASY_COUNT, VarbitID.FREMENNIK_EASY_REWARD, 10),
			tier(VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_MED_COUNT, VarbitID.FREMENNIK_MEDIUM_REWARD, 9),
			tier(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_HARD_COUNT, VarbitID.FREMENNIK_HARD_REWARD, 9),
			tier(VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE, VarbitID.FREMENNIK_ELITE_COUNT, VarbitID.FREMENNIK_ELITE_REWARD, 6)),
		region("kandarin", "Kandarin",
			tier(VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_EASY_COUNT, VarbitID.KANDARIN_EASY_REWARD, 11),
			tier(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_MED_COUNT, VarbitID.KANDARIN_MEDIUM_REWARD, 14),
			tier(VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_HARD_COUNT, VarbitID.KANDARIN_HARD_REWARD, 11),
			tier(VarbitID.KANDARIN_DIARY_ELITE_COMPLETE, VarbitID.KANDARIN_ELITE_COUNT, VarbitID.KANDARIN_ELITE_REWARD, 7)),
		region("karamja", "Karamja",
			tier(VarbitID.ATJUN_EASY_DONE, VarbitID.KARAMJA_EASY_COUNT, VarbitID.ATJUN_EASY_REWARD, 10),
			tier(VarbitID.ATJUN_MED_DONE, VarbitID.KARAMJA_MED_COUNT, VarbitID.ATJUN_MED_REWARD, 19),
			tier(VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_HARD_COUNT, VarbitID.ATJUN_HARD_REWARD, 10),
			tier(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, VarbitID.KARAMJA_ELITE_COUNT, VarbitID.KARAMJA_ELITE_REWARD, 5)),
		region("kourend_kebos", "Kourend & Kebos",
			tier(VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_EASY_COUNT, VarbitID.KOUREND_EASY_REWARD, 12),
			tier(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_MED_COUNT, VarbitID.KOUREND_MEDIUM_REWARD, 13),
			tier(VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_HARD_COUNT, VarbitID.KOUREND_HARD_REWARD, 10),
			tier(VarbitID.KOUREND_DIARY_ELITE_COMPLETE, VarbitID.KOUREND_ELITE_COUNT, VarbitID.KOUREND_ELITE_REWARD, 8)),
		region("lumbridge_draynor", "Lumbridge & Draynor",
			tier(VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_EASY_COUNT, VarbitID.LUMBRIDGE_EASY_REWARD, 12),
			tier(VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_MED_COUNT, VarbitID.LUMBRIDGE_MEDIUM_REWARD, 12),
			tier(VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_HARD_COUNT, VarbitID.LUMBRIDGE_HARD_REWARD, 11),
			tier(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, VarbitID.LUMBRIDGE_ELITE_COUNT, VarbitID.LUMBRIDGE_ELITE_REWARD, 6)),
		region("morytania", "Morytania",
			tier(VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_EASY_COUNT, VarbitID.MORYTANIA_EASY_REWARD, 11),
			tier(VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_MED_COUNT, VarbitID.MORYTANIA_MEDIUM_REWARD, 11),
			tier(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_HARD_COUNT, VarbitID.MORYTANIA_HARD_REWARD, 10),
			tier(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE, VarbitID.MORYTANIA_ELITE_COUNT, VarbitID.MORYTANIA_ELITE_REWARD, 6)),
		region("varrock", "Varrock",
			tier(VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_EASY_COUNT, VarbitID.VARROCK_EASY_REWARD, 14),
			tier(VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_MED_COUNT, VarbitID.VARROCK_MEDIUM_REWARD, 13),
			tier(VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_HARD_COUNT, VarbitID.VARROCK_HARD_REWARD, 10),
			tier(VarbitID.VARROCK_DIARY_ELITE_COMPLETE, VarbitID.VARROCK_ELITE_COUNT, VarbitID.VARROCK_ELITE_REWARD, 5)),
		region("western_provinces", "Western Provinces",
			tier(VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_EASY_COUNT, VarbitID.WESTERN_EASY_REWARD, 11),
			tier(VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_MED_COUNT, VarbitID.WESTERN_MEDIUM_REWARD, 13),
			tier(VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_HARD_COUNT, VarbitID.WESTERN_HARD_REWARD, 13),
			tier(VarbitID.WESTERN_DIARY_ELITE_COMPLETE, VarbitID.WESTERN_ELITE_COUNT, VarbitID.WESTERN_ELITE_REWARD, 7)),
		region("wilderness", "Wilderness",
			tier(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_EASY_COUNT, VarbitID.WILDERNESS_EASY_REWARD, 12),
			tier(VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_MED_COUNT, VarbitID.WILDERNESS_MEDIUM_REWARD, 11),
			tier(VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_HARD_COUNT, VarbitID.WILDERNESS_HARD_REWARD, 10),
			tier(VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE, VarbitID.WILDERNESS_ELITE_COUNT, VarbitID.WILDERNESS_ELITE_REWARD, 7))
	);

	private AchievementDiarySnapshot()
	{
	}

	static Map<String, Object> build(SignalReader reader)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> regions = new LinkedHashMap<>();
		int completed = 0;
		int known = 0;
		for (RegionDefinition definition : REGIONS)
		{
			Map<String, Object> region = buildRegion(definition, reader);
			regions.put(definition.key, region);
			Integer regionCompleted = (Integer) region.get("completedTierCount");
			if (regionCompleted != null)
			{
				completed += regionCompleted;
				known += TIERS_PER_REGION;
			}
		}

		int total = REGIONS.size() * TIERS_PER_REGION;
		Integer completedTierCount = known == total ? completed : null;
		result.put("schemaVersion", SCHEMA_VERSION);
		result.put("source", SOURCE);
		result.put("sourceVersion", SOURCE_VERSION);
		result.put("completedTierCount", completedTierCount);
		result.put("totalTierCount", total);
		result.put("completionPercent", completedTierCount == null ? null : completedTierCount * 100.0 / total);
		result.put("allComplete", completedTierCount == null ? null : completedTierCount == total);
		result.put("regions", regions);
		return result;
	}

	private static Map<String, Object> buildRegion(RegionDefinition definition, SignalReader reader)
	{
		Map<String, Object> region = new LinkedHashMap<>();
		region.put("name", definition.name);
		int completed = 0;
		int known = 0;
		for (int i = 0; i < definition.tiers.length; i++)
		{
			Map<String, Object> tier = buildTier(definition.tiers[i], reader);
			region.put(tierName(i), tier);
			Boolean tierComplete = (Boolean) tier.get("tierComplete");
			if (tierComplete != null)
			{
				known++;
				if (tierComplete)
				{
					completed++;
				}
			}
		}
		Integer completedTierCount = known == TIERS_PER_REGION ? completed : null;
		region.put("completedTierCount", completedTierCount);
		region.put("totalTierCount", TIERS_PER_REGION);
		region.put("allComplete", completedTierCount == null ? null : completedTierCount == TIERS_PER_REGION);
		return region;
	}

	private static Map<String, Object> buildTier(TierDefinition definition, SignalReader reader)
	{
		Integer completionValue = readNonNegative(reader, definition.completionVarbit);
		Integer rawCount = readNonNegative(reader, definition.countVarbit);
		Integer rewardValue = readNonNegative(reader, definition.rewardVarbit);
		Integer count = rawCount != null && rawCount <= definition.totalTaskCount ? rawCount : null;
		Boolean rewardClaimed = rewardValue == null ? null : rewardValue > 0;
		Boolean tierComplete;
		if ((completionValue != null && completionValue > 0)
			|| Boolean.TRUE.equals(rewardClaimed)
			|| (count != null && count == definition.totalTaskCount))
		{
			tierComplete = true;
		}
		else if (completionValue != null || count != null)
		{
			tierComplete = false;
		}
		else
		{
			tierComplete = null;
		}

		Integer completedTaskCount = Boolean.TRUE.equals(tierComplete) ? Integer.valueOf(definition.totalTaskCount) : count;
		int availableSignals = (completionValue == null ? 0 : 1) + (count == null ? 0 : 1) + (rewardValue == null ? 0 : 1);
		String signalStatus = availableSignals == 3 ? "available" : availableSignals == 0 ? "unavailable" : "partial";

		Map<String, Object> tier = new LinkedHashMap<>();
		tier.put("complete", tierComplete);
		tier.put("value", rewardValue);
		tier.put("rewardClaimed", rewardClaimed);
		tier.put("tierComplete", tierComplete);
		tier.put("completedTaskCount", completedTaskCount);
		tier.put("totalTaskCount", definition.totalTaskCount);
		tier.put("signalStatus", signalStatus);
		return tier;
	}

	private static Integer readNonNegative(SignalReader reader, int varbitId)
	{
		try
		{
			Integer value = reader.read(varbitId);
			return value != null && value >= 0 ? value : null;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static String tierName(int index)
	{
		return Arrays.asList("easy", "medium", "hard", "elite").get(index);
	}

	private static RegionDefinition region(String key, String name, TierDefinition... tiers)
	{
		return new RegionDefinition(key, name, tiers);
	}

	private static TierDefinition tier(int completionVarbit, int countVarbit, int rewardVarbit, int totalTaskCount)
	{
		return new TierDefinition(completionVarbit, countVarbit, rewardVarbit, totalTaskCount);
	}

	interface SignalReader
	{
		Integer read(int varbitId);
	}

	private static final class RegionDefinition
	{
		private final String key;
		private final String name;
		private final TierDefinition[] tiers;

		private RegionDefinition(String key, String name, TierDefinition[] tiers)
		{
			this.key = key;
			this.name = name;
			this.tiers = tiers;
		}
	}

	private static final class TierDefinition
	{
		private final int completionVarbit;
		private final int countVarbit;
		private final int rewardVarbit;
		private final int totalTaskCount;

		private TierDefinition(int completionVarbit, int countVarbit, int rewardVarbit, int totalTaskCount)
		{
			this.completionVarbit = completionVarbit;
			this.countVarbit = countVarbit;
			this.rewardVarbit = rewardVarbit;
			this.totalTaskCount = totalTaskCount;
		}
	}
}
