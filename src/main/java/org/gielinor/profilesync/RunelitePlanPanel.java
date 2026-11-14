package org.gielinor.profilesync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

final class RunelitePlanPanel extends PluginPanel
{
	private static final DateTimeFormatter UPDATED_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
		.withZone(ZoneId.systemDefault());
	private static final Color CONSTRUCTION_ACCENT = new Color(221, 174, 91);
	private static final Color COOKING_ACCENT = new Color(223, 112, 71);
	private final Set<String> locallyChecked = new HashSet<>();
	private final Set<String> manualOverrides = new HashSet<>();
	private String revision;
	private RunelitePlan currentPlan;
	private Map<String, Integer> inventoryQuantities;
	private final RunelitePlanProgress savedProgress;
	private String accountName;
	private boolean showChecked;

	RunelitePlanPanel()
	{
		this(null);
	}

	RunelitePlanPanel(RunelitePlanProgress savedProgress)
	{
		this.savedProgress = savedProgress;
		setBorder(new EmptyBorder(10, 10, 10, 10));
		renderEmpty();
	}

	void showPlan(RunelitePlan plan)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> showPlan(plan));
			return;
		}
		if (plan == null)
		{
			currentPlan = null;
			revision = null;
			locallyChecked.clear();
			manualOverrides.clear();
			inventoryQuantities = null;
			renderEmpty();
			return;
		}
		if (plan.getRevision().equals(revision))
		{
			return;
		}
		locallyChecked.clear();
		manualOverrides.clear();
		inventoryQuantities = null;
		revision = plan.getRevision();
		currentPlan = plan;
		restoreProgress();
		renderPlan();
	}

	void setAccount(String account)
	{
		accountName = account;
		locallyChecked.clear();
		manualOverrides.clear();
		inventoryQuantities = null;
		restoreProgress();
		if (currentPlan != null) { renderPlan(); }
	}

	private void restoreProgress()
	{
		if (savedProgress == null || accountName == null || currentPlan == null) { return; }
		RunelitePlanProgress.State state = savedProgress.read(accountName, currentPlan.getRevision());
		for (RunelitePlan.Item item : currentPlan.getItems())
		{
			if (state.checked.contains(item.getId())) { locallyChecked.add(item.getId()); }
			if (state.manual.contains(item.getId())) { manualOverrides.add(item.getId()); }
		}
	}

	private void persistProgress()
	{
		if (savedProgress != null && accountName != null && currentPlan != null)
		{
			savedProgress.write(accountName, currentPlan.getRevision(), locallyChecked, manualOverrides);
		}
	}

	void showInventory(String planRevision, Map<String, Integer> quantities)
	{
		if (currentPlan == null || !currentPlan.getRevision().equals(planRevision))
		{
			return;
		}
		Map<String, Integer> carried = new HashMap<>();
		boolean changed = false;
		for (RunelitePlan.Item item : currentPlan.getItems())
		{
			if (item.getRequired() <= 0 || "facility".equals(item.getKind()))
			{
				continue;
			}
			int quantity = quantities.getOrDefault(item.getLabel().trim().toLowerCase(Locale.ROOT), 0);
			carried.put(item.getId(), quantity);
			if (quantity >= item.getRequired() && !manualOverrides.contains(item.getId()))
			{
				changed |= locallyChecked.add(item.getId());
			}
		}
		if (changed) { persistProgress(); }
		if (carried.equals(inventoryQuantities) && !changed)
		{
			return;
		}
		inventoryQuantities = carried;
		renderPlan();
	}

	void resetSession()
	{
		accountName = null;
		showChecked = false;
		locallyChecked.clear();
		manualOverrides.clear();
		inventoryQuantities = null;
		if (currentPlan != null)
		{
			renderPlan();
		}
	}

	private void renderEmpty()
	{
		removeAll();
		add(heading("SAILOR'S LOG", ColorScheme.BRAND_ORANGE));
		add(title("Private plan"));
		add(body("No checklist is waiting on this PC."));
		add(body("Build a Construction or Cooking plan on Sailor's Log, send it to RuneLite, then choose Sync now in the Windows companion."));
		add(note("Check off items here as you work through your plan."));
		finishRender();
	}

	private void renderPlan()
	{
		removeAll();
		Color accent = "construction".equals(currentPlan.getTheme()) ? CONSTRUCTION_ACCENT : COOKING_ACCENT;
		add(heading("SAILOR'S LOG · " + currentPlan.getTheme().toUpperCase(Locale.ROOT), accent));
		add(title(currentPlan.getTargetLabel()));
		add(body(currentPlan.getTargetDetail()));
		add(note(currentPlan.getFreshnessLabel()));

		JProgressBar progress = new JProgressBar(0, currentPlan.getItems().size());
		progress.setValue(completedCount());
		progress.setStringPainted(true);
		progress.setString(completedCount() + " / " + currentPlan.getItems().size() + " lines checked");
		progress.setForeground(accent);
		progress.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progress.setBorder(new EmptyBorder(7, 0, 8, 0));
		add(progress);
		add(note("Checked supplies stay hidden after use. Show them below to undo a check."));
		JButton reveal = new JButton(showChecked ? "Hide checked supplies" : "Show checked supplies");
		reveal.addActionListener(event -> { showChecked = !showChecked; renderPlan(); });
		add(reveal);

		for (RunelitePlan.Item item : currentPlan.getItems())
		{
			if (showChecked || !locallyChecked.contains(item.getId())) { add(itemRow(item, accent)); }
		}
		if (!showChecked && completedCount() == currentPlan.getItems().size())
		{
			add(body("All supplies are checked. Publish a new plan when you are ready."));
		}

		add(note("Updated " + UPDATED_FORMAT.format(currentPlan.getUpdatedAt()) + " · progress saved for this character and plan"));
		finishRender();
	}

	private JPanel itemRow(RunelitePlan.Item item, Color accent)
	{
		JPanel row = new JPanel(new BorderLayout(6, 3));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, stateColor(item, accent)),
			new EmptyBorder(7, 7, 7, 6)));

		JCheckBox checkBox = new JCheckBox();
		checkBox.setOpaque(false);
		checkBox.setSelected(locallyChecked.contains(item.getId()));
		checkBox.setToolTipText("Check or uncheck this supply on this PC");
		checkBox.addActionListener(event ->
		{
			manualOverrides.add(item.getId());
			if (checkBox.isSelected())
			{
				locallyChecked.add(item.getId());
			}
			else
			{
				locallyChecked.remove(item.getId());
			}
			persistProgress();
			renderPlan();
		});
		row.add(checkBox, BorderLayout.WEST);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel label = new JLabel(quantity(item) + " × " + item.getLabel());
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getDefaultBoldFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		copy.add(label);
		JLabel status = new JLabel(status(item));
		status.setForeground(item.isReady() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		status.setFont(FontManager.getDefaultFont().deriveFont(11f));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		copy.add(status);
		if (inventoryQuantities != null && inventoryQuantities.containsKey(item.getId()))
		{
			JLabel carried = new JLabel(inventoryQuantities.get(item.getId()) + " / " + item.getRequired() + " in inventory");
			carried.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			carried.setFont(FontManager.getDefaultFont().deriveFont(11f));
			carried.setAlignmentX(Component.LEFT_ALIGNMENT);
			copy.add(carried);
		}
		if (item.getNote() != null)
		{
			JLabel itemNote = htmlLabel(item.getNote(), ColorScheme.LIGHT_GRAY_COLOR, 185);
			itemNote.setAlignmentX(Component.LEFT_ALIGNMENT);
			copy.add(itemNote);
		}
		row.add(copy, BorderLayout.CENTER);
		return row;
	}

	private int completedCount()
	{
		int complete = 0;
		for (RunelitePlan.Item item : currentPlan.getItems())
		{
			if (locallyChecked.contains(item.getId()))
			{
				complete++;
			}
		}
		return complete;
	}

	private static String quantity(RunelitePlan.Item item)
	{
		return item.getRequired() == 0 && "facility".equals(item.getKind()) ? "Verify" : Integer.toString(item.getRequired());
	}

	private static String status(RunelitePlan.Item item)
	{
		String suffix = item.isOptional() ? " · optional" : "";
		if (item.isReady())
		{
			return (item.getOwned() == null ? "Ready when published" : item.getOwned() + " owned when published") + suffix;
		}
		if ("unknown".equals(item.getState()))
		{
			return "Ownership unconfirmed" + suffix;
		}
		if (item.getMissing() != null)
		{
			return item.getMissing() + " to get" + suffix;
		}
		return "Still needed" + suffix;
	}

	private static Color stateColor(RunelitePlan.Item item, Color accent)
	{
		if (item.isReady())
		{
			return ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		if ("unknown".equals(item.getState()))
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return accent;
	}

	private static JLabel heading(String value, Color color)
	{
		JLabel label = new JLabel(value);
		label.setForeground(color);
		label.setFont(FontManager.getDefaultBoldFont().deriveFont(11f));
		label.setBorder(new EmptyBorder(0, 0, 2, 0));
		return label;
	}

	private static JLabel title(String value)
	{
		JLabel label = htmlLabel(value, Color.WHITE, 205);
		label.setFont(FontManager.getDefaultBoldFont().deriveFont(18f));
		return label;
	}

	private static JLabel body(String value)
	{
		JLabel label = htmlLabel(value, ColorScheme.TEXT_COLOR, 205);
		label.setBorder(new EmptyBorder(2, 0, 4, 0));
		return label;
	}

	private static JLabel note(String value)
	{
		JLabel label = htmlLabel(value, ColorScheme.LIGHT_GRAY_COLOR, 205);
		label.setFont(FontManager.getDefaultFont().deriveFont(11f));
		label.setBorder(new EmptyBorder(3, 0, 6, 0));
		return label;
	}

	private static JLabel htmlLabel(String value, Color color, int width)
	{
		JLabel label = new JLabel("<html><div style='width:" + width + "px'>" + escapeHtml(value) + "</div></html>");
		label.setForeground(color);
		label.setVerticalAlignment(SwingConstants.TOP);
		return label;
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}

	private void finishRender()
	{
		add(Box.createRigidArea(new Dimension(0, 4)));
		revalidate();
		repaint();
	}
}
