package org.gielinor.profilesync;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Model;

final class PlayerModelSnapshot
{
	private static final int MODEL_SCHEMA_VERSION = 1;

	private PlayerModelSnapshot()
	{
	}

	static Map<String, Object> from(Model model)
	{
		if (model == null)
		{
			return null;
		}

		return encode(
			model.getVerticesCount(),
			model.getVerticesX(),
			model.getVerticesY(),
			model.getVerticesZ(),
			model.getFaceCount(),
			model.getFaceIndices1(),
			model.getFaceIndices2(),
			model.getFaceIndices3(),
			model.getFaceColors1(),
			model.getFaceColors2(),
			model.getFaceColors3(),
			model.getFaceTransparencies(),
			model.getFaceTextures());
	}

	static Map<String, Object> poseFrom(Model model)
	{
		if (model == null)
		{
			return null;
		}
		return encodePose(
			model.getVerticesCount(),
			model.getVerticesX(),
			model.getVerticesY(),
			model.getVerticesZ());
	}

	static Map<String, Object> encodePose(int vertexCount, float[] verticesX, float[] verticesY, float[] verticesZ)
	{
		if (vertexCount <= 0 ||
			!hasLength(verticesX, vertexCount) ||
			!hasLength(verticesY, vertexCount) ||
			!hasLength(verticesZ, vertexCount))
		{
			return null;
		}
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("vertexCount", vertexCount);
		output.put("verticesX", roundedCopy(verticesX, vertexCount));
		output.put("verticesY", roundedCopy(verticesY, vertexCount));
		output.put("verticesZ", roundedCopy(verticesZ, vertexCount));
		return output;
	}

	static Map<String, Object> encode(
		int vertexCount,
		float[] verticesX,
		float[] verticesY,
		float[] verticesZ,
		int faceCount,
		int[] faces1,
		int[] faces2,
		int[] faces3,
		int[] colors1,
		int[] colors2,
		int[] colors3,
		byte[] transparencies,
		short[] textures)
	{
		if (vertexCount <= 0 || faceCount <= 0 ||
			!hasLength(verticesX, vertexCount) ||
			!hasLength(verticesY, vertexCount) ||
			!hasLength(verticesZ, vertexCount) ||
			!hasLength(faces1, faceCount) ||
			!hasLength(faces2, faceCount) ||
			!hasLength(faces3, faceCount) ||
			!hasLength(colors1, faceCount) ||
			!hasLength(colors2, faceCount) ||
			!hasLength(colors3, faceCount))
		{
			return null;
		}

		Map<String, Object> output = new LinkedHashMap<>();
		output.put("schemaVersion", MODEL_SCHEMA_VERSION);
		output.put("vertexCount", vertexCount);
		output.put("faceCount", faceCount);
		output.put("verticesX", roundedCopy(verticesX, vertexCount));
		output.put("verticesY", roundedCopy(verticesY, vertexCount));
		output.put("verticesZ", roundedCopy(verticesZ, vertexCount));
		output.put("faces1", Arrays.copyOf(faces1, faceCount));
		output.put("faces2", Arrays.copyOf(faces2, faceCount));
		output.put("faces3", Arrays.copyOf(faces3, faceCount));
		output.put("colors1", Arrays.copyOf(colors1, faceCount));
		output.put("colors2", Arrays.copyOf(colors2, faceCount));
		output.put("colors3", Arrays.copyOf(colors3, faceCount));
		if (hasLength(transparencies, faceCount))
		{
			output.put("transparencies", unsignedCopy(transparencies, faceCount));
		}
		if (hasLength(textures, faceCount))
		{
			output.put("textures", integerCopy(textures, faceCount));
		}
		return output;
	}

	private static boolean hasLength(float[] values, int length)
	{
		return values != null && values.length >= length;
	}

	private static boolean hasLength(int[] values, int length)
	{
		return values != null && values.length >= length;
	}

	private static boolean hasLength(byte[] values, int length)
	{
		return values != null && values.length >= length;
	}

	private static boolean hasLength(short[] values, int length)
	{
		return values != null && values.length >= length;
	}

	private static int[] roundedCopy(float[] values, int length)
	{
		int[] output = new int[length];
		for (int index = 0; index < length; index++)
		{
			output[index] = Math.round(values[index]);
		}
		return output;
	}

	private static int[] unsignedCopy(byte[] values, int length)
	{
		int[] output = new int[length];
		for (int index = 0; index < length; index++)
		{
			output[index] = Byte.toUnsignedInt(values[index]);
		}
		return output;
	}

	private static int[] integerCopy(short[] values, int length)
	{
		int[] output = new int[length];
		for (int index = 0; index < length; index++)
		{
			output[index] = values[index];
		}
		return output;
	}
}
