package org.gielinor.profilesync;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RuneliteOrdersTest
{
	private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	private RuneliteOrders read(String json) throws Exception
	{
		Files.write(folder.getRoot().toPath().resolve("runelite-orders.json"), json.getBytes(StandardCharsets.UTF_8));
		return RuneliteOrders.read(Filepath.Unchecked.getRooted(folder.getRoot().toPath()), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private String document()
	{
		return "{\"schemaVersion\":1,\"accountName\":\"Test Captain\",\"updatedAt\":\"2026-11-13T05:00:00Z\","
			+ "\"snapshotAt\":\"2026-11-13T04:59:00Z\",\"expiresAt\":\"2026-11-14T05:00:00Z\",\"cards\":["
			+ "{\"id\":\"quest:test\",\"kind\":\"quest\",\"questName\":\"Test Quest\",\"title\":\"Quest <test>\","
			+ "\"summary\":\"Tracked requirements met\",\"details\":[\"Useful unlock\"],\"sourceUrl\":\"https://oldschool.runescape.wiki/w/Test_Quest\"},"
			+ "{\"id\":\"skill:Herblore\",\"kind\":\"skill\",\"skill\":\"Herblore\",\"targetXp\":50000,\"title\":\"Herblore\","
			+ "\"summary\":\"Lowest skill at sync\",\"details\":[\"Steady option\",\"Another idea\"],\"sourceUrl\":\"https://oldschool.runescape.wiki/w/Herblore_training\"}]}";
	}

	@Test public void validatesLifetimeAccountAndGuideBoundaries() throws Exception
	{
		RuneliteOrders orders = read(document());
		assertNotNull(orders);
		assertTrue(orders.belongsTo("test_captain"));
		assertFalse(orders.belongsTo("Other Captain"));
		assertEquals(2, orders.cards.size());
		assertNull(read(document().replace("2026-11-14T05:00:00Z", "2026-11-13T05:59:00Z")));
		assertNull(read(document().replace("2026-11-14T05:00:00Z", "2026-11-15T05:00:00Z")));
		assertNull(read(document().replace("\"targetXp\":50000", "\"targetXp\":1.5")));
		assertNull(read(document().replace("oldschool.runescape.wiki/w/Test_Quest", "evil.example/w/Test_Quest")));
		assertNull(read(document().replace("https://oldschool.runescape.wiki/w/Test_Quest", "javascript:alert(1)")));
		assertNull(read(document().replace("skill:Herblore", "quest:test")));
		assertNull(read("{invalid"));
		assertNull(read(" ".repeat(RuneliteOrders.MAXIMUM_BYTES + 1)));
	}

	@Test public void onlyObservedAssignmentsBecomeCards()
	{
		assertTrue(RuneliteOrdersLive.tasks(Collections.emptyMap(), Collections.emptyMap()).isEmpty());
		Map<String, Object> slayer = Map.of("taskLoaded", true, "task", Map.of("monster", "Trolls", "remaining", 12, "requiredLocation", "Death Plateau"));
		Map<String, Object> hunter = Map.of("loaded", true, "fromCache", true, "current", Map.of("active", true, "rumour", "Sabre-toothed kyatt", "hunterName", "Teco", "complete", true));
		List<RuneliteOrders.Card> cards = RuneliteOrdersLive.tasks(slayer, hunter);
		assertEquals(2, cards.size());
		assertEquals("12 remaining", cards.get(0).summary);
		assertTrue(cards.get(0).details.get(0).contains("Death Plateau"));
		assertTrue(cards.get(1).summary.contains("Rare piece found"));
		assertTrue(cards.get(1).details.get(1).contains("Retained"));
		assertTrue(RuneliteOrdersLive.tasks(Map.of("taskLoaded", true, "task", Map.of("monster", "Trolls", "remaining", 0)), Collections.emptyMap()).isEmpty());
	}

	@Test public void suggestionsFollowLiveCompletionXpAndAccountSwitches() throws Exception
	{
		RuneliteOrders orders = read(document());
		List<RuneliteOrders.Card> cards = RuneliteOrdersLive.suggestions(orders, "Test Captain", name -> true, name -> 49000, name -> 40, NOW);
		assertEquals(1, cards.size());
		assertTrue(cards.get(0).summary.contains("1,000 XP"));
		assertTrue(RuneliteOrdersLive.suggestions(orders, "Other Captain", name -> false, name -> 0, name -> 1, NOW).isEmpty());
		assertTrue(RuneliteOrdersLive.suggestions(orders, "Test Captain", name -> false, name -> 0, name -> 1, NOW.plusSeconds(86400)).isEmpty());
		cards = RuneliteOrdersLive.suggestions(orders, "Test Captain", name -> false, name -> 51000, name -> 42, NOW);
		assertEquals(2, cards.size());
		assertTrue(cards.get(1).summary.contains("Milestone reached"));
	}

	@Test public void focusSurvivesProgressUpdatesAndNeverResetsSupplyChecks() throws Exception
	{
		RuneliteOrders orders = read(document());
		RuneliteOrdersPanel panel = new RuneliteOrdersPanel();
		RunelitePlan plan = new RunelitePlan("cooking:test", "cooking", "Salmon", "Cook one.", "Bank observed", "revision", NOW, NOW.plusSeconds(3600),
			List.of(new RunelitePlan.Item("salmon", "Raw salmon", 1, 1, 0, "ready", null, false, "material")));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.showPlan(plan);
			panel.showOrders(orders.cards, orders, true);
			panel.showInventory("revision", Map.of("raw salmon", 1));
		});
		assertTrue(descendants(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).findFirst().get().isSelected());
		JButton focus = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
			.filter(button -> button.getText().equals("Make this my focus")).findFirst().get();
		SwingUtilities.invokeAndWait(focus::doClick);
		assertTrue(labels(panel).contains("Useful unlock"));
		assertFalse(labels(panel).contains("Lowest skill at sync"));
		assertTrue(labels(panel).contains("Quest &lt;test&gt;"));
		List<RuneliteOrders.Card> changed = List.of(orders.cards.get(0).withSummary("Live progress"), orders.cards.get(1));
		SwingUtilities.invokeAndWait(() -> panel.showOrders(changed, orders, true));
		assertTrue(labels(panel).contains("Useful unlock"));
		assertTrue(descendants(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).findFirst().get().isSelected());
		SwingUtilities.invokeAndWait(() -> panel.showOrders(List.of(orders.cards.get(1)), orders, true));
		assertFalse(labels(panel).contains("Useful unlock"));
		assertTrue(labels(panel).contains("Lowest skill at sync"));
		SwingUtilities.invokeAndWait(panel::resetSession);
		assertFalse(labels(panel).contains("Herblore"));
	}

	private static String labels(Container root)
	{
		StringBuilder result = new StringBuilder();
		for (Component component : descendants(root)) { if (component instanceof JLabel) { result.append(((JLabel) component).getText()); } }
		return result.toString();
	}

	private static List<Component> descendants(Container root)
	{
		List<Component> all = new ArrayList<>();
		for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container) { all.addAll(descendants((Container) child)); } }
		return all;
	}
}
