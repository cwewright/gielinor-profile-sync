package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileExportStoreTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void writesLatestAndAccountSnapshot() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporaryFolder.getRoot().toPath());
		ProfileExportStore store = new ProfileExportStore(new Gson(), directory);
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("schemaVersion", 1);
		snapshot.put("rsn", "Chew/4L");

		store.write("Chew/4L", snapshot);

		assertTrue(Files.isRegularFile(temporaryFolder.getRoot().toPath().resolve("latest.json")));
		assertTrue(Files.isRegularFile(temporaryFolder.getRoot().toPath().resolve("Chew_4L.json")));
		assertEquals("Chew/4L", store.readLatest().get("rsn"));
	}

	@Test
	public void replacesExistingSnapshotsAndPreservesUtf8() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporaryFolder.getRoot().toPath()).join("exports");
		ProfileExportStore store = new ProfileExportStore(new Gson(), directory);
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("rsn", "Player");
		store.write("Player", snapshot);

		snapshot.put("note", "Caf\u00e9");
		store.write("Player", snapshot);

		assertEquals("Caf\u00e9", store.readLatest().get("note"));
		assertEquals(Files.readString(temporaryFolder.getRoot().toPath().resolve("exports/latest.json")),
			Files.readString(temporaryFolder.getRoot().toPath().resolve("exports/Player.json")));
		assertFalse(directory.join("latest.json.tmp").exists());
		assertFalse(directory.join("Player.json.tmp").exists());
	}

	@Test
	public void missingOrInvalidSnapshotsReturnAnEmptyMap() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporaryFolder.getRoot().toPath());
		ProfileExportStore store = new ProfileExportStore(new Gson(), directory);
		assertTrue(store.readLatest().isEmpty());

		directory.join("latest.json").write("not json");
		assertTrue(store.readLatest().isEmpty());
	}

	@Test
	public void fallsBackToPlayerForUnsafeEmptyName()
	{
		assertEquals("player", ProfileExportStore.safeFileName("***"));
		assertEquals("player", ProfileExportStore.safeFileName(null));
		assertEquals("_CON", ProfileExportStore.safeFileName("CON"));
	}
}
