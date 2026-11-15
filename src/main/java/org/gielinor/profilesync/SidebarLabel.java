package org.gielinor.profilesync;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

/** Wraps to the actual available sidebar width, including inside a tab or supply row. */
final class SidebarLabel extends JLabel
{
	SidebarLabel(String text, Color color)
	{
		super("<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;") + "</html>");
		setForeground(color);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	@Override public Dimension getPreferredSize()
	{
		Container parent = getParent();
		Insets border = getInsets();
		int width = 185;
		if (parent != null && parent.getWidth() > 0)
		{
			Insets padding = parent.getInsets();
			width = Math.max(1, parent.getWidth() - padding.left - padding.right);
		}
		View html = (View) getClientProperty(BasicHTML.propertyKey);
		if (html == null) { return super.getPreferredSize(); }
		html.setSize(Math.max(1, width - border.left - border.right), 0);
		return new Dimension(width, (int) Math.ceil(html.getPreferredSpan(View.Y_AXIS)) + border.top + border.bottom);
	}

	@Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
	@Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}
