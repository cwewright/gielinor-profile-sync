package org.gielinor.profilesync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RunelitePlanStoreTest
{
	private static final Instant NOW = Instant.parse("2026-09-03T20:00:00Z");

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void readsValidatedCurrentPlan() throws Exception
	{
		writePlan(validPlan("2026-10-03T20:00:00Z"));

		RunelitePlan plan = store().read();

		assertNotNull(plan);
		assertEquals("construction", plan.getTheme());
		assertEquals("Oak larder", plan.getTargetLabel());
		assertEquals(2, plan.getItems().size());
		assertEquals(Integer.valueOf(5), plan.getItems().get(0).getOwned());
		assertTrue(plan.getItems().get(0).isReady());
	}

	@Test
	public void rejectsExpiredMalformedAndFractionalPlans() throws Exception
	{
		writePlan(validPlan("2026-09-03T19:59:59Z"));
		assertNull(store().read());

		writePlan("{not-json");
		assertNull(store().read());

		writePlan(validPlan("2026-10-03T20:00:00Z").replace("\"required\": 5", "\"required\": 5.5"));
		assertNull(store().read());
	}

	@Test
	public void rejectsOversizedAndDuplicateItemPlans() throws Exception
	{
		Path planPath = temporaryFolder.getRoot().toPath().resolve("runelite-plan.json");
		Files.write(planPath, new byte[RunelitePlanStore.MAXIMUM_FILE_BYTES + 1]);
		assertNull(store().read());

		String duplicate = validPlan("2026-10-03T20:00:00Z").replace("\"id\": \"hammer\"", "\"id\": \"planks\"");
		writePlan(duplicate);
		assertNull(store().read());
	}

	private RunelitePlanStore store()
	{
		return new RunelitePlanStore(
			temporaryFolder.getRoot().toPath(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void writePlan(String json) throws Exception
	{
		Files.write(
			temporaryFolder.getRoot().toPath().resolve("runelite-plan.json"),
			json.getBytes(StandardCharsets.UTF_8));
	}

	private static String validPlan(String expiresAt)
	{
		return "{\n" +
			"  \"schemaVersion\": 1,\n" +
			"  \"planKey\": \"construction:oak-larder\",\n" +
			"  \"theme\": \"construction\",\n" +
			"  \"targetLabel\": \"Oak larder\",\n" +
			"  \"targetDetail\": \"Build one in the kitchen.\",\n" +
			"  \"freshnessLabel\": \"Bank seen moments ago\",\n" +
			"  \"revision\": \"67c03f5b-4368-4c75-b2b4-24042a32ab2f\",\n" +
			"  \"updatedAt\": \"2026-09-03T19:00:00Z\",\n" +
			"  \"expiresAt\": \"" + expiresAt + "\",\n" +
			"  \"items\": [\n" +
			"    {\"id\": \"planks\", \"label\": \"Oak plank\", \"required\": 5, \"owned\": 5, \"missing\": 0, \"state\": \"ready\", \"kind\": \"material\"},\n" +
			"    {\"id\": \"hammer\", \"label\": \"Hammer\", \"required\": 1, \"state\": \"unknown\", \"optional\": true, \"note\": \"Verify before leaving the bank\", \"kind\": \"tool\"}\n" +
			"  ]\n" +
			"}";
	}
}
