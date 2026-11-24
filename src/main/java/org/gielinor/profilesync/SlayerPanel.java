package org.gielinor.profilesync;

import java.awt.Color;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

final class SlayerPanel extends JPanel
{
	private final ItemManager itemManager;
	private SlayerGuide guide;
	private Map<String, Object> lastSnapshot;
	private boolean loggedIn;

	SlayerPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));
	}

	void showTask(Map<String, Object> snapshot, boolean inGame)
	{
		if (loggedIn == inGame && snapshot.equals(lastSnapshot))
		{
			return;
		}
		loggedIn = inGame;
		lastSnapshot = snapshot;
		render();
	}

	void setGuide(SlayerGuide guide)
	{
		this.guide = guide;
		render();
	}

	private void render()
	{
		removeAll();
		heading("SLAYER");
		if (!loggedIn)
		{
			line("Log in to see your Slayer assignment.");
		}
		else if ("live".equals(lastSnapshot.get("status")) && lastSnapshot.get("task") instanceof Map)
		{
			addTask((Map<?, ?>) lastSnapshot.get("task"));
		}
		else
		{
			line("none".equals(lastSnapshot.get("status")) ? "No active Slayer assignment." : "Waiting for Slayer assignment data.");
		}
		revalidate();
		repaint();
	}

	private void addTask(Map<?, ?> task)
	{
		String monster = task.get("monster") instanceof String ? (String) task.get("monster") : null;
		String area = task.get("requiredLocation") instanceof String ? (String) task.get("requiredLocation") : null;
		boolean areaRestricted = area != null || task.get("requiredLocationId") != null;
		SlayerGuide.Task entry = guide == null ? null : guide.find(monster);
		add(RuneliteOrdersPanel.copy(monster == null ? "Assignment name unavailable" : monster, Color.WHITE));
		if (entry != null)
		{
			JLabel image = new JLabel(new ImageIcon(entry.portrait));
			image.setAlignmentX(LEFT_ALIGNMENT);
			image.setToolTipText(entry.portraitName + " · Jagex via OSRS Wiki · " + entry.imageFile);
			add(image);
			line(entry.portraitName + " · Jagex via OSRS Wiki");
		}
		else if (itemManager != null)
		{
			AsyncBufferedImage sprite = itemManager.getImage(ItemID.SLAYER_GEM);
			if (sprite != null)
			{
				JLabel icon = new JLabel();
				icon.setAlignmentX(LEFT_ALIGNMENT);
				sprite.addTo(icon);
				add(icon);
			}
		}
		heading(task.get("remaining") + " remaining");
		if (task.get("original") instanceof Number)
		{
			line("Starting count " + task.get("original"));
		}
		if (areaRestricted)
		{
			heading("REQUIRED AREA");
			line(area == null ? "Check your Slayer gem for the assigned area." : area);
		}
		if (entry != null)
		{
			addTravelAndSupplies(entry, area, areaRestricted, task.get("assignedById"));
		}
		else if (monster != null)
		{
			line("Use the task guide for locations, travel and supplies.");
		}
		if (area != null)
		{
			link("Open area guide", RuneliteOrdersLive.wiki(area));
		}
		if (monster != null)
		{
			link("Open task guide", entry != null && RuneliteOrders.safeGuide(entry.sourceUrl) ? entry.sourceUrl : RuneliteOrdersLive.wiki(monster));
		}
		link("Slayer on Sailor's Log", "https://osrs.cwew.org/#/slayer");
	}

	private void addTravelAndSupplies(SlayerGuide.Task entry, String area, boolean areaRestricted, Object masterId)
	{
		heading("WHERE TO GO");
		List<SlayerGuide.Venue> venues = guide.venues(entry, area, areaRestricted, masterId);
		if (venues.isEmpty())
		{
			line("Check the task or area guide for travel to your assigned location.");
		}
		for (SlayerGuide.Venue venue : venues)
		{
			add(RuneliteOrdersPanel.copy(venue.label + " · " + venue.creature, Color.WHITE));
			if (venue.wilderness)
			{
				line("Wilderness route · bring only gear you can risk.");
			}
			showRequirements(venue.requirements);
			SlayerGuide.Travel travel = guide.travel.get(venue.key);
			if (travel == null)
			{
				link("Open location guide", RuneliteOrdersLive.wiki(venue.label));
				continue;
			}
			for (SlayerGuide.Option option : travel.options)
			{
				line(option.label + " · " + option.directions);
				showRequirements(option.requirements);
			}
			if (travel.note != null)
			{
				line(travel.note);
			}
		}
		heading("WHAT TO BRING");
		for (String supply : entry.supplies)
		{
			line(supply);
		}
		if (entry.boss)
		{
			line("Pack food and a way out for the route you choose.");
			line("Use the boss guide for encounter-specific gear and supplies.");
		}
		else if (entry.supplies.isEmpty())
		{
			line("Bring food and a return teleport. Check the creature guide for any special equipment.");
		}
	}

	private void showRequirements(List<String> requirements)
	{
		if (!requirements.isEmpty())
		{
			line("Requires " + String.join(" · ", requirements));
		}
	}

	private void heading(String text)
	{
		add(RuneliteOrdersPanel.copy(text, ColorScheme.BRAND_ORANGE));
	}

	private void line(String text)
	{
		add(RuneliteOrdersPanel.copy(text, Color.LIGHT_GRAY));
	}

	private void link(String label, String url)
	{
		JButton button = new JButton(label);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMargin(new Insets(4, 4, 4, 4));
		button.addActionListener(event -> LinkBrowser.browse(url));
		add(button);
	}
}
