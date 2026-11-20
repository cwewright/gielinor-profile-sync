package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedChecklistPanelTest
{
	@Test
	public void packedSuppliesStayHiddenAcrossPanelRecreationButNotNewPlansOrCharacters() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			Map<String, String> saved = new HashMap<>();
			RunelitePlanProgress progress = new RunelitePlanProgress(new Gson(), saved::get, saved::put);
			RunelitePlanPanel first = new RunelitePlanPanel(progress);
			first.setAccount("Sailor A");
			first.showPlan(plan("revision-one"));
			assertEquals(1, checkboxes(first));
			first.showInventory("revision-one", Collections.singletonMap("raw salmon", 1));
			assertEquals(0, checkboxes(first));
			assertFalse(saved.isEmpty());

			RunelitePlanPanel restarted = new RunelitePlanPanel(progress);
			restarted.setAccount("Sailor A");
			restarted.showPlan(plan("revision-one"));
			restarted.showInventory("revision-one", Collections.emptyMap());
			assertEquals(0, checkboxes(restarted));
			restarted.setAccount("Sailor B");
			assertEquals(1, checkboxes(restarted));
			restarted.setAccount("Sailor A");
			assertEquals(0, checkboxes(restarted));
			restarted.showPlan(plan("revision-two"));
			assertEquals(1, checkboxes(restarted));
		});
	}

	@Test
	public void standaloneChecklistTextFitsTheActualSidebarWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			RunelitePlanPanel panel = new RunelitePlanPanel();
			panel.showPlan(plan("layout"));
			panel.setSize(PluginPanel.PANEL_WIDTH, 1400);
			for (int pass = 0; pass < 3; pass++) { layout(panel); }
			checkBounds(panel, panel);
		});
	}

	private static RunelitePlan plan(String revision)
	{
		return new RunelitePlan("cooking:test", "cooking", "Prepare a useful batch of salmon", "Pack the required raw supplies before making your way to a range or fire.", "Cached bank evidence", revision, Instant.now(), Instant.now().plusSeconds(3600), Collections.singletonList(new RunelitePlan.Item("salmon", "Raw salmon", 1, 1, 0, "ready", "Bring the raw supply in your inventory.", false, "material")));
	}
	private static int checkboxes(Container parent)
	{
		int count = 0;
		for (Component child : parent.getComponents())
		{
			if (child instanceof JCheckBox) { count++; }
			if (child instanceof Container) { count += checkboxes((Container) child); }
		}
		return count;
	}
	private static void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
	}
	private static void checkBounds(Container parent, Container root)
	{
		for (Component child : parent.getComponents())
		{
			if (!child.isVisible()) { continue; }
			if (child instanceof SidebarLabel)
			{
				Rectangle box = SwingUtilities.convertRectangle(parent, child.getBounds(), root);
				assertTrue("Text starts outside the sidebar", box.x >= 0);
				assertTrue("Text extends beyond the sidebar", box.x + box.width <= root.getWidth());
				assertTrue("Text wrapping exceeds its actual bounds", child.getPreferredSize().width <= child.getWidth());
			}
			if (child instanceof Container) { checkBounds((Container) child, root); }
		}
	}
}
