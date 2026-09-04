package org.gielinor.profilesync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class RunelitePlanStore
{
	static final int MAXIMUM_FILE_BYTES = 64 * 1024;
	private static final int MAXIMUM_ITEMS = 80;
	private final Path planPath;
	private final Clock clock;

	RunelitePlanStore(Path directory)
	{
		this(directory, Clock.systemUTC());
	}

	RunelitePlanStore(Path directory, Clock clock)
	{
		this.planPath = directory.resolve("runelite-plan.json");
		this.clock = clock;
	}

	RunelitePlan read()
	{
		try
		{
			if (!Files.isRegularFile(planPath))
			{
				return null;
			}
			long size = Files.size(planPath);
			if (size < 2 || size > MAXIMUM_FILE_BYTES)
			{
				return null;
			}
			String json = new String(Files.readAllBytes(planPath), StandardCharsets.UTF_8);
			JsonElement parsed = new JsonParser().parse(json);
			if (!parsed.isJsonObject())
			{
				return null;
			}
			RunelitePlan plan = parsePlan(parsed.getAsJsonObject());
			return plan.getExpiresAt().isAfter(clock.instant()) ? plan : null;
		}
		catch (IOException | RuntimeException e)
		{
			return null;
		}
	}

	private static RunelitePlan parsePlan(JsonObject object)
	{
		if (wholeNumber(object, "schemaVersion", 1) != 1)
		{
			throw new IllegalArgumentException("Unsupported plan schema.");
		}
		String theme = text(object, "theme", 20);
		if (!"construction".equals(theme) && !"cooking".equals(theme))
		{
			throw new IllegalArgumentException("Unsupported plan theme.");
		}
		String revision = text(object, "revision", 64);
		if (!UUID.fromString(revision).toString().equals(revision.toLowerCase(Locale.ROOT)))
		{
			throw new IllegalArgumentException("Invalid plan revision.");
		}
		Instant updatedAt = timestamp(object, "updatedAt");
		Instant expiresAt = timestamp(object, "expiresAt");
		if (!expiresAt.isAfter(updatedAt))
		{
			throw new IllegalArgumentException("Invalid plan lifetime.");
		}
		JsonElement itemElement = object.get("items");
		if (itemElement == null || !itemElement.isJsonArray() || itemElement.getAsJsonArray().size() < 1 || itemElement.getAsJsonArray().size() > MAXIMUM_ITEMS)
		{
			throw new IllegalArgumentException("Invalid plan checklist.");
		}

		List<RunelitePlan.Item> items = new ArrayList<>();
		Set<String> itemIds = new HashSet<>();
		for (JsonElement element : itemElement.getAsJsonArray())
		{
			if (!element.isJsonObject())
			{
				throw new IllegalArgumentException("Invalid plan item.");
			}
			JsonObject item = element.getAsJsonObject();
			String id = text(item, "id", 96);
			if (!itemIds.add(id.toLowerCase(Locale.ROOT)))
			{
				throw new IllegalArgumentException("Duplicate plan item.");
			}
			String state = text(item, "state", 16);
			if (!"ready".equals(state) && !"partial".equals(state) && !"missing".equals(state) && !"unknown".equals(state))
			{
				throw new IllegalArgumentException("Invalid item state.");
			}
			String kind = optionalText(item, "kind", 16);
			if (kind != null && !"material".equals(kind) && !"tool".equals(kind) && !"facility".equals(kind))
			{
				throw new IllegalArgumentException("Invalid item kind.");
			}
			items.add(new RunelitePlan.Item(
				id,
				text(item, "label", 100),
				wholeNumber(item, "required", Integer.MAX_VALUE),
				optionalWholeNumber(item, "owned"),
				optionalWholeNumber(item, "missing"),
				state,
				optionalText(item, "note", 160),
				optionalBoolean(item, "optional"),
				kind));
		}

		return new RunelitePlan(
			text(object, "planKey", 160),
			theme,
			text(object, "targetLabel", 140),
			text(object, "targetDetail", 320),
			text(object, "freshnessLabel", 140),
			revision,
			updatedAt,
			expiresAt,
			items);
	}

	private static String text(JsonObject object, String field, int maximumLength)
	{
		JsonElement element = object.get(field);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
		{
			throw new IllegalArgumentException("Missing text field.");
		}
		String value = element.getAsString().trim().replaceAll("\\s+", " ");
		if (value.isEmpty() || value.length() > maximumLength)
		{
			throw new IllegalArgumentException("Invalid text field.");
		}
		return value;
	}

	private static String optionalText(JsonObject object, String field, int maximumLength)
	{
		return object.has(field) && !object.get(field).isJsonNull() ? text(object, field, maximumLength) : null;
	}

	private static int wholeNumber(JsonObject object, String field, int maximum)
	{
		JsonElement element = object.get(field);
		if (element == null || !element.isJsonPrimitive())
		{
			throw new IllegalArgumentException("Missing numeric field.");
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isNumber())
		{
			throw new IllegalArgumentException("Invalid numeric field.");
		}
		try
		{
			int value = new BigDecimal(primitive.getAsString()).intValueExact();
			if (value < 0 || value > maximum)
			{
				throw new IllegalArgumentException("Numeric field out of range.");
			}
			return value;
		}
		catch (ArithmeticException | NumberFormatException e)
		{
			throw new IllegalArgumentException("Invalid numeric field.", e);
		}
	}

	private static Integer optionalWholeNumber(JsonObject object, String field)
	{
		return object.has(field) && !object.get(field).isJsonNull() ? wholeNumber(object, field, Integer.MAX_VALUE) : null;
	}

	private static boolean optionalBoolean(JsonObject object, String field)
	{
		if (!object.has(field) || object.get(field).isJsonNull())
		{
			return false;
		}
		JsonElement element = object.get(field);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
		{
			throw new IllegalArgumentException("Invalid boolean field.");
		}
		return element.getAsBoolean();
	}

	private static Instant timestamp(JsonObject object, String field)
	{
		try
		{
			return Instant.parse(text(object, field, 40));
		}
		catch (DateTimeParseException e)
		{
			throw new IllegalArgumentException("Invalid plan timestamp.", e);
		}
	}
}
