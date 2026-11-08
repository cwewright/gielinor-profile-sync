package org.gielinor.profilesync;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A deliberately partial cache of Collection Log pages the player has opened.
 * RuneLite only exposes per-slot state while a page is rendered, so pages that
 * have never been observed must remain unknown.
 */
final class CollectionLogSnapshot
{
	static final String SOURCE = "RuneLite Collection Log interface observations";
	private static final int SCHEMA_VERSION = 1;
	private final Map<String, PageObservation> pages = new LinkedHashMap<>();
	private Integer uniqueObtainedCount;
	private Integer uniqueItemCount;
	private long uniqueCountsObservedAt;

	synchronized void reset()
	{
		pages.clear();
		uniqueObtainedCount = null;
		uniqueItemCount = null;
		uniqueCountsObservedAt = 0L;
	}

	synchronized boolean observePage(String rawTitle, List<ItemObservation> rawItems, long observedAt)
	{
		return observePage(null, rawTitle, rawItems, observedAt);
	}

	synchronized boolean observePage(String rawCategory, String rawTitle, List<ItemObservation> rawItems, long observedAt)
	{
		String title = cleanText(rawTitle, 160);
		String category = canonicalCategory(rawCategory);
		String key = pageKey(title);
		if (key == null || rawItems == null || rawItems.isEmpty() || observedAt <= 0L)
		{
			return false;
		}

		Map<Integer, ItemObservation> items = new LinkedHashMap<>();
		for (ItemObservation item : rawItems)
		{
			if (item == null || item.itemId <= 0)
			{
				continue;
			}
			String itemName = cleanText(item.itemName, 160);
			if (itemName.isEmpty())
			{
				itemName = "Item " + item.itemId;
			}
			items.put(item.itemId, new ItemObservation(item.itemId, itemName, item.obtained, Math.max(0, item.quantity)));
		}
		if (items.isEmpty())
		{
			return false;
		}

		PageObservation next = new PageObservation(category, title, Math.max(0L, observedAt), items);
		PageObservation previous = pages.get(key);
		if (previous != null && previous.observedAt > next.observedAt)
		{
			return false;
		}
		if (previous != null)
		{
			long previousObtained = previous.items.values().stream().filter(item -> item.obtained).count();
			long nextObtained = next.items.values().stream().filter(item -> item.obtained).count();
			if (next.items.size() < previous.items.size() || nextObtained < previousObtained)
			{
				return false;
			}
		}
		pages.put(key, next);
		return true;
	}

	synchronized void observeUniqueCounts(int obtained, int total, long observedAt)
	{
		if (total <= 0 || obtained < 0 || obtained > total || observedAt <= 0L || observedAt < uniqueCountsObservedAt)
		{
			return;
		}
		uniqueObtainedCount = obtained;
		uniqueItemCount = total;
		uniqueCountsObservedAt = Math.max(0L, observedAt);
	}

	@SuppressWarnings("unchecked")
	synchronized void restore(Object rawSnapshot)
	{
		if (!(rawSnapshot instanceof Map))
		{
			return;
		}
		Object rawPages = ((Map<?, ?>) rawSnapshot).get("pages");
		Integer storedUniqueObtained = integer(((Map<?, ?>) rawSnapshot).get("uniqueObtainedCount"));
		Integer storedUniqueTotal = integer(((Map<?, ?>) rawSnapshot).get("uniqueItemCount"));
		long storedUniqueObservedAt = timestamp(((Map<?, ?>) rawSnapshot).get("uniqueCountsObservedAt"));
		if (storedUniqueObtained != null && storedUniqueTotal != null && storedUniqueObservedAt > 0L)
		{
			observeUniqueCounts(storedUniqueObtained, storedUniqueTotal, storedUniqueObservedAt);
		}
		if (!(rawPages instanceof Map))
		{
			return;
		}

		for (Object rawPage : ((Map<?, ?>) rawPages).values())
		{
			if (!(rawPage instanceof Map))
			{
				continue;
			}
			Map<?, ?> page = (Map<?, ?>) rawPage;
			Object category = page.get("category");
			Object title = page.get("title");
			Object rawEntries = page.get("entries");
			if (!(title instanceof String) || !(rawEntries instanceof Map))
			{
				continue;
			}

			List<ItemObservation> items = new ArrayList<>();
			for (Object rawEntry : ((Map<?, ?>) rawEntries).values())
			{
				if (!(rawEntry instanceof Map))
				{
					continue;
				}
				Map<?, ?> entry = (Map<?, ?>) rawEntry;
				Integer itemId = integer(entry.get("itemId"));
				Object itemName = entry.get("itemName");
				Object obtained = entry.get("obtained");
				Integer quantity = integer(entry.get("quantity"));
				if (itemId == null || !(itemName instanceof String) || !(obtained instanceof Boolean))
				{
					continue;
				}
				items.add(new ItemObservation(itemId, (String) itemName, (Boolean) obtained, quantity == null ? 0 : quantity));
			}
			long observedAt = timestamp(page.get("observedAt"));
			if (observedAt > 0L)
			{
				observePage(category instanceof String ? (String) category : null, (String) title, items, observedAt);
			}
		}
	}

	synchronized Map<String, Object> toMap()
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schemaVersion", SCHEMA_VERSION);
		result.put("source", SOURCE);
		result.put("status", pages.isEmpty() ? "unavailable" : "partial");
		result.put("coverage", "observed-pages-only");
		result.put("requiresManualPageVisits", true);
		if (uniqueObtainedCount != null && uniqueItemCount != null)
		{
			result.put("uniqueObtainedCount", uniqueObtainedCount);
			result.put("uniqueItemCount", uniqueItemCount);
			result.put("uniqueCompletionPercent", uniqueItemCount > 0 ? uniqueObtainedCount * 100.0 / uniqueItemCount : 0.0);
			result.put("uniqueCountsObservedAt", Instant.ofEpochMilli(uniqueCountsObservedAt).toString());
		}

		Map<String, Object> pagesOut = new LinkedHashMap<>();
		Map<Integer, Boolean> uniqueItemStates = new LinkedHashMap<>();
		int observedSlotCount = 0;
		int obtainedSlotCount = 0;
		long newestObservation = 0L;
		for (Map.Entry<String, PageObservation> pageEntry : pages.entrySet())
		{
			PageObservation page = pageEntry.getValue();
			Map<String, Object> pageOut = new LinkedHashMap<>();
			pageOut.put("title", page.title);
			if (page.category != null)
			{
				pageOut.put("category", page.category);
			}
			pageOut.put("state", "observed");
			pageOut.put("observedAt", Instant.ofEpochMilli(page.observedAt).toString());

			Map<String, Object> entriesOut = new LinkedHashMap<>();
			int pageObtained = 0;
			for (ItemObservation item : page.items.values())
			{
				Map<String, Object> itemOut = new LinkedHashMap<>();
				itemOut.put("itemId", item.itemId);
				itemOut.put("itemName", item.itemName);
				itemOut.put("obtained", item.obtained);
				itemOut.put("quantity", item.quantity);
				entriesOut.put(String.valueOf(item.itemId), itemOut);
				if (item.obtained)
				{
					pageObtained++;
				}
				uniqueItemStates.merge(item.itemId, item.obtained, (left, right) -> left || right);
			}
			int pageTotal = page.items.size();
			pageOut.put("obtainedSlotCount", pageObtained);
			pageOut.put("missingSlotCount", pageTotal - pageObtained);
			pageOut.put("slotCount", pageTotal);
			pageOut.put("entries", entriesOut);
			pagesOut.put(pageEntry.getKey(), pageOut);
			observedSlotCount += pageTotal;
			obtainedSlotCount += pageObtained;
			newestObservation = Math.max(newestObservation, page.observedAt);
		}

		long uniqueObtained = uniqueItemStates.values().stream().filter(Boolean.TRUE::equals).count();
		result.put("observedPageCount", pages.size());
		result.put("observedSlotCount", observedSlotCount);
		result.put("obtainedObservedSlotCount", obtainedSlotCount);
		result.put("uniqueObservedItemCount", uniqueItemStates.size());
		result.put("uniqueObtainedObservedItemCount", uniqueObtained);
		if (newestObservation > 0L)
		{
			result.put("lastObservedAt", Instant.ofEpochMilli(newestObservation).toString());
		}
		result.put("pages", pagesOut);
		return result;
	}

	private static Integer integer(Object value)
	{
		if (!(value instanceof Number))
		{
			return null;
		}
		double raw = ((Number) value).doubleValue();
		if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE)
		{
			return null;
		}
		return (int) raw;
	}

	private static long timestamp(Object value)
	{
		if (!(value instanceof String))
		{
			return 0L;
		}
		try
		{
			return Instant.parse((String) value).toEpochMilli();
		}
		catch (DateTimeParseException ignored)
		{
			return 0L;
		}
	}

	private static String cleanText(String value, int limit)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.replaceAll("\\s+", " ").trim();
		return clean.length() <= limit ? clean : clean.substring(0, limit).trim();
	}

	private static String pageKey(String title)
	{
		String key = title.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-+|-+$", "");
		return key.isEmpty() ? null : key;
	}

	private static String canonicalCategory(String value)
	{
		String clean = cleanText(value, 40);
		for (String category : new String[]{"Bosses", "Raids", "Clues", "Minigames", "Other"})
		{
			if (category.equalsIgnoreCase(clean))
			{
				return category;
			}
		}
		return null;
	}

	static final class ItemObservation
	{
		final int itemId;
		final String itemName;
		final boolean obtained;
		final int quantity;

		ItemObservation(int itemId, String itemName, boolean obtained, int quantity)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.obtained = obtained;
			this.quantity = quantity;
		}
	}

	private static final class PageObservation
	{
		private final String category;
		private final String title;
		private final long observedAt;
		private final Map<Integer, ItemObservation> items;

		private PageObservation(String category, String title, long observedAt, Map<Integer, ItemObservation> items)
		{
			this.category = category;
			this.title = title;
			this.observedAt = observedAt;
			this.items = items;
		}
	}
}
