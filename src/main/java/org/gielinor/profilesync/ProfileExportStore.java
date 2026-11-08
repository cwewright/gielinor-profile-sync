package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import net.runelite.client.util.Filepath;

final class ProfileExportStore
{
	private final Gson gson;
	private final Filepath directory;

	ProfileExportStore(Gson gson, Filepath directory)
	{
		this.gson = gson.newBuilder().serializeNulls().setPrettyPrinting().create();
		this.directory = directory;
	}

	Map<String, Object> readLatest()
	{
		return read(directory.join("latest.json"));
	}

	Map<String, Object> readAccount(String rsn)
	{
		return read(directory.join(safeFileName(rsn) + ".json"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> read(Filepath latest)
	{
		if (!latest.isFile())
		{
			return Collections.emptyMap();
		}

		try (Reader reader = latest.openBufferedReader())
		{
			Map<String, Object> parsed = gson.fromJson(reader, Map.class);
			return parsed != null ? parsed : Collections.emptyMap();
		}
		catch (IOException | RuntimeException e)
		{
			return Collections.emptyMap();
		}
	}

	void write(String rsn, Map<String, Object> snapshot) throws IOException
	{
		directory.createDirectories();
		String json = gson.toJson(snapshot) + System.lineSeparator();
		writeAtomically(directory.join("latest.json"), json);
		writeAtomically(directory.join(safeFileName(rsn) + ".json"), json);
	}

	private void writeAtomically(Filepath destination, String json) throws IOException
	{
		Filepath temporary = directory.join(destination.getFileName() + ".tmp");
		temporary.write(json);
		try
		{
			temporary.moveTo(destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			temporary.moveTo(destination, StandardCopyOption.REPLACE_EXISTING);
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
