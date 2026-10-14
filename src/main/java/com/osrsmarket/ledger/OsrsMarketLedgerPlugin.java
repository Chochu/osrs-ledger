package com.osrsmarket.ledger;

import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.QuantityFormatter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "OSRS Ledger",
	description = "GE slot colors, offer P&L, and sync completed GE History fills to your OSRS Ledger account",
	tags = {"ge", "grand exchange", "flip", "profit", "ledger"}
)
public class OsrsMarketLedgerPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final int SLOTS = 8;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private OsrsMarketLedgerConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private OkHttpClient http;
	private OkHttpClient ledgerHttp;
	@Inject
	private Gson gson;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private GeOffersOverlay geOverlay;
	@Inject
	private CostBasis costBasis;
	@Inject
	private WikiQuotes quotes;
	@Inject
	private ItemManager itemManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private GeOfferChatbox geChatbox;
	@Inject
	private GeOfferHotkeys geHotkeys;
	@Inject
	private Notifier notifier;

	private OsrsMarketLedgerPanel panel;
	private NavigationButton navButton;

	private final int[] prevQty = new int[SLOTS];
	private final int[] prevItem = new int[SLOTS];
	private final GrandExchangeOfferState[] prevState = new GrandExchangeOfferState[SLOTS];
	private final long[] listedAtMs = new long[SLOTS];
	private final long[] lastFillAtMs = new long[SLOTS];
	private final int[] listedSigItem = new int[SLOTS];
	private final long[] listedSigPrice = new long[SLOTS];
	/** Setup-screen price. Unnamed long varp; replaced varbit GE_NEWOFFER_PRICE. */
	private static final int GE_OFFER_PRICE = 5753;
	private final int[] listedSigQty = new int[SLOTS];
	private final SlotListing[] serverListing = new SlotListing[SLOTS];
	private volatile boolean listingsHydrated;
	private ScheduledFuture<?> syncTask;
	private ScheduledFuture<?> deskTask;
	private long deskCash = -1;
	private volatile int deskReq;
	private int historyRetries;
	private int historyStableTicks;
	private String historyStableSig;
	private boolean historySyncing;
	private long historySyncStartedAt;
	private boolean historyWasOpen;
	private boolean geWasOpen;
	private String panelStamp;
	private int lookupItemId;
	private boolean lookupSticky;
	private boolean lookupFromPanel;
	private int followedSlot = Integer.MIN_VALUE;
	private int followedItemId;
	private int historyItemId;
	private long historyFetchedAt;
	private long sessionStartedAt;
	private long sessionRealized;
	private long sessionTax;
	private OsrsMarketLedgerPanel.NamedItem[] rotation = new OsrsMarketLedgerPanel.NamedItem[0];
	private final LinkedHashSet<Integer> skippedNext = new LinkedHashSet<>();
	private boolean cyclingNext;
	private String statsRange = "session";

	@Provides
	OsrsMarketLedgerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsMarketLedgerConfig.class);
	}

	@Override
	protected void startUp()
	{
		// RuneLite's shared OkHttp client is ~10s; history batch ingest needs longer.
		ledgerHttp = http.newBuilder()
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(90, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.callTimeout(120, TimeUnit.SECONDS)
			.build();
		panel = injector.getInstance(OsrsMarketLedgerPanel.class);
		navButton = NavigationButton.builder()
			.tooltip("OSRS Ledger")
			.icon(navIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(geOverlay);
		keyManager.registerKeyListener(geHotkeys);
		quotes.setOnUpdated(() -> clientThread.invoke(this::refreshPanel));
		panel.setOnSetPrice(price -> clientThread.invoke(() -> typeOfferPrice(price)));
		panel.setOnSetQty(qty -> clientThread.invoke(() -> typeOfferQty(qty)));
		panel.setOnLookup(itemId -> clientThread.invoke(() -> lookupItem(itemId)));
		panel.setOnPickDeskItem(itemId -> clientThread.invoke(() ->
		{
			if (panel != null)
			{
				panel.showItemPage();
			}
			lookupItem(itemId);
			GeLastSearch.prime(client, itemId);
		}));
		panel.setOnToggleRotation(this::toggleRotation);
		panel.setOnRefreshNext(this::skipShownNext);
		panel.setOnStatsRange(range ->
		{
			statsRange = range == null || range.isEmpty() ? "session" : range;
			fetchStats();
		});

		resetPrime();
		sessionStartedAt = 0;
		fetchLedgerBasis();
		fetchDesk();
		fetchStats();
		deskTask = executor.scheduleAtFixedRate(() ->
		{
			try
			{
				fetchDesk();
				fetchStats();
				clientThread.invoke(this::refreshPanel);
			}
			catch (Exception e)
			{
				log.debug("OSRS Ledger desk tick failed: {}", e.getMessage());
			}
		}, 15, 15, TimeUnit.SECONDS);
		clientThread.invokeLater(this::catchUpAndSync);
	}

	@Override
	protected void shutDown()
	{
		if (syncTask != null)
		{
			syncTask.cancel(false);
			syncTask = null;
		}
		if (deskTask != null)
		{
			deskTask.cancel(false);
			deskTask = null;
		}
		resetPrime();
		sessionStartedAt = 0;
		sessionRealized = 0;
		sessionTax = 0;
		rotation = new OsrsMarketLedgerPanel.NamedItem[0];
		skippedNext.clear();
		cyclingNext = false;
		deskCash = -1;
		statsRange = "session";
		costBasis.clear();
		historySyncing = false;
		historySyncStartedAt = 0;
		historyRetries = 0;
		historyStableTicks = 0;
		historyStableSig = "";
		historyWasOpen = false;
		geWasOpen = false;
		panelStamp = null;
		lookupItemId = 0;
		lookupSticky = false;
		lookupFromPanel = false;
		followedSlot = Integer.MIN_VALUE;
		followedItemId = 0;
		quotes.setOnUpdated(null);
		geHotkeys.setContext(GeOfferInput.Kind.NONE, 0, 0, 0, 0);
		keyManager.unregisterKeyListener(geHotkeys);
		overlayManager.remove(geOverlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN)
		{
			resetPrime();
			sessionStartedAt = 0;
			sessionRealized = 0;
			sessionTax = 0;
			lookupItemId = 0;
			lookupSticky = false;
			lookupFromPanel = false;
			followedSlot = Integer.MIN_VALUE;
			followedItemId = 0;
			skippedNext.clear();
			cyclingNext = false;
			statsRange = "session";
			deskCash = -1;
			if (panel != null)
			{
				panel.showLoggedOut();
			}
			return;
		}
		if (state == GameState.LOGGED_IN)
		{
			if (sessionStartedAt <= 0)
			{
				sessionStartedAt = System.currentTimeMillis();
			}
			fetchDesk();
			clientThread.invokeLater(this::catchUpAndSync);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"osrsmarketledger".equals(event.getGroup()))
		{
			return;
		}
		if (event.getKey() != null && event.getKey().startsWith("listed."))
		{
			return;
		}
		panelStamp = null;
		clientThread.invoke(this::refreshPanel);
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int slot = event.getSlot();
		if (slot < 0 || slot >= SLOTS)
		{
			return;
		}
		applyOffer(slot, event.getOffer());
		scheduleSync();
		refreshPanel();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			clientThread.invokeLater(this::refreshPanel);
		}
		if (!config.syncTradeHistory() || event.getGroupId() != InterfaceID.GE_HISTORY)
		{
			return;
		}
		beginHistorySync();
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VarClientInt.INPUT_TYPE)
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		if (varbit == VarbitID.GE_SELECTEDSLOT
			|| varbit == VarbitID.GE_NEWOFFER_TYPE
			|| varbit == VarbitID.GE_NEWOFFER_QUANTITY
			|| event.getVarpId() == GE_OFFER_PRICE
			|| event.getVarpId() == VarPlayerID.TRADINGPOST_SEARCH
			|| event.getVarpId() == VarPlayerID.GE_LAST_SEARCHED)
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD
			|| event.getScriptId() == ScriptID.GE_ITEM_SEARCH)
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long cash = inventoryCash();
		if (cash == deskCash)
		{
			return;
		}
		deskCash = cash;
		fetchDesk();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean open = widgetVisible(InterfaceID.GeOffers.UNIVERSE)
			|| widgetVisible(InterfaceID.GeOffers.CONTENTS);
		if (open || geWasOpen)
		{
			geWasOpen = open;
			refreshPanel();
		}

		boolean histOpen = widgetVisible(InterfaceID.GeHistory.LIST);
		if (histOpen && !historyWasOpen)
		{
			beginHistorySync();
		}
		historyWasOpen = histOpen;
		if (histOpen && historyRetries > 0 && historyRetries <= 8)
		{
			trySyncGeHistory();
		}
	}

	private void beginHistorySync()
	{
		if (!config.syncTradeHistory())
		{
			return;
		}
		historyRetries = 0;
		historyStableTicks = 0;
		historyStableSig = "";
		trySyncGeHistory();
	}

	private void trySyncGeHistory()
	{
		if (!config.syncTradeHistory() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (historySyncing && historySyncStartedAt > 0
			&& System.currentTimeMillis() - historySyncStartedAt > 15_000)
		{
			historySyncing = false;
		}
		Widget list = client.getWidget(InterfaceID.GeHistory.LIST);
		if (list == null || list.isHidden())
		{
			retryHistorySync(null);
			return;
		}
		List<GeHistoryExtractor.Fill> newest = GeHistoryExtractor.fromWidgetTree(list);
		if (newest.isEmpty())
		{
			Widget root = client.getWidget(InterfaceID.GeHistory.UNIVERSE);
			if (root != null && root != list)
			{
				newest = GeHistoryExtractor.fromWidgetTree(root);
			}
		}
		if (newest.isEmpty())
		{
			retryHistorySync(GeHistoryExtractor.slotsFromWidgetTree(list));
			return;
		}
		String sig = historySignature(newest);
		if (!sig.equals(historyStableSig))
		{
			historyStableSig = sig;
			historyStableTicks = 1;
			if (historyRetries < 8)
			{
				historyRetries++;
			}
			log.debug("OSRS Ledger history waiting for stable rows {}", GeHistoryExtractor.debugFills(newest));
			return;
		}
		historyStableTicks++;
		if (historyStableTicks < 2 && historyRetries < 8)
		{
			historyRetries++;
			return;
		}
		historyRetries = 0;
		if (!historySyncing)
		{
			log.info("OSRS Ledger history extracted {} row(s) {}",
				newest.size(), GeHistoryExtractor.debugFills(newest));
			log.debug("OSRS Ledger history LIST dump\n{}", GeHistoryExtractor.debugLayout(list));
			Widget universe = client.getWidget(InterfaceID.GeHistory.UNIVERSE);
			if (universe != null && universe != list)
			{
				log.debug("OSRS Ledger history UNIVERSE dump\n{}", GeHistoryExtractor.debugLayout(universe));
			}
		}
		else
		{
			log.debug("OSRS Ledger history extracted {} row(s)", newest.size());
		}
		postGeHistory(newest);
	}

	private static String historySignature(List<GeHistoryExtractor.Fill> fills)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fills.size(); i++)
		{
			if (i > 0)
			{
				sb.append(';');
			}
			sb.append(fills.get(i).tupleKey());
		}
		return sb.toString();
	}

	private void retryHistorySync(List<GeHistoryExtractor.Slot> slots)
	{
		if (historyRetries < 8)
		{
			historyRetries++;
			return;
		}
		if (historyRetries == 8)
		{
			historyRetries++;
			String dump = GeHistoryExtractor.debugSlots(slots, 24);
			log.warn("OSRS Ledger: GE History had no readable rows ({})", dump);
			Widget list = client.getWidget(InterfaceID.GeHistory.LIST);
			if (list != null)
			{
				log.warn("OSRS Ledger history LIST dump\n{}", GeHistoryExtractor.debugLayout(list));
			}
			errorNotify("OSRS Ledger: couldn't read GE History rows.");
		}
	}

	private void postGeHistory(List<GeHistoryExtractor.Fill> newestFirst)
	{
		if (historySyncing || newestFirst == null || newestFirst.isEmpty())
		{
			return;
		}
		List<GeHistoryExtractor.Fill> snapshot = new ArrayList<>(newestFirst);
		JsonObject body = historyBody(snapshot, false);
		historySyncing = true;
		historySyncStartedAt = System.currentTimeMillis();
		postHistory(body, snapshot, false, accountHashHeader(), accountNameHeader());
	}

	private String accountHashHeader()
	{
		return client.getAccountHash() != 0
			? Long.toUnsignedString(client.getAccountHash())
			: null;
	}

	private String accountNameHeader()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null || local.getName().isEmpty())
		{
			return null;
		}
		return URLEncoder.encode(local.getName(), StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static JsonObject historyBody(List<GeHistoryExtractor.Fill> newestFirst, boolean withSeq)
	{
		JsonArray payload = new JsonArray();
		int n = Math.min(newestFirst.size(), 40);
		for (int i = 0; i < n; i++)
		{
			GeHistoryExtractor.Fill fill = newestFirst.get(i);
			JsonObject obj = new JsonObject();
			obj.addProperty("itemId", fill.itemId);
			obj.addProperty("side", fill.side);
			obj.addProperty("qty", fill.qty);
			obj.addProperty("priceEach", fill.priceEach);
			if (withSeq)
			{
				obj.addProperty("seq", n - i);
			}
			payload.add(obj);
		}
		JsonObject body = new JsonObject();
		body.add("rows", payload);
		return body;
	}

	private void postHistory(
		JsonObject body,
		List<GeHistoryExtractor.Fill> newestFirst,
		boolean legacyRetry,
		String accountHashHeader,
		String accountNameHeader)
	{
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			historySyncing = false;
			historySyncStartedAt = 0;
			errorNotify("OSRS Ledger: set your plugin key in RuneLite settings.");
			return;
		}
		int sent = body.has("rows") && body.get("rows").isJsonArray()
			? body.getAsJsonArray("rows").size()
			: 0;
		Request.Builder builder = new Request.Builder()
			.url(base + "/api/ledger/sync/history")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.post(RequestBody.create(JSON, gson.toJson(body)));
		if (accountHashHeader != null)
		{
			builder.header("X-Osrs-Account-Hash", accountHashHeader);
		}
		if (accountNameHeader != null)
		{
			builder.header("X-Osrs-Display-Name", accountNameHeader);
		}
		Request request = builder.build();
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				historySyncing = false;
				historySyncStartedAt = 0;
				log.warn("OSRS Ledger history sync failed: {}", e.getMessage());
				errorNotify("OSRS Ledger history sync failed: " + e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						historySyncing = false;
						historySyncStartedAt = 0;
						String errBody = res.body() != null ? res.body().string() : res.message();
						String errMsg = apiErrorMessage(errBody, res.message());
						log.warn("OSRS Ledger history sync HTTP {}: {}", res.code(), errMsg);
						errorNotify("OSRS Ledger history sync failed (" + res.code() + "): " + errMsg);
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					int imported = root != null && root.has("imported") ? root.get("imported").getAsInt() : 0;
					int already = root != null && root.has("alreadyOnBook") ? root.get("alreadyOnBook").getAsInt() : 0;
					if (root != null && root.has("errors") && root.get("errors").isJsonArray())
					{
						JsonArray errs = root.getAsJsonArray("errors");
						if (errs.size() > 0)
						{
							String detail = errs.get(0).getAsString();
							log.warn("OSRS Ledger history sync partial: {}", detail);
							warnNotify("OSRS Ledger history sync partial: " + detail);
						}
					}
					if (imported == 0 && already == 0 && sent > 0 && !legacyRetry)
					{
						log.warn("OSRS Ledger history API ignored {} row(s); watermarking against journal", sent);
						if (legacyHistoryFallback(newestFirst, base, key, accountHashHeader, accountNameHeader))
						{
							return;
						}
					}
					historySyncing = false;
					historySyncStartedAt = 0;
					if (imported > 0)
					{
						fetchLedgerBasis();
						historyFetchedAt = 0;
					}
					say("OSRS Ledger: history "
						+ imported + " imported · "
						+ already + " already on book.");
				}
				catch (IOException | RuntimeException e)
				{
					historySyncing = false;
					historySyncStartedAt = 0;
					log.warn("OSRS Ledger history sync read failed", e);
				}
			}
		});
	}

	private boolean legacyHistoryFallback(
		List<GeHistoryExtractor.Fill> newestFirst,
		String base,
		String key,
		String accountHashHeader,
		String accountNameHeader)
	{
		Request.Builder get = new Request.Builder()
			.url(base + "/api/ledger/fills?page=1&pageSize=100")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.get();
		if (accountHashHeader != null)
		{
			get.header("X-Osrs-Account-Hash", accountHashHeader);
		}
		if (accountNameHeader != null)
		{
			get.header("X-Osrs-Display-Name", accountNameHeader);
		}
		try (Response journal = ledgerHttp.newCall(get.build()).execute())
		{
			if (!journal.isSuccessful() || journal.body() == null)
			{
				log.warn("OSRS Ledger history journal watermark HTTP {}", journal.code());
				return false;
			}
			JsonObject root = gson.fromJson(journal.body().charStream(), JsonObject.class);
			Set<String> known = journalTuples(root);
			List<GeHistoryExtractor.Fill> prefix = HistorySyncLog.unseenPrefix(
				newestFirst, Collections.emptyList(), known);
			if (!HistorySyncLog.journalAligns(newestFirst.size(), prefix.size(), !known.isEmpty()))
			{
				log.warn("OSRS Ledger history journal did not line up with GE History");
				return false;
			}
			if (prefix.isEmpty())
			{
				historySyncing = false;
				historySyncStartedAt = 0;
				say("OSRS Ledger: history 0 imported · " + newestFirst.size() + " already on book.");
				return true;
			}
			log.info("OSRS Ledger history legacy prefix {} row(s) {}",
				prefix.size(), GeHistoryExtractor.debugFills(prefix));
			postHistory(historyBody(prefix, true), newestFirst, true, accountHashHeader, accountNameHeader);
			return true;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("OSRS Ledger history journal watermark failed", e);
			return false;
		}
	}

	private static Set<String> journalTuples(JsonObject root)
	{
		Set<String> known = new HashSet<>();
		if (root == null || !root.has("fills") || !root.get("fills").isJsonArray())
		{
			return known;
		}
		for (JsonElement el : root.getAsJsonArray("fills"))
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject fill = el.getAsJsonObject();
			if (!fill.has("itemId") || !fill.has("side") || !fill.has("qty") || !fill.has("priceEach"))
			{
				continue;
			}
			String side = fill.get("side").getAsString();
			if (!"buy".equals(side) && !"sell".equals(side))
			{
				continue;
			}
			known.add(fill.get("itemId").getAsInt() + ":" + side + ":"
				+ fill.get("qty").getAsInt() + ":" + fill.get("priceEach").getAsLong());
		}
		return known;
	}

	private boolean geHistoryVisible()
	{
		return widgetVisible(InterfaceID.GeHistory.LIST)
			|| widgetVisible(InterfaceID.GeHistory.UNIVERSE);
	}

	private void postCompletedOffer(int itemId, String side, int qty, long priceEach)
	{
		if (!config.syncTradeHistory() || side == null || itemId <= 0 || qty <= 0 || priceEach <= 0)
		{
			return;
		}
		if (geHistoryVisible())
		{
			return;
		}
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("side", side);
		body.addProperty("qty", qty);
		body.addProperty("priceEach", priceEach);
		body.addProperty("idempotencyKey", "history:row:" + UUID.randomUUID());
		String base = OsrsMarketUrls.apiOrigin();
		Request.Builder builder = new Request.Builder()
			.url(base + "/api/ledger/sync/complete")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.post(RequestBody.create(JSON, gson.toJson(body)));
		attachOsrsAccount(builder);
		log.info("OSRS Ledger complete {} {} x {} @ {}", side, itemId, qty, priceEach);
		ledgerHttp.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("OSRS Ledger complete failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful())
					{
						String errBody = res.body() != null ? res.body().string() : res.message();
						log.warn("OSRS Ledger complete HTTP {}: {}", res.code(),
							apiErrorMessage(errBody, res.message()));
						return;
					}
					JsonObject root = res.body() == null
						? null
						: gson.fromJson(res.body().charStream(), JsonObject.class);
					boolean created = root != null && root.has("created") && root.get("created").getAsBoolean();
					if (created)
					{
						fetchLedgerBasis();
					}
				}
				catch (IOException | RuntimeException e)
				{
					log.warn("OSRS Ledger complete read failed", e);
				}
			}
		});
	}

	private void catchUpAndSync()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			if (panel != null)
			{
				panel.showLoggedOut();
			}
			return;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int i = 0; i < SLOTS; i++)
		{
			GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
			applyOffer(i, offer);
		}
		readAndPostOffers();
		if (sessionStartedAt <= 0)
		{
			sessionStartedAt = System.currentTimeMillis();
		}
		fetchLedgerBasis();
		fetchDesk();
		refreshPanel();
	}

	private void applyOffer(int slot, GrandExchangeOffer offer)
	{
		int qty = offer == null ? 0 : offer.getQuantitySold();
		int itemId = offer == null ? 0 : offer.getItemId();
		GrandExchangeOfferState state = offer == null ? GrandExchangeOfferState.EMPTY : offer.getState();
		boolean empty = itemId <= 0 || state == GrandExchangeOfferState.EMPTY;
		GrandExchangeOfferState prev = prevState[slot] == null ? GrandExchangeOfferState.EMPTY : prevState[slot];

		boolean filled = !empty && itemId == prevItem[slot] && qty > prevQty[slot];
		if (filled)
		{
			lastFillAtMs[slot] = System.currentTimeMillis();
		}

		boolean completed = !empty && itemId == prevItem[slot] && qty > 0 && offer.getPrice() > 0
			&& ((prev == GrandExchangeOfferState.BUYING && state == GrandExchangeOfferState.BOUGHT)
				|| (prev == GrandExchangeOfferState.SELLING && state == GrandExchangeOfferState.SOLD));

		prevQty[slot] = empty ? 0 : qty;
		prevItem[slot] = empty ? 0 : itemId;
		prevState[slot] = empty ? GrandExchangeOfferState.EMPTY : state;
		touchListing(slot, offer);
		if (filled)
		{
			saveListing(slot);
		}
		if (completed)
		{
			postCompletedOffer(itemId, sideOf(state), qty, offer.getPrice());
		}
	}

	private void touchListing(int slot, GrandExchangeOffer offer)
	{
		if (offer == null || offer.getItemId() <= 0 || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			listedAtMs[slot] = 0;
			lastFillAtMs[slot] = 0;
			listedSigItem[slot] = 0;
			listedSigPrice[slot] = 0;
			listedSigQty[slot] = 0;
			saveListing(slot);
			return;
		}
		if (!isListed(offer.getState()))
		{
			return;
		}
		int itemId = offer.getItemId();
		long price = offer.getPrice();
		int qtyTotal = offer.getTotalQuantity();
		if (itemId == listedSigItem[slot] && price == listedSigPrice[slot] && qtyTotal == listedSigQty[slot])
		{
			return;
		}
		listedSigItem[slot] = itemId;
		listedSigPrice[slot] = price;
		listedSigQty[slot] = qtyTotal;
		SlotListing saved = matchListing(slot, itemId, price, qtyTotal);
		if (saved != null)
		{
			listedAtMs[slot] = saved.listedAt;
			lastFillAtMs[slot] = saved.lastFillAt;
		}
		else if (!listingsHydrated)
		{
			return;
		}
		else
		{
			listedAtMs[slot] = System.currentTimeMillis();
			lastFillAtMs[slot] = 0;
		}
		saveListing(slot);
	}

	private SlotListing matchListing(int slot, int itemId, long price, int qtyTotal)
	{
		SlotListing server = slot >= 0 && slot < serverListing.length ? serverListing[slot] : null;
		if (server != null && server.sameOffer(itemId, price, qtyTotal))
		{
			return server;
		}
		SlotListing local = loadListing(slot);
		if (local != null && local.sameOffer(itemId, price, qtyTotal))
		{
			return local;
		}
		return null;
	}

	private SlotListing loadListing(int slot)
	{
		try
		{
			return SlotListing.parse(configManager.getRSProfileConfiguration("osrsmarketledger", SlotListing.key(slot)));
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private void saveListing(int slot)
	{
		try
		{
			if (listedSigItem[slot] <= 0 || listedAtMs[slot] <= 0)
			{
				configManager.unsetRSProfileConfiguration("osrsmarketledger", SlotListing.key(slot));
				return;
			}
			SlotListing row = new SlotListing(
				listedSigItem[slot],
				listedSigPrice[slot],
				listedSigQty[slot],
				listedAtMs[slot],
				lastFillAtMs[slot]);
			configManager.setRSProfileConfiguration("osrsmarketledger", SlotListing.key(slot), row.encode());
		}
		catch (RuntimeException ignored)
		{
			/* RS profile config is only valid while logged in */
		}
	}

	private void resetPrime()
	{
		Arrays.fill(prevQty, 0);
		Arrays.fill(prevItem, 0);
		Arrays.fill(prevState, GrandExchangeOfferState.EMPTY);
		Arrays.fill(listedAtMs, 0);
		Arrays.fill(lastFillAtMs, 0);
		Arrays.fill(listedSigItem, 0);
		Arrays.fill(listedSigPrice, 0);
		Arrays.fill(listedSigQty, 0);
		Arrays.fill(serverListing, null);
		listingsHydrated = false;
	}

	private boolean hasCredentials()
	{
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		return !key.isEmpty();
	}

	private static boolean isListed(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING;
	}

	private static String sideOf(GrandExchangeOfferState state)
	{
		if (state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY)
		{
			return "buy";
		}
		if (state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			return "sell";
		}
		return null;
	}

	private void scheduleSync()
	{
		if (syncTask != null)
		{
			syncTask.cancel(false);
		}
		syncTask = executor.schedule(() -> clientThread.invoke(this::readAndPostOffers), 400, TimeUnit.MILLISECONDS);
	}

	private void readAndPostOffers()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		JsonArray slots = new JsonArray();
		for (int i = 0; i < SLOTS; i++)
		{
			JsonObject slot = new JsonObject();
			slot.addProperty("slot", i);
			GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
			if (offer == null || offer.getItemId() <= 0 || !isListed(offer.getState()))
			{
				slot.addProperty("state", "EMPTY");
			}
			else
			{
				slot.addProperty("itemId", offer.getItemId());
				String side = sideOf(offer.getState());
				if (side != null)
				{
					slot.addProperty("side", side);
				}
				slot.addProperty("priceEach", offer.getPrice());
				slot.addProperty("qtyTotal", offer.getTotalQuantity());
				slot.addProperty("qtyFilled", offer.getQuantitySold());
				slot.addProperty("spent", offer.getSpent());
				slot.addProperty("state", offer.getState().name());
				if (listedAtMs[i] > 0)
				{
					slot.addProperty("listedAt", listedAtMs[i]);
				}
				if (lastFillAtMs[i] > 0)
				{
					slot.addProperty("lastFillAt", lastFillAtMs[i]);
				}
			}
			slots.add(slot);
		}
		JsonObject body = new JsonObject();
		body.add("slots", slots);
		post("/api/ledger/offers/sync", "PUT", body);
	}

	private void fetchLedgerBasis()
	{
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}
		Request.Builder builder = new Request.Builder()
			.url(base + "/api/ledger")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.get();
		attachOsrsAccount(builder);
		Request request = builder.build();
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("OSRS Ledger GET failed: {}", e.getMessage());
				clientThread.invoke(() -> applyServerListings(null));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						clientThread.invoke(() -> applyServerListings(null));
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					if (root == null)
					{
						clientThread.invoke(() -> applyServerListings(null));
						return;
					}
					if (root.has("positions") && root.get("positions").isJsonArray())
					{
					JsonArray positions = root.getAsJsonArray("positions");
					Map<Integer, int[]> map = new HashMap<>();
					for (JsonElement el : positions)
					{
						if (!el.isJsonObject())
						{
							continue;
						}
						JsonObject p = el.getAsJsonObject();
						if (!p.has("itemId") || !p.has("qty") || !p.has("avgCost"))
						{
							continue;
						}
						int itemId = p.get("itemId").getAsInt();
						int qty = p.get("qty").getAsInt();
						int avg = p.get("avgCost").getAsInt();
						if (itemId > 0 && qty > 0 && avg > 0)
						{
							map.put(itemId, new int[]{qty, avg});
						}
					}
					costBasis.replaceAll(map);
					}
					JsonArray offers = root.has("offers") && root.get("offers").isJsonArray()
						? root.getAsJsonArray("offers")
						: null;
					clientThread.invoke(() -> applyServerListings(offers));
					fetchDesk();
				}
				catch (RuntimeException e)
				{
					log.debug("OSRS Ledger parse failed: {}", e.getMessage());
					clientThread.invoke(() -> applyServerListings(null));
				}
			}
		});
	}

	private void applyServerListings(JsonArray offers)
	{
		Arrays.fill(serverListing, null);
		if (offers != null)
		{
			for (JsonElement el : offers)
			{
				if (!el.isJsonObject())
				{
					continue;
				}
				JsonObject o = el.getAsJsonObject();
				int slot = jsonInt(o, "slot");
				int itemId = jsonInt(o, "itemId");
				long price = jsonLong(o, "priceEach");
				int qty = jsonInt(o, "qtyTotal");
				long listedAt = parseTimeMs(o, "listedAt");
				long lastFillAt = parseTimeMs(o, "lastFillAt");
				if (slot < 0 || slot >= SLOTS || itemId <= 0 || listedAt <= 0)
				{
					continue;
				}
				serverListing[slot] = new SlotListing(itemId, price, qty, listedAt, lastFillAt);
			}
		}
		listingsHydrated = true;
		Arrays.fill(listedSigItem, 0);
		Arrays.fill(listedSigPrice, 0);
		Arrays.fill(listedSigQty, 0);
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		GrandExchangeOffer[] ge = client.getGrandExchangeOffers();
		for (int i = 0; i < SLOTS; i++)
		{
			GrandExchangeOffer offer = ge != null && i < ge.length ? ge[i] : null;
			applyOffer(i, offer);
		}
		readAndPostOffers();
		refreshPanel();
	}

	private void fetchDesk()
	{
		if (panel == null)
		{
			return;
		}
		if (!hasCredentials())
		{
			panel.noteNext("Add your plugin key in OSRS Ledger settings, then press Refresh.", 0);
			return;
		}
		clientThread.invoke(() ->
		{
			if (panel == null || client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			long cash = inventoryCash();
			deskCash = cash;
			panel.showNextWaiting(cash);
			LinkedHashSet<Integer> excludeIds = new LinkedHashSet<>();
			GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
			for (int i = 0; i < SLOTS; i++)
			{
				GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
				if (offer != null && offer.getItemId() > 0 && isListed(offer.getState()))
				{
					excludeIds.add(offer.getItemId());
				}
			}
			excludeIds.addAll(skippedNext);
			getDesk(cash, joinIds(excludeIds));
		});
	}

	private void skipShownNext()
	{
		if (panel == null)
		{
			return;
		}
		for (int id : panel.shownNextIds())
		{
			if (id > 0)
			{
				skippedNext.add(id);
			}
		}
		while (skippedNext.size() > 24)
		{
			Integer first = skippedNext.iterator().next();
			skippedNext.remove(first);
		}
		cyclingNext = false;
		fetchDesk();
	}

	private static String joinIds(LinkedHashSet<Integer> ids)
	{
		if (ids == null || ids.isEmpty())
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int id : ids)
		{
			if (id <= 0)
			{
				continue;
			}
			if (out.length() > 0)
			{
				out.append(',');
			}
			out.append(id);
		}
		return out.toString();
	}

	private void fetchStats()
	{
		if (panel == null || !hasCredentials() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String range = panel.selectedStatsRange();
		if (range == null || range.isEmpty())
		{
			range = statsRange;
		}
		statsRange = range;
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}
		StringBuilder url = new StringBuilder(base).append("/api/flip/stats?range=").append(enc(range));
		if ("session".equals(range) && sessionStartedAt > 0)
		{
			url.append("&since=").append(enc(Instant.ofEpochMilli(sessionStartedAt).toString()));
		}
		Request.Builder builder = new Request.Builder()
			.url(url.toString())
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.get();
		attachOsrsAccount(builder);
		Request request = builder.build();
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("OSRS Ledger stats GET failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					applyStats(root);
				}
				catch (RuntimeException e)
				{
					log.debug("OSRS Ledger stats parse failed: {}", e.getMessage());
				}
			}
		});
	}

	private void applyStats(JsonObject root)
	{
		if (root == null || panel == null)
		{
			return;
		}
		String range = root.has("range") && !root.get("range").isJsonNull()
			? root.get("range").getAsString()
			: statsRange;
		panel.setStats(new OsrsMarketLedgerPanel.StatsPnl(
			range,
			jsonLong(root, "realized"),
			jsonLong(root, "taxPaid"),
			jsonLong(root, "gpPerHour"),
			jsonLong(root, "buySpend"),
			jsonLong(root, "sellProceeds"),
			jsonLong(root, "fillCount"),
			jsonLong(root, "buyCount"),
			jsonLong(root, "sellCount"),
			jsonLong(root, "buyQty"),
			jsonLong(root, "sellQty"),
			jsonLong(root, "winCount"),
			jsonDouble(root, "hours"),
			jsonLongObj(root, "paperDump"),
			jsonLongObj(root, "paperPatient")));
	}

	private static Long jsonLongObj(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			return obj.get(key).getAsLong();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static double jsonDouble(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return 0;
		}
		try
		{
			return obj.get(key).getAsDouble();
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	private void getDesk(long cash, String exclude)
	{
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}
		StringBuilder url = new StringBuilder(base).append("/api/flip/desk?cash=").append(Math.max(0, cash));
		if (sessionStartedAt > 0)
		{
			url.append("&since=").append(enc(Instant.ofEpochMilli(sessionStartedAt).toString()));
		}
		if (exclude != null && !exclude.isEmpty())
		{
			url.append("&exclude=").append(enc(exclude));
		}
		Request.Builder builder = new Request.Builder()
			.url(url.toString())
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.get();
		attachOsrsAccount(builder);
		Request request = builder.build();
		int req = ++deskReq;
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("OSRS Ledger desk GET failed: {}", e.getMessage());
				if (req == deskReq && panel != null)
				{
					panel.noteNext("Couldn't load buy ideas. Press Refresh.", cash);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (req != deskReq)
					{
						return;
					}
					if (!res.isSuccessful() || res.body() == null)
					{
						if (panel != null)
						{
							panel.noteNext("Couldn't load buy ideas (" + res.code() + "). Press Refresh.", cash);
						}
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					applyDesk(root);
				}
				catch (RuntimeException e)
				{
					log.debug("OSRS Ledger desk parse failed: {}", e.getMessage());
					if (req == deskReq && panel != null)
					{
						panel.noteNext("Couldn't read buy ideas. Press Refresh.", cash);
					}
				}
			}
		});
	}

	private void applyDesk(JsonObject root)
	{
		if (root == null || panel == null)
		{
			return;
		}
		OsrsMarketLedgerPanel.SessionPnl session = sessionOf(asObject(root, "session"));
		OsrsMarketLedgerPanel.NamedItem[] nextRotation = namedItems(asArray(root, "rotation"));
		OsrsMarketLedgerPanel.NextFlip[] next = nextFlips(asArray(root, "next"));
		rotation = nextRotation;
		if (next.length == 0 && !skippedNext.isEmpty() && !cyclingNext)
		{
			skippedNext.clear();
			cyclingNext = true;
			fetchDesk();
			return;
		}
		cyclingNext = false;
		if (session != null)
		{
			sessionRealized = session.realized;
			sessionTax = session.taxPaid;
		}
		long cash = root.has("cash") ? jsonLong(root, "cash") : 0;
		panel.setDesk(nextRotation, next, cash);
	}

	private static JsonObject asObject(JsonObject root, String key)
	{
		if (root == null || !root.has(key) || !root.get(key).isJsonObject())
		{
			return null;
		}
		return root.getAsJsonObject(key);
	}

	private static JsonArray asArray(JsonObject root, String key)
	{
		if (root == null || !root.has(key) || !root.get(key).isJsonArray())
		{
			return null;
		}
		return root.getAsJsonArray(key);
	}

	private static OsrsMarketLedgerPanel.SessionPnl sessionOf(JsonObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		return new OsrsMarketLedgerPanel.SessionPnl(
			jsonLong(obj, "realized"),
			jsonLong(obj, "taxPaid"),
			jsonLong(obj, "gpPerHour"));
	}

	private static OsrsMarketLedgerPanel.NamedItem[] namedItems(JsonArray arr)
	{
		if (arr == null || arr.size() == 0)
		{
			return new OsrsMarketLedgerPanel.NamedItem[0];
		}
		List<OsrsMarketLedgerPanel.NamedItem> out = new ArrayList<>();
		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			int id = jsonInt(o, "id");
			String name = o.has("name") ? o.get("name").getAsString() : ("Item " + id);
			if (id > 0)
			{
				out.add(new OsrsMarketLedgerPanel.NamedItem(id, name));
			}
		}
		return out.toArray(new OsrsMarketLedgerPanel.NamedItem[0]);
	}

	private static OsrsMarketLedgerPanel.NextFlip[] nextFlips(JsonArray arr)
	{
		if (arr == null || arr.size() == 0)
		{
			return new OsrsMarketLedgerPanel.NextFlip[0];
		}
		List<OsrsMarketLedgerPanel.NextFlip> out = new ArrayList<>();
		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			int id = jsonInt(o, "id");
			if (id <= 0)
			{
				continue;
			}
			String name = o.has("name") ? o.get("name").getAsString() : ("Item " + id);
			int profit = (int) jsonLong(o, "profit");
			int qtyAfford = (int) jsonLong(o, "qtyAfford");
			long cashProfit = o.has("cashProfit")
				? jsonLong(o, "cashProfit")
				: (long) profit * qtyAfford;
			out.add(new OsrsMarketLedgerPanel.NextFlip(
				id,
				name,
				profit,
				qtyAfford,
				cashProfit));
		}
		return out.toArray(new OsrsMarketLedgerPanel.NextFlip[0]);
	}

	private void toggleRotation(int itemId)
	{
		if (itemId <= 0)
		{
			return;
		}
		List<Integer> ids = new ArrayList<>();
		boolean found = false;
		OsrsMarketLedgerPanel.NamedItem[] cur = rotation;
		for (OsrsMarketLedgerPanel.NamedItem item : cur)
		{
			if (item == null)
			{
				continue;
			}
			if (item.id == itemId)
			{
				found = true;
				continue;
			}
			ids.add(item.id);
		}
		if (!found)
		{
			if (ids.size() >= 8)
			{
				say("Rotation is full (8 items). Remove one first.");
				return;
			}
			ids.add(itemId);
		}
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		for (int id : ids)
		{
			arr.add(id);
		}
		body.add("itemIds", arr);
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}
		Request.Builder builder = new Request.Builder()
			.url(base + "/api/rotation")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.put(RequestBody.create(JSON, gson.toJson(body)));
		attachOsrsAccount(builder);
		Request request = builder.build();
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("OSRS Ledger rotation PUT failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					if (root != null && root.has("items") && root.get("items").isJsonArray())
					{
						rotation = namedItems(root.getAsJsonArray("items"));
					}
					fetchDesk();
				}
				catch (RuntimeException e)
				{
					log.debug("OSRS Ledger rotation parse failed: {}", e.getMessage());
				}
			}
		});
	}

	private static String enc(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static long parseTimeMs(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return 0;
		}
		JsonElement el = obj.get(key);
		try
		{
			if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber())
			{
				long n = el.getAsLong();
				return n > 0 && n < 1_000_000_000_000L ? n * 1000L : n;
			}
			String raw = el.getAsString();
			if (raw == null || raw.isEmpty())
			{
				return 0;
			}
			if (raw.chars().allMatch(Character::isDigit))
			{
				long n = Long.parseLong(raw);
				return n > 0 && n < 1_000_000_000_000L ? n * 1000L : n;
			}
			return Instant.parse(raw).toEpochMilli();
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	private static long jsonLong(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return 0;
		}
		try
		{
			return obj.get(key).getAsLong();
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	private static int jsonInt(JsonObject obj, String key)
	{
		return (int) jsonLong(obj, key);
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			if (!"out".equals(panelStamp))
			{
				panelStamp = "out";
				geHotkeys.setContext(GeOfferInput.Kind.NONE, 0, 0, 0, 0);
				panel.showLoggedOut();
			}
			return;
		}
		quotes.refreshIfStale();
		GeOfferInput.Kind input = GeOfferInput.kind(client);
		OsrsMarketLedgerPanel.ItemPage geItem = geItemPage();
		followGeInspect(geItem);
		OsrsMarketLedgerPanel.ItemPage shown = shownItemPage(geItem);
		maybeFetchItemHistory(shown == null ? 0 : shown.itemId);
		OsrsMarketLedgerPanel.ItemPage typed = geItem != null ? geItem : shown;
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		OsrsMarketLedgerPanel.SlotView[] views = new OsrsMarketLedgerPanel.SlotView[SLOTS];
		StringBuilder stamp = new StringBuilder("s");
		for (int i = 0; i < SLOTS; i++)
		{
			GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
			views[i] = toSlotView(i, offer);
			stamp.append(':').append(views[i].itemId).append(',').append(views[i].qtyFilled)
				.append(',').append(views[i].priceEach).append(',').append(views[i].health)
				.append(',').append(views[i].idleMinutes).append(',').append(views[i].stalled);
		}
		Integer wikiBuy = typed == null ? null : typed.margin.instantBuy;
		Integer wikiSell = typed == null ? null : typed.margin.instantSell;
		int geLimit = typed == null ? 0 : typed.geLimit;
		int allQty = typed == null ? 0 : typed.allQty;
		geChatbox.sync(input, wikiBuy, wikiSell, geLimit, allQty);
		geHotkeys.setContext(
			input,
			wikiBuy == null ? 0 : wikiBuy,
			wikiSell == null ? 0 : wikiSell,
			geLimit,
			allQty);
		stamp.append("|").append(input);
		stamp.append("|t:").append(System.currentTimeMillis() / 15_000L);
		if (shown != null)
		{
			stamp.append("|i:").append(shown.itemId).append(':').append(shown.buy)
				.append(':').append(shown.canSetPrice)
				.append(':').append(shown.offerLine)
				.append(':').append(shown.margin.adjust).append(':').append(shown.margin.instantBuy)
				.append(':').append(shown.margin.instantSell).append(':').append(shown.margin.ifSellAtIb)
				.append(':').append(shown.margin.vsBook)
				.append(':').append(shown.geLimit).append(':').append(shown.allQty)
				.append("|l:").append(lookupItemId).append(':').append(lookupSticky)
				.append(':').append(lookupFromPanel);
		}
		String next = stamp.toString();
		if (next.equals(panelStamp))
		{
			return;
		}
		panelStamp = next;
		panel.show(views, shown, input);
	}

	private void maybeFetchItemHistory(int itemId)
	{
		if (panel == null)
		{
			return;
		}
		if (itemId <= 0)
		{
			historyItemId = 0;
			panel.setItemHistory(0, new OsrsMarketLedgerPanel.FillView[0]);
			return;
		}
		if (!hasCredentials())
		{
			panel.setItemHistory(itemId, new OsrsMarketLedgerPanel.FillView[0]);
			return;
		}
		long now = System.currentTimeMillis();
		if (itemId == historyItemId && now - historyFetchedAt < 20_000)
		{
			return;
		}
		historyItemId = itemId;
		historyFetchedAt = now;
		fetchItemHistory(itemId);
	}

	private void fetchItemHistory(int itemId)
	{
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty() || itemId <= 0)
		{
			return;
		}
		Request.Builder builder = new Request.Builder()
			.url(base + "/api/ledger/fills?itemId=" + itemId)
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.get();
		attachOsrsAccount(builder);
		Request request = builder.build();
		ledgerHttp.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("OSRS Ledger item fills GET failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						return;
					}
					JsonObject root = gson.fromJson(res.body().charStream(), JsonObject.class);
					OsrsMarketLedgerPanel.FillView[] fills = parseItemFills(root);
					clientThread.invoke(() ->
					{
						if (historyItemId != itemId || panel == null)
						{
							return;
						}
						panel.setItemHistory(itemId, fills);
					});
				}
				catch (RuntimeException e)
				{
					log.debug("OSRS Ledger item fills parse failed: {}", e.getMessage());
				}
			}
		});
	}

	private static OsrsMarketLedgerPanel.FillView[] parseItemFills(JsonObject root)
	{
		if (root == null || !root.has("fills") || !root.get("fills").isJsonArray())
		{
			return new OsrsMarketLedgerPanel.FillView[0];
		}
		JsonArray arr = root.getAsJsonArray("fills");
		List<OsrsMarketLedgerPanel.FillView> out = new ArrayList<>();
		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String side = jsonString(o, "side");
			if (!"buy".equals(side) && !"sell".equals(side) && !"craft".equals(side))
			{
				continue;
			}
			int qty = jsonInt(o, "qty");
			long price = jsonLong(o, "priceEach");
			if (qty <= 0 || price <= 0)
			{
				continue;
			}
			Integer realized = null;
			if (o.has("realized") && !o.get("realized").isJsonNull())
			{
				realized = jsonInt(o, "realized");
			}
			out.add(new OsrsMarketLedgerPanel.FillView(
				side,
				qty,
				price,
				realized,
				parseTimeMs(o, "filledAt")));
		}
		return out.toArray(new OsrsMarketLedgerPanel.FillView[0]);
	}

	private static String jsonString(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return "";
		}
		try
		{
			return obj.get(key).getAsString();
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	private void followGeInspect(OsrsMarketLedgerPanel.ItemPage geItem)
	{
		int itemId = geItem == null ? 0 : geItem.itemId;
		if (itemId <= 0)
		{
			followedSlot = Integer.MIN_VALUE;
			followedItemId = 0;
			return;
		}
		int selected = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1;
		if (selected == followedSlot && itemId == followedItemId)
		{
			return;
		}
		followedSlot = selected;
		followedItemId = itemId;
		lookupFromPanel = false;
		lookupItemId = itemId;
		lookupSticky = true;
		panelStamp = null;
		if (panel != null)
		{
			panel.showItemPage();
		}
	}

	private void lookupItem(int itemId)
	{
		int id = Math.max(0, itemId);
		boolean fromPanel = id > 0;
		if (id == lookupItemId && lookupSticky == fromPanel && lookupFromPanel == fromPanel)
		{
			return;
		}
		lookupItemId = id;
		lookupSticky = fromPanel;
		lookupFromPanel = fromPanel;
		panelStamp = null;
		if (fromPanel && panel != null)
		{
			panel.showItemPage();
		}
		refreshPanel();
	}

	private OsrsMarketLedgerPanel.ItemPage shownItemPage(OsrsMarketLedgerPanel.ItemPage geItem)
	{
		if (lookupFromPanel && lookupItemId > 0)
		{
			return itemPageFromLookup(lookupItemId);
		}
		if (geItem != null)
		{
			return geItem;
		}
		if (lookupSticky && lookupItemId > 0)
		{
			return itemPageFromLookup(lookupItemId);
		}
		return null;
	}

	private OsrsMarketLedgerPanel.ItemPage geItemPage()
	{
		if (!widgetVisible(InterfaceID.GeOffers.UNIVERSE)
			&& !widgetVisible(InterfaceID.GeOffers.CONTENTS))
		{
			return null;
		}
		boolean details = widgetVisible(InterfaceID.GeOffers.DETAILS);
		boolean setup = widgetVisible(InterfaceID.GeOffers.SETUP);
		if (!details && !setup)
		{
			return null;
		}

		int searched = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (setup && searched > 0)
		{
			boolean buy = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 0;
			long price = Math.max(0, client.getVarpLongValue(GE_OFFER_PRICE));
			int qty = Math.max(0, client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY));
			return itemPageFromSetup(searched, buy, price, qty);
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		int selected = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1;
		if (selected >= 0 && offers != null && selected < offers.length)
		{
			GrandExchangeOffer offer = offers[selected];
			if (offer != null && offer.getItemId() > 0
				&& offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				return itemPageFromOffer(offer);
			}
		}

		if (searched <= 0)
		{
			return null;
		}
		boolean buy = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 0;
		long price = Math.max(0, client.getVarpLongValue(GE_OFFER_PRICE));
		int qty = Math.max(0, client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY));
		return itemPageFromSetup(searched, buy, price, qty);
	}

	private OsrsMarketLedgerPanel.ItemPage itemPageFromOffer(GrandExchangeOffer offer)
	{
		int itemId = offer.getItemId();
		boolean buy = offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.BOUGHT
			|| offer.getState() == GrandExchangeOfferState.CANCELLED_BUY;
		boolean sell = !buy;
		int avg = costBasis.avgCost(itemId);
		SlotMargin margin = SlotMargin.analyze(offer, quotes.get(itemId), avg);
		String name = itemName(itemId);
		return new OsrsMarketLedgerPanel.ItemPage(
			itemId,
			name.isEmpty() ? "Item " + itemId : name,
			buy,
			sell,
			false,
			offerLine(buy, offer.getQuantitySold(), offer.getTotalQuantity(), offer.getPrice()),
			margin,
			geLimit(itemId),
			allQty(itemId, buy, offer.getPrice()));
	}

	private OsrsMarketLedgerPanel.ItemPage itemPageFromSetup(int itemId, boolean buy, long price, int qty)
	{
		int avg = costBasis.avgCost(itemId);
		SlotMargin margin = SlotMargin.analyze(buy, !buy, price, 0, qty, quotes.get(itemId), avg);
		String name = itemName(itemId);
		return new OsrsMarketLedgerPanel.ItemPage(
			itemId,
			name.isEmpty() ? "Item " + itemId : name,
			buy,
			!buy,
			true,
			offerLine(buy, 0, qty, price),
			margin,
			geLimit(itemId),
			allQty(itemId, buy, price));
	}

	private OsrsMarketLedgerPanel.ItemPage itemPageFromLookup(int itemId)
	{
		int avg = costBasis.avgCost(itemId);
		SlotMargin margin = SlotMargin.analyze(false, false, 0, 0, 0, quotes.get(itemId), avg);
		String name = itemName(itemId);
		return new OsrsMarketLedgerPanel.ItemPage(
			itemId,
			name.isEmpty() ? "Item " + itemId : name,
			false,
			false,
			false,
			"Wiki lookup",
			margin,
			geLimit(itemId),
			inventoryCount(itemId));
	}

	private int geLimit(int itemId)
	{
		if (itemId <= 0)
		{
			return 0;
		}
		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null)
		{
			return 0;
		}
		return Math.max(0, stats.getGeLimit());
	}

	private int allQty(int itemId, boolean buy, long price)
	{
		if (buy)
		{
			if (price <= 0)
			{
				return 0;
			}
			long coins = inventoryCash();
			long n = coins / price;
			return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
		}
		return inventoryCount(itemId);
	}

	private long inventoryCash()
	{
		long coins = cashIn(client.getItemContainer(InventoryID.INV));
		if (coins > 0)
		{
			return coins;
		}
		Widget bag = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (bag == null)
		{
			return 0;
		}
		return cashOn(bag.getDynamicChildren()) + cashOn(bag.getChildren());
	}

	private static long cashIn(ItemContainer inv)
	{
		if (inv == null)
		{
			return 0;
		}
		return inv.count(ItemID.COINS) + (long) inv.count(ItemID.PLATINUM) * 1000L;
	}

	private static long cashOn(Widget[] kids)
	{
		if (kids == null)
		{
			return 0;
		}
		long n = 0;
		for (Widget kid : kids)
		{
			if (kid == null)
			{
				continue;
			}
			int id = kid.getItemId();
			int qty = Math.max(0, kid.getItemQuantity());
			if (id == ItemID.COINS)
			{
				n += qty;
			}
			else if (id == ItemID.PLATINUM)
			{
				n += (long) qty * 1000L;
			}
		}
		return n;
	}

	private int inventoryCount(int itemId)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null || itemId <= 0)
		{
			return 0;
		}
		int n = 0;
		int linked = linkedItem(itemId);
		for (Item item : inv.getItems())
		{
			if (item == null)
			{
				continue;
			}
			if (item.getId() == itemId || (linked > 0 && item.getId() == linked))
			{
				n += item.getQuantity();
			}
		}
		return n;
	}

	private int linkedItem(int itemId)
	{
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			int linked = def.getLinkedNoteId();
			return linked > 0 && linked != itemId ? linked : -1;
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}

	private void typeOfferPrice(int price)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (GeOfferInput.kind(client) == GeOfferInput.Kind.QUANTITY)
		{
			say("Open the GE price field to type a price.");
			return;
		}
		if (!GeOfferPrice.apply(client, price))
		{
			say("Click the GE price field, then click IB / IS to type it in.");
		}
	}

	private void typeOfferQty(int qty)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (GeOfferInput.kind(client) == GeOfferInput.Kind.PRICE)
		{
			say("Open the GE quantity field to type a quantity.");
			return;
		}
		if (!GeOfferQty.apply(client, qty))
		{
			say("Click the GE quantity field, then click a quantity to type it in.");
			return;
		}
		if (GeOfferInput.kind(client) != GeOfferInput.Kind.QUANTITY)
		{
			say("Set offer quantity to " + QuantityFormatter.formatNumber(qty));
		}
		panelStamp = null;
		refreshPanel();
	}

	private static String offerLine(boolean buy, int qtySold, int qtyTotal, long price)
	{
		StringBuilder s = new StringBuilder(buy ? "Buy" : "Sell");
		if (qtyTotal > 0)
		{
			s.append(' ').append(qtySold).append(" / ").append(qtyTotal);
		}
		if (price > 0)
		{
			s.append(" @ ").append(QuantityFormatter.formatNumber(price)).append(" gp");
		}
		return s.toString();
	}

	private boolean widgetVisible(int packedId)
	{
		Widget w = client.getWidget(packedId);
		return w != null && !w.isHidden();
	}

	private OsrsMarketLedgerPanel.SlotView toSlotView(int index, GrandExchangeOffer offer)
	{
		if (offer == null || offer.getItemId() <= 0 || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return OsrsMarketLedgerPanel.SlotView.empty(index);
		}
		int itemId = offer.getItemId();
		String name = client.getItemDefinition(itemId).getName();
		GrandExchangeOfferState state = offer.getState();
		boolean buy = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
		int qtyFilled = offer.getQuantitySold();
		int qtyTotal = Math.max(offer.getTotalQuantity(), 0);
		long price = offer.getPrice();
		int left = Math.max(qtyTotal - qtyFilled, 0);
		long leftGp = (long) left * price;
		WikiQuotes.Quote quote = quotes.get(itemId);
		int avg = costBasis.avgCost(itemId);
		SlotMargin margin = SlotMargin.analyze(offer, quote, avg);
		Long paper = null;
		if (buy && quote != null && quote.high != null)
		{
			paper = GeTax.afterTax(quote.high) - price;
		}
		else if (!buy && avg > 0)
		{
			paper = GeTax.afterTax(price) - avg;
		}
		else if (!buy && quote != null && quote.low != null)
		{
			paper = GeTax.afterTax(price) - quote.low;
		}
		long now = System.currentTimeMillis();
		boolean listed = isListed(state);
		String idleLabel = SlotIdle.label(now, listedAtMs[index], lastFillAtMs[index], qtyFilled, listed);
		boolean stalled = SlotIdle.stalled(now, listedAtMs[index], lastFillAtMs[index], qtyFilled, listed);
		int idleMinutes = SlotIdle.idleMinutes(now, listedAtMs[index], lastFillAtMs[index]);
		return OsrsMarketLedgerPanel.SlotView.of(
			index,
			itemId,
			name,
			stateLabel(state),
			listed,
			buy,
			qtyFilled,
			qtyTotal,
			price,
			leftGp,
			margin,
			quote == null ? null : quote.high,
			quote == null ? null : quote.low,
			paper,
			idleMinutes,
			idleLabel,
			stalled
		);
	}

	private static String stateLabel(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
				return "Buying";
			case BOUGHT:
				return "Bought";
			case SELLING:
				return "Selling";
			case SOLD:
				return "Sold";
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return "Cancelled";
			default:
				return "Empty";
		}
	}

	private static BufferedImage navIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x1a73e8));
		g.fillRoundRect(0, 0, 16, 16, 4, 4);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
		g.drawString("GE", 2, 11);
		g.dispose();
		return image;
	}

	private void attachOsrsAccount(Request.Builder builder)
	{
		long hash = client.getAccountHash();
		if (hash != 0)
		{
			builder.header("X-Osrs-Account-Hash", Long.toUnsignedString(hash));
		}
		Player local = client.getLocalPlayer();
		if (local != null)
		{
			String name = local.getName();
			if (name != null && !name.isEmpty())
			{
				builder.header("X-Osrs-Display-Name",
					URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
			}
		}
	}

	private void post(String path, String method, JsonObject body)
	{
		String base = OsrsMarketUrls.apiOrigin();
		String key = config.apiKey() == null ? "" : config.apiKey().trim();
		if (key.isEmpty())
		{
			return;
		}

		Request.Builder builder = new Request.Builder()
			.url(base + path)
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json");
		attachOsrsAccount(builder);
		RequestBody requestBody = RequestBody.create(JSON, gson.toJson(body));
		if ("PUT".equals(method))
		{
			builder.put(requestBody);
		}
		else
		{
			builder.post(requestBody);
		}

		ledgerHttp.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("OSRS Ledger {} failed: {}", path, e.getMessage());
				errorNotify("OSRS Ledger failed: " + e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful())
					{
						String errBody = res.body() != null ? res.body().string() : res.message();
						String errMsg = apiErrorMessage(errBody, res.message());
						log.warn("OSRS Ledger {} HTTP {}: {}", path, res.code(), errMsg);
						errorNotify("OSRS Ledger failed (" + res.code() + "): " + errMsg);
					}
				}
				catch (IOException e)
				{
					log.warn("OSRS Ledger read failed", e);
				}
			}
		});
	}

	private String itemName(int itemId)
	{
		if (itemId <= 0)
		{
			return "";
		}
		try
		{
			String name = client.getItemDefinition(itemId).getName();
			return name == null || name.equals("null") ? "" : name;
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	private String apiErrorMessage(String body, String fallback)
	{
		if (body == null || body.isEmpty())
		{
			return fallback == null ? "Request failed" : fallback;
		}
		try
		{
			JsonObject o = gson.fromJson(body, JsonObject.class);
			if (o != null && o.has("error") && !o.get("error").isJsonNull())
			{
				return o.get("error").getAsString();
			}
		}
		catch (RuntimeException ignored)
		{
			// plain text or non-json body
		}
		String trimmed = body.trim();
		if (trimmed.length() > 120)
		{
			return trimmed.substring(0, 120) + "…";
		}
		return trimmed;
	}

	private void errorNotify(String msg)
	{
		notifier.notify(msg, TrayIcon.MessageType.ERROR);
	}

	private void warnNotify(String msg)
	{
		notifier.notify(msg, TrayIcon.MessageType.WARNING);
	}

	private void say(String msg)
	{
		if (!config.chatFeedback())
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
			}
		});
	}
}
