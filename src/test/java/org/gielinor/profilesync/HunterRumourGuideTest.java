package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class HunterRumourGuideTest
{
	@Test
	public void everySupportedCreatureHasLocalPictureLocationsAndEquipment() throws Exception
	{
		HunterRumourGuide guide = HunterRumourGuide.load(new Gson());
		assertEquals(29, guide.creatures.size());
		for (HunterRumourGuide.Creature creature : guide.creatures)
		{
			assertFalse(creature.name, creature.locations.isEmpty());
			assertFalse(creature.name, guide.equipment(creature).required.isEmpty());
			assertNotNull(creature.name, guide.image(creature));
			assertTrue(guide.image(creature).getWidth() <= 96);
			assertTrue(guide.image(creature).getHeight() <= 96);
			assertTrue(RuneliteOrders.safeGuide(creature.sourceUrl));
		}
		assertEquals("Sabre-toothed kyatt", guide.find("SABRE-TOOTHED KYATT").name);
		assertEquals(List.of("Rellekka Hunter area"), guide.find("Sabre-toothed kyatt").locations);
		assertSame(guide.find("Carnivorous chinchompa"), guide.find("Red chinchompa"));
		assertNull(guide.find("Unobserved creature"));
		assertFalse(guide.equipment(guide.find("Herbiboar")).required.contains("Noose wand"));
	}

	@Test
	public void hunterTabRemainsSeparateAndClearsOnLogout() throws Exception
	{
		HunterRumourGuide guide = HunterRumourGuide.load(new Gson());
		RuneliteOrdersPanel panel = new RuneliteOrdersPanel();
		List<RuneliteOrders.Card> cards = RuneliteOrdersLive.tasks(Collections.emptyMap(), Map.of("loaded", true,
			"current", Map.of("active", true, "rumour", "Sabre-toothed kyatt", "hunterName", "Teco")));
		SwingUtilities.invokeAndWait(() -> {
			panel.showHunterGuide(guide);
			panel.showOrders(cards, null, true);
			JTabbedPane tabs = (JTabbedPane) panel.getComponent(0);
			assertEquals("Hunter Rumours", tabs.getTitleAt(1));
			assertTrue(tabs.getComponentAt(1) instanceof HunterRumourPanel);
			assertTrue(labels((Container) tabs.getComponentAt(1)).contains("Sabre-toothed kyatt"));
			assertTrue(labels((Container) tabs.getComponentAt(1)).contains("Rellekka Hunter area"));
			assertFalse(labels((Container) tabs.getComponentAt(0)).contains("Sabre-toothed kyatt"));
			panel.resetSession();
			assertFalse(labels((Container) tabs.getComponentAt(1)).contains("Sabre-toothed kyatt"));
		});
	}

	private static String labels(Container parent)
	{
		StringBuilder text = new StringBuilder();
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel) { text.append(((JLabel) child).getText()); }
			if (child instanceof Container) { text.append(labels((Container) child)); }
		}
		return text.toString();
	}
}
