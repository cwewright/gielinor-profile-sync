package org.gielinor.profilesync;
import java.util.Map;
import org.junit.Test;
import net.runelite.api.gameval.VarPlayerID;
import static org.junit.Assert.*;
public class MinigameProgressTrackerTest {
 @Test public void parsesOnlyKnownVisibleShopFormats() {
  assertEquals(Integer.valueOf(1234),MinigameProgressTracker.parse("Points: <col=ffffff>1,234</col>",false));
  assertEquals(Integer.valueOf(0),MinigameProgressTracker.parse("Points: <col=ffffff>0</col><br>Wins: <col=ffffff>Lots!</col>",true));
  for(String s:new String[]{"Coins: 1","Points: -1","Points: 1,2","Points: 2.1","Points: 2,147,483,648","Points: 42 extra"}) assertNull(MinigameProgressTracker.parse(s,false));
  assertNull(MinigameProgressTracker.parse("Points: 99",true));
 }
 @Test public void preservesRealZeroAndUnavailableCurrenciesIndependently() {
  MinigameProgressTracker tracker=new MinigameProgressTracker();
  Map<String,Object> result=tracker.snapshot(id->{if(id==VarPlayerID.NZONE_REWARDPOINTS)return 0;throw new IllegalStateException();},id->{throw new IllegalStateException();},id->null,1000);
  Map<?,?> balances=(Map<?,?>)result.get("balances"); assertEquals(1,balances.size());
  assertEquals(0,((Map<?,?>)balances.get("nightmare-zone:nmz")).get("balance"));
  result=tracker.snapshot(id->{throw new IllegalStateException();},id->{throw new IllegalStateException();},id->null,2000);
  Map<?,?> retained=(Map<?,?>)((Map<?,?>)result.get("balances")).get("nightmare-zone:nmz");
  assertEquals(true,retained.get("fromCache"));assertEquals("1970-01-01T00:00:01Z",retained.get("observedAt"));
  tracker.resetAccount();assertTrue(((Map<?,?>)tracker.snapshot(id->-1,id->-1,id->null,3000).get("balances")).isEmpty());
 }
 @Test public void restoreIsAllowlistedAndAccountScoped() {
  MinigameProgressTracker tracker=new MinigameProgressTracker();
  Map<String,Object> value=Map.of("balance",45,"observedAt","2026-01-01T00:00:00Z","fromCache",false,"source","reward-shop");
  tracker.restore(Map.of("schemaVersion",1,"balances",Map.of("last-man-standing:lmsPoints",value,"world",value)));
  Map<?,?> result=(Map<?,?>)tracker.snapshot(id->-1,id->-1,id->null,3000).get("balances");
  assertEquals(1,result.size());assertEquals(45,((Map<?,?>)result.get("last-man-standing:lmsPoints")).get("balance"));
  tracker.resetAccount();assertTrue(((Map<?,?>)tracker.snapshot(id->-1,id->-1,id->null,4000).get("balances")).isEmpty());
 }
 @Test public void riftCheckRetainsOnlyActualTotalsAndNeverConvertsRoundEnergy() {
  MinigameProgressTracker tracker=new MinigameProgressTracker();
  tracker.observeRiftTotals("You have <col=ff0000>12</col> catalytic energy and <col=00ff00>3</col> elemental energy.",1000);
  Map<?,?> values=(Map<?,?>)tracker.snapshot(id->-1,id->-1,id->null,2000).get("balances");
  assertEquals(12,((Map<?,?>)values.get("guardians-of-the-rift:catalytic")).get("balance"));
  assertEquals(3,((Map<?,?>)values.get("guardians-of-the-rift:elemental")).get("balance"));
  tracker.observeRiftTotals("You gained 500 elemental energy",3000);
  tracker.observeRiftTotals("You have 25 catalytic energy and 2,147,483,648 elemental energy.",3000);
  values=(Map<?,?>)tracker.snapshot(id->-1,id->-1,id->null,4000).get("balances");
  assertEquals(12,((Map<?,?>)values.get("guardians-of-the-rift:catalytic")).get("balance"));
 }
}
