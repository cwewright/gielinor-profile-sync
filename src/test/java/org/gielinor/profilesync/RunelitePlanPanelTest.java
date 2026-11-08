package org.gielinor.profilesync;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

		SwingUtilities.invokeAndWait(() -> panel.showPlan(plan));
		List<Component> firstRender = descendants(panel);
		assertEquals(2, firstRender.stream().filter(JCheckBox.class::isInstance).count());
		JProgressBar initialProgress = firstRender.stream().filter(JProgressBar.class::isInstance)
			.map(JProgressBar.class::cast).findFirst().orElseThrow(AssertionError::new);
		assertEquals(1, initialProgress.getValue());
		assertTrue(firstRender.stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
			.anyMatch(label -> label.getText().contains("Oak &lt;larder&gt;")));

		JCheckBox localCheck = firstRender.stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
			.filter(Component::isEnabled).findFirst().orElseThrow(AssertionError::new);
		SwingUtilities.invokeAndWait(localCheck::doClick);
		JProgressBar completedProgress = descendants(panel).stream().filter(JProgressBar.class::isInstance)
			.map(JProgressBar.class::cast).findFirst().orElseThrow(AssertionError::new);
		assertEquals(2, completedProgress.getValue());

		SwingUtilities.invokeAndWait(() -> panel.showPlan(null));
		assertEquals(0, descendants(panel).stream().filter(JCheckBox.class::isInstance).count());
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
