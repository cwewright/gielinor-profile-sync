package org.gielinor.profilesync;

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

final class RuneliteOrdersPanel extends PluginPanel
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault());
	private final JPanel ordersPanel = new JPanel();
	private final RunelitePlanPanel planPanel = new RunelitePlanPanel();
	private final HunterRumourPanel hunterPanel = new HunterRumourPanel();
	private HunterRumourGuide hunterGuide;
	private List<RuneliteOrders.Card> cards = Collections.emptyList();
	private RuneliteOrders orders;
	private String focusId;
	private String signature = "";
	private boolean loggedIn;

	RuneliteOrdersPanel()
	{
		ordersPanel.setLayout(new BoxLayout(ordersPanel, BoxLayout.Y_AXIS));
		ordersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel ordersTab = new JPanel();
		ordersTab.setLayout(new BoxLayout(ordersTab, BoxLayout.Y_AXIS));
		ordersTab.add(ordersPanel);
		ordersTab.add(planPanel);
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Orders", ordersTab);
		tabs.addTab("Hunter Rumours", hunterPanel);
		add(tabs);
		planPanel.setVisible(false);
		render();
	}

	void showOrders(List<RuneliteOrders.Card> value, RuneliteOrders document, boolean inGame)
	{
		if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> showOrders(value, document, inGame)); return; }
		StringBuilder key = new StringBuilder(Boolean.toString(inGame));
		if (document != null) { key.append(document.accountName).append(document.updatedAt).append(document.snapshotAt); }
		for (RuneliteOrders.Card card : value) { key.append(card.id).append(card.title).append(card.summary).append(card.details); }
		if (signature.equals(key.toString())) { return; }
		signature = key.toString();
		cards = value;
		orders = document;
		loggedIn = inGame;
		if (cards.stream().noneMatch(card -> card.id.equals(focusId))) { focusId = null; }
		planPanel.setVisible(loggedIn);
		render();
	}

	void showPlan(RunelitePlan plan) { planPanel.showPlan(plan); }
	void showHunterGuide(HunterRumourGuide guide)
	{
		if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> showHunterGuide(guide)); return; }
		hunterGuide = guide;
		render();
	}
	void showInventory(String revision, Map<String, Integer> quantities) { planPanel.showInventory(revision, quantities); }
	void resetSession()
	{
		if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::resetSession); return; }
		focusId = null;
		planPanel.resetSession();
		showOrders(Collections.emptyList(), null, false);
	}

	private void render()
	{
		hunterPanel.showRumour(cards, hunterGuide, loggedIn);
		ordersPanel.removeAll();
		ordersPanel.add(copy("CAPTAIN'S ORDERS", ColorScheme.BRAND_ORANGE));
		if (!loggedIn) { ordersPanel.add(copy("Log in to see your current assignments and suggestions.", Color.LIGHT_GRAY)); }
		else
		{
			if (orders == null) { ordersPanel.add(copy("Sync the Windows companion to bring two quests and your lowest-skill ideas here. Current assignments update locally.", Color.LIGHT_GRAY)); }
			else { ordersPanel.add(copy("Suggestions " + TIME.format(orders.updatedAt) + " · account snapshot " + TIME.format(orders.snapshotAt), Color.LIGHT_GRAY)); }
			if (cards.isEmpty()) { ordersPanel.add(copy("No assignment has been observed yet. Check your Slayer task or Hunter whistle in-game.", Color.LIGHT_GRAY)); }
			for (RuneliteOrders.Card card : cards)
			{
				if (card.id.startsWith("hunter:")) { continue; }
				boolean focused = card.id.equals(focusId);
				JPanel row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setBorder(BorderFactory.createEmptyBorder(7, 6, 9, 6));
				row.add(copy(card.title, Color.WHITE));
				if (focusId == null || focused) { row.add(copy(card.summary, Color.LIGHT_GRAY)); }
				if (focused)
				{
					for (String detail : card.details) { row.add(copy(detail, Color.LIGHT_GRAY)); }
					if (card.sourceUrl != null && RuneliteOrders.safeGuide(card.sourceUrl))
					{
						JButton guide = new JButton("Open Wiki guide");
						guide.addActionListener(event -> LinkBrowser.browse(card.sourceUrl));
						row.add(guide);
					}
				}
				JButton focus = new JButton(focused ? "Show all suggestions" : "Make this my focus");
				focus.addActionListener(event -> { focusId = focused ? null : card.id; render(); });
				row.add(focus);
				ordersPanel.add(row);
			}
		}
		ordersPanel.revalidate();
		ordersPanel.repaint();
	}

	static JLabel copy(String text, Color color)
	{
		String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
		JLabel label = new JLabel("<html><div style='width:185px;padding-bottom:6px'>" + safe + "</div></html>");
		label.setForeground(color);
		return label;
	}
}
