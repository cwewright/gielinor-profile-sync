package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Small bundled field guide; no remote requests and no player data in the catalogue. */
final class HunterRumourGuide
{
	String verifiedAt;
	Map<String, Equipment> methods;
	List<Creature> creatures;
	private final transient Map<String, Creature> byName = new HashMap<>();
	private final transient Map<String, BufferedImage> images = new HashMap<>();

	static HunterRumourGuide load(Gson gson) throws IOException
	{
		try (InputStream stream = HunterRumourGuide.class.getResourceAsStream("hunter/guide.json"))
		{
			if (stream == null) { throw new IOException("Missing Hunter field guide"); }
			HunterRumourGuide guide = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), HunterRumourGuide.class);
			for (Creature creature : guide.creatures)
			{
				guide.byName.put(creature.name.toLowerCase(Locale.ROOT), creature);
				try (InputStream image = HunterRumourGuide.class.getResourceAsStream("hunter/" + creature.slug + ".png"))
				{
					if (image != null) { guide.images.put(creature.slug, ImageIO.read(image)); }
				}
			}
			guide.byName.put("red chinchompa", guide.byName.get("carnivorous chinchompa"));
			return guide;
		}
	}

	Creature find(String name) { return byName.get(name.toLowerCase(Locale.ROOT)); }
	BufferedImage image(Creature creature) { return images.get(creature.slug); }
	Equipment equipment(Creature creature) { return creature.equipmentOverride == null ? methods.get(creature.method) : creature.equipmentOverride; }

	static final class Creature
	{
		String name;
		String slug;
		String method;
		String sourceUrl;
		int level;
		List<String> locations;
		Equipment equipmentOverride;
	}

	static final class Equipment
	{
		List<String> required;
		List<String> optional;
	}
}
