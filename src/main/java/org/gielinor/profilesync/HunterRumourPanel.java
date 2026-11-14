package org.gielinor.profilesync;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

final class HunterRumourPanel extends JPanel
{
	HunterRumourPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));
	}

	void showRumour(List<RuneliteOrders.Card> cards, HunterRumourGuide guide, boolean loggedIn)
	{
		removeAll();
		add(RuneliteOrdersPanel.copy("HUNTER RUMOURS", ColorScheme.BRAND_ORANGE));
		RuneliteOrders.Card current = cards.stream().filter(card -> card.id.startsWith("hunter:")).findFirst().orElse(null);
		if (!loggedIn || current == null)
		{
			add(RuneliteOrdersPanel.copy(loggedIn ? "Check your Quetzal whistle or speak to your Guild Hunter to observe your active rumour. Unobserved assignments stay unknown." : "Log in to see your observed Hunter assignment.", Color.LIGHT_GRAY));
		}
		else
		{
			String name = current.id.substring("hunter:".length());
			add(RuneliteOrdersPanel.copy(name, Color.WHITE));
			add(RuneliteOrdersPanel.copy(current.summary, ColorScheme.BRAND_ORANGE));
			for (String detail : current.details) { add(RuneliteOrdersPanel.copy(detail, Color.LIGHT_GRAY)); }
			HunterRumourGuide.Creature creature = guide == null ? null : guide.find(name);
			if (creature == null)
			{
				add(RuneliteOrdersPanel.copy("Field guide unavailable for this creature. Your observed assignment is still shown above.", Color.LIGHT_GRAY));
			}
			else
			{
				BufferedImage image = guide.image(creature);
				if (image != null) { JLabel picture = new JLabel(new ImageIcon(image)); picture.setToolTipText(creature.name + " · Jagex / OSRS Wiki"); add(picture); }
				add(RuneliteOrdersPanel.copy(creature.method + " · Hunter " + creature.level, Color.WHITE));
				add(RuneliteOrdersPanel.copy("WHERE TO HUNT", ColorScheme.BRAND_ORANGE));
				for (String location : creature.locations)
				{
					add(RuneliteOrdersPanel.copy(location, Color.LIGHT_GRAY));
					for (HunterRumourGuide.TravelOption route : guide.routes(location))
					{
						add(RuneliteOrdersPanel.copy(route.label, Color.WHITE));
						add(RuneliteOrdersPanel.copy(route.notes, Color.LIGHT_GRAY));
						add(RuneliteOrdersPanel.copy("Requires: " + route.requirements, Color.LIGHT_GRAY));
					}
				}
				add(RuneliteOrdersPanel.copy("Choose a travel route you have unlocked.", Color.LIGHT_GRAY));
				add(RuneliteOrdersPanel.copy("WHAT TO BRING", ColorScheme.BRAND_ORANGE));
				HunterRumourGuide.Equipment gear = guide.equipment(creature);
				for (String item : gear.required) { add(RuneliteOrdersPanel.copy("• " + item, Color.WHITE)); }
				for (String item : gear.optional) { add(RuneliteOrdersPanel.copy("Optional: " + item, Color.LIGHT_GRAY)); }
				add(RuneliteOrdersPanel.copy("Bring a way back to the Guild and leave room for the rare part. Location access may require quests or travel unlocks.", Color.LIGHT_GRAY));
				JButton locations = new JButton("Open Wiki guide");
				locations.addActionListener(event -> LinkBrowser.browse(creature.sourceUrl));
				add(locations);
				add(RuneliteOrdersPanel.copy("Guide checked " + guide.verifiedAt + " · images © Jagex via OSRS Wiki", Color.LIGHT_GRAY));
			}
		}
		revalidate();
		repaint();
	}
}
