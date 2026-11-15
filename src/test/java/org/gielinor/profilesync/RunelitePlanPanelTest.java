package org.gielinor.profilesync;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunelitePlanPanelTest
{
	@Test
	public void rendersAReadOnlyPlanAndKeepsLocalChecks() throws Exception
	{
		RunelitePlan plan = new RunelitePlan(
			"construction:test",
			"construction",
			"Oak <larder>",
			"Build one useful upgrade.",
			"Fresh bank evidence",
			"67c03f5b-4368-4c75-b2b4-24042a32ab2f",
			Instant.parse("2026-09-03T19:00:00Z"),
			Instant.parse("2026-10-03T19:00:00Z"),
			Arrays.asList(
				new RunelitePlan.Item("planks", "Oak plank", 8, 8, 0, "ready", null, false, "material"),
				new RunelitePlan.Item("hammer", "Hammer", 1, null, null, "unknown", "Verify at the bank", false, "tool")));
		RunelitePlanPanel panel = new RunelitePlanPanel();

		SwingUtilities.invokeAndWait(() -> { panel.showPlan(plan); showChecked(panel); });
		List<Component> firstRender = descendants(panel);
		assertEquals(2, firstRender.stream().filter(JCheckBox.class::isInstance).count());
		JProgressBar initialProgress = firstRender.stream().filter(JProgressBar.class::isInstance)
			.map(JProgressBar.class::cast).findFirst().orElseThrow(AssertionError::new);
		assertEquals(0, initialProgress.getValue());
		assertTrue(firstRender.stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
			.anyMatch(label -> label.getText().contains("Oak &lt;larder&gt;")));

		JCheckBox localCheck = firstRender.stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
			.filter(Component::isEnabled).findFirst().orElseThrow(AssertionError::new);
		assertFalse(localCheck.isSelected());
		SwingUtilities.invokeAndWait(localCheck::doClick);
		JProgressBar completedProgress = descendants(panel).stream().filter(JProgressBar.class::isInstance)
			.map(JProgressBar.class::cast).findFirst().orElseThrow(AssertionError::new);
		assertEquals(1, completedProgress.getValue());
		SwingUtilities.invokeAndWait(() -> panel.showPlan(plan));
		JCheckBox readySupply = descendants(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
			.findFirst().orElseThrow(AssertionError::new);
		assertTrue(readySupply.isSelected());
		assertTrue(readySupply.isEnabled());
		SwingUtilities.invokeAndWait(readySupply::doClick);
		assertEquals(0, descendants(panel).stream().filter(JProgressBar.class::isInstance)
			.map(JProgressBar.class::cast).findFirst().orElseThrow(AssertionError::new).getValue());

		SwingUtilities.invokeAndWait(() -> panel.showPlan(null));
		assertEquals(0, descendants(panel).stream().filter(JCheckBox.class::isInstance).count());
	}

	@Test
	public void checksPackedSuppliesAndKeepsChecksAfterUse() throws Exception
	{
		RunelitePlan plan = cookingPlan("revision");
		RunelitePlanPanel panel = new RunelitePlanPanel();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.showPlan(plan);
			showChecked(panel);
			panel.showInventory("revision", Collections.singletonMap("salmon", 2));
		});
		assertFalse(supplyCheck(panel).isSelected());
		SwingUtilities.invokeAndWait(() -> panel.showInventory("revision", Collections.singletonMap("raw salmon", 1)));
		assertFalse(supplyCheck(panel).isSelected());
		SwingUtilities.invokeAndWait(() -> panel.showInventory("revision", Collections.singletonMap("raw salmon", 2)));
		assertTrue(supplyCheck(panel).isSelected());
		assertTrue(supplyCheck(panel).isEnabled());
		SwingUtilities.invokeAndWait(() ->
		{
			panel.showPlan(plan);
			panel.showInventory("revision", Collections.emptyMap());
		});
		assertTrue(supplyCheck(panel).isSelected());
		assertTrue(descendants(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
			.anyMatch(label -> label.getText().equals("0 / 2 in inventory")));
	}

	@Test
	public void inventoryUpdatesRespectManualUnchecking() throws Exception
	{
		RunelitePlanPanel panel = new RunelitePlanPanel();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.showPlan(cookingPlan("revision"));
			showChecked(panel);
			panel.showInventory("revision", Collections.singletonMap("raw salmon", 2));
			supplyCheck(panel).doClick();
			panel.showInventory("revision", Collections.singletonMap("raw salmon", 3));
		});
		assertFalse(supplyCheck(panel).isSelected());
		assertTrue(descendants(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
			.anyMatch(label -> label.getText().equals("3 / 2 in inventory")));
		SwingUtilities.invokeAndWait(() ->
		{
			panel.resetSession();
			showChecked(panel);
			panel.showInventory("revision", Collections.singletonMap("raw salmon", 2));
		});
		assertTrue(supplyCheck(panel).isSelected());
	}

	@Test
	public void newPlanClearsChecksAndIgnoresAnOldInventoryUpdate() throws Exception
	{
		RunelitePlanPanel panel = new RunelitePlanPanel();
		SwingUtilities.invokeAndWait(() ->
		{
			panel.showPlan(cookingPlan("old"));
			panel.showInventory("old", Collections.singletonMap("raw salmon", 2));
			panel.showPlan(cookingPlan("new"));
			panel.showInventory("old", Collections.singletonMap("raw salmon", 2));
		});
		assertFalse(supplyCheck(panel).isSelected());
		assertEquals(0, descendants(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
			.filter(label -> label.getText().contains("in inventory")).count());
		SwingUtilities.invokeAndWait(() -> panel.showInventory("new", Collections.singletonMap("range", 1)));
		assertEquals(0, descendants(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
			.filter(JCheckBox::isSelected).count());
	}

	private static void showChecked(RunelitePlanPanel panel)
	{
		for (Component child : descendants(panel))
		{
			if (child instanceof JButton && "Show checked supplies".equals(((JButton) child).getText()))
			{
				((JButton) child).doClick();
				return;
			}
		}
	}

	private static RunelitePlan cookingPlan(String revision)
	{
		return new RunelitePlan("cooking:salmon:2", "cooking", "2 × Salmon", "Range or fire", "Cached pantry",
			revision, Instant.now(), Instant.now().plusSeconds(3600), Arrays.asList(
				new RunelitePlan.Item("raw salmon", "Raw salmon", 2, 401, 0, "ready", null, false, "material"),
				new RunelitePlan.Item("range", "Range", 0, null, null, "unknown", null, false, "facility")));
	}

	private static JCheckBox supplyCheck(RunelitePlanPanel panel)
	{
		return descendants(panel).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static List<Component> descendants(Container root)
	{
		List<Component> result = new ArrayList<>();
		for (Component component : root.getComponents())
		{
			result.add(component);
			if (component instanceof Container)
			{
				result.addAll(descendants((Container) component));
			}
		}
		return result;
	}
}
