package org.gielinor.profilesync;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Item;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Allowlisted reward balances only: no chat, arbitrary widgets or raw variables leave the client. */
final class MinigameProgressTracker
{
    // Gameval fields and decoded scripts verified 2026-11-15, cache ea06305a.
    private final Map<String, Map<String, Object>> retained = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> unlocks = new LinkedHashMap<>();
    static final String[] KEYS = {"nightmare-zone:nmz", "soul-wars:zeal", "emirs-arena:arenaPoints", "tithe-farm:tithe", "giants-foundry:reputation", "vale-totems:research", "trouble-brewing:pieces", "bounty-hunter:bounty", "tempoross:permits", "pest-control:commendation", "last-man-standing:lmsPoints", "volcanic-mine:volcanic", "mage-training-arena:telekinetic", "mage-training-arena:alchemist", "mage-training-arena:enchantment", "mage-training-arena:graveyard", "barbarian-assault:attacker", "barbarian-assault:collector", "barbarian-assault:healer", "barbarian-assault:defender", "castle-wars:plaudits", "guardians-of-the-rift:elemental", "guardians-of-the-rift:catalytic", "forestry:bark-kit"};
    static Integer parse(String text, boolean lms)
    {
        if (text == null) return null;
        String plain = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "").trim();
        String number = "((?:[0-9]{1,3}(?:,[0-9]{3})+)|[0-9]+)";
        Matcher match = Pattern.compile("^Points: ?" + number + (lms ? " +Wins: *(?:[0-9,]+|Lots!)" : "") + "$", Pattern.CASE_INSENSITIVE).matcher(plain);
        if (!match.matches()) return null;
        try { return Integer.valueOf(match.group(1).replace(",", "")); } catch (NumberFormatException invalid) { return null; }
    }
    // Only numeric reward totals are retained; no message text is exported.
    // Reward-check wording cross-checked against DatBear/Guardians-of-the-Rift-Helper.
    void observeRiftTotals(String text, long now)
    {
        if (text == null) return;
        String plain = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "").trim();
        Matcher check = Pattern.compile("^You have ([0-9,]+) catalytic energy and ([0-9,]+) elemental energy(?:[.\\s]|$)").matcher(plain);
        Matcher elemental = Pattern.compile("(?:^|[.\\s])Total elemental energy: *([0-9,]+)(?:[.\\s]|$)").matcher(plain);
        Matcher catalytic = Pattern.compile("(?:^|[.\\s])Total catalytic energy: *([0-9,]+)(?:[.\\s]|$)").matcher(plain);
        Integer elementalValue = null, catalyticValue = null;
        if (check.find()) { elementalValue = parse("Points: " + check.group(2), false); catalyticValue = parse("Points: " + check.group(1), false); }
        else if (elemental.find() && catalytic.find()) { elementalValue = parse("Points: " + elemental.group(1), false); catalyticValue = parse("Points: " + catalytic.group(1), false); }
        if (elementalValue != null && catalyticValue != null) {
            put("guardians-of-the-rift:elemental", elementalValue, now, "reward-shop"); put("guardians-of-the-rift:catalytic", catalyticValue, now, "reward-shop");
        }
    }

    private void put(String key, int value, long now, String source)
    {
        if (value < 0 || now <= 0) return;
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("balance", value); observation.put("observedAt", Instant.ofEpochMilli(now).toString());
        observation.put("fromCache", false); observation.put("source", source); retained.put(key, observation);
    }
    private void read(String key, IntUnaryOperator read, int id, long now)
    {
        try { put(key, read.applyAsInt(id), now, "account-variable"); } catch (RuntimeException absent) { /* Per-currency unknown. */ }
    }
    private void widget(String key, Widget widget, long now, boolean lms, boolean digits)
    {
        if (widget == null || widget.isHidden()) return;
        String text = widget.getText();
        Integer value = digits ? parse("Points: " + text, false) : parse(text, lms);
        if (value != null) put(key, value, now, "reward-shop");
    }
    private void unlock(String key, IntUnaryOperator varbit, int id, long now)
    {
        try { int state=varbit.applyAsInt(id);
            if (state == 1) { Map<String,Object> value=new LinkedHashMap<>();value.put("observedAt",Instant.ofEpochMilli(now).toString());value.put("fromCache",false);unlocks.put(key,value); }
            else if (state == 0) unlocks.remove(key);
        } catch (RuntimeException unavailable) { /* Preserve an older positive observation with its time. */ }
    }
    void observeWidgets(IntFunction<Widget> widgets, long now)
    {
        Widget check = widgets.apply(InterfaceID.Messagebox.TEXT);
        if (check != null && !check.isHidden()) observeRiftTotals(check.getText(), now);
        widget("pest-control:commendation", widgets.apply(InterfaceID.PestRewardshop.TOTAL), now, false, false);
        widget("last-man-standing:lmsPoints", widgets.apply(InterfaceID.BrRewardShop.TOTAL), now, true, false);
        widget("volcanic-mine:volcanic", widgets.apply(InterfaceID.FossilVolcanicShop.POINTS), now, false, false);
        widget("barbarian-assault:attacker", widgets.apply(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARD_PTS_ATT), now, false, true);
        widget("barbarian-assault:defender", widgets.apply(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARD_PTS_DEF), now, false, true);
        widget("barbarian-assault:collector", widgets.apply(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARD_PTS_COL), now, false, true);
        widget("barbarian-assault:healer", widgets.apply(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARD_PTS_HEAL), now, false, true);
        widget("mage-training-arena:telekinetic", widgets.apply(InterfaceID.MagictrainingMain.MAGICTRAIN_TELE_POINTS), now, false, true);
        widget("mage-training-arena:alchemist", widgets.apply(InterfaceID.MagictrainingMain.MAGICTRAIN_ALCH_POINTS), now, false, true);
        widget("mage-training-arena:enchantment", widgets.apply(InterfaceID.MagictrainingMain.MAGICTRAIN_ENCH_POINTS), now, false, true);
        widget("mage-training-arena:graveyard", widgets.apply(InterfaceID.MagictrainingMain.MAGICTRAIN_GRAVE_POINTS), now, false, true);
    }
    Map<String, Object> snapshot(IntUnaryOperator varp, IntUnaryOperator varbit, IntFunction<Widget> widgets, long now)
    { return snapshot(varp, varbit, widgets, null, now); }
    Map<String, Object> snapshot(IntUnaryOperator varp, IntUnaryOperator varbit, IntFunction<Widget> widgets, ItemContainer forestryKit, long now)
    {
        retained.values().forEach(value -> value.put("fromCache", true));
        unlocks.values().forEach(value -> value.put("fromCache", true));
        if (forestryKit != null) {
            long bark = 0;
            for (Item item : forestryKit.getItems()) if (item != null && item.getId() == ItemID.FORESTRY_CURRENCY && item.getQuantity() > 0) bark += item.getQuantity();
            if (bark <= Integer.MAX_VALUE) put("forestry:bark-kit", (int) bark, now, "item-container");
        }
        read("nightmare-zone:nmz", varp, VarPlayerID.NZONE_REWARDPOINTS, now);
        read("soul-wars:zeal", varp, VarPlayerID.SOUL_WARS_ZEAL_TOKENS, now);
        read("giants-foundry:reputation", varp, VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, now);
        read("vale-totems:research", varp, VarPlayerID.ENT_TOTEMS_RESEARCH_POINTS, now);
        read("trouble-brewing:pieces", varp, VarPlayerID.BREW_PIECES, now);
        read("castle-wars:plaudits", varp, VarPlayerID.CASTLEWARS_PLAUDITS, now);
        read("bounty-hunter:bounty", varp, VarPlayerID.BH_2023_POINTS, now);
        read("tithe-farm:tithe", varbit, VarbitID.HOSIDIUS_TITHE_REWARDPOINTS, now);
        read("emirs-arena:arenaPoints", varbit, VarbitID.PVPA_POINTS_CURRENCY, now);
        read("tempoross:permits", varbit, VarbitID.TEMPOROSS_REWARDPERMITS, now);
        try { if (varbit.applyAsInt(VarbitID.GOTR_UNLOCKED_NEEDLE) == 1) {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("observedAt", Instant.ofEpochMilli(now).toString()); value.put("fromCache", false); unlocks.put("guardians-of-the-rift:abyssal-needle", value);
        } } catch (RuntimeException unavailable) { /* Unknown unlock. */ }
        try { if (varbit.applyAsInt(VarbitID.MM_POTION_STORAGE_UNLOCKED) == 1) {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("observedAt", Instant.ofEpochMilli(now).toString()); value.put("fromCache", false); unlocks.put("mastering-mixology:potion-storage", value);
        } } catch (RuntimeException unavailable) { /* Unknown unlock. */ }
        try { if (varbit.applyAsInt(VarbitID.MAGICTRAINING_BONESPEACHES) == 1) {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("observedAt", Instant.ofEpochMilli(now).toString()); value.put("fromCache", false); unlocks.put("mage-training-arena:bones-to-peaches", value);
        } } catch (RuntimeException unavailable) { /* Unknown unlock. */ }
        // Exact struct-to-varbit joins: cache proc giants_foundry_mould_is_unlocked, structs 4297–4323.
        unlock("giants-foundry:stiletto-forte", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_7, now);
        unlock("giants-foundry:defender-base", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_8, now);
        unlock("giants-foundry:juggernaut-forte", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_9, now);
        unlock("giants-foundry:chopper-forte-plus-1", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_10, now);
        unlock("giants-foundry:spiker", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_11, now);
        unlock("giants-foundry:flamberge-blade", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_7, now);
        unlock("giants-foundry:serpent-blade", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_8, now);
        unlock("giants-foundry:claymore-blade", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_9, now);
        unlock("giants-foundry:fleur-de-blade", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_10, now);
        unlock("giants-foundry:choppa", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_11, now);
        unlock("giants-foundry:corrupted-point", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_7, now);
        unlock("giants-foundry:defenders-tip", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_8, now);
        unlock("giants-foundry:serrated-tip", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_9, now);
        unlock("giants-foundry:needle-point", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_10, now);
        unlock("giants-foundry:the-point", varbit, VarbitID.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_11, now);
        unlock("hallowed-sepulchre:hallowed-grapple", varbit, VarbitID.HALLOWED_STORAGE_GRAPPLE, now);
        unlock("hallowed-sepulchre:hallowed-focus", varbit, VarbitID.HALLOWED_STORAGE_FOCUS, now);
        unlock("hallowed-sepulchre:hallowed-symbol", varbit, VarbitID.HALLOWED_STORAGE_SYMBOL, now);
        unlock("hallowed-sepulchre:hallowed-hammer", varbit, VarbitID.HALLOWED_STORAGE_HAMMER, now);
        unlock("hallowed-sepulchre:hallowed-ring", varbit, VarbitID.HALLOWED_STORAGE_RING, now);
        unlock("hallowed-sepulchre:private-instances", varbit, VarbitID.HALLOWED_PRIVATE_INSTANCES_PURCHASED, now);
        observeWidgets(widgets, now);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("schemaVersion", 1);
        Map<String, Object> copy = new LinkedHashMap<>(); retained.forEach((key, value) -> copy.put(key, new LinkedHashMap<>(value)));
        Map<String,Object> unlockCopy=new LinkedHashMap<>();unlocks.forEach((key,value)->unlockCopy.put(key,new LinkedHashMap<>(value)));
        result.put("balances", copy); result.put("unlocks", unlockCopy); return result;
    }
    void restore(Object raw)
    {
        if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get("schemaVersion") instanceof Number) || ((Number)((Map<?, ?>) raw).get("schemaVersion")).doubleValue() != 1) return;
        Object balances = ((Map<?, ?>) raw).get("balances");
        if (!(balances instanceof Map)) return;
        for (String key : KEYS) {
            Object value = ((Map<?, ?>) balances).get(key);
            if (retained.containsKey(key) || !(value instanceof Map)) continue;
            Map<?, ?> record = (Map<?, ?>) value;
            Object balance = record.get("balance"), time = record.get("observedAt"), source = record.get("source");
            if (!(balance instanceof Number) || !(time instanceof String) || !("reward-shop".equals(source) || "account-variable".equals(source) || "item-container".equals(source))) continue;
            double number = ((Number) balance).doubleValue();
            try { long timestamp = Instant.parse((String) time).toEpochMilli();
                if (number >= 0 && number <= Integer.MAX_VALUE && number == Math.floor(number) && timestamp > 0 && timestamp <= System.currentTimeMillis() + 300000) {
                    put(key, (int) number, timestamp, (String) source); retained.get(key).put("fromCache", true);
                }
            } catch (RuntimeException invalid) { /* Unknown remains unknown. */ }
        }
    }
    void resetAccount() { retained.clear(); unlocks.clear(); }
}
