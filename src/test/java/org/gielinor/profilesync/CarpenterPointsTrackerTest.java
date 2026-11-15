package org.gielinor.profilesync;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class CarpenterPointsTrackerTest
{
	@Test public void parsesOnlyExactShopBalancesIncludingObservedZero()
	{
		assertEquals(Integer.valueOf(0), CarpenterPointsTracker.parse("Carpenter Points:<br><col=ffffff>0</col>"));
		assertEquals(Integer.valueOf(1234), CarpenterPointsTracker.parse("Carpenter Points:<br><col=ffffff>1,234</col>"));
		for (String invalid : new String[] { "Coins: 400", "Carpenter Points: -1", "Carpenter Points: 1,2", "Carpenter Points: 2,147,483,648", "Carpenter Points: 1.5", "Carpenter Points: 5 other", "" })
			assertNull(CarpenterPointsTracker.parse(invalid));
	}
	@Test public void retainsObservedBalanceWithoutRefreshingItsTimeAndResetsBetweenAccounts()
	{
		CarpenterPointsTracker tracker = new CarpenterPointsTracker();
		assertEquals(false, tracker.snapshot(null, 1000).get("loaded"));
		tracker.observeText("Carpenter Points: 42", 2000);
		Map<String, Object> value = tracker.snapshot(null, 3000);
		assertEquals(42, value.get("points"));
		assertEquals(true, value.get("fromCache"));
		assertEquals("1970-01-01T00:00:02Z", value.get("observedAt"));
		CarpenterPointsTracker restored = new CarpenterPointsTracker();
		restored.restore(value);
		assertEquals(42, restored.snapshot(null, 4000).get("points"));
		restored.observeText("Coins: 999", 5000);
		assertEquals(value.get("observedAt"), restored.snapshot(null, 6000).get("observedAt"));
		restored.resetAccount();
		assertEquals(false, restored.snapshot(null, 7000).get("loaded"));
	}
}
