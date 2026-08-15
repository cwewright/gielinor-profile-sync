package org.gielinor.profilesync;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

final class CharacterCaptureOverlay extends Overlay
{
	private static final Color GUIDE_COLOR = new Color(230, 185, 80, 190);
	private static final Color GOOD_COLOR = new Color(120, 205, 177, 220);
	private static final Color NEEDS_WORK_COLOR = new Color(224, 134, 105, 220);
	private static final Stroke GUIDE_STROKE = new BasicStroke(
		2f,
		BasicStroke.CAP_ROUND,
		BasicStroke.JOIN_ROUND,
		1f,
		new float[]{7f, 6f},
		0f
	);

	private final Client client;
	private final GielinorProfileSyncConfig config;
	private final GielinorProfileSyncPlugin plugin;

	@Inject
	CharacterCaptureOverlay(Client client, GielinorProfileSyncConfig config, GielinorProfileSyncPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.MED);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showFramingGuide() || plugin.isCaptureInProgress()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		Rectangle guide = plugin.getCaptureCrop();
		if (guide.width <= 0 || guide.height <= 0)
		{
			return null;
		}

		Shape hull = player.getConvexHull();
		Rectangle playerBounds = hull == null ? null : hull.getBounds();
		boolean ready = playerBounds != null
			&& guide.contains(playerBounds)
			&& playerBounds.height >= guide.height * 0.16;

		Stroke previousStroke = graphics.getStroke();
		Color previousColor = graphics.getColor();
		try
		{
			graphics.setColor(new Color(GUIDE_COLOR.getRed(), GUIDE_COLOR.getGreen(), GUIDE_COLOR.getBlue(), 24));
			graphics.fill(guide);
			graphics.setStroke(GUIDE_STROKE);
			graphics.setColor(ready ? GOOD_COLOR : GUIDE_COLOR);
			graphics.draw(guide);

			if (hull != null)
			{
				graphics.setStroke(new BasicStroke(2f));
				graphics.setColor(ready ? GOOD_COLOR : NEEDS_WORK_COLOR);
				graphics.draw(hull);
			}

			graphics.setColor(ready ? GOOD_COLOR : GUIDE_COLOR);
			graphics.drawString(ready ? "Scene ready" : "Gielinor history frame", guide.x + 8, guide.y + 18);
		}
		finally
		{
			graphics.setStroke(previousStroke);
			graphics.setColor(previousColor);
		}

		return null;
	}
}
