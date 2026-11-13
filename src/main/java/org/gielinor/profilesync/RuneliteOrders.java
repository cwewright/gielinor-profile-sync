package org.gielinor.profilesync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.util.Filepath;

import static org.gielinor.profilesync.RunelitePlanStore.*;

/** A small account-bound document delivered by the Windows companion. */
final class RuneliteOrders
{
	static final int MAXIMUM_BYTES = 16 * 1024;
	final String accountName;
	final Instant updatedAt;
	final Instant snapshotAt;
	final Instant expiresAt;
	final List<Card> cards;

	private RuneliteOrders(JsonObject document)
	{
		if (wholeNumber(document, "schemaVersion", 1) != 1) { throw new IllegalArgumentException("Invalid orders schema"); }
		accountName = text(document, "accountName", 40);
		updatedAt = timestamp(document, "updatedAt");
		snapshotAt = timestamp(document, "snapshotAt");
		expiresAt = timestamp(document, "expiresAt");
		if (!expiresAt.isAfter(updatedAt) || expiresAt.isAfter(updatedAt.plusSeconds(86400)) || snapshotAt.isAfter(updatedAt.plusSeconds(300)))
		{
			throw new IllegalArgumentException("Invalid orders lifetime");
		}
		JsonElement entries = document.get("cards");
		if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().size() > 3) { throw new IllegalArgumentException("Invalid orders list"); }
		List<Card> parsed = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		int quests = 0;
		int skills = 0;
		for (JsonElement entry : entries.getAsJsonArray())
		{
			JsonObject value = entry.getAsJsonObject();
			String kind = text(value, "kind", 16);
			String id = text(value, "id", 320);
			if (!ids.add(id)) { throw new IllegalArgumentException("Duplicate orders card"); }
			String questName = null;
			String skill = null;
			int targetXp = 0;
			if ("quest".equals(kind)) { quests++; questName = text(value, "questName", 140); }
			else if ("skill".equals(kind)) { skills++; skill = text(value, "skill", 32); targetXp = wholeNumber(value, "targetXp", 200000000); }
			else { throw new IllegalArgumentException("Invalid orders kind"); }
			JsonElement details = value.get("details");
			if (details == null || !details.isJsonArray() || details.getAsJsonArray().size() > 6) { throw new IllegalArgumentException("Invalid orders details"); }
			List<String> lines = new ArrayList<>();
			for (JsonElement detail : details.getAsJsonArray())
			{
				JsonObject wrapped = new JsonObject(); wrapped.add("line", detail);
				lines.add(text(wrapped, "line", 320));
			}
			String url = text(value, "sourceUrl", 500);
			if (!safeGuide(url)) { throw new IllegalArgumentException("Invalid orders guide"); }
			parsed.add(new Card(id, kind, text(value, "title", 320), text(value, "summary", 320), lines, url, questName, skill, targetXp));
		}
		if (quests > 2 || skills > 1) { throw new IllegalArgumentException("Too many orders cards"); }
		cards = Collections.unmodifiableList(parsed);
	}

	static RuneliteOrders read(Filepath directory, Clock clock)
	{
		Filepath path = directory.join("runelite-orders.json");
		try
		{
			if (!path.isFile() || path.size() > MAXIMUM_BYTES) { return null; }
			byte[] bytes;
			try (InputStream stream = path.openInputStream()) { bytes = stream.readNBytes(MAXIMUM_BYTES + 1); }
			if (bytes.length > MAXIMUM_BYTES) { return null; }
			RuneliteOrders orders = new RuneliteOrders(new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
			return orders.expiresAt.isAfter(clock.instant()) && !orders.updatedAt.isAfter(clock.instant().plusSeconds(300)) ? orders : null;
		}
		catch (IOException | RuntimeException e) { return null; }
	}

	boolean belongsTo(String rsn)
	{
		return rsn != null && normalize(accountName).equals(normalize(rsn));
	}

	private static String normalize(String name) { return name.replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT); }

	static boolean safeGuide(String value)
	{
		try
		{
			URI uri = URI.create(value);
			return "https".equals(uri.getScheme()) && "oldschool.runescape.wiki".equals(uri.getHost())
				&& uri.getUserInfo() == null && uri.getPort() == -1 && uri.getPath() != null && uri.getPath().startsWith("/w/");
		}
		catch (IllegalArgumentException e) { return false; }
	}

	static final class Card
	{
		final String id, kind, title, summary, sourceUrl, questName, skill;
		final List<String> details;
		final int targetXp;
		Card(String id, String kind, String title, String summary, List<String> details, String sourceUrl, String questName, String skill, int targetXp)
		{
			this.id = id; this.kind = kind; this.title = title; this.summary = summary;
			this.details = Collections.unmodifiableList(new ArrayList<>(details)); this.sourceUrl = sourceUrl;
			this.questName = questName; this.skill = skill; this.targetXp = targetXp;
		}
		Card withSummary(String value) { return new Card(id, kind, title, value, details, sourceUrl, questName, skill, targetXp); }
	}
}
