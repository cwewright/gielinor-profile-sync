package org.gielinor.profilesync;

import java.awt.Color;
import java.awt.Insets;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
		removeAll();
		heading("SLAYER");
		if (!inGame)
		{
			line("Log in to see your Slayer assignment.");
		}
		else if ("live".equals(snapshot.get("status")) && snapshot.get("task") instanceof Map)
		{
			addTask((Map<?, ?>) snapshot.get("task"));
		}
		else
		{
			line("none".equals(snapshot.get("status")) ? "No active Slayer assignment." : "Waiting for Slayer assignment data.");
		}
		revalidate();
		repaint();
	}

	private void addTask(Map<?, ?> task)
	{
		String monster = task.get("monster") instanceof String ? (String) task.get("monster") : null;
		String area = task.get("requiredLocation") instanceof String ? (String) task.get("requiredLocation") : null;
		boolean areaRestricted = area != null || task.get("requiredLocationId") != null;
		boolean kalphites = "Kalphites".equalsIgnoreCase(monster);
		add(RuneliteOrdersPanel.copy(monster == null ? "Assignment name unavailable" : monster, Color.WHITE));
		if (itemManager != null)
		{
			AsyncBufferedImage sprite = itemManager.getImage(kalphites ? ItemID.POH_KALPHITE_SOLDIER : ItemID.SLAYER_GEM);
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
		if (kalphites)
		{
			addKalphiteGuide(area, areaRestricted);
		}
		if (area != null)
		{
			link("Open area guide", RuneliteOrdersLive.wiki(area));
		}
		if (monster != null)
		{
			link("Open task guide", kalphites ? "https://oldschool.runescape.wiki/w/Slayer_task/Kalphites" : RuneliteOrdersLive.wiki(monster));
		}
		link("Slayer on Sailor's Log", "https://osrs.cwew.org/#/slayer");
	}

	private void addKalphiteGuide(String area, boolean areaRestricted)
	{
		boolean cave = !areaRestricted || "Kalphite Cave".equalsIgnoreCase(area);
		boolean lair = !areaRestricted || "Kalphite Lair".equalsIgnoreCase(area);
		if (cave || lair)
		{
			heading("WHERE TO GO");
			if (cave) { line("Kalphite Cave · east of Shantay Pass. From fairy ring BIQ, head northeast."); }
			if (lair) { line("Kalphite Lair · near fairy ring BIQ or west of Shantay Pass."); }
			line("Use fairy ring BIQ if unlocked, or bring a Shantay pass via Al Kharid.");
		}
		heading("WHAT TO BRING");
		line("Bring poison protection for soldiers or guardians. Workers do not poison.");
		if (lair) { line("Bring rope for the Lair entrance if your rope is not already in place."); }
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
