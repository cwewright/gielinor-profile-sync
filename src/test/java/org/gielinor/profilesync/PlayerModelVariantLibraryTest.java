package org.gielinor.profilesync;

import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerModelVariantLibraryTest
{
	@Test
	public void exportsDistinctRecentKindsWithoutRepeatingCurrentPose()
	{
		PlayerModelVariantLibrary library = new PlayerModelVariantLibrary();
		Map<String, Object> idle = model(0);
		Map<String, Object> movement = model(10);
		Map<String, Object> firstActivity = model(20);
		Map<String, Object> currentActivity = model(30);

		library.observe("outfit-a", "idle", idle);
		library.observe("outfit-a", "movement", movement);
		library.observe("outfit-a", "activity", firstActivity);
		library.observe("outfit-a", "activity", currentActivity);

		List<Map<String, Object>> variants = library.variantsFor("outfit-a", currentActivity);
		assertEquals(3, variants.size());
		assertEquals("activity", variants.get(0).get("kind"));
		assertEquals("movement", variants.get(1).get("kind"));
		assertEquals("idle", variants.get(2).get("kind"));
		assertArrayEquals(new int[]{21, 22, 23}, (int[]) variants.get(0).get("verticesX"));
		assertFalse(variants.stream().anyMatch(variant -> ((int[]) variant.get("verticesX"))[0] == 31));
	}

	@Test
	public void resetsWhenTheOutfitChangesAndCopiesPoseArrays()
	{
		PlayerModelVariantLibrary library = new PlayerModelVariantLibrary();
		Map<String, Object> oldPose = model(0);
		Map<String, Object> newPose = model(10);
		library.observe("outfit-a", "idle", oldPose);
		library.observe("outfit-b", "activity", newPose);

		assertTrue(library.variantsFor("outfit-a", oldPose).isEmpty());
		List<Map<String, Object>> variants = library.variantsFor("outfit-b", model(20));
		assertEquals(1, variants.size());
		int[] exportedX = (int[]) variants.get(0).get("verticesX");
		((int[]) newPose.get("verticesX"))[0] = 999;
		assertArrayEquals(new int[]{11, 12, 13}, exportedX);
	}

	@Test
	public void capsTheExportAndNormalizesUnknownKinds()
	{
		PlayerModelVariantLibrary library = new PlayerModelVariantLibrary();
		for (int index = 0; index < 10; index++)
		{
			library.observe("outfit", index == 0 ? "mystery" : "activity", model(index * 10));
		}

		List<Map<String, Object>> variants = library.variantsFor("outfit", model(200));
		assertEquals(4, variants.size());
		assertTrue(variants.stream().allMatch(variant -> "activity".equals(variant.get("kind"))));
	}

	private static Map<String, Object> model(int offset)
	{
		return PlayerModelSnapshot.encode(
			3,
			new float[]{offset + 1, offset + 2, offset + 3},
			new float[]{offset + 4, offset + 5, offset + 6},
			new float[]{offset + 7, offset + 8, offset + 9},
			1,
			new int[]{0},
			new int[]{1},
			new int[]{2},
			new int[]{100},
			new int[]{101},
			new int[]{102},
			null,
			null);
	}
}
