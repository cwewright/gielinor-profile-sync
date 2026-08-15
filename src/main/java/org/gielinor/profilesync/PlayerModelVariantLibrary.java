package org.gielinor.profilesync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlayerModelVariantLibrary
{
	private static final int MAX_CACHED_VARIANTS = 8;
	private static final int MAX_EXPORTED_VARIANTS = 4;
	private static final List<String> PREFERRED_KINDS = Arrays.asList("activity", "movement", "idle");

	private final LinkedHashMap<String, Variant> variants = new LinkedHashMap<>();
	private String appearanceFingerprint;

	void reset()
	{
		appearanceFingerprint = null;
		variants.clear();
	}

	void observe(String fingerprint, String kind, Map<String, Object> model)
	{
		if (fingerprint == null || model == null)
		{
			return;
		}
		if (!fingerprint.equals(appearanceFingerprint))
		{
			reset();
			appearanceFingerprint = fingerprint;
		}

		String signature = geometrySignature(model);
		Map<String, Object> pose = poseVariant(kind, model);
		if (signature == null || pose == null)
		{
			return;
		}

		variants.remove(signature);
		variants.put(signature, new Variant(normalizeKind(kind), pose));
		while (variants.size() > MAX_CACHED_VARIANTS)
		{
			variants.remove(variants.keySet().iterator().next());
		}
	}

	List<Map<String, Object>> variantsFor(String fingerprint, Map<String, Object> currentModel)
	{
		if (!fingerprintMatches(fingerprint) || currentModel == null)
		{
			return Collections.emptyList();
		}

		String currentSignature = geometrySignature(currentModel);
		List<Map.Entry<String, Variant>> newestFirst = new ArrayList<>(variants.entrySet());
		Collections.reverse(newestFirst);
		List<Map<String, Object>> selected = new ArrayList<>();
		List<String> selectedSignatures = new ArrayList<>();

		for (String preferredKind : PREFERRED_KINDS)
		{
			for (Map.Entry<String, Variant> entry : newestFirst)
			{
				if (selected.size() >= MAX_EXPORTED_VARIANTS)
				{
					return selected;
				}
				if (!entry.getKey().equals(currentSignature) &&
					!selectedSignatures.contains(entry.getKey()) &&
					preferredKind.equals(entry.getValue().kind))
				{
					selected.add(entry.getValue().pose);
					selectedSignatures.add(entry.getKey());
					break;
				}
			}
		}

		for (Map.Entry<String, Variant> entry : newestFirst)
		{
			if (selected.size() >= MAX_EXPORTED_VARIANTS)
			{
				break;
			}
			if (!entry.getKey().equals(currentSignature) && !selectedSignatures.contains(entry.getKey()))
			{
				selected.add(entry.getValue().pose);
				selectedSignatures.add(entry.getKey());
			}
		}
		return selected;
	}

	private boolean fingerprintMatches(String fingerprint)
	{
		return fingerprint != null && fingerprint.equals(appearanceFingerprint);
	}

	private static String normalizeKind(String kind)
	{
		return PREFERRED_KINDS.contains(kind) ? kind : "activity";
	}

	private static String geometrySignature(Map<String, Object> model)
	{
		Object x = model.get("verticesX");
		Object y = model.get("verticesY");
		Object z = model.get("verticesZ");
		if (!(x instanceof int[]) || !(y instanceof int[]) || !(z instanceof int[]))
		{
			return null;
		}
		return model.get("vertexCount") + ":" +
			Integer.toHexString(Arrays.hashCode((int[]) x)) + ":" +
			Integer.toHexString(Arrays.hashCode((int[]) y)) + ":" +
			Integer.toHexString(Arrays.hashCode((int[]) z));
	}

	private static Map<String, Object> poseVariant(String kind, Map<String, Object> model)
	{
		Object vertexCount = model.get("vertexCount");
		Object x = model.get("verticesX");
		Object y = model.get("verticesY");
		Object z = model.get("verticesZ");
		if (!(vertexCount instanceof Integer) || !(x instanceof int[]) || !(y instanceof int[]) || !(z instanceof int[]))
		{
			return null;
		}
		int count = (Integer) vertexCount;
		if (((int[]) x).length != count || ((int[]) y).length != count || ((int[]) z).length != count)
		{
			return null;
		}

		Map<String, Object> pose = new LinkedHashMap<>();
		pose.put("schemaVersion", 1);
		pose.put("kind", normalizeKind(kind));
		pose.put("verticesX", Arrays.copyOf((int[]) x, count));
		pose.put("verticesY", Arrays.copyOf((int[]) y, count));
		pose.put("verticesZ", Arrays.copyOf((int[]) z, count));
		return pose;
	}

	private static final class Variant
	{
		private final String kind;
		private final Map<String, Object> pose;

		private Variant(String kind, Map<String, Object> pose)
		{
			this.kind = kind;
			this.pose = pose;
		}
	}
}
