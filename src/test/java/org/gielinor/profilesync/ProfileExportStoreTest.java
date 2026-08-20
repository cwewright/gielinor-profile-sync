package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileExportStoreTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void writesLatestAndAccountSnapshot() throws Exception
	{
		ProfileExportStore store = new ProfileExportStore(new Gson(), temporaryFolder.getRoot().toPath());
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("schemaVersion", 1);
		snapshot.put("rsn", "Chew/4L");

		store.write("Chew/4L", snapshot);

		assertTrue(Files.isRegularFile(temporaryFolder.getRoot().toPath().resolve("latest.json")));
		assertTrue(Files.isRegularFile(temporaryFolder.getRoot().toPath().resolve("Chew_4L.json")));
		assertEquals("Chew/4L", store.readLatest().get("rsn"));
		assertEquals("Chew/4L", store.readAccount("Chew/4L").get("rsn"));
	}

	@Test
	public void readsEachAccountSnapshotInsteadOfOnlyLatest() throws Exception
	{
		ProfileExportStore store = new ProfileExportStore(new Gson(), temporaryFolder.getRoot().toPath());
		Map<String, Object> sailorA = new LinkedHashMap<>();
		sailorA.put("rsn", "Sailor A");
		Map<String, Object> sailorB = new LinkedHashMap<>();
		sailorB.put("rsn", "Sailor B");
		store.write("Sailor A", sailorA);
		store.write("Sailor B", sailorB);

		assertEquals("Sailor B", store.readLatest().get("rsn"));
		assertEquals("Sailor A", store.readAccount("Sailor A").get("rsn"));
		assertEquals("Sailor B", store.readAccount("Sailor B").get("rsn"));
	}

	@Test
	public void writesExplicitNullsForUnavailableSignals() throws Exception
	{
		ProfileExportStore store = new ProfileExportStore(new Gson(), temporaryFolder.getRoot().toPath());
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("tierComplete", null);

		store.write("player", snapshot);

		String json = new String(
			Files.readAllBytes(temporaryFolder.getRoot().toPath().resolve("latest.json")),
			java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(json.contains("\"tierComplete\": null"));
	}

	@Test
	public void fallsBackToPlayerForUnsafeEmptyName()
	{
		assertEquals("player", ProfileExportStore.safeFileName("***"));
		assertEquals("player", ProfileExportStore.safeFileName(null));
		assertEquals("_CON", ProfileExportStore.safeFileName("CON"));
	}
}
