package org.gielinor.profilesync;

import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class PlayerModelSnapshotTest
{
	@Test
	public void encodesCompactImmutableMeshArrays()
	{
		float[] x = {1.2f, -2.8f, 3.5f};
		Map<String, Object> model = PlayerModelSnapshot.encode(
			3,
			x,
			new float[]{4, 5, 6},
			new float[]{7, 8, 9},
			1,
			new int[]{0},
			new int[]{1},
			new int[]{2},
			new int[]{100},
			new int[]{101},
			new int[]{102},
			new byte[]{(byte) 200},
			new short[]{-1});

		assertEquals(1, model.get("schemaVersion"));
		assertEquals(3, model.get("vertexCount"));
		assertEquals(1, model.get("faceCount"));
		assertArrayEquals(new int[]{1, -3, 4}, (int[]) model.get("verticesX"));
		assertArrayEquals(new int[]{200}, (int[]) model.get("transparencies"));
		assertArrayEquals(new int[]{-1}, (int[]) model.get("textures"));

		x[0] = 99;
		assertArrayEquals(new int[]{1, -3, 4}, (int[]) model.get("verticesX"));
	}

	@Test
	public void omitsOptionalChannelsAndRejectsIncompleteGeometry()
	{
		Map<String, Object> model = PlayerModelSnapshot.encode(
			3,
			new float[]{1, 2, 3},
			new float[]{1, 2, 3},
			new float[]{1, 2, 3},
			1,
			new int[]{0},
			new int[]{1},
			new int[]{2},
			new int[]{100},
			new int[]{100},
			new int[]{-1},
			null,
			null);

		assertFalse(model.containsKey("transparencies"));
		assertFalse(model.containsKey("textures"));
		assertNull(PlayerModelSnapshot.encode(
			3,
			new float[]{1, 2},
			new float[]{1, 2, 3},
			new float[]{1, 2, 3},
			1,
			new int[]{0},
			new int[]{1},
			new int[]{2},
			new int[]{100},
			new int[]{100},
			new int[]{100},
			null,
			null));
	}
}
