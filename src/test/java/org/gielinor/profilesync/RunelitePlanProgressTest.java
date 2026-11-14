package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunelitePlanProgressTest
{
	@Test public void storageIsScopedToCharacterAndPublishedRevision()
	{
		Map<String, String> settings = new HashMap<>();
		RunelitePlanProgress progress = new RunelitePlanProgress(new Gson(), settings::get, settings::put);
		progress.write("First Captain", "revision", Set.of("fish"), Set.of("fish"));
		assertTrue(progress.read("first captain", "revision").checked.contains("fish"));
		assertTrue(progress.read("Other Captain", "revision").checked.isEmpty());
		assertTrue(progress.read("First Captain", "new revision").checked.isEmpty());
		settings.replaceAll((key, value) -> "{broken");
		assertTrue(progress.read("First Captain", "revision").checked.isEmpty());
	}

	@Test public void packedItemsStayHiddenAfterRestartAndCanBeUnchecked() throws Exception
	{
		Map<String, String> settings = new HashMap<>();
		RunelitePlanProgress store = new RunelitePlanProgress(new Gson(), settings::get, settings::put);
		SwingUtilities.invokeAndWait(() -> {
			RunelitePlanPanel original = new RunelitePlanPanel(store);
			original.setAccount("Captain");
			original.showPlan(plan("revision"));
			original.showInventory("revision", Map.of("raw salmon", 1));
			assertFalse(hasSupply(original));
			assertTrue(labels(original).contains("All supplies are checked"));
			original.resetSession();
			RunelitePlanPanel restarted = new RunelitePlanPanel(new RunelitePlanProgress(new Gson(), settings::get, settings::put));
			restarted.showPlan(plan("revision"));
			restarted.setAccount("Captain");
			restarted.showInventory("revision", Map.of());
			assertFalse(hasSupply(restarted));
			((JButton) find(restarted, JButton.class)).doClick();
			JCheckBox fish = (JCheckBox) find(restarted, JCheckBox.class);
			assertTrue(fish.isSelected());
			fish.doClick();
			RunelitePlanPanel again = new RunelitePlanPanel(store);
			again.setAccount("Captain");
			again.showPlan(plan("revision"));
			again.showInventory("revision", Map.of("raw salmon", 1));
			assertTrue(hasSupply(again));
			assertFalse(((JCheckBox) find(again, JCheckBox.class)).isSelected());
			again.showPlan(plan("new revision"));
			assertTrue(hasSupply(again));
			again.showInventory("new revision", Map.of("raw salmon", 1));
			assertFalse(hasSupply(again));
		});
	}

	@Test public void switchingCharactersDoesNotHideAnotherCharactersSupplies() throws Exception
	{
		Map<String, String> settings = new HashMap<>();
		RunelitePlanProgress store = new RunelitePlanProgress(new Gson(), settings::get, settings::put);
		store.write("First", "revision", Set.of("fish"), Set.of("fish"));
		SwingUtilities.invokeAndWait(() -> {
			RunelitePlanPanel panel = new RunelitePlanPanel(store);
			panel.showPlan(plan("revision"));
			panel.setAccount("First");
			assertFalse(hasSupply(panel));
			panel.resetSession();
			panel.setAccount("Second");
			assertTrue(hasSupply(panel));
		});
	}

	private static RunelitePlan plan(String revision)
	{
		return new RunelitePlan("cooking:salmon", "cooking", "Salmon", "Cook one.", "Pantry", revision,
			Instant.now(), Instant.now().plusSeconds(3600), List.of(new RunelitePlan.Item("fish", "Raw salmon", 1, 1, 0, "ready", null, false, "material")));
	}
	private static boolean hasSupply(Container parent) { return find(parent, JCheckBox.class) != null; }
	private static Component find(Container parent, Class<?> type)
	{
		for (Component child : parent.getComponents())
		{
			if (type.isInstance(child)) { return child; }
			if (child instanceof Container) { Component found = find((Container) child, type); if (found != null) { return found; } }
		}
		return null;
	}
	private static String labels(Container parent)
	{
		StringBuilder result = new StringBuilder();
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel) { result.append(((JLabel) child).getText()); }
			if (child instanceof Container) { result.append(labels((Container) child)); }
		}
		return result.toString();
	}
}
