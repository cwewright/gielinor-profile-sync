package org.gielinor.profilesync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
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
	private String revision;
	private RunelitePlan currentPlan;

	RunelitePlanPanel()
	{
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
			renderEmpty();
			return;
		}
		if (plan.getRevision().equals(revision))
		{
			return;
		}
		locallyChecked.clear();
		revision = plan.getRevision();
		currentPlan = plan;
		renderPlan();
	}

	private void renderEmpty()
	{
		removeAll();
		add(heading("SAILOR'S LOG", ColorScheme.BRAND_ORANGE));
		add(title("Private plan"));
		add(body("No checklist is waiting on this PC."));
		add(body("Build a Construction or Cooking plan on Sailor's Log, send it to RuneLite, then choose Sync now in the Windows companion."));
		add(note("The plug-in reads one local file. It never receives your upload key and never clicks or controls the game."));
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
		progress.setString(completedCount() + " / " + currentPlan.getItems().size() + " lines covered");
		progress.setForeground(accent);
		progress.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progress.setBorder(new EmptyBorder(7, 0, 8, 0));
		add(progress);

		for (RunelitePlan.Item item : currentPlan.getItems())
		{
			add(itemRow(item, accent));
		}

		add(note("Updated " + UPDATED_FORMAT.format(currentPlan.getUpdatedAt()) + " · checks stay local to this RuneLite session"));
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
		checkBox.setSelected(item.isReady() || locallyChecked.contains(item.getId()));
		checkBox.setEnabled(!item.isReady());
		checkBox.setToolTipText(item.isReady() ? "Already covered by the latest account snapshot" : "Mark this line complete on this PC");
		checkBox.addActionListener(event ->
		{
			if (checkBox.isSelected())
			{
				locallyChecked.add(item.getId());
			}
			else
			{
				locallyChecked.remove(item.getId());
			}
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
			if (item.isReady() || locallyChecked.contains(item.getId()))
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
			return (item.getOwned() == null ? "Ready" : item.getOwned() + " held · ready") + suffix;
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
