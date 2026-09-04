package org.gielinor.profilesync;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Gielinor Profile Sync",
	description = "Exports a privacy-minded local profile for personal dashboards and progression tools.",
	tags = {"profile", "export", "json", "progress", "avatar"}
)
public class GielinorProfileSyncPlugin extends Plugin
{
	static final String CONFIG_GROUP = "gielinor-profile-sync";
	private static final String PLUGIN_VERSION = "0.3.14";
	private static final int SCHEMA_VERSION = 1;
	private static final int LOGIN_SETTLE_TICKS = 5;
	private static final int PLAN_REFRESH_TICKS = 10;
	private static final int COLLECTION_LOG_ENTRY_TITLE_INDEX = 0;
	private static final int[] SAILING_CARGO_INVENTORIES = {
		InventoryID.SAILING_BOAT_1_CARGOHOLD,
		InventoryID.SAILING_BOAT_2_CARGOHOLD,
		InventoryID.SAILING_BOAT_3_CARGOHOLD,
		InventoryID.SAILING_BOAT_4_CARGOHOLD,
		InventoryID.SAILING_BOAT_5_CARGOHOLD
	};

	@Inject
	private Client client;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private GielinorProfileSyncConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private DrawManager drawManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CharacterCaptureOverlay characterCaptureOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	private ExecutorService fileExecutor;
	private ProfileExportStore exportStore;
	private RunelitePlanStore runelitePlanStore;
	private volatile RunelitePlanPanel runelitePlanPanel;
	private NavigationButton runelitePlanNavigation;
	private final AtomicBoolean planRefreshQueued = new AtomicBoolean();
	private volatile Map<String, Object> previousSnapshot = Collections.emptyMap();
	private int ticksSincePlanRefresh;
	private int ticksLoggedIn;
	private int ticksSinceExport;
	private String lastRsn = "";
	private CachedContainer lastGoodBank;
	private CachedContainer lastGoodInventory;
	private CachedContainer lastGoodEquipment;
	private final CachedContainer[] lastGoodSailingCargo = new CachedContainer[SAILING_CARGO_INVENTORIES.length];
	private volatile boolean captureInProgress;
	private boolean bankInterfaceOpen;
	private long bankContextUntil;
	private final CaptureActivityTracker captureActivityTracker = new CaptureActivityTracker();
	private final PohSnapshotTracker pohSnapshots = new PohSnapshotTracker();
	private final HunterRumourSnapshot hunterRumours = new HunterRumourSnapshot();
	private int ticksSinceAutomaticCapture;
	private boolean automaticBankCapturePending;
	private volatile int knownBankCaptureCount = -1;
	private final CollectionLogAccountSnapshots collectionLogSnapshots = new CollectionLogAccountSnapshots();

	private final HotkeyListener captureHotkeyListener = new HotkeyListener(() -> config.captureHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invokeLater(() -> requestCharacterCapture("manual"));
		}
	};

	private final HotkeyListener captureVesselHotkeyListener = new HotkeyListener(() -> config.captureVesselHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invokeLater(() -> requestVesselCapture());
		}
	};

	@Provides
	GielinorProfileSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GielinorProfileSyncConfig.class);
	}

	@Override
	protected void startUp()
	{
		File profileDirectory = new File(RuneLite.RUNELITE_DIR, CONFIG_GROUP);
		exportStore = new ProfileExportStore(gson, profileDirectory.toPath());
		runelitePlanStore = new RunelitePlanStore(profileDirectory.toPath());
		runelitePlanPanel = new RunelitePlanPanel();
		runelitePlanNavigation = NavigationButton.builder()
			.tooltip("Sailor's Log plan")
			.icon(createPlanIcon())
			.priority(7)
			.panel(runelitePlanPanel)
			.build();
		clientToolbar.addNavigation(runelitePlanNavigation);
		fileExecutor = Executors.newSingleThreadExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, CONFIG_GROUP + "-writer");
			thread.setDaemon(true);
			return thread;
		});
		fileExecutor.execute(() -> previousSnapshot = exportStore.readLatest());
		requestPlanRefresh();
		fileExecutor.execute(() ->
		{
			try
			{
				knownBankCaptureCount = CaptureBundle.countSceneTag(getCaptureRootDirectory(), "bank", gson);
			}
			catch (IOException e)
			{
				knownBankCaptureCount = 0;
				log.debug("Could not inspect existing character capture tags.", e);
			}
		});
		keyManager.registerKeyListener(captureHotkeyListener);
		keyManager.registerKeyListener(captureVesselHotkeyListener);
		overlayManager.add(characterCaptureOverlay);
		log.info("Gielinor Profile Sync started.");
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(captureHotkeyListener);
		keyManager.unregisterKeyListener(captureVesselHotkeyListener);
		overlayManager.remove(characterCaptureOverlay);
		if (runelitePlanNavigation != null)
		{
			clientToolbar.removeNavigation(runelitePlanNavigation);
			runelitePlanNavigation = null;
		}
		runelitePlanPanel = null;
		runelitePlanStore = null;
		planRefreshQueued.set(false);
		captureInProgress = false;
		if (fileExecutor != null)
		{
			fileExecutor.shutdownNow();
			fileExecutor = null;
		}
		resetSession();
		log.info("Gielinor Profile Sync stopped.");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			pohSnapshots.clearScene();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		observePohObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		pohSnapshots.forget(event.getGameObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		observePohObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		pohSnapshots.forget(event.getDecorativeObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		observePohObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		pohSnapshots.forget(event.getWallObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		observePohObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		pohSnapshots.forget(event.getGroundObject());
	}

	private void observePohObject(TileObject object)
	{
		if (object == null)
		{
			return;
		}
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		pohSnapshots.observe(object, composition);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankInterfaceOpen = true;
			refreshBankContextWindow();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}
		Player localPlayer = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN
			|| localPlayer == null
			|| client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0)
		{
			return;
		}
		String rsn = localPlayer.getName();
		if (rsn == null || rsn.trim().isEmpty())
		{
			return;
		}

		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget contents = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (header == null || header.getChildren() == null || contents == null || contents.getChildren() == null)
		{
			return;
		}
		Widget titleWidget = header.getChild(COLLECTION_LOG_ENTRY_TITLE_INDEX);
		if (titleWidget == null || titleWidget.getText() == null)
		{
			return;
		}

		List<CollectionLogSnapshot.ItemObservation> items = new ArrayList<>();
		for (Widget child : contents.getChildren())
		{
			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}
			String itemName = itemManager.getItemComposition(itemId).getName();
			items.add(new CollectionLogSnapshot.ItemObservation(
				itemId,
				itemName,
				child.getOpacity() == 0,
				child.getItemQuantity()
			));
		}

		String category = activeCollectionLogCategory();
		long observedAt = System.currentTimeMillis();
		CollectionLogSnapshot collectionLogSnapshot = collectionLogSnapshots.activate(rsn);
		collectionLogSnapshot.observeUniqueCounts(
			client.getVarpValue(VarPlayerID.COLLECTION_COUNT),
			client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX),
			observedAt
		);
		if (collectionLogSnapshot.observePage(category, Text.removeTags(titleWidget.getText()), items, observedAt))
		{
			ticksSinceExport = Math.max(10, config.exportIntervalTicks());
		}
	}

	private String activeCollectionLogCategory()
	{
		Widget tabs = client.getWidget(InterfaceID.Collection.TABS);
		if (tabs == null || tabs.getStaticChildren() == null)
		{
			return null;
		}
		Widget[] children = tabs.getStaticChildren();
		int activeIndex = client.getVarbitValue(VarbitID.COLLECTION_LAST_TAB);
		if (activeIndex < 0 || activeIndex >= children.length || children[activeIndex] == null)
		{
			return null;
		}
		String rawName = children[activeIndex].getName();
		return rawName == null ? null : Text.removeTags(rawName);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankInterfaceOpen = false;
			refreshBankContextWindow();
			if (config.automaticCaptures() && knownBankCaptureCount >= 0 && knownBankCaptureCount < 2)
			{
				automaticBankCapturePending = true;
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			captureActivityTracker.observe(event.getSkill(), event.getXp(), System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (hunterRumours.observe(event.getType(), event.getMessage(), isInHunterBurrows(), System.currentTimeMillis()))
		{
			ticksSinceExport = Math.max(10, config.exportIntervalTicks());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.BANK || containerId == InventoryID.INV || containerId == InventoryID.WORN || isSailingCargoContainer(containerId))
		{
			ticksSinceExport = Math.max(10, config.exportIntervalTicks());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ticksSincePlanRefresh++;
		if (ticksSincePlanRefresh >= PLAN_REFRESH_TICKS)
		{
			ticksSincePlanRefresh = 0;
			requestPlanRefresh();
		}

		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			ticksLoggedIn = 0;
			ticksSinceExport = 0;
			return;
		}

		String rsn = player.getName();
		if (rsn == null || rsn.trim().isEmpty())
		{
			return;
		}

		if (!rsn.equals(lastRsn))
		{
			resetAccount(rsn);
		}

		ticksLoggedIn++;
		ticksSinceExport++;
		if (config.automaticCaptures())
		{
			ticksSinceAutomaticCapture++;
		}
		else
		{
			ticksSinceAutomaticCapture = 0;
			automaticBankCapturePending = false;
		}
		if (ticksLoggedIn < LOGIN_SETTLE_TICKS)
		{
			return;
		}

		requestAutomaticCaptureIfDue();

		int interval = Math.max(10, config.exportIntervalTicks());
		if (ticksSinceExport < interval)
		{
			return;
		}

		ticksSinceExport = 0;
		exportSnapshot(player, rsn);
	}

	private void requestPlanRefresh()
	{
		ExecutorService executor = fileExecutor;
		RunelitePlanStore store = runelitePlanStore;
		RunelitePlanPanel panel = runelitePlanPanel;
		if (executor == null || store == null || panel == null || !planRefreshQueued.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			executor.execute(() ->
			{
				try
				{
					RunelitePlan plan = store.read();
					SwingUtilities.invokeLater(() ->
					{
						if (runelitePlanPanel == panel)
						{
							panel.showPlan(plan);
						}
					});
				}
				finally
				{
					planRefreshQueued.set(false);
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			planRefreshQueued.set(false);
		}
	}

	private static BufferedImage createPlanIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.setColor(new Color(216, 179, 96));
			graphics.drawRoundRect(3, 2, 10, 12, 2, 2);
			graphics.drawLine(6, 1, 10, 1);
			graphics.setColor(new Color(111, 203, 189));
			graphics.drawLine(5, 8, 7, 10);
			graphics.drawLine(7, 10, 11, 5);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private void exportSnapshot(Player player, String rsn)
	{
		long now = System.currentTimeMillis();
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("schemaVersion", SCHEMA_VERSION);
		snapshot.put("version", PLUGIN_VERSION);
		snapshot.put("timestamp", now);
		snapshot.put("timestampIso", Instant.ofEpochMilli(now).toString());
		snapshot.put("source", buildSource());
		snapshot.put("capabilities", Arrays.asList("skills", "quests", "achievementDiaries", "achievementDiaryTaskProgress", "slayerTask", "hunterRumours", "collectionLogPageObservations", "containers", "sailingFleet", "sailingBoatNames", "vesselPortraitCaptures", "playerOwnedHouse", "playerOwnedHouseLayout", "grandExchange", "appearance", "playerModel", "characterCaptures", "automaticSkillCaptures", "coarseLocationTags"));
		snapshot.put("rsn", rsn);
		snapshot.put("combatLevel", player.getCombatLevel());
		snapshot.put("totalLevel", calculateTotalLevel());
		snapshot.put("totalXp", calculateTotalXp());
		snapshot.put("skills", buildSkills());
		snapshot.put("appearance", buildAppearance(player));

		CachedContainer inventory = chooseContainer("inventory", buildContainer(InventoryID.INV), lastGoodInventory, now, rsn);
		CachedContainer equipment = chooseContainer("equipment", buildContainer(InventoryID.WORN), lastGoodEquipment, now, rsn);
		CachedContainer bank = chooseContainer("bank", buildContainer(InventoryID.BANK), lastGoodBank, now, rsn);
		lastGoodInventory = inventory;
		lastGoodEquipment = equipment;
		lastGoodBank = bank;

		putContainer(snapshot, "inventory", inventory);
		putContainer(snapshot, "equipment", equipment);
		putContainer(snapshot, "bank", bank);

		List<Map<String, Object>> sailingCargo = new ArrayList<>(SAILING_CARGO_INVENTORIES.length);
		for (int boatIndex = 0; boatIndex < SAILING_CARGO_INVENTORIES.length; boatIndex++)
		{
			CachedContainer cargo = chooseSailingCargo(
				boatIndex,
				buildContainer(SAILING_CARGO_INVENTORIES[boatIndex]),
				lastGoodSailingCargo[boatIndex],
				now,
				rsn
			);
			lastGoodSailingCargo[boatIndex] = cargo;
			sailingCargo.add(nestedContainer(cargo));
		}
		snapshot.put("sailing", SailingFleetSnapshot.build(
			now,
			client::getVarbitValue,
			sailingCargo::get,
			SailingFleetDecoder.forClient(client)
		));

		long inventoryValue = inventory.loaded ? getLong(inventory.data.get("value")) : 0;
		long equipmentValue = equipment.loaded ? getLong(equipment.data.get("value")) : 0;
		long bankValue = bank.loaded ? getLong(bank.data.get("value")) : 0;
		snapshot.put("inventoryValue", inventoryValue);
		snapshot.put("equipmentValue", equipmentValue);
		snapshot.put("bankValue", bankValue);
		snapshot.put("bankItemCount", bank.loaded ? getInt(bank.data.get("itemCount")) : 0);
		snapshot.put("carriedValue", inventoryValue + equipmentValue);
		snapshot.put("knownAccountValue", inventoryValue + equipmentValue + bankValue);

		Map<String, Object> grandExchange = buildGrandExchangeOffers();
		snapshot.put("grandExchange", grandExchange);
		snapshot.put("grandExchangeAccountValueEstimate", getLong(grandExchange.get("accountValueEstimate")));
		snapshot.put("quests", buildQuests());
		snapshot.put("achievementDiaries", buildAchievementDiaries());
		if (rsn.equals(previousSnapshot.get("rsn")))
		{
			pohSnapshots.restore(previousSnapshot.get("playerOwnedHouse"));
			hunterRumours.restore(previousSnapshot.get("hunterRumours"));
		}
		snapshot.put("playerOwnedHouse", pohSnapshots.snapshot(
			now,
			client.getVarbitValue(VarbitID.POH_BUILDING_MODE) > 0,
			client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION),
			client.getVarbitValue(VarbitID.POH_HOUSE_STYLE),
			client.getVarbitValue(VarbitID.POH_HOUSE_SIZE),
			client.isInInstancedRegion() ? client.getInstanceTemplateChunks() : null
		));
		snapshot.put("slayer", SlayerTaskSnapshot.build(client, now));
		snapshot.put("hunterRumours", hunterRumours.snapshot());
		snapshot.put("collectionLog", collectionLogSnapshots.current().toMap());

		ExecutorService executor = fileExecutor;
		if (executor != null)
		{
			executor.execute(() -> writeSnapshot(rsn, snapshot));
		}
	}

	private Map<String, Object> buildSource()
	{
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("name", "Gielinor Profile Sync");
		source.put("version", PLUGIN_VERSION);
		source.put("transport", "local-file");
		source.put("networkRequests", false);
		return source;
	}

	private boolean isInHunterBurrows()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return false;
		}
		WorldPoint location = localPlayer.getWorldLocation();
		return location.getPlane() == 0
			&& location.getX() >= 1549 && location.getX() <= 1565
			&& location.getY() >= 9449 && location.getY() <= 9464;
	}

	private void writeSnapshot(String rsn, Map<String, Object> snapshot)
	{
		try
		{
			Map<String, Object> accountSnapshot = exportStore.readAccount(rsn);
			Object mergedCollectionLog = CollectionLogAccountSnapshots.mergeForExport(
				accountSnapshot.get("collectionLog"),
				snapshot.get("collectionLog")
			);
			snapshot.put("collectionLog", mergedCollectionLog);
			exportStore.write(rsn, snapshot);
			previousSnapshot = snapshot;
			clientThread.invokeLater(() -> collectionLogSnapshots.merge(rsn, mergedCollectionLog));
			log.debug("Wrote local profile snapshot for {}.", rsn);
		}
		catch (IOException e)
		{
			log.warn("Could not write the local profile snapshot.", e);
		}
	}

	private Map<String, Object> buildSkills()
	{
		Map<String, Object> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("level", client.getRealSkillLevel(skill));
			data.put("boostedLevel", client.getBoostedSkillLevel(skill));
			data.put("xp", client.getSkillExperience(skill));
			skills.put(skill.getName(), data);
		}
		return skills;
	}

	private int calculateTotalLevel()
	{
		int total = 0;
		for (Skill skill : Skill.values())
		{
			total += client.getRealSkillLevel(skill);
		}
		return total;
	}

	private long calculateTotalXp()
	{
		long total = 0;
		for (Skill skill : Skill.values())
		{
			total += client.getSkillExperience(skill);
		}
		return total;
	}

	private Map<String, Object> buildAppearance(Player player)
	{
		Map<String, Object> appearance = new LinkedHashMap<>();
		PlayerComposition composition = player.getPlayerComposition();
		appearance.put("loaded", composition != null);
		if (composition == null)
		{
			return appearance;
		}

		int gender = composition.getGender();
		int[] colors = composition.getColors().clone();
		int[] equipmentIds = composition.getEquipmentIds().clone();
		int transformedNpcId = composition.getTransformedNpcId();
		appearance.put("gender", gender);
		appearance.put("colors", toIntegerList(colors));
		appearance.put("equipmentIds", toIntegerList(equipmentIds));
		appearance.put("transformedNpcId", transformedNpcId);
		appearance.put(
			"fingerprint",
			String.format("%08x-%08x-%d-%d", Arrays.hashCode(equipmentIds), Arrays.hashCode(colors), gender, transformedNpcId)
		);
		Map<String, Object> model = PlayerModelSnapshot.from(player.getModel());
		if (model != null)
		{
			appearance.put("model", model);
		}

		Map<String, Object> slots = new LinkedHashMap<>();
		for (KitType kitType : KitType.values())
		{
			int rawId = composition.getEquipmentId(kitType);
			Map<String, Object> slot = new LinkedHashMap<>();
			slot.put("rawId", rawId);
			if (rawId >= PlayerComposition.ITEM_OFFSET)
			{
				slot.put("kind", "item");
				slot.put("id", rawId - PlayerComposition.ITEM_OFFSET);
			}
			else if (rawId >= PlayerComposition.KIT_OFFSET)
			{
				slot.put("kind", "kit");
				slot.put("id", rawId - PlayerComposition.KIT_OFFSET);
			}
			else
			{
				slot.put("kind", "empty");
				slot.put("id", -1);
			}
			slots.put(kitType.name().toLowerCase(), slot);
		}
		appearance.put("slots", slots);
		return appearance;
	}

	private List<Integer> toIntegerList(int[] values)
	{
		List<Integer> output = new ArrayList<>(values.length);
		for (int value : values)
		{
			output.add(value);
		}
		return output;
	}

	private void requestCharacterCapture(String trigger)
	{
		requestCharacterCapture(trigger, null);
	}

	private void requestCharacterCapture(String trigger, Map<String, Object> vesselContext)
	{
		Player localPlayer = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || localPlayer == null || captureInProgress)
		{
			return;
		}

		if (bankInterfaceOpen)
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Close the bank before capturing so no bank contents appear in the scene.",
				null
			);
			return;
		}

		if (client.isMenuOpen())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Close the right-click menu before capturing the scene.",
				null
			);
			return;
		}

		ExecutorService executor = fileExecutor;
		if (executor == null)
		{
			return;
		}

		String captureId = UUID.randomUUID().toString();
		int logicalCanvasWidth = Math.max(1, client.getCanvasWidth());
		int logicalCanvasHeight = Math.max(1, client.getCanvasHeight());
		Rectangle logicalCaptureCrop = getCaptureCrop(logicalCanvasWidth, logicalCanvasHeight);
		Shape playerHull = localPlayer.getConvexHull();
		Rectangle logicalPlayerBounds = playerHull == null ? null : playerHull.getBounds();
		Map<String, Object> metadata = buildCaptureMetadata(captureId, trigger, localPlayer, vesselContext);
		int retention = Math.max(30, Math.min(500, config.captureRetention()));
		captureInProgress = true;

		drawManager.requestNextFrameListener(image ->
		{
			captureInProgress = false;
			Rectangle imageCaptureCrop;
			try
			{
				imageCaptureCrop = CaptureBundle.prepareFrameMetadata(
					metadata,
					logicalCaptureCrop,
					logicalPlayerBounds,
					logicalCanvasWidth,
					logicalCanvasHeight,
					image.getWidth(null),
					image.getHeight(null)
				);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Could not prepare Gielinor character history capture {}.", captureId, e);
				clientThread.invokeLater(() -> client.addChatMessage(
					ChatMessageType.GAMEMESSAGE,
					"",
					"Gielinor Profile Sync could not prepare that scene; check the RuneLite log.",
					null
				));
				return;
			}
			executor.execute(() ->
			{
				try
				{
					CaptureBundle.write(getCapturePendingDirectory(), captureId, image, imageCaptureCrop, metadata, gson);
					CaptureBundle.pruneDiverse(getCapturePendingDirectory(), retention, gson);
					Object rawContext = metadata.get("context");
					if (rawContext instanceof Map && "bank".equals(((Map<?, ?>) rawContext).get("sceneTag")))
					{
						knownBankCaptureCount = Math.max(0, knownBankCaptureCount) + 1;
					}
					log.debug("Gielinor character history capture {} saved locally.", captureId);
					clientThread.invokeLater(() -> client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
						"Gielinor history scene saved locally.",
						null
					));
				}
				catch (IOException | RuntimeException e)
				{
					log.warn("Could not save Gielinor character history capture {}.", captureId, e);
					clientThread.invokeLater(() -> client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
						"Gielinor Profile Sync could not save that scene; check the RuneLite log.",
						null
					));
				}
			});
		});
	}

	@SuppressWarnings("unchecked")
	private void requestVesselCapture()
	{
		Map<String, Object> fleet = SailingFleetSnapshot.build(
			System.currentTimeMillis(),
			client::getVarbitValue,
			ignored -> null,
			SailingFleetDecoder.forClient(client)
		);
		Object rawActive = fleet.get("activeBoat");
		if (!(rawActive instanceof Map) || !"confirmed".equals(((Map<String, Object>) rawActive).get("status")))
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Board your own vessel before capturing it; no fleet slot could be safely confirmed.",
				null
			);
			return;
		}

		Map<String, Object> active = (Map<String, Object>) rawActive;
		Map<String, Object> vessel = new LinkedHashMap<>();
		vessel.put("correlationStatus", "confirmed");
		vessel.put("boatId", active.get("id"));
		vessel.put("boatSlot", active.get("slot"));
		vessel.put("typeId", active.get("typeId"));
		copyDecodedLabel(active.get("decodedType"), vessel, "typeName");
		copyDecodedLabel(active.get("decodedName"), vessel, "boatName");
		vessel.put("correlation", active.get("correlation"));
		requestCharacterCapture("manual", vessel);
	}

	@SuppressWarnings("unchecked")
	private void copyDecodedLabel(Object rawDecoded, Map<String, Object> target, String key)
	{
		if (!(rawDecoded instanceof Map))
		{
			return;
		}
		Map<String, Object> decoded = (Map<String, Object>) rawDecoded;
		if ("resolved".equals(decoded.get("status")) && decoded.get("name") instanceof String)
		{
			target.put(key, decoded.get("name"));
		}
	}

	private Map<String, Object> buildCaptureMetadata(
		String captureId,
		String trigger,
		Player localPlayer,
		Map<String, Object> vesselContext
	)
	{
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("schemaVersion", 1);
		metadata.put("captureId", captureId);
		metadata.put("capturedAt", Instant.now().toString());

		Map<String, Object> source = new LinkedHashMap<>();
		source.put("plugin", CONFIG_GROUP);
		source.put("pluginVersion", PLUGIN_VERSION);
		source.put("profileSchemaVersion", SCHEMA_VERSION);
		source.put("captureMode", "local-only");
		metadata.put("source", source);

		boolean vesselCapture = vesselContext != null;
		boolean bankContext = !vesselCapture && System.currentTimeMillis() <= bankContextUntil;
		int skillWindowMinutes = Math.max(1, Math.min(30, config.skillContextMinutes()));
		String skillTag = captureActivityTracker.recentSkillTag(
			System.currentTimeMillis(),
			skillWindowMinutes * 60_000L
		);
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("trigger", trigger);
		context.put("sceneTag", vesselCapture ? "vessel" : bankContext ? "bank" : "adventure");
		context.put("bankContext", bankContext);
		context.put("classification", vesselCapture ? "confirmed-active-owned-vessel" : bankContext ? "recent-bank-interface" : skillTag == null ? "unclassified" : "recent-skill-activity");
		if (vesselCapture)
		{
			context.put("skillTag", "Sailing");
			context.put("fleet", vesselContext);
		}
		else if (skillTag != null)
		{
			context.put("skillTag", skillTag);
		}
		String locationTag = CaptureActivityTracker.coarseLocationTag(localPlayer.getWorldLocation());
		if (locationTag != null)
		{
			context.put("locationTag", locationTag);
		}
		metadata.put("context", context);

		Map<String, Object> camera = new LinkedHashMap<>();
		camera.put("yaw", client.getCameraYaw());
		camera.put("pitch", client.getCameraPitch());
		camera.put("scale", client.getScale());
		metadata.put("camera", camera);

		Map<String, Object> character = new LinkedHashMap<>();
		character.put("animation", localPlayer.getAnimation());
		character.put("animationFrame", localPlayer.getAnimationFrame());
		character.put("poseAnimation", localPlayer.getPoseAnimation());
		character.put("poseAnimationFrame", localPlayer.getPoseAnimationFrame());
		character.put("idlePoseAnimation", localPlayer.getIdlePoseAnimation());
		character.put("orientation", localPlayer.getOrientation());
		character.put("currentOrientation", localPlayer.getCurrentOrientation());
		metadata.put("character", character);
		metadata.put("appearance", buildAppearance(localPlayer));

		Map<String, Object> privacy = new LinkedHashMap<>();
		privacy.put("imageScope", "central-world-scene");
		privacy.put("standardGameUiExcluded", true);
		privacy.put("structuredExactLocationIncluded", false);
		privacy.put("structuredCoarseRegionIncluded", locationTag != null);
		privacy.put("structuredWorldNumberIncluded", false);
		privacy.put("structuredNearbyPlayerNamesIncluded", false);
		privacy.put("structuredChatIncluded", false);
		metadata.put("privacy", privacy);
		return metadata;
	}

	Rectangle getCaptureCrop()
	{
		return getCaptureCrop(client.getCanvasWidth(), client.getCanvasHeight());
	}

	private Rectangle getCaptureCrop(int canvasWidth, int canvasHeight)
	{
		return CaptureBundle.buildSafeCaptureCrop(
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getViewportWidth(),
			client.getViewportHeight(),
			canvasWidth,
			canvasHeight,
			client.isResized()
		);
	}

	boolean isCaptureInProgress()
	{
		return captureInProgress;
	}

	private java.nio.file.Path getCapturePendingDirectory()
	{
		return new File(new File(new File(RuneLite.RUNELITE_DIR, CONFIG_GROUP), "captures"), "pending").toPath();
	}

	private java.nio.file.Path getCaptureRootDirectory()
	{
		return new File(new File(RuneLite.RUNELITE_DIR, CONFIG_GROUP), "captures").toPath();
	}

	private void requestAutomaticCaptureIfDue()
	{
		if (!config.automaticCaptures() || captureInProgress || bankInterfaceOpen || client.isMenuOpen())
		{
			return;
		}
		if (automaticBankCapturePending)
		{
			automaticBankCapturePending = false;
			requestCharacterCapture("scheduled");
			return;
		}

		int minutes = Math.max(15, Math.min(240, config.automaticCaptureMinutes()));
		if (ticksSinceAutomaticCapture < minutes * 100)
		{
			return;
		}
		int skillWindowMinutes = Math.max(1, Math.min(30, config.skillContextMinutes()));
		if (captureActivityTracker.recentSkillTag(System.currentTimeMillis(), skillWindowMinutes * 60_000L) == null)
		{
			return;
		}
		ticksSinceAutomaticCapture = 0;
		requestCharacterCapture("scheduled");
	}

	private void refreshBankContextWindow()
	{
		int seconds = Math.max(5, Math.min(300, config.recentBankSeconds()));
		bankContextUntil = System.currentTimeMillis() + seconds * 1000L;
	}

	private boolean isBankInterface(int groupId)
	{
		return groupId == InterfaceID.BANKMAIN || groupId == InterfaceID.BANK_DEPOSITBOX;
	}

	private Map<String, Object> buildContainer(int inventoryId)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		ItemContainer container = client.getItemContainer(inventoryId);
		boolean loaded = container != null;
		result.put("loaded", loaded);

		long totalValue = 0;
		int itemCount = 0;
		Map<String, Object> items = new LinkedHashMap<>();
		if (container != null)
		{
			Item[] containerItems = container.getItems();
			for (int slotIndex = 0; slotIndex < containerItems.length; slotIndex++)
			{
				Item item = containerItems[slotIndex];
				if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
				{
					continue;
				}

				int id = item.getId();
				int quantity = item.getQuantity();
				int price = itemManager.getItemPrice(id);
				long value = (long) price * quantity;
				Map<String, Object> itemData = new LinkedHashMap<>();
				itemData.put("slot", slotIndex);
				itemData.put("id", id);
				itemData.put("name", itemManager.getItemComposition(id).getName());
				itemData.put("quantity", quantity);
				itemData.put("price", price);
				itemData.put("value", value);
				items.put(String.valueOf(slotIndex), itemData);
				totalValue += value;
				itemCount++;
			}
		}

		result.put("value", totalValue);
		result.put("itemCount", itemCount);
		result.put("items", items);
		return result;
	}

	private CachedContainer chooseContainer(String key, Map<String, Object> current, CachedContainer memory, long now, String rsn)
	{
		if (Boolean.TRUE.equals(current.get("loaded")))
		{
			return new CachedContainer(current, true, false, now);
		}
		if (memory != null && memory.loaded)
		{
			return new CachedContainer(memory.data, true, true, memory.lastSeenTimestamp);
		}

		Map<String, Object> previous = previousSnapshot;
		if (rsn.equals(previous.get("rsn")) && previous.get(key) instanceof Map)
		{
			@SuppressWarnings("unchecked")
			Map<String, Object> previousContainer = (Map<String, Object>) previous.get(key);
			if (Boolean.TRUE.equals(previousContainer.get("loaded")))
			{
				long lastSeen = getLong(previous.get(key + "LastSeenTimestamp"));
				if (lastSeen <= 0)
				{
					lastSeen = getLong(previous.get("timestamp"));
				}
				return new CachedContainer(previousContainer, true, true, lastSeen);
			}
		}

		return new CachedContainer(current, false, false, 0);
	}

	private CachedContainer chooseSailingCargo(int boatIndex, Map<String, Object> current, CachedContainer memory, long now, String rsn)
	{
		if (Boolean.TRUE.equals(current.get("loaded")))
		{
			return new CachedContainer(current, true, false, now);
		}
		if (memory != null && memory.loaded)
		{
			return new CachedContainer(memory.data, true, true, memory.lastSeenTimestamp);
		}

		Map<String, Object> previous = previousSnapshot;
		if (rsn.equals(previous.get("rsn")) && previous.get("sailing") instanceof Map)
		{
			@SuppressWarnings("unchecked")
			Map<String, Object> previousSailing = (Map<String, Object>) previous.get("sailing");
			Object previousBoats = previousSailing.get("boats");
			if (previousBoats instanceof List)
			{
				for (Object rawBoat : (List<?>) previousBoats)
				{
					if (!(rawBoat instanceof Map))
					{
						continue;
					}
					Map<?, ?> boat = (Map<?, ?>) rawBoat;
					if (getInt(boat.get("slot")) != boatIndex + 1 || !(boat.get("cargo") instanceof Map))
					{
						continue;
					}
					@SuppressWarnings("unchecked")
					Map<String, Object> previousCargo = (Map<String, Object>) boat.get("cargo");
					if (!Boolean.TRUE.equals(previousCargo.get("loaded")))
					{
						continue;
					}
					long lastSeen = getLong(previousCargo.get("lastSeenTimestamp"));
					if (lastSeen <= 0)
					{
						lastSeen = getLong(previousSailing.get("fleetLastSeenTimestamp"));
					}
					return new CachedContainer(previousCargo, true, true, lastSeen);
				}
			}
		}

		return new CachedContainer(current, false, false, 0);
	}

	private Map<String, Object> nestedContainer(CachedContainer container)
	{
		Map<String, Object> result = new LinkedHashMap<>(container.data);
		result.put("loaded", container.loaded);
		result.put("fromCache", container.fromCache);
		result.put("lastSeenTimestamp", container.lastSeenTimestamp);
		return result;
	}

	private boolean isSailingCargoContainer(int containerId)
	{
		for (int sailingCargoInventory : SAILING_CARGO_INVENTORIES)
		{
			if (containerId == sailingCargoInventory)
			{
				return true;
			}
		}
		return false;
	}

	private void putContainer(Map<String, Object> snapshot, String key, CachedContainer container)
	{
		snapshot.put(key, container.data);
		snapshot.put(key + "Loaded", container.loaded);
		snapshot.put(key + "FromCache", container.fromCache);
		snapshot.put(key + "LastSeenTimestamp", container.lastSeenTimestamp);
	}

	private Map<String, Object> buildGrandExchangeOffers()
	{
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> offersOut = new LinkedHashMap<>();
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		result.put("loaded", offers != null);
		int activeCount = 0;
		long listedValueEstimate = 0;
		long accountValueEstimate = 0;

		if (offers != null)
		{
			for (int slotIndex = 0; slotIndex < offers.length; slotIndex++)
			{
				GrandExchangeOffer offer = offers[slotIndex];
				if (offer == null || offer.getState() == null || "EMPTY".equals(offer.getState().name()))
				{
					continue;
				}

				int itemId = offer.getItemId();
				int marketPrice = itemId > 0 ? itemManager.getItemPrice(itemId) : 0;
				int totalQuantity = offer.getTotalQuantity();
				int completedQuantity = offer.getQuantitySold();
				int remainingQuantity = Math.max(0, totalQuantity - completedQuantity);
				String state = offer.getState().name();
				long offerListedValue = (long) offer.getPrice() * totalQuantity;
				long offerAccountValue = estimateGrandExchangeValue(state, marketPrice, offer, completedQuantity, remainingQuantity);

				Map<String, Object> offerOut = new LinkedHashMap<>();
				offerOut.put("slot", slotIndex);
				offerOut.put("state", state);
				offerOut.put("itemId", itemId);
				offerOut.put("itemName", itemId > 0 ? itemManager.getItemComposition(itemId).getName() : "");
				offerOut.put("listedPrice", offer.getPrice());
				offerOut.put("marketPrice", marketPrice);
				offerOut.put("totalQuantity", totalQuantity);
				offerOut.put("completedQuantity", completedQuantity);
				offerOut.put("remainingQuantity", remainingQuantity);
				offerOut.put("spent", offer.getSpent());
				offerOut.put("listedValueEstimate", offerListedValue);
				offerOut.put("accountValueEstimate", offerAccountValue);
				offersOut.put(String.valueOf(slotIndex), offerOut);
				activeCount++;
				listedValueEstimate += offerListedValue;
				accountValueEstimate += offerAccountValue;
			}
		}

		result.put("activeCount", activeCount);
		result.put("listedValueEstimate", listedValueEstimate);
		result.put("accountValueEstimate", accountValueEstimate);
		result.put("offers", offersOut);
		return result;
	}

	private long estimateGrandExchangeValue(String state, int marketPrice, GrandExchangeOffer offer, int completed, int remaining)
	{
		if (state.contains("SELL"))
		{
			return (long) offer.getSpent() + (long) marketPrice * remaining;
		}
		if (state.contains("BUY"))
		{
			return (long) marketPrice * completed + (long) offer.getPrice() * remaining;
		}
		return (long) marketPrice * offer.getTotalQuantity();
	}

	private Map<String, Object> buildQuests()
	{
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> entries = new LinkedHashMap<>();
		int notStarted = 0;
		int inProgress = 0;
		int finished = 0;
		int unknown = 0;

		for (Quest quest : Quest.values())
		{
			QuestState state;
			try
			{
				state = quest.getState(client);
			}
			catch (RuntimeException e)
			{
				state = null;
			}

			String stateName = state != null ? state.name() : "UNKNOWN";
			if (state == QuestState.NOT_STARTED)
			{
				notStarted++;
			}
			else if (state == QuestState.IN_PROGRESS)
			{
				inProgress++;
			}
			else if (state == QuestState.FINISHED)
			{
				finished++;
			}
			else
			{
				unknown++;
			}

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("name", quest.getName());
			data.put("state", stateName);
			entries.put(quest.name(), data);
		}

		result.put("notStarted", notStarted);
		result.put("inProgress", inProgress);
		result.put("finished", finished);
		result.put("unknown", unknown);
		result.put("total", entries.size());
		result.put("entries", entries);
		return result;
	}

	private Map<String, Object> buildAchievementDiaries()
	{
		return AchievementDiarySnapshot.build(client::getVarbitValue);
	}

	private long getLong(Object value)
	{
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

	private int getInt(Object value)
	{
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	private void resetSession()
	{
		ticksLoggedIn = 0;
		ticksSinceExport = 0;
		lastRsn = "";
		lastGoodBank = null;
		lastGoodInventory = null;
		lastGoodEquipment = null;
		Arrays.fill(lastGoodSailingCargo, null);
		bankInterfaceOpen = false;
		bankContextUntil = 0;
		captureInProgress = false;
		captureActivityTracker.reset();
		ticksSinceAutomaticCapture = 0;
		automaticBankCapturePending = false;
		collectionLogSnapshots.deactivate();
		pohSnapshots.resetAccount();
		hunterRumours.resetAccount();
	}

	private void resetAccount(String rsn)
	{
		boolean accountChanged = !lastRsn.isEmpty() && !lastRsn.equals(rsn);
		lastRsn = rsn;
		collectionLogSnapshots.activate(rsn);
		if (accountChanged)
		{
			pohSnapshots.resetAccount();
			hunterRumours.resetAccount();
		}
		lastGoodBank = null;
		lastGoodInventory = null;
		lastGoodEquipment = null;
		Arrays.fill(lastGoodSailingCargo, null);
		bankInterfaceOpen = false;
		bankContextUntil = 0;
		captureActivityTracker.reset();
		ticksSinceAutomaticCapture = 0;
		automaticBankCapturePending = false;
		ticksSinceExport = Math.max(10, config.exportIntervalTicks());
	}

	private static final class CachedContainer
	{
		private final Map<String, Object> data;
		private final boolean loaded;
		private final boolean fromCache;
		private final long lastSeenTimestamp;

		private CachedContainer(Map<String, Object> data, boolean loaded, boolean fromCache, long lastSeenTimestamp)
		{
			this.data = data;
			this.loaded = loaded;
			this.fromCache = fromCache;
			this.lastSeenTimestamp = lastSeenTimestamp;
		}
	}
}
