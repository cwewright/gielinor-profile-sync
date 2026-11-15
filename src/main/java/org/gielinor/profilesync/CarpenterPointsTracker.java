package org.gielinor.profilesync;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;

/** Observes only the visible Mahogany Homes reward-shop balance, never shared IF1 elsewhere. */
final class CarpenterPointsTracker
{
	// Cache scripts: con_contract_shop_init and con_contract_shop_showpoints.
	// https://github.com/Joshua-F/osrs-dumps/tree/ea06305a06cc7985124a177d032638a4988a66b0/script
	// Verified 2026-11-15; no corresponding RuneLite ScriptID constants exist.
	static final int SHOP_INIT = 3146;
	static final int SHOP_POINTS = 3524;
	private static final Pattern BALANCE = Pattern.compile("^Carpenter Points:\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+)|[0-9]+)$", Pattern.CASE_INSENSITIVE);
	private Integer points;
	private long observedAt;

	static Integer parse(String text)
	{
		if (text == null) return null;
		String plain = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "").trim();
		Matcher match = BALANCE.matcher(plain);
		if (!match.matches()) return null;
		try { return Integer.valueOf(match.group(1).replace(",", "")); }
		catch (NumberFormatException invalid) { return null; }
	}

	void observe(Widget widget, long now)
	{
		if (widget != null && !widget.isHidden()) observeText(widget.getText(), now);
	}

	void observeText(String text, long now)
	{
		Integer value = parse(text);
		if (value != null && now > 0) { points = value; observedAt = now; }
	}

	void restore(Object raw)
	{
		if (points != null || !(raw instanceof Map)) return;
		Map<?, ?> value = (Map<?, ?>) raw;
		if (!Boolean.TRUE.equals(value.get("loaded")) || !"reward-shop".equals(value.get("source"))
			|| !(value.get("points") instanceof Number) || !(value.get("observedAt") instanceof String)) return;
		double number = ((Number) value.get("points")).doubleValue();
		try
		{
			long time = Instant.parse((String) value.get("observedAt")).toEpochMilli();
			if (number >= 0 && number <= Integer.MAX_VALUE && number == Math.floor(number) && time > 0 && time <= System.currentTimeMillis() + 300000)
			{ points = (int) number; observedAt = time; }
		}
		catch (RuntimeException invalid) { /* Unknown remains unknown. */ }
	}

	Map<String, Object> snapshot(Widget widget, long now)
	{
		boolean visible = widget != null && !widget.isHidden() && parse(widget.getText()) != null;
		if (visible) observe(widget, now);
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("schemaVersion", 1);
		value.put("source", "reward-shop");
		value.put("loaded", points != null);
		if (points != null)
		{
			value.put("points", points);
			value.put("observedAt", Instant.ofEpochMilli(observedAt).toString());
			value.put("fromCache", !visible);
		}
		return value;
	}

	void resetAccount() { points = null; observedAt = 0; }
}
