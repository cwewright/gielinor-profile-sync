package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

final class ProfileExportStore
{
	private final Gson gson;
	private final Path directory;

	ProfileExportStore(Gson gson, Path directory)
	{
		this.gson = gson.newBuilder().serializeNulls().setPrettyPrinting().create();
		this.directory = directory;
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> readLatest()
	{
		Path latest = directory.resolve("latest.json");
		if (!Files.isRegularFile(latest))
		{
			return Collections.emptyMap();
		}

		try
		{
			String json = new String(Files.readAllBytes(latest), StandardCharsets.UTF_8);
			Map<String, Object> parsed = gson.fromJson(json, Map.class);
			return parsed != null ? parsed : Collections.emptyMap();
		}
		catch (IOException | RuntimeException e)
		{
			return Collections.emptyMap();
		}
	}

	void write(String rsn, Map<String, Object> snapshot) throws IOException
	{
		Files.createDirectories(directory);
		String json = gson.toJson(snapshot) + System.lineSeparator();
		writeAtomically(directory.resolve("latest.json"), json);
		writeAtomically(directory.resolve(safeFileName(rsn) + ".json"), json);
	}

	private void writeAtomically(Path destination, String json) throws IOException
	{
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
		try
		{
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	static String safeFileName(String rsn)
	{
		String safe = rsn == null ? "" : rsn.replaceAll("[^a-zA-Z0-9 _-]+", "_").trim();
		safe = safe.replaceAll("^[ _.]+|[ _.]+$", "");
		if (safe.isEmpty())
		{
			return "player";
		}
		if (safe.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
		{
			return "_" + safe;
		}
		return safe;
	}
}
