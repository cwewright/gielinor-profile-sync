package org.gielinor.profilesync;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarLayoutTest
{
	@Test public void paragraphsWrapToTheirActualContainerWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			JPanel parent = new JPanel();
			parent.setSize(160, 500);
			SidebarLabel label = new SidebarLabel("Suggestions and detailed quest requirements should wrap inside a narrow sidebar without clipping.", Color.WHITE);
			parent.add(label);
			assertEquals(160, label.getPreferredSize().width);
			assertTrue(label.getPreferredSize().height > 30);
			parent.setSize(120, 500);
			assertEquals(120, label.getPreferredSize().width);
			assertTrue(label.getMinimumSize().width < 120);
		});
	}

	@Test public void expandedOrdersAndChecklistFitRuneLitesSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			RuneliteOrdersPanel panel = new RuneliteOrdersPanel();
			panel.showPlan(new RunelitePlan("cooking:test", "cooking", "1 × Salmon", "A long cooking description with details about preparing your supplies.", "Cached bank", "revision", Instant.now(), Instant.now().plusSeconds(3600), List.of(new RunelitePlan.Item("fish", "Raw salmon", 1, 1, 0, "ready", null, false, "material"))));
			panel.showOrders(List.of(new RuneliteOrders.Card("quest:test", "quest", "Barbarian Training", "Tracked requirements met. A detailed quest summary should wrap and remain inside the available space.", List.of("Resume and complete Barbarian Training at your own pace."), null, null, null, 0)), null, true);
			findFocus(panel).doClick();
			panel.setSize(PluginPanel.PANEL_WIDTH, 1400);
			for (int i = 0; i < 3; i++) { layout(panel); }
			checkBounds(panel, panel);
		});
	}

	private static JButton findFocus(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && "Make this my focus".equals(((JButton) child).getText())) { return (JButton) child; }
			if (child instanceof Container) { JButton button = findFocus((Container) child); if (button != null) { return button; } }
		}
		return null;
	}
	private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } } }
	private static void checkBounds(Container parent, Container root)
	{
		for (Component child : parent.getComponents())
		{
			if (!child.isVisible()) { continue; }
			if (child instanceof SidebarLabel)
			{
				Rectangle box = SwingUtilities.convertRectangle(parent, child.getBounds(), root);
				assertTrue("Text starts outside the sidebar", box.x >= 0);
				assertTrue("Text extends beyond the sidebar: " + box, box.x + box.width <= root.getWidth());
				assertTrue("Text wrapping is wider than its actual bounds", child.getPreferredSize().width <= child.getWidth());
			}
			if (child instanceof Container) { checkBounds((Container) child, root); }
		}
	}
}
