package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** Offline excerpts of the Sailor's Log bestiary and reviewed travel catalogue. */
final class SlayerGuide
{
	private static final int TILE = 88;
	private static final int COLUMNS = 12;
	private static final int KRYSTILIA_MASTER_ID = 7;
	List<Task> tasks;
	Map<String, Travel> travel;
	private final transient Map<String, Task> byName = new HashMap<>();
	private transient BufferedImage portraits;

	static SlayerGuide load(Gson gson) throws IOException
	{
		try (InputStream data = SlayerGuide.class.getResourceAsStream("slayer/guide.json");
			InputStream sheet = SlayerGuide.class.getResourceAsStream("slayer/portraits.png"))
		{
			if (data == null || sheet == null)
			{
				throw new IOException("Missing Slayer field guide");
			}
			SlayerGuide guide = gson.fromJson(new InputStreamReader(data, StandardCharsets.UTF_8), SlayerGuide.class);
			guide.portraits = ImageIO.read(sheet);
			int rows = (guide.tasks.size() + COLUMNS - 1) / COLUMNS;
			if (guide.portraits == null || guide.portraits.getWidth() != COLUMNS * TILE || guide.portraits.getHeight() != rows * TILE)
			{
				throw new IOException("Slayer portrait sheet does not match the catalogue");
			}
			for (int index = 0; index < guide.tasks.size(); index++)
			{
				Task task = guide.tasks.get(index);
				task.portrait = guide.portraits.getSubimage(index % COLUMNS * TILE, index / COLUMNS * TILE, TILE, TILE);
				guide.byName.put(taskKey(task.name), task);
				for (String alias : task.aliases)
				{
					guide.byName.putIfAbsent(taskKey(alias), task);
				}
			}
			return guide;
		}
	}

	Task find(String name)
	{
		return name == null ? null : byName.get(taskKey(name));
	}

	List<Venue> venues(Task task, String requiredArea, boolean areaRestricted, Object masterId)
	{
		if (areaRestricted && requiredArea == null)
		{
			return List.of();
		}
		String area = requiredArea == null ? "" : areaKey(requiredArea);
		return task.venues.stream().filter(venue ->
		{
			if (areaRestricted)
			{
				return venue.key.equals(area) || venue.names.stream().anyMatch(name -> areaKey(name).equals(area))
					|| ("wilderness".equals(area) && venue.wilderness);
			}
			return !Integer.valueOf(KRYSTILIA_MASTER_ID).equals(masterId) || venue.wilderness;
		}).sorted(Comparator.comparing(venue -> !travel.containsKey(venue.key)))
			.limit(2).collect(Collectors.toList());
	}

	private static String taskKey(String name)
	{
		return areaKey(name.replaceFirst("(?i)^the\\s+", "")).replaceFirst("s$", "");
	}

	private static String areaKey(String name)
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	static final class Task
	{
		String name;
		List<String> aliases;
		String sourceUrl;
		String imageFile;
		String portraitName;
		boolean boss;
		List<String> supplies = List.of();
		List<Venue> venues;
		transient BufferedImage portrait;
	}

	static final class Venue
	{
		String key;
		String label;
		String creature;
		List<String> names = List.of();
		List<String> requirements = List.of();
		boolean wilderness;
	}

	static final class Travel
	{
		String note;
		List<Option> options;
	}

	static final class Option
	{
		String label;
		String directions;
		List<String> requirements = List.of();
	}
}
