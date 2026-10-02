package com.osrsmarket.ledger;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.ThinProgressBar;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

@Singleton
class OsrsMarketLedgerPanel extends PluginPanel
{
	private static final Color BUY = new Color(110, 180, 255);
	private static final Color SELL = new Color(255, 180, 70);
	private static final Color PROFIT = new Color(80, 220, 110);
	private static final Color LOSS = new Color(255, 95, 95);
	private static final Color GOLD = new Color(255, 203, 61);
	private static final Color CARD = new Color(30, 30, 30);
	private static final Color CARD_EMPTY = new Color(26, 26, 26);
	private static final Color TAB_ON = new Color(42, 42, 42);
	private static final int SIDE_PAD = 12;
	private static final int SEARCH_HITS = 8;
	private static final int ROW_H = 18;
	private static final int KV_LABEL_W = 102;
	private static final int KV_ROW_H = 20;
	private static final int DESK_ICON = 32;
	private static final int DESK_ROW_H = 44;
	private static final int HISTORY_ROW_H = 36;
	private static final int HISTORY_VISIBLE = 5;
	private static final int HISTORY_GAP = 3;
	private static final Color BUY_BLUE = new Color(110, 175, 255);
	private static final int SLOT_BAR_H = 8;
	private static final int SLOT_TOGGLE = 32;
	private static final String COLLAPSED_KEY = "collapsedSlots";
	private static final int COLLAPSED_ALL = 0xFF;
	private static final Color HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
	private static final float UI_SIZE = 12f;
	private static final String CARD_SLOTS = "slots";
	private static final String CARD_FLIP = "flip";
	private static final String CARD_STATS = "stats";
	private static final String CARD_ITEM = "item";
	private static final class RangeOpt
	{
		final String key;
		final String label;

		RangeOpt(String key, String label)
		{
			this.key = key;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private static final RangeOpt[] STATS_RANGE_OPTS = {
		new RangeOpt("session", "This login"),
		new RangeOpt("1d", "Today"),
		new RangeOpt("1w", "Last 7 days"),
		new RangeOpt("1m", "Last 30 days"),
		new RangeOpt("6m", "Last 6 months"),
		new RangeOpt("1y", "Last year"),
		new RangeOpt("all", "All time")
	};

	private enum Page
	{
		SLOTS,
		FLIP,
		STATS,
		ITEM
	}

	private final OsrsMarketLedgerConfig config;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private int collapsedMask;
	private final JLabel title = new JLabel("Your offers");
	private final CardLayout pagesLayout = new CardLayout();
	private final JPanel pages = new JPanel(pagesLayout);
	private JPanel slotsPage;
	private final JPanel offerList = new JPanel();
	private final JScrollPane offersScroll = new JScrollPane(offerList);
	private boolean offersHooked;
	private final JLabel openStat = statValue();
	private final JLabel paperStat = statValue();
	private final JLabel buyStat = statValue();
	private final JLabel sellStat = statValue();
	private final JLabel hint = new JLabel(" ");
	private final JPanel hintWrap = new JPanel(new BorderLayout());
	private final SlotCard[] cards = new SlotCard[8];
	private final JPanel itemStripe = new JPanel(new BorderLayout(8, 0));
	private final JLabel itemIcon = new JLabel();
	private final JLabel itemName = new JLabel();
	private final JLabel itemHealth = new JLabel();
	private final JLabel itemOffer = new JLabel();
	private final JLabel itemIb = statValue();
	private final JLabel itemIs = statValue();
	private final JLabel itemSpread = statValue();
	private final JLabel itemPaper = statValue();
	private final JPanel itemQuotes = new JPanel();
	private JPanel itemPaperRow;
	private final JPanel itemSellBlock = new JPanel();
	private final JLabel itemAvg = statValue();
	private final JLabel itemBreakeven = statValue();
	private final JLabel itemVsBook = statValue();
	private final JLabel itemAdvice = new JLabel();
	private final JPanel itemHistory = new JPanel();
	private final JPanel itemHistoryList = new JPanel();
	private final JScrollPane itemHistoryScroll = new JScrollPane(itemHistoryList);
	private final JButton setIb = priceButton("Type instant buy");
	private final JButton setIs = priceButton("Type instant sell");
	private final JButton priceIbMinus = priceButton("Wiki buy −1");
	private final JButton priceIsPlus = priceButton("Wiki sell +1");
	private final JPanel priceActions = new JPanel(new GridLayout(2, 2, 4, 4));
	private final JButton tabSlots = tabButton("Offers");
	private final JButton tabFlip = tabButton("Next");
	private final JButton tabStats = tabButton("Profit");
	private final JButton tabItem = tabButton("Item");
	private final JButton qtyLimit = priceButton("GE limit");
	private final JButton qtyAll = priceButton("All");
	private final JButton qtyOne = priceButton("1");
	private final JPanel qtyActions = new JPanel(new GridLayout(1, 3, 4, 0));
	private final JLabel typeKeys = new JLabel();
	private final JPanel rotationBox = new JPanel();
	private final JPanel nextBox = new JPanel();
	private final JComboBox<RangeOpt> statsRangeBox = new JComboBox<>(STATS_RANGE_OPTS);
	private final JPanel statsHero = new JPanel();
	private final JPanel statsBox = new JPanel();
	private final JPanel statsPaper = new JPanel();
	private boolean ignoreRangePick;
	private final JLabel statsRealized = statValue();
	private final JLabel statsHour = statValue();
	private final JLabel statsTax = statValue();
	private final JLabel statsBuySpend = statValue();
	private final JLabel statsSellProceeds = statValue();
	private final JLabel statsBuys = statValue();
	private final JLabel statsSells = statValue();
	private final JLabel statsFills = statValue();
	private final JLabel statsWins = statValue();
	private final JLabel statsHours = statValue();
	private final JLabel statsPaperDump = statValue();
	private final JLabel statsPaperPatient = statValue();
	private String statsRange = "session";
	private Consumer<String> onStatsRange;
	private final JButton rotateBtn = linkButton("Add to rotation", () ->
	{
	});
	private final IconTextField itemSearch = new IconTextField();
	private final JPanel searchHits = new JPanel();
	private final JLabel searchEmpty = new JLabel();
	private int shownInspectId = Integer.MIN_VALUE;
	private Runnable itemPageOpen;
	private IntConsumer setOfferPrice;
	private IntConsumer setOfferQty;
	private IntConsumer lookupItem;
	private IntConsumer toggleRotation;
	private IntConsumer pickDeskItem;
	private Runnable refreshNext;
	private int[] rotationIds = new int[0];
	private int[] shownNextIds = new int[0];
	private String nextNote;
	private int pendingIb;
	private int pendingIs;
	private int pendingLimit;
	private int pendingAll;
	private Page page = Page.SLOTS;
	private boolean userPicked;
	private boolean ignoreSearch;
	private SlotView[] lastSlots;
	private ItemPage lastItem;
	private GeOfferInput.Kind lastInput = GeOfferInput.Kind.NONE;
	private ScheduledFuture<?> searchTask;
	private List<ItemPrice> shownHits = new ArrayList<>();

	@Inject
	OsrsMarketLedgerPanel(
		OsrsMarketLedgerConfig config,
		ConfigManager configManager,
		ItemManager itemManager,
		Client client,
		ClientThread clientThread,
		ScheduledExecutorService executor)
	{
		this.config = config;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.collapsedMask = readCollapsedMask();
		setIb.addActionListener(e -> typePrice(pendingIb));
		setIs.addActionListener(e -> typePrice(pendingIs));
		priceIbMinus.addActionListener(e -> typePrice(pendingIb > 1 ? pendingIb - 1 : 0));
		priceIsPlus.addActionListener(e -> typePrice(pendingIs > 0 ? pendingIs + 1 : 0));
		priceIbMinus.setToolTipText("Undercut instant buy by 1 gp — types IB−1 into Set a price");
		priceIsPlus.setToolTipText("Overcut instant sell by 1 gp — types IS+1 into Set a price");
		qtyLimit.addActionListener(e -> typeQty(pendingLimit));
		qtyAll.addActionListener(e -> typeQty(pendingAll));
		qtyOne.addActionListener(e -> typeQty(1));
		rotateBtn.addActionListener(e ->
		{
			if (lastItem != null && lastItem.itemId > 0 && toggleRotation != null)
			{
				toggleRotation.accept(lastItem.itemId);
			}
		});
		styleCombo(statsRangeBox);
		statsRangeBox.setToolTipText("How far back to count logged fills");
		statsRangeBox.addActionListener(e ->
		{
			if (ignoreRangePick)
			{
				return;
			}
			RangeOpt opt = (RangeOpt) statsRangeBox.getSelectedItem();
			if (opt == null)
			{
				return;
			}
			statsRange = opt.key;
			if (onStatsRange != null)
			{
				onStatsRange.accept(opt.key);
			}
		});
		tabSlots.addActionListener(e -> pick(Page.SLOTS));
		tabFlip.addActionListener(e -> pick(Page.FLIP));
		tabStats.addActionListener(e ->
		{
			pick(Page.STATS);
			if (onStatsRange != null)
			{
				onStatsRange.accept(statsRange);
			}
		});
		tabItem.addActionListener(e -> pick(Page.ITEM));
		bindSearch();
		MouseAdapter openPage = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1 && itemPageOpen != null)
				{
					itemPageOpen.run();
				}
			}
		};
		itemStripe.addMouseListener(openPage);
		itemIcon.addMouseListener(openPage);
		itemName.addMouseListener(openPage);
		itemStripe.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		itemIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		itemName.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		itemStripe.setToolTipText("Open this item on the website");

		setBorder(new EmptyBorder(10, SIDE_PAD, 12, SIDE_PAD));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		pages.setOpaque(false);
		stretch(pages);
		pages.add(buildSlotsPage(), CARD_SLOTS);
		pages.add(buildFlipPage(), CARD_FLIP);
		pages.add(buildStatsPage(), CARD_STATS);
		pages.add(buildItemPage(), CARD_ITEM);

		JPanel tabs = new JPanel(new GridLayout(1, 4, 3, 0));
		tabs.setOpaque(true);
		tabs.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tabs.setBorder(new EmptyBorder(3, 3, 3, 3));
		tabs.add(tabSlots);
		tabs.add(tabFlip);
		tabs.add(tabStats);
		tabs.add(tabItem);
		stretch(tabs);
		tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		add(tabs);
		add(Box.createVerticalStrut(10));

		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getDefaultFont().deriveFont(Font.BOLD, 16f));
		stretch(title);
		add(title);
		add(Box.createVerticalStrut(8));
		add(pages);
		paintTabs();

		showLoggedOut();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		if (!offersHooked && getScrollPane() != null)
		{
			offersHooked = true;
			getScrollPane().getViewport().addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentResized(ComponentEvent e)
				{
					fitOffers();
				}
			});
		}
		SwingUtilities.invokeLater(this::fitOffers);
	}

	/** Offers list fills whatever height the client sidebar has left under the overview. */
	private void fitOffers()
	{
		JScrollPane outer = getScrollPane();
		if (outer == null || slotsPage == null)
		{
			return;
		}
		int viewH = outer.getViewport().getHeight();
		if (viewH <= 0)
		{
			return;
		}
		int chrome = getInsets().top + getInsets().bottom + Math.max(0, getComponentCount() - 1) * 3;
		for (Component c : getComponents())
		{
			if (c != pages)
			{
				chrome += c.getPreferredSize().height;
			}
		}
		for (Component c : slotsPage.getComponents())
		{
			if (c != offersScroll)
			{
				chrome += c.getPreferredSize().height;
			}
		}
		int offersH = Math.max(96, viewH - chrome);
		int pageH = offersH;
		for (Component c : slotsPage.getComponents())
		{
			if (c != offersScroll)
			{
				pageH += c.getPreferredSize().height;
			}
		}
		boolean offersFit = offersScroll.getPreferredSize().height == offersH;
		boolean pageFit = page != Page.SLOTS
			|| (pages.isPreferredSizeSet() && pages.getPreferredSize().height == pageH);
		if (offersFit && pageFit)
		{
			return;
		}
		offersScroll.setPreferredSize(new Dimension(1, offersH));
		offersScroll.setMinimumSize(new Dimension(0, 96));
		offersScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, offersH));
		if (page == Page.SLOTS)
		{
			pages.setPreferredSize(new Dimension(1, pageH));
			pages.setMaximumSize(new Dimension(Integer.MAX_VALUE, pageH));
			revalidate();
		}
	}

	private JPanel buildSlotsPage()
	{
		JPanel page = new JPanel();
		slotsPage = page;
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setOpaque(false);

		JPanel stats = new JPanel(new GridLayout(2, 2, 8, 8));
		stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stats.setBorder(new EmptyBorder(10, 10, 10, 10));
		stretch(stats);
		stats.add(statCell("Slots used", openStat));
		stats.add(statCell("If sold now", paperStat));
		stats.add(statCell("Still buying", buyStat));
		stats.add(statCell("Still selling", sellStat));
		stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
		page.add(sectionLabel("Overview"));
		page.add(stats);
		page.add(Box.createVerticalStrut(8));

		hint.setFont(uiFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hintWrap.setOpaque(false);
		hintWrap.setBorder(new EmptyBorder(2, 4, 8, 4));
		hintWrap.add(hint, BorderLayout.CENTER);
		stretch(hintWrap);
		hintWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		page.add(hintWrap);
		page.add(sectionLabel("Your 8 offers"));

		offerList.setLayout(new BoxLayout(offerList, BoxLayout.Y_AXIS));
		offerList.setOpaque(false);
		for (int i = 0; i < cards.length; i++)
		{
			cards[i] = new SlotCard();
			stretch(cards[i]);
			offerList.add(cards[i]);
			offerList.add(Box.createVerticalStrut(6));
		}
		offersScroll.setBorder(BorderFactory.createEmptyBorder());
		offersScroll.setOpaque(false);
		offersScroll.getViewport().setOpaque(false);
		offersScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		offersScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		offersScroll.getVerticalScrollBar().setUnitIncrement(16);
		offersScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		offersScroll.setPreferredSize(new Dimension(1, 280));
		offersScroll.setMinimumSize(new Dimension(0, 96));
		offersScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
		page.add(offersScroll);

		JPanel links = linkRow();
		stretch(links);
		links.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		page.add(links);
		return page;
	}

	private JPanel buildFlipPage()
	{
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setOpaque(false);

		JLabel intro = new JLabel("<html>Buys that fit the coins in your inventory. Skips thin volume and stale quotes.</html>");
		intro.setFont(uiFont());
		intro.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		intro.setBorder(new EmptyBorder(0, 2, 2, 2));
		hugLabel(intro);
		page.add(intro);
		page.add(Box.createVerticalStrut(6));

		nextBox.setLayout(new BoxLayout(nextBox, BoxLayout.Y_AXIS));
		nextBox.setOpaque(false);
		stretch(nextBox);
		page.add(nextBox);
		page.add(Box.createVerticalStrut(10));

		rotationBox.setLayout(new BoxLayout(rotationBox, BoxLayout.Y_AXIS));
		rotationBox.setOpaque(false);
		stretch(rotationBox);
		page.add(rotationBox);
		page.add(Box.createVerticalGlue());
		return page;
	}

	private JPanel buildStatsPage()
	{
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setOpaque(false);

		page.add(sectionLabel("Time range"));
		styleCombo(statsRangeBox);
		statsRangeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsRangeBox.setMinimumSize(new Dimension(0, 28));
		statsRangeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		statsRangeBox.setPreferredSize(new Dimension(1, 28));
		page.add(statsRangeBox);
		page.add(Box.createVerticalStrut(10));
		selectRangeCombo(statsRange);

		styleCard(statsHero);
		JLabel realizedCap = new JLabel("Realized profit");
		realizedCap.setFont(uiFont());
		realizedCap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		realizedCap.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsRealized.setFont(FontManager.getDefaultFont().deriveFont(Font.BOLD, 18f));
		statsRealized.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsHero.add(realizedCap);
		statsHero.add(Box.createVerticalStrut(2));
		statsHero.add(statsRealized);
		statsHero.add(Box.createVerticalStrut(6));
		statsHero.add(kv("GP / hour", statsHour));
		statsHero.add(kv("Window", statsHours));
		hug(statsHero);
		page.add(statsHero);
		page.add(Box.createVerticalStrut(8));

		page.add(sectionLabel("Activity"));
		styleCard(statsBox);
		statsBox.add(kv("Fills logged", statsFills));
		statsBox.add(kv("Buys", statsBuys));
		statsBox.add(kv("Sells", statsSells));
		statsBox.add(kv("Winning sells", statsWins));
		statsBox.add(kv("Tax paid", statsTax));
		statsBox.add(kv("Spent on buys", statsBuySpend));
		statsBox.add(kv("Sell proceeds", statsSellProceeds));
		hug(statsBox);
		page.add(statsBox);
		page.add(Box.createVerticalStrut(8));

		page.add(sectionLabel("Open lots"));
		styleCard(statsPaper);
		statsPaper.add(kv(
			"If dumped now",
			statsPaperDump,
			"Paper P&L if you instantly sell all leftover lots at wiki instant-sell (low), after GE tax, minus your FIFO cost. Assumes you undercut to sell now."));
		statsPaper.add(kv(
			"If sold patient",
			statsPaperPatient,
			"Paper P&L if you sell leftover lots at wiki instant-buy (high), after GE tax, minus FIFO cost. Assumes you wait for a buyer at the higher price."));
		hug(statsPaper);
		page.add(statsPaper);
		page.add(Box.createVerticalGlue());
		fillStats(null);
		return page;
	}

	private JPanel buildItemPage()
	{
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setOpaque(false);

		itemSearch.setIcon(IconTextField.Icon.SEARCH);
		itemSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemSearch.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		itemSearch.setMinimumSize(new Dimension(0, 30));
		itemSearch.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 30));
		itemSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		itemSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemSearch.setToolTipText("Search a GE item for wiki buy and sell prices");
		page.add(itemSearch);
		page.add(Box.createVerticalStrut(6));

		searchHits.setLayout(new BoxLayout(searchHits, BoxLayout.Y_AXIS));
		searchHits.setOpaque(false);
		searchHits.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchHits.setVisible(false);
		hug(searchHits);
		page.add(searchHits);
		searchEmpty.setFont(uiFont());
		searchEmpty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		searchEmpty.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchEmpty.setVisible(false);
		hugLabel(searchEmpty);
		page.add(searchEmpty);
		page.add(Box.createVerticalStrut(6));

		itemStripe.setBackground(CARD);
		itemStripe.setBorder(cardAccent(ColorScheme.DARK_GRAY_COLOR));
		itemIcon.setPreferredSize(new Dimension(36, 36));
		itemName.setFont(valueFont());
		itemName.setForeground(ColorScheme.BRAND_ORANGE);
		itemHealth.setFont(uiFont());
		itemHealth.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel names = new JPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.setOpaque(false);
		itemName.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemHealth.setAlignmentX(Component.LEFT_ALIGNMENT);
		names.add(itemName);
		names.add(Box.createVerticalStrut(2));
		names.add(itemHealth);
		itemStripe.add(itemIcon, BorderLayout.WEST);
		itemStripe.add(names, BorderLayout.CENTER);
		stretch(itemStripe);
		itemStripe.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		page.add(itemStripe);
		page.add(Box.createVerticalStrut(4));

		itemOffer.setFont(uiFont());
		itemOffer.setForeground(Color.WHITE);
		hugLabel(itemOffer);
		page.add(itemOffer);
		page.add(Box.createVerticalStrut(6));

		page.add(sectionLabel("Wiki prices"));
		itemQuotes.setLayout(new BoxLayout(itemQuotes, BoxLayout.Y_AXIS));
		itemQuotes.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemQuotes.setBorder(new EmptyBorder(6, 8, 6, 8));
		itemQuotes.add(kv("Wiki buy", itemIb));
		itemQuotes.add(kv("Wiki sell", itemIs));
		itemQuotes.add(kv("After-tax spread", itemSpread));
		itemPaperRow = kv("If you sell now", itemPaper);
		itemQuotes.add(itemPaperRow);
		hug(itemQuotes);
		page.add(itemQuotes);
		page.add(Box.createVerticalStrut(4));

		priceActions.setOpaque(false);
		priceActions.add(setIb);
		priceActions.add(setIs);
		priceActions.add(priceIbMinus);
		priceActions.add(priceIsPlus);
		hug(priceActions);
		priceActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
		page.add(sectionLabel("Type a price"));
		page.add(priceActions);
		page.add(Box.createVerticalStrut(4));

		qtyActions.setOpaque(false);
		qtyActions.add(qtyLimit);
		qtyActions.add(qtyAll);
		qtyActions.add(qtyOne);
		hug(qtyActions);
		qtyActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		page.add(sectionLabel("Type a quantity"));
		page.add(qtyActions);
		page.add(Box.createVerticalStrut(4));
		typeKeys.setFont(uiFont());
		typeKeys.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hugLabel(typeKeys);
		page.add(typeKeys);
		page.add(Box.createVerticalStrut(4));

		itemSellBlock.setLayout(new BoxLayout(itemSellBlock, BoxLayout.Y_AXIS));
		itemSellBlock.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemSellBlock.setBorder(new EmptyBorder(6, 8, 6, 8));
		itemSellBlock.add(kv("Avg buy", itemAvg));
		itemSellBlock.add(kv("Break even", itemBreakeven));
		itemSellBlock.add(kv("Vs wiki", itemVsBook));
		hug(itemSellBlock);
		page.add(itemSellBlock);
		page.add(Box.createVerticalStrut(4));

		itemAdvice.setFont(uiFont());
		itemAdvice.setForeground(Color.WHITE);
		hugLabel(itemAdvice);
		page.add(itemAdvice);
		page.add(Box.createVerticalStrut(6));

		itemHistory.setLayout(new BoxLayout(itemHistory, BoxLayout.Y_AXIS));
		itemHistory.setOpaque(false);
		itemHistory.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemHistoryList.setLayout(new BoxLayout(itemHistoryList, BoxLayout.Y_AXIS));
		itemHistoryList.setOpaque(false);
		itemHistoryScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		itemHistoryScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		itemHistoryScroll.setBorder(BorderFactory.createEmptyBorder());
		itemHistoryScroll.setOpaque(false);
		itemHistoryScroll.getViewport().setOpaque(false);
		itemHistoryScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemHistoryScroll.getVerticalScrollBar().setUnitIncrement(HISTORY_ROW_H + HISTORY_GAP);
		fillHistory(0, new FillView[0]);
		page.add(itemHistory);
		page.add(Box.createVerticalStrut(4));

		page.add(legendLine(SlotMargin.Health.GOOD.color, "On the market"));
		page.add(legendLine(SlotMargin.Health.WARN.color, "Inside the spread, not at the edge"));
		page.add(legendLine(SlotMargin.Health.BAD.color, "Off-market — update the offer"));
		page.add(Box.createVerticalStrut(6));

		JButton open = linkButton("Open on website", () ->
		{
			if (itemPageOpen != null)
			{
				itemPageOpen.run();
			}
		});
		stretch(open);
		open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		page.add(open);
		page.add(Box.createVerticalStrut(4));
		rotateBtn.setToolTipText("Pin this item on today's rotation (up to 8)");
		stretch(rotateBtn);
		rotateBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		page.add(rotateBtn);
		page.add(Box.createVerticalStrut(4));
		JPanel links = linkRow();
		stretch(links);
		links.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		page.add(links);
		page.add(Box.createVerticalGlue());
		return page;
	}

	void showLoggedOut()
	{
		SwingUtilities.invokeLater(() ->
		{
			userPicked = false;
			page = Page.SLOTS;
			lastSlots = null;
			lastItem = null;
			lastInput = GeOfferInput.Kind.NONE;
			title.setText("Your offers");
			openStat.setText("—");
			paperStat.setText("—");
			buyStat.setText("—");
			sellStat.setText("—");
			setHint("Log in to see live offers. Fills still sync while you play.", false);
			rotationIds = new int[0];
			nextNote = null;
			fillNamedList(rotationBox, "Today's rotation", new NamedItem[0], "Open an item and tap Add to rotation (up to 8).", true);
			fillNextList(new NextFlip[0], 0);
			fillHistory(0, new FillView[0]);
			fillStats(null);
			rotateBtn.setText("Add to rotation");
			rotateBtn.setEnabled(false);
			for (int i = 0; i < cards.length; i++)
			{
				cards[i].showEmpty(i);
			}
			fillTypeButtons(null);
			paintTabs();
			showPage();
		});
	}

	void show(SlotView[] slots, ItemPage item, GeOfferInput.Kind input)
	{
		SwingUtilities.invokeLater(() -> applyAll(slots, item, input));
	}

	void setOnSetPrice(IntConsumer setOfferPrice)
	{
		this.setOfferPrice = setOfferPrice;
	}

	void setOnSetQty(IntConsumer setOfferQty)
	{
		this.setOfferQty = setOfferQty;
	}

	void showItemPage()
	{
		SwingUtilities.invokeLater(() ->
		{
			userPicked = true;
			page = Page.ITEM;
			paintTabs();
			showPage();
			scrollToTop();
		});
	}

	void setOnLookup(IntConsumer lookupItem)
	{
		this.lookupItem = lookupItem;
	}

	void setOnToggleRotation(IntConsumer toggleRotation)
	{
		this.toggleRotation = toggleRotation;
	}

	void setOnPickDeskItem(IntConsumer pickDeskItem)
	{
		this.pickDeskItem = pickDeskItem;
	}

	void setOnRefreshNext(Runnable refreshNext)
	{
		this.refreshNext = refreshNext;
	}

	void setOnStatsRange(Consumer<String> onStatsRange)
	{
		this.onStatsRange = onStatsRange;
	}

	String selectedStatsRange()
	{
		return statsRange;
	}

	int[] shownNextIds()
	{
		return shownNextIds.clone();
	}

	private void typePrice(int price)
	{
		if (price > 0 && setOfferPrice != null)
		{
			setOfferPrice.accept(price);
		}
	}

	private void typeQty(int qty)
	{
		if (qty > 0 && setOfferQty != null)
		{
			setOfferQty.accept(qty);
		}
	}

	private static JButton priceButton(String text)
	{
		JButton btn = linkButton(text, () ->
		{
		});
		btn.setToolTipText("Types this value into the GE chatbox when Set a price / How many is open");
		return btn;
	}

	private static JButton tabButton(String text)
	{
		JButton btn = new JButton(text);
		btn.setFont(uiFont());
		btn.setFocusable(false);
		btn.setOpaque(true);
		btn.setContentAreaFilled(true);
		btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btn.setBorder(new EmptyBorder(6, 2, 6, 2));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}

	private void pick(Page next)
	{
		userPicked = true;
		page = next;
		paintTabs();
		showPage();
		scrollToTop();
		if (next == Page.ITEM && lastItem != null)
		{
			fillItem(lastItem);
		}
		else if (next == Page.ITEM)
		{
			fillItemEmpty();
		}
		else if (next == Page.SLOTS && lastSlots != null)
		{
			fillSlots(lastSlots);
		}
	}

	private void applyAll(SlotView[] slots, ItemPage item, GeOfferInput.Kind input)
	{
		Page before = page;
		lastSlots = slots;
		lastItem = item;
		lastInput = input == null ? GeOfferInput.Kind.NONE : input;
		// Only clear sticky tab choice while on Offers — otherwise Profit/Flip
		// refreshes would flip the user back to Offers.
		if (item == null && lastInput == GeOfferInput.Kind.NONE && page == Page.SLOTS)
		{
			userPicked = false;
		}
		if (!userPicked)
		{
			if (item != null || lastInput != GeOfferInput.Kind.NONE)
			{
				page = Page.ITEM;
			}
			else
			{
				page = Page.SLOTS;
			}
		}
		if (slots != null)
		{
			fillSlots(slots);
		}
		if (item != null)
		{
			fillItem(item);
		}
		else
		{
			fillItemEmpty();
		}
		paintTabs();
		showPage();
		if (page != before)
		{
			scrollToTop();
		}
	}

	private void scrollToTop()
	{
		JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
		if (sp == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> sp.getViewport().setViewPosition(new Point(0, 0)));
	}

	private void showPage()
	{
		switch (page)
		{
			case ITEM:
				title.setText(lastItem == null ? "Look up an item" : lastItem.buy ? "Buy offer" : lastItem.sell ? "Sell offer" : lastItem.name);
				pagesLayout.show(pages, CARD_ITEM);
				break;
			case FLIP:
				title.setText("What to buy");
				pagesLayout.show(pages, CARD_FLIP);
				break;
			case STATS:
				title.setText("Your profit");
				pagesLayout.show(pages, CARD_STATS);
				break;
			default:
				title.setText("Your offers");
				pagesLayout.show(pages, CARD_SLOTS);
				break;
		}
		if (page == Page.SLOTS)
		{
			fitOffers();
		}
		else
		{
			pages.setPreferredSize(null);
			pages.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
			revalidate();
		}
	}

	private void paintTabs()
	{
		markTab(tabSlots, page == Page.SLOTS);
		markTab(tabFlip, page == Page.FLIP);
		markTab(tabStats, page == Page.STATS);
		markTab(tabItem, page == Page.ITEM);
	}

	private static void markTab(JButton tab, boolean on)
	{
		tab.setOpaque(true);
		tab.setContentAreaFilled(true);
		tab.setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		tab.setBackground(on ? TAB_ON : ColorScheme.DARKER_GRAY_COLOR);
		tab.setBorder(on
			? BorderFactory.createCompoundBorder(
				new MatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE),
				new EmptyBorder(6, 2, 4, 2))
			: new EmptyBorder(6, 2, 6, 2));
	}

	private void fillSlots(SlotView[] slots)
	{
		if (slots == null)
		{
			return;
		}
		int used = 0;
		long buying = 0;
		long selling = 0;
		long paper = 0;
		boolean anyPaper = false;
		int needUpdate = 0;
		int stalled = 0;
		for (int i = 0; i < cards.length && i < slots.length; i++)
		{
			SlotView slot = slots[i];
			cards[i].show(slot);
			if (slot.itemId <= 0)
			{
				continue;
			}
			if (slot.listed)
			{
				used += 1;
				if (slot.buy)
				{
					buying += slot.leftGp;
				}
				else
				{
					selling += slot.leftGp;
				}
				if (slot.paperEach != null)
				{
					anyPaper = true;
					paper += (long) slot.paperEach * Math.max(slot.qtyTotal - slot.qtyFilled, 0);
				}
				if (slot.stalled)
				{
					stalled += 1;
				}
			}
			if (slot.health == SlotMargin.Health.BAD)
			{
				needUpdate += 1;
			}
		}
		openStat.setText(used + " / 8");
		buyStat.setText(compact(buying));
		sellStat.setText(compact(selling));
		paperStat.setText(anyPaper ? signedCompact(paper) : "—");
		paperStat.setForeground(!anyPaper ? Color.WHITE : paper >= 0 ? PROFIT : LOSS);
		if (needUpdate > 0)
		{
			setHint(needUpdate + " offer" + (needUpdate == 1 ? " is" : "s are")
				+ " off-market. Open one to see a better price.", true);
		}
		else if (stalled > 0)
		{
			setHint(stalled + " offer" + (stalled == 1 ? " hasn't" : "s haven't")
				+ " moved. Open one to reprice or skip.", true);
		}
		else
		{
			setHint("Green edge means the offer is still in the wiki spread. Click a slot to inspect.", false);
		}
	}

	private void fillItem(ItemPage item)
	{
		if (item.itemId != shownInspectId)
		{
			shownInspectId = item.itemId;
			itemIcon.setIcon(null);
			if (item.itemId > 0)
			{
				itemManager.getImage(item.itemId).addTo(itemIcon);
			}
		}
		itemName.setText(item.name);
		Color accent = item.margin.health.color;
		itemStripe.setBorder(cardAccent(accent));
		itemHealth.setText(item.margin.health.label.isEmpty() ? "Wiki prices" : item.margin.health.label);
		itemHealth.setForeground(accent);
		itemOffer.setText("<html>" + esc(item.offerLine) + "</html>");
		hugLabel(itemOffer);
		setMoney(itemIb, item.margin.instantBuy, false);
		setMoney(itemIs, item.margin.instantSell, false);
		setMoneyLong(itemSpread, item.margin.spreadAfterTax, true);
		boolean showPaper = item.buy && item.margin.ifSellAtIb != null;
		itemPaperRow.setVisible(showPaper);
		if (showPaper)
		{
			setMoneyLong(itemPaper, item.margin.ifSellAtIb, true);
		}
		hug(itemQuotes);
		itemSellBlock.setVisible(item.sell && item.margin.avgCost > 0);
		if (item.sell && item.margin.avgCost > 0)
		{
			hug(itemSellBlock);
		}
		if (item.sell)
		{
			itemAvg.setText(item.margin.avgCost > 0 ? SlotMargin.gp(item.margin.avgCost) : "—");
			itemBreakeven.setText(item.margin.breakeven > 0 ? SlotMargin.gp(item.margin.breakeven) : "—");
			setMoneyLong(itemVsBook, item.margin.vsBook, true);
			if (item.margin.vsBook != null)
			{
				itemVsBook.setText(itemVsBook.getText() + "/ea");
			}
		}
		itemAdvice.setText("<html><body style='width:190px'>" + esc(item.margin.adjust) + "</body></html>");
		itemAdvice.setForeground(item.margin.health == SlotMargin.Health.BAD ? LOSS : Color.WHITE);
		hugLabel(itemAdvice);
		boolean onBoard = inRotation(item.itemId);
		rotateBtn.setEnabled(item.itemId > 0 && (onBoard || rotationIds.length < 8));
		rotateBtn.setText(onBoard ? "Remove from rotation" : "Add to rotation");
		itemPageOpen = () -> LinkBrowser.browse(OsrsMarketUrls.itemPage(item.itemId));
		fillTypeButtons(item);
		revalidate();
		repaint();
	}

	private void fillItemEmpty()
	{
		shownInspectId = Integer.MIN_VALUE;
		itemIcon.setIcon(null);
		itemName.setText("Find an item");
		itemHealth.setText("Search above, or click an offer");
		itemHealth.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		itemStripe.setBorder(cardAccent(ColorScheme.DARK_GRAY_COLOR));
		itemOffer.setText(" ");
		itemIb.setText("—");
		itemIs.setText("—");
		itemSpread.setText("—");
		itemPaperRow.setVisible(false);
		hug(itemQuotes);
		itemSellBlock.setVisible(false);
		itemAdvice.setText(" ");
		hugLabel(itemOffer);
		hugLabel(itemAdvice);
		itemPageOpen = null;
		rotateBtn.setText("Add to rotation");
		rotateBtn.setEnabled(false);
		fillTypeButtons(null);
		fillHistory(0, new FillView[0]);
	}

	void setItemHistory(int itemId, FillView[] fills)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (itemId > 0 && (lastItem == null || lastItem.itemId != itemId))
			{
				return;
			}
			fillHistory(itemId, fills == null ? new FillView[0] : fills);
		});
	}

	private void bindSearch()
	{
		itemSearch.addActionListener(e -> pickFirstHit());
		itemSearch.addClearListener(() ->
		{
			hideHits();
			itemSearch.setIcon(IconTextField.Icon.SEARCH);
			if (lookupItem != null)
			{
				lookupItem.accept(0);
			}
		});
		itemSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}
		});
	}

	private void scheduleSearch()
	{
		if (ignoreSearch)
		{
			return;
		}
		if (searchTask != null)
		{
			searchTask.cancel(false);
		}
		String query = itemSearch.getText() == null ? "" : itemSearch.getText().trim();
		if (query.length() < 2)
		{
			hideHits();
			itemSearch.setIcon(IconTextField.Icon.SEARCH);
			if (query.isEmpty() && lookupItem != null)
			{
				lookupItem.accept(0);
			}
			return;
		}
		itemSearch.setIcon(IconTextField.Icon.LOADING);
		searchTask = executor.schedule(() -> runSearch(query), 180, TimeUnit.MILLISECONDS);
	}

	private void runSearch(String query)
	{
		List<ItemPrice> found;
		try
		{
			found = itemManager.search(query);
		}
		catch (RuntimeException e)
		{
			SwingUtilities.invokeLater(() ->
			{
				itemSearch.setIcon(IconTextField.Icon.ERROR);
				hideHits();
			});
			return;
		}
		clientThread.invoke(() ->
		{
			List<ItemPrice> hits = rankHits(query, filterTradeable(found));
			SwingUtilities.invokeLater(() -> showHits(query, hits));
		});
	}

	static List<ItemPrice> rankHits(String query, List<ItemPrice> found)
	{
		String q = query.toLowerCase();
		List<ItemPrice> hits = new ArrayList<>();
		if (found != null)
		{
			hits.addAll(found);
		}
		hits.sort(Comparator
			.comparing((ItemPrice p) ->
			{
				String n = p.getName() == null ? "" : p.getName().toLowerCase();
				if (n.equals(q))
				{
					return 0;
				}
				if (n.startsWith(q))
				{
					return 1;
				}
				return 2;
			})
			.thenComparing(p -> p.getName() == null ? "" : p.getName(), String.CASE_INSENSITIVE_ORDER));
		if (hits.size() > SEARCH_HITS)
		{
			return new ArrayList<>(hits.subList(0, SEARCH_HITS));
		}
		return hits;
	}

	private List<ItemPrice> filterTradeable(List<ItemPrice> found)
	{
		List<ItemPrice> out = new ArrayList<>();
		if (found == null)
		{
			return out;
		}
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		for (ItemPrice price : found)
		{
			if (price == null || price.getId() <= 0 || price.getName() == null || price.getName().equals("null"))
			{
				continue;
			}
			if (loggedIn)
			{
				try
				{
					ItemComposition comp = itemManager.getItemComposition(price.getId());
					if (comp.getNote() != -1 || !comp.isGeTradeable())
					{
						continue;
					}
				}
				catch (RuntimeException e)
				{
					continue;
				}
			}
			out.add(price);
		}
		return out;
	}

	private void showHits(String query, List<ItemPrice> hits)
	{
		if (!query.equalsIgnoreCase(itemSearch.getText() == null ? "" : itemSearch.getText().trim()))
		{
			return;
		}
		shownHits = hits;
		searchHits.removeAll();
		if (hits.isEmpty())
		{
			itemSearch.setIcon(IconTextField.Icon.ERROR);
			searchEmpty.setText("No GE items match \"" + query + "\"");
			searchEmpty.setVisible(true);
			searchHits.setVisible(false);
			revalidate();
			repaint();
			return;
		}
		itemSearch.setIcon(IconTextField.Icon.SEARCH);
		searchEmpty.setVisible(false);
		for (ItemPrice hit : hits)
		{
			searchHits.add(hitRow(hit));
			searchHits.add(Box.createVerticalStrut(4));
		}
		searchHits.setVisible(true);
		searchHits.setMaximumSize(new Dimension(Integer.MAX_VALUE, hits.size() * 32));
		previewHit(hits.get(0));
		revalidate();
		repaint();
	}

	private JPanel hitRow(ItemPrice hit)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(CARD);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(24, 24));
		itemManager.getImage(hit.getId()).addTo(icon);
		JLabel name = new JLabel(hit.getName());
		name.setFont(uiFont());
		name.setForeground(Color.WHITE);
		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					pickHit(hit);
				}
			}
		});
		return row;
	}

	private void pickFirstHit()
	{
		if (shownHits.isEmpty())
		{
			scheduleSearch();
			return;
		}
		pickHit(shownHits.get(0));
	}

	private void previewHit(ItemPrice hit)
	{
		if (hit == null || lookupItem == null)
		{
			return;
		}
		userPicked = true;
		page = Page.ITEM;
		paintTabs();
		showPage();
		lookupItem.accept(hit.getId());
	}

	private void pickHit(ItemPrice hit)
	{
		ignoreSearch = true;
		itemSearch.setText(hit.getName());
		ignoreSearch = false;
		hideHits();
		itemSearch.setIcon(IconTextField.Icon.SEARCH);
		previewHit(hit);
	}

	private void hideHits()
	{
		shownHits = new ArrayList<>();
		searchHits.removeAll();
		searchHits.setVisible(false);
		searchEmpty.setVisible(false);
	}

	private void fillTypeButtons(ItemPage item)
	{
		pendingIb = item != null && item.margin.instantBuy != null ? item.margin.instantBuy : 0;
		pendingIs = item != null && item.margin.instantSell != null ? item.margin.instantSell : 0;
		pendingLimit = item == null ? 0 : item.geLimit;
		pendingAll = item == null ? 0 : item.allQty;
		boolean hasItem = item != null && item.itemId > 0;
		String allLabel = item != null && item.buy ? "Cash" : "Inv";
		setIb.setText(pendingIb > 0 ? "Buy " + compact(pendingIb) : "Buy now");
		setIs.setText(pendingIs > 0 ? "Sell " + compact(pendingIs) : "Sell now");
		priceIbMinus.setText("Buy −1");
		priceIsPlus.setText("Sell +1");
		setIb.setEnabled(pendingIb > 0);
		setIs.setEnabled(pendingIs > 0);
		priceIbMinus.setEnabled(pendingIb > 1);
		priceIsPlus.setEnabled(pendingIs > 0);
		qtyLimit.setText(pendingLimit > 0 ? "Limit " + compact(pendingLimit) : "Limit");
		qtyAll.setText(pendingAll > 0 ? allLabel + " " + compact(pendingAll) : allLabel);
		qtyLimit.setEnabled(pendingLimit > 0);
		qtyAll.setEnabled(pendingAll > 0);
		qtyOne.setEnabled(hasItem);
		typeKeys.setText("<html>Hotkeys: " + esc(config.wikiBuyHotkey().toString()) + " buy · "
			+ esc(config.wikiSellHotkey().toString()) + " sell · "
			+ esc(config.geLimitHotkey().toString()) + " limit · "
			+ esc(config.allQtyHotkey().toString()) + " all</html>");
		hugLabel(typeKeys);
	}

	private static void setMoney(JLabel label, Integer value, boolean signed)
	{
		if (value == null)
		{
			label.setText("—");
			label.setToolTipText(null);
			label.setForeground(Color.WHITE);
			return;
		}
		String full = signed ? SlotMargin.signed(value) : SlotMargin.gp(value);
		label.setText(signed ? signedCompact((long) value) : compact(value));
		label.setToolTipText(full);
		if (signed)
		{
			label.setForeground(value > 0 ? PROFIT : value < 0 ? LOSS : Color.WHITE);
		}
		else
		{
			label.setForeground(Color.WHITE);
		}
	}

	private static String esc(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void setHint(String text, boolean warn)
	{
		hint.setText("<html>" + text + "</html>");
		hint.setForeground(warn ? LOSS : ColorScheme.LIGHT_GRAY_COLOR);
	}

	void showNextWaiting(long cash)
	{
		noteNext(cash > 0 ? "Checking buys for " + compact(cash) + "…" : null, cash);
	}

	void noteNext(String note, long cash)
	{
		SwingUtilities.invokeLater(() ->
		{
			nextNote = note;
			if (shownNextIds.length == 0)
			{
				fillNextList(new NextFlip[0], cash);
			}
		});
	}

	void setDesk(NamedItem[] rotation, NextFlip[] next, long cash)
	{
		SwingUtilities.invokeLater(() ->
		{
			nextNote = null;
			rotationIds = idsOf(rotation);
			fillNamedList(rotationBox, "Today's rotation", rotation == null ? new NamedItem[0] : rotation,
				"Open an item and tap Add to rotation (up to 8).", true);
			fillNextList(next == null ? new NextFlip[0] : next, cash);
			if (lastItem != null)
			{
				boolean onBoard = inRotation(lastItem.itemId);
				rotateBtn.setEnabled(lastItem.itemId > 0 && (onBoard || rotationIds.length < 8));
				rotateBtn.setText(onBoard ? "Remove from rotation" : "Add to rotation");
			}
			revalidate();
			repaint();
		});
	}

	void setStats(StatsPnl stats)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (stats != null && stats.range != null)
			{
				statsRange = stats.range;
			}
			selectRangeCombo(statsRange);
			fillStats(stats);
			if (page == Page.STATS)
			{
				hug(statsHero);
				hug(statsBox);
				hug(statsPaper);
			}
			revalidate();
			repaint();
		});
	}

	private void selectRangeCombo(String range)
	{
		String key = range == null || range.isEmpty() ? "session" : range;
		ignoreRangePick = true;
		try
		{
			for (int i = 0; i < statsRangeBox.getItemCount(); i++)
			{
				RangeOpt opt = statsRangeBox.getItemAt(i);
				if (opt != null && key.equals(opt.key))
				{
					statsRangeBox.setSelectedIndex(i);
					return;
				}
			}
		}
		finally
		{
			ignoreRangePick = false;
		}
	}

	private void fillStats(StatsPnl stats)
	{
		if (stats == null)
		{
			statsRealized.setText("—");
			statsHour.setText("—");
			statsTax.setText("—");
			statsBuySpend.setText("—");
			statsSellProceeds.setText("—");
			statsFills.setText("—");
			statsBuys.setText("—");
			statsSells.setText("—");
			statsWins.setText("—");
			statsHours.setText("—");
			statsPaperDump.setText("—");
			statsPaperPatient.setText("—");
			statsRealized.setForeground(Color.WHITE);
			statsHour.setForeground(Color.WHITE);
			statsPaperDump.setForeground(Color.WHITE);
			statsPaperPatient.setForeground(Color.WHITE);
			return;
		}
		setMoneyLong(statsRealized, stats.realized, true);
		setMoneyLong(statsHour, stats.gpPerHour, true);
		setMoneyLong(statsTax, stats.taxPaid, false);
		setMoneyLong(statsBuySpend, stats.buySpend, false);
		setMoneyLong(statsSellProceeds, stats.sellProceeds, false);
		statsFills.setText(compact(stats.fillCount));
		statsFills.setForeground(Color.WHITE);
		statsBuys.setText(compact(stats.buyCount) + " · " + compact(stats.buyQty));
		statsBuys.setForeground(Color.WHITE);
		statsSells.setText(compact(stats.sellCount) + " · " + compact(stats.sellQty));
		statsSells.setForeground(Color.WHITE);
		if (stats.sellCount > 0)
		{
			statsWins.setText(compact(stats.winCount) + " / " + compact(stats.sellCount));
		}
		else
		{
			statsWins.setText("—");
		}
		statsWins.setForeground(Color.WHITE);
		statsHours.setText(formatHours(stats.hours));
		statsHours.setForeground(Color.WHITE);
		setMoneyLong(statsPaperDump, stats.paperDump, true);
		setMoneyLong(statsPaperPatient, stats.paperPatient, true);
	}

	private static void setMoneyLong(JLabel label, Long value, boolean signed)
	{
		if (value == null)
		{
			label.setText("—");
			label.setToolTipText(null);
			label.setForeground(Color.WHITE);
			return;
		}
		label.setText(signed ? signedCompact(value) : compact(value));
		label.setToolTipText(QuantityFormatter.formatNumber(value) + " gp");
		if (signed)
		{
			label.setForeground(value > 0 ? PROFIT : value < 0 ? LOSS : Color.WHITE);
		}
		else
		{
			label.setForeground(Color.WHITE);
		}
	}

	private static String formatHours(double hours)
	{
		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + "m";
		}
		if (hours < 48)
		{
			return trimDecimal(hours) + "h";
		}
		return trimDecimal(hours / 24.0) + "d";
	}

	private boolean inRotation(int itemId)
	{
		for (int id : rotationIds)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		return false;
	}

	private static int[] idsOf(NamedItem[] items)
	{
		if (items == null)
		{
			return new int[0];
		}
		int[] ids = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i] == null ? 0 : items[i].id;
		}
		return ids;
	}

	private static int[] idsOf(NextFlip[] items)
	{
		if (items == null)
		{
			return new int[0];
		}
		int[] ids = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i] == null ? 0 : items[i].id;
		}
		return ids;
	}

	private void fillHistory(int itemId, FillView[] fills)
	{
		itemHistory.removeAll();
		itemHistoryList.removeAll();
		String title = itemId > 0 && fills.length > 0
			? "Recent fills · " + fills.length
			: "Recent fills";
		itemHistory.add(deskHead(title));
		if (itemId <= 0)
		{
			JLabel cap = new JLabel("Search or click an offer to see fills.");
			cap.setFont(uiFont());
			cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			hugLabel(cap);
			itemHistory.add(cap);
		}
		else if (fills.length == 0)
		{
			JLabel cap = new JLabel("No fills logged for this item yet.");
			cap.setFont(uiFont());
			cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			hugLabel(cap);
			itemHistory.add(cap);
		}
		else
		{
			int n = Math.min(fills.length, 40);
			for (int i = 0; i < n; i++)
			{
				itemHistoryList.add(historyRow(fills[i]));
				if (i < n - 1)
				{
					itemHistoryList.add(Box.createVerticalStrut(HISTORY_GAP));
				}
			}
			int visible = Math.min(n, HISTORY_VISIBLE);
			int viewportH = visible * HISTORY_ROW_H + Math.max(0, visible - 1) * HISTORY_GAP;
			itemHistoryScroll.setPreferredSize(new Dimension(1, viewportH));
			itemHistoryScroll.setMinimumSize(new Dimension(0, viewportH));
			itemHistoryScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, viewportH));
			itemHistoryScroll.getVerticalScrollBar().setValue(0);
			itemHistory.add(itemHistoryScroll);
			if (fills.length > n)
			{
				JLabel more = new JLabel("+" + (fills.length - n) + " more on the ledger page");
				more.setFont(uiFont());
				more.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				hugLabel(more);
				itemHistory.add(more);
			}
		}
		hug(itemHistory);
		itemHistory.revalidate();
		itemHistory.repaint();
	}

	private JPanel historyRow(FillView fill)
	{
		Color stripe = "sell".equals(fill.side)
			? ColorScheme.BRAND_ORANGE
			: "craft".equals(fill.side)
				? GOLD
				: BUY_BLUE;
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD);
		row.setOpaque(true);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, stripe),
			new EmptyBorder(4, 6, 4, 6)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, HISTORY_ROW_H));
		row.setPreferredSize(new Dimension(1, HISTORY_ROW_H));

		String side = "sell".equals(fill.side)
			? "Sell"
			: "craft".equals(fill.side)
				? "Craft"
				: "Buy";
		JLabel line = new JLabel(side + " " + compact(fill.qty) + " @ " + compact(fill.priceEach));
		line.setFont(uiFont());
		line.setForeground(Color.WHITE);

		JLabel when = new JLabel(ago(fill.filledAtMs));
		when.setFont(uiFont());
		when.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		when.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(line);
		text.add(when);
		row.add(text, BorderLayout.CENTER);

		if (fill.realized != null && "sell".equals(fill.side))
		{
			JLabel pnl = new JLabel(signedCompact(fill.realized));
			pnl.setFont(valueFont());
			pnl.setForeground(fill.realized > 0 ? PROFIT : fill.realized < 0 ? LOSS : Color.WHITE);
			row.add(pnl, BorderLayout.EAST);
		}
		row.setToolTipText(SlotMargin.gp(fill.priceEach) + " · " + fill.qty + " × "
			+ (fill.realized == null ? side.toLowerCase() : SlotMargin.signed(fill.realized)));
		return row;
	}

	private static String ago(long at)
	{
		if (at <= 0)
		{
			return "";
		}
		long sec = Math.max(0, (System.currentTimeMillis() - at) / 1000);
		if (sec < 60)
		{
			return sec + "s ago";
		}
		if (sec < 3600)
		{
			return (sec / 60) + "m ago";
		}
		if (sec < 86400)
		{
			return (sec / 3600) + "h ago";
		}
		long days = sec / 86400;
		return days + "d ago";
	}

	private void fillNamedList(JPanel box, String title, NamedItem[] items, String empty, boolean remove)
	{
		box.removeAll();
		box.add(deskHead(title));
		if (items.length == 0)
		{
			JLabel cap = new JLabel("<html>" + esc(empty) + "</html>");
			cap.setFont(uiFont());
			cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			hugLabel(cap);
			box.add(cap);
		}
		else
		{
			for (NamedItem item : items)
			{
				if (item != null && item.id > 0)
				{
					box.add(deskRow(item.id, item.name, null, null, remove));
					box.add(Box.createVerticalStrut(3));
				}
			}
		}
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			Math.max(24, box.getPreferredSize().height + 4)));
		box.revalidate();
	}

	private void fillNextList(NextFlip[] items, long cash)
	{
		shownNextIds = idsOf(items);
		nextBox.removeAll();
		nextBox.add(nextHeadRow(cash > 0 ? "Suggested · " + compact(cash) : "Suggested buys"));
		if (items.length == 0)
		{
			String empty = nextNote != null ? nextNote
				: cash <= 0
				? "Add coins to your inventory to see buy ideas."
				: "Nothing at instant-buy fits " + compact(cash) + " right now.";
			JLabel cap = new JLabel("<html>" + empty + "</html>");
			cap.setFont(uiFont());
			cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			hugLabel(cap);
			nextBox.add(cap);
		}
		else
		{
			for (NextFlip item : items)
			{
				long made = item.cashProfit > 0 ? item.cashProfit : (long) item.profit * item.qtyAfford;
				String detail = compact(item.qtyAfford) + " you can buy · " + signedCompact(made);
				Color tone = made > 0 ? PROFIT : made < 0 ? LOSS : ColorScheme.LIGHT_GRAY_COLOR;
				nextBox.add(deskRow(item.id, item.name, detail, tone, false));
				nextBox.add(Box.createVerticalStrut(3));
			}
		}
		nextBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			Math.max(24, nextBox.getPreferredSize().height + 4)));
		nextBox.revalidate();
	}

	private JPanel nextHeadRow(String title)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel head = deskHead(title);
		head.setBorder(new EmptyBorder(2, 2, 4, 0));
		row.add(head, BorderLayout.CENTER);
		JButton refresh = linkButton("Refresh", () ->
		{
			if (refreshNext != null)
			{
				refreshNext.run();
			}
		});
		refresh.setToolTipText("Reload buy ideas for the coins in your inventory");
		refresh.setEnabled(refreshNext != null);
		refresh.setMaximumSize(new Dimension(72, 22));
		row.add(refresh, BorderLayout.EAST);
		hug(row);
		return row;
	}

	private static JLabel deskHead(String title)
	{
		JLabel head = new JLabel(title);
		head.setFont(uiFont());
		head.setForeground(ColorScheme.BRAND_ORANGE);
		head.setBorder(new EmptyBorder(2, 2, 4, 0));
		hugLabel(head);
		return head;
	}

	private JPanel deskRow(int itemId, String name, String detail, Color detailColor, boolean remove)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(CARD);
		row.setOpaque(true);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(4, 6, 4, 6)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, DESK_ROW_H));
		row.setPreferredSize(new Dimension(1, DESK_ROW_H));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText("Click to inspect this item");

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(DESK_ICON, DESK_ICON));
		icon.setMinimumSize(new Dimension(DESK_ICON, DESK_ICON));
		icon.setOpaque(false);
		if (itemId > 0)
		{
			itemManager.getImage(itemId).addTo(icon);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel cap = new JLabel(name);
		cap.setFont(uiFont());
		cap.setForeground(Color.WHITE);
		cap.setAlignmentX(Component.LEFT_ALIGNMENT);
		cap.setVerticalAlignment(SwingConstants.TOP);
		text.add(cap);
		if (detail != null && !detail.isEmpty())
		{
			JLabel meta = new JLabel(detail);
			meta.setFont(uiFont());
			meta.setForeground(detailColor == null ? ColorScheme.LIGHT_GRAY_COLOR : detailColor);
			meta.setAlignmentX(Component.LEFT_ALIGNMENT);
			meta.setVerticalAlignment(SwingConstants.TOP);
			text.add(meta);
		}

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);

		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				java.awt.Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row);
				if (!row.contains(p))
				{
					row.setBackground(CARD);
				}
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1 && pickDeskItem != null)
				{
					pickDeskItem.accept(itemId);
				}
			}
		};
		row.addMouseListener(hover);
		icon.addMouseListener(hover);
		cap.addMouseListener(hover);
		for (Component kid : text.getComponents())
		{
			if (kid != cap)
			{
				kid.addMouseListener(hover);
			}
		}

		if (remove)
		{
			JButton drop = new JButton("×");
			drop.setFont(uiFont());
			drop.setFocusable(false);
			drop.setOpaque(false);
			drop.setContentAreaFilled(false);
			drop.setBorder(new EmptyBorder(0, 4, 0, 0));
			drop.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			drop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			drop.setToolTipText("Remove from rotation");
			drop.addActionListener(e ->
			{
				if (toggleRotation != null)
				{
					toggleRotation.accept(itemId);
				}
			});
			row.add(drop, BorderLayout.EAST);
		}
		return row;
	}

	private static JPanel kv(String label, JLabel value)
	{
		return kv(label, value, null);
	}

	private static JPanel kv(String label, JLabel value, String tip)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		JLabel cap = new JLabel(label);
		cap.setFont(uiFont());
		cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cap.setPreferredSize(new Dimension(KV_LABEL_W, KV_ROW_H));
		cap.setMinimumSize(new Dimension(KV_LABEL_W, KV_ROW_H));
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		value.setMinimumSize(new Dimension(0, KV_ROW_H));

		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = 0;
		left.weightx = 0;
		left.anchor = GridBagConstraints.WEST;
		left.insets = new Insets(1, 0, 1, 8);
		row.add(cap, left);

		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = 0;
		right.weightx = 1;
		right.fill = GridBagConstraints.HORIZONTAL;
		right.anchor = GridBagConstraints.EAST;
		right.insets = new Insets(1, 0, 1, 0);
		row.add(value, right);

		if (tip != null && !tip.isEmpty())
		{
			row.setToolTipText(tip);
			cap.setToolTipText(tip);
			// Value tip is set later to the exact GP figure; keep the explain text on the label/row.
		}

		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, KV_ROW_H + 2));
		return row;
	}

	private JPanel legendLine(Color color, String text)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel swatch = new JLabel("●");
		swatch.setForeground(color);
		swatch.setFont(uiFont());
		JLabel cap = new JLabel("<html>" + esc(text) + "</html>");
		cap.setFont(uiFont());
		cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(swatch, BorderLayout.WEST);
		row.add(cap, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H * 2));
		return row;
	}

	private JPanel linkRow()
	{
		JPanel links = new JPanel(new GridLayout(1, 2, 6, 0));
		links.setOpaque(false);
		links.add(linkButton("Ledger", () -> LinkBrowser.browse(OsrsMarketUrls.ledgerPage())));
		links.add(linkButton("Tracker", () -> LinkBrowser.browse(OsrsMarketUrls.trackerPage())));
		return links;
	}

	private static CompoundBorder cardAccent(Color accent)
	{
		return BorderFactory.createCompoundBorder(
			new MatteBorder(0, 3, 0, 0, accent),
			new EmptyBorder(8, 8, 8, 8)
		);
	}

	private static JPanel statCell(String label, JLabel value)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setOpaque(false);
		JLabel cap = new JLabel(label);
		cap.setFont(uiFont());
		cap.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		cap.setAlignmentX(Component.LEFT_ALIGNMENT);
		cell.add(cap);
		cell.add(value);
		return cell;
	}

	private static JLabel statValue()
	{
		JLabel value = new JLabel("—");
		value.setForeground(Color.WHITE);
		value.setFont(valueFont());
		return value;
	}

	private static JButton linkButton(String text, Runnable action)
	{
		JButton btn = new JButton(text);
		btn.setFont(uiFont());
		btn.setFocusable(false);
		btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btn.setForeground(Color.WHITE);
		btn.setBorder(new EmptyBorder(5, 8, 5, 8));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addActionListener(e -> action.run());
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (btn.isEnabled())
				{
					btn.setBackground(HOVER);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
		return btn;
	}

	private static void styleCard(JPanel panel)
	{
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel head = new JLabel(text);
		head.setFont(valueFont());
		head.setForeground(ColorScheme.BRAND_ORANGE);
		head.setBorder(new EmptyBorder(0, 2, 6, 2));
		hugLabel(head);
		return head;
	}

	private static void styleCombo(JComboBox<?> combo)
	{
		combo.setFont(uiFont());
		combo.setFocusable(false);
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
		combo.setMaximumRowCount(8);
		combo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setFont(uiFont());
				setOpaque(true);
				setBorder(new EmptyBorder(4, 8, 4, 8));
				setBackground(isSelected ? HOVER : ColorScheme.DARKER_GRAY_COLOR);
				setForeground(Color.WHITE);
				return this;
			}
		});
	}

	private static Font uiFont()
	{
		return FontManager.getDefaultFont().deriveFont(UI_SIZE);
	}

	private static Font valueFont()
	{
		return FontManager.getDefaultFont().deriveFont(Font.BOLD, UI_SIZE);
	}

	private static int wrapWidth()
	{
		return Math.max(120, PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH - SIDE_PAD * 2);
	}

	private static void hugLabel(JLabel label)
	{
		hugLabel(label, wrapWidth());
	}

	private static void hugLabel(JLabel label, int width)
	{
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setVerticalAlignment(SwingConstants.TOP);
		String text = label.getText();
		boolean empty = text == null || text.isBlank();
		if (!empty)
		{
			String plain = text.replaceAll("(?i)<br\\s*/?>", "\n")
				.replaceAll("(?i)<[^>]+>", " ")
				.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.trim();
			empty = plain.isEmpty();
		}
		if (empty)
		{
			label.setVisible(false);
			label.setMinimumSize(new Dimension(0, 0));
			label.setPreferredSize(new Dimension(0, 0));
			label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
			return;
		}
		label.setVisible(true);
		int insets = 0;
		if (label.getBorder() != null)
		{
			Insets in = label.getBorder().getBorderInsets(label);
			insets = in.top + in.bottom;
			width = Math.max(80, width - in.left - in.right);
		}
		int line = Math.max(ROW_H, label.getFontMetrics(label.getFont()).getHeight() + 2);
		int h = line;
		if (text.regionMatches(true, 0, "<html>", 0, 6))
		{
			BasicHTML.updateRenderer(label, text);
			label.setSize(width, Short.MAX_VALUE);
			View view = (View) label.getClientProperty(BasicHTML.propertyKey);
			if (view != null)
			{
				view.setSize(width, 0);
				h = Math.max(h, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)));
			}
			else
			{
				h = Math.max(h, label.getPreferredSize().height);
			}
		}
		else
		{
			h = Math.max(h, label.getPreferredSize().height);
		}
		h = Math.min(h + insets + 2, line * 8 + insets);
		label.setMinimumSize(new Dimension(0, h));
		label.setPreferredSize(new Dimension(width, h));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		label.setSize(width, h);
	}

	private static void stretch(JPanel panel)
	{
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
	}

	private static void hug(JPanel panel)
	{
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 8));
	}

	private static void stretch(JLabel label)
	{
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		int h = Math.max(ROW_H, label.getFontMetrics(label.getFont()).getHeight() + 4);
		label.setMinimumSize(new Dimension(0, h));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
	}

	private static void stretch(JButton button)
	{
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	private static void stretch(SlotCard card)
	{
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	private int readCollapsedMask()
	{
		String raw = configManager.getConfiguration("osrsmarketledger", COLLAPSED_KEY);
		if (raw == null || raw.isEmpty())
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(raw.trim()) & COLLAPSED_ALL;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private boolean slotCollapsed(int index)
	{
		if (index < 0 || index >= 8)
		{
			return true;
		}
		return (collapsedMask & (1 << index)) != 0;
	}

	private void setSlotCollapsed(int index, boolean collapsed)
	{
		if (index < 0 || index >= 8)
		{
			return;
		}
		if (collapsed)
		{
			collapsedMask |= 1 << index;
		}
		else
		{
			collapsedMask &= ~(1 << index);
		}
		configManager.setConfiguration("osrsmarketledger", COLLAPSED_KEY, Integer.toString(collapsedMask));
	}

	/** Compact million-plus counts so 9-digit quantities fit the slot column. */
	private static String slotCount(int n, boolean compactEarly)
	{
		if (Math.abs((long) n) >= (compactEarly ? 10_000L : 1_000_000L))
		{
			return compact(n);
		}
		return QuantityFormatter.formatNumber(n);
	}

	private static String compact(long n)
	{
		long a = Math.abs(n);
		if (a < 10_000)
		{
			return QuantityFormatter.formatNumber(n);
		}
		String body;
		if (a < 1_000_000)
		{
			body = trimDecimal(a / 1_000.0) + "k";
		}
		else if (a < 1_000_000_000)
		{
			body = trimDecimal(a / 1_000_000.0) + "m";
		}
		else
		{
			body = trimDecimal(a / 1_000_000_000.0) + "b";
		}
		return n < 0 ? "-" + body : body;
	}

	private static String trimDecimal(double n)
	{
		String s = String.format(java.util.Locale.US, "%.1f", n);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String signedCompact(Integer n)
	{
		return n == null ? "" : signedCompact((long) n);
	}

	private static String signedCompact(long n)
	{
		String body = compact(Math.abs(n));
		return n > 0 ? "+" + body : n < 0 ? "-" + body : body;
	}

	private class SlotCard extends JPanel
	{
		private int shownItemId = Integer.MIN_VALUE;
		private SlotView lastSlot;
		private int hoverDepth;
		private final JPanel summary = new JPanel(new BorderLayout(8, 0));
		private final JPanel copy = new JPanel();
		private final JPanel extra = new JPanel();
		private final JLabel icon = new JLabel();
		private final JLabel status = new JLabel();
		private final JButton toggle = new JButton();
		private final JLabel name = new JLabel();
		private final JLabel fill = new JLabel();
		private final JLabel meta = new JLabel();
		private final JLabel idle = new JLabel();
		private final JLabel quotes = new JLabel();
		private final JLabel paper = new JLabel();
		private final ThinProgressBar bar = new ThinProgressBar();
		private final JPanel barWrap = new JPanel(new BorderLayout());
		private final JPanel footer = new JPanel(new BorderLayout(6, 0));
		private MouseAdapter itemClick;
		private final MouseAdapter foldClick = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					toggleCollapsed();
				}
			}
		};

		SlotCard()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(CARD);
			setOpaque(true);
			setBorder(cardBorder(ColorScheme.DARK_GRAY_COLOR, false));

			icon.setPreferredSize(new Dimension(32, 32));
			icon.setMinimumSize(new Dimension(32, 32));
			icon.setMaximumSize(new Dimension(32, 32));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			icon.setVerticalAlignment(SwingConstants.CENTER);

			toggle.setFont(FontManager.getDefaultFont().deriveFont(Font.BOLD, 16f));
			toggle.setForeground(Color.WHITE);
			toggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			toggle.setOpaque(true);
			toggle.setContentAreaFilled(true);
			toggle.setBorderPainted(false);
			toggle.setFocusPainted(false);
			toggle.setFocusable(false);
			toggle.setMargin(new Insets(0, 0, 0, 0));
			toggle.setBorder(new EmptyBorder(0, 0, 0, 0));
			toggle.setPreferredSize(new Dimension(SLOT_TOGGLE, SLOT_TOGGLE));
			toggle.setMinimumSize(new Dimension(SLOT_TOGGLE, SLOT_TOGGLE));
			toggle.setMaximumSize(new Dimension(SLOT_TOGGLE, SLOT_TOGGLE));
			toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			toggle.addActionListener(e -> toggleCollapsed());
			toggle.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					toggle.setBackground(HOVER);
					enterHover();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					toggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					exitHover();
				}
			});

			status.setFont(uiFont());
			name.setFont(uiFont());
			fill.setFont(uiFont());
			meta.setFont(uiFont());
			quotes.setFont(uiFont());
			paper.setFont(uiFont());
			idle.setFont(uiFont());
			name.setForeground(ColorScheme.BRAND_ORANGE);
			fill.setForeground(Color.WHITE);
			meta.setForeground(Color.WHITE);
			quotes.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			idle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			paper.setHorizontalAlignment(SwingConstants.RIGHT);
			status.setAlignmentX(Component.LEFT_ALIGNMENT);
			name.setAlignmentX(Component.LEFT_ALIGNMENT);
			fill.setAlignmentX(Component.LEFT_ALIGNMENT);
			meta.setAlignmentX(Component.LEFT_ALIGNMENT);
			idle.setAlignmentX(Component.LEFT_ALIGNMENT);

			copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
			copy.setOpaque(false);
			copy.add(status);
			copy.add(name);
			copy.add(fill);

			summary.setOpaque(true);
			summary.setBackground(CARD);
			summary.add(icon, BorderLayout.WEST);
			summary.add(copy, BorderLayout.CENTER);
			summary.add(toggle, BorderLayout.EAST);
			summary.setAlignmentX(Component.LEFT_ALIGNMENT);
			Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
			summary.setCursor(hand);
			status.setCursor(hand);
			fill.setCursor(hand);
			copy.setCursor(hand);
			summary.addMouseListener(foldClick);
			status.addMouseListener(foldClick);
			fill.addMouseListener(foldClick);
			copy.addMouseListener(foldClick);
			armHover(summary);
			armHover(copy);
			armHover(status);
			armHover(fill);
			armHover(icon);
			armHover(name);

			barWrap.setOpaque(false);
			barWrap.setBorder(new EmptyBorder(5, 0, 5, 0));
			bar.setPreferredSize(new Dimension(1, SLOT_BAR_H));
			bar.setMinimumSize(new Dimension(1, SLOT_BAR_H));
			bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, SLOT_BAR_H));
			barWrap.add(bar, BorderLayout.CENTER);
			barWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
			barWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, SLOT_BAR_H + 10));

			footer.setOpaque(false);
			footer.add(quotes, BorderLayout.CENTER);
			footer.add(paper, BorderLayout.EAST);
			footer.setAlignmentX(Component.LEFT_ALIGNMENT);

			extra.setLayout(new BoxLayout(extra, BoxLayout.Y_AXIS));
			extra.setOpaque(false);
			extra.setAlignmentX(Component.LEFT_ALIGNMENT);
			extra.add(meta);
			extra.add(idle);
			extra.add(barWrap);
			extra.add(footer);

			add(summary);
			add(extra);
		}

		void showEmpty(int index)
		{
			show(SlotView.empty(index));
		}

		void show(SlotView slot)
		{
			lastSlot = slot;
			Color accent = slot.itemId > 0 && slot.health != null
				? slot.health.color
				: ColorScheme.DARK_GRAY_COLOR;
			boolean empty = slot.itemId <= 0;
			boolean collapsed = !empty && slotCollapsed(slot.index);
			setBackground(empty ? CARD_EMPTY : CARD);
			summary.setBackground(hoverDepth > 0 && !empty ? HOVER : empty ? CARD_EMPTY : CARD);
			setBorder(cardBorder(accent, empty));
			setToolTipText(null);
			clearTooltips(this);

			if (slot.itemId != shownItemId)
			{
				shownItemId = slot.itemId;
				icon.setIcon(null);
				if (slot.itemId > 0)
				{
					itemManager.getImage(slot.itemId).addTo(icon);
				}
			}

			bindInspect(empty ? 0 : slot.itemId);

			if (empty)
			{
				toggle.setVisible(false);
				icon.setVisible(false);
				name.setVisible(false);
				fill.setVisible(false);
				extra.setVisible(false);
				status.setText("Slot " + (slot.index + 1) + " empty");
				status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				status.setCursor(Cursor.getDefaultCursor());
				summary.setCursor(Cursor.getDefaultCursor());
				setCardHeight(32);
				return;
			}

			toggle.setVisible(true);
			toggle.setText(collapsed ? "+" : "−");
			toggle.setToolTipText(collapsed ? "Show details" : "Hide details");
			icon.setVisible(true);
			icon.setToolTipText("Open item");
			name.setVisible(true);
			name.setToolTipText("Open item");
			fill.setVisible(true);
			summary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			status.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			fill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			summary.setToolTipText(collapsed
				? "Click to show details. Click the item to inspect."
				: "Click the side or − to hide details. Click the item to inspect.");
			status.setToolTipText(summary.getToolTipText());
			fill.setToolTipText(QuantityFormatter.formatNumber(slot.qtyFilled)
				+ " of "
				+ QuantityFormatter.formatNumber(slot.qtyTotal)
				+ " @ "
				+ QuantityFormatter.formatNumber(slot.priceEach));
			paintStatus(slot.stateLabel);
			name.setText(wrapSlotHtml(slot.name));

			if (collapsed)
			{
				status.setText(wrapSlotHtml(slot.stateLabel));
				fill.setText(wrapSlotHtml(
					slotCount(slot.qtyFilled, true)
						+ " / "
						+ slotCount(slot.qtyTotal, true)
						+ " @ "
						+ compact(slot.priceEach)));
				extra.setVisible(false);
				reflowSlotCard(true, false);
				return;
			}

			status.setText(wrapSlotHtml(
				slot.stateLabel + (slot.healthLabel.isEmpty() ? "" : " · " + slot.healthLabel)));
			fill.setText(wrapSlotHtml(
				slotCount(slot.qtyFilled, false)
					+ " of "
					+ slotCount(slot.qtyTotal, false)
					+ " filled"));
			meta.setText(wrapSlotHtml(compact(slot.priceEach) + " each · " + compact(slot.leftGp) + " left"));
			idle.setText(wrapSlotHtml(slot.idleLabel));
			idle.setForeground(slot.stalled ? LOSS : ColorScheme.LIGHT_GRAY_COLOR);
			extra.setVisible(true);

			int pct = slot.qtyTotal > 0 ? (int) Math.round(100.0 * slot.qtyFilled / slot.qtyTotal) : 0;
			bar.setMaximumValue(Math.max(slot.qtyTotal, 1));
			bar.setValue(Math.min(slot.qtyFilled, Math.max(slot.qtyTotal, 1)));
			boolean done = pct >= 100
				|| "Bought".equals(slot.stateLabel)
				|| "Sold".equals(slot.stateLabel);
			if (done)
			{
				bar.setForeground(PROFIT);
			}
			else if ("Cancelled".equals(slot.stateLabel))
			{
				bar.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			}
			else
			{
				bar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			}

			StringBuilder mkt = new StringBuilder();
			if (slot.instantBuy != null)
			{
				mkt.append("IB ").append(compact(slot.instantBuy));
			}
			if (slot.instantSell != null)
			{
				if (mkt.length() > 0)
				{
					mkt.append("  ");
				}
				mkt.append("IS ").append(compact(slot.instantSell));
			}
			if (mkt.length() == 0)
			{
				mkt.append("Wiki prices…");
			}
			quotes.setText(wrapSlotHtml(mkt.toString()));
			if (slot.paperEach != null)
			{
				paper.setText(signedCompact(slot.paperEach) + "/ea");
				paper.setForeground(slot.paperEach >= 0 ? PROFIT : LOSS);
			}
			else
			{
				paper.setText("");
			}
			reflowSlotCard(false, !slot.idleLabel.isEmpty());
		}

		private void armHover(Component c)
		{
			c.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					enterHover();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					exitHover();
				}
			});
		}

		private void enterHover()
		{
			hoverDepth++;
			if (lastSlot != null && lastSlot.itemId > 0)
			{
				summary.setBackground(HOVER);
			}
		}

		private void exitHover()
		{
			hoverDepth = Math.max(0, hoverDepth - 1);
			if (hoverDepth == 0)
			{
				summary.setBackground(lastSlot != null && lastSlot.itemId <= 0 ? CARD_EMPTY : CARD);
			}
		}

		private void toggleCollapsed()
		{
			if (lastSlot == null || lastSlot.itemId <= 0)
			{
				return;
			}
			setSlotCollapsed(lastSlot.index, !slotCollapsed(lastSlot.index));
			show(lastSlot);
			revalidate();
			repaint();
			Container parent = getParent();
			if (parent != null)
			{
				parent.revalidate();
				parent.repaint();
			}
		}

		private void paintStatus(String stateLabel)
		{
			if ("Buying".equals(stateLabel))
			{
				status.setForeground(BUY);
			}
			else if ("Selling".equals(stateLabel))
			{
				status.setForeground(SELL);
			}
			else if ("Bought".equals(stateLabel) || "Sold".equals(stateLabel))
			{
				status.setForeground(PROFIT);
			}
			else
			{
				status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		}

		private int slotTextWidth()
		{
			return Math.max(80, wrapWidth() - 3 - 16 - 32 - 8 - SLOT_TOGGLE - 8);
		}

		private String wrapSlotHtml(String plain)
		{
			if (plain == null || plain.isEmpty())
			{
				return "";
			}
			return "<html><body style='width:" + slotTextWidth() + "px'>" + esc(plain) + "</body></html>";
		}

		private void reflowSlotCard(boolean collapsed, boolean showIdle)
		{
			int w = slotTextWidth();
			hugLabel(status, w);
			hugLabel(name, w);
			hugLabel(fill, w);

			int copyH = status.getPreferredSize().height
				+ name.getPreferredSize().height
				+ fill.getPreferredSize().height;
			int summaryH = Math.max(SLOT_TOGGLE, copyH);
			summary.setPreferredSize(new Dimension(1, summaryH));
			summary.setMinimumSize(new Dimension(1, summaryH));
			summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, summaryH));

			int extraH = 0;
			if (!collapsed)
			{
				hugLabel(meta, w);
				if (showIdle)
				{
					hugLabel(idle, w);
				}
				else
				{
					hideLine(idle);
				}
				hugLabel(quotes, Math.max(72, w - 56));
				int paperH = Math.max(ROW_H, paper.getFontMetrics(paper.getFont()).getHeight() + 2);
				if (paper.getText() != null && !paper.getText().isEmpty())
				{
					paper.setPreferredSize(new Dimension(paper.getPreferredSize().width, paperH));
				}
				int footerH = Math.max(quotes.getPreferredSize().height, paperH);
				footer.setPreferredSize(new Dimension(w, footerH));
				footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, footerH));
				extraH += meta.getPreferredSize().height;
				if (showIdle)
				{
					extraH += idle.getPreferredSize().height;
				}
				extraH += SLOT_BAR_H + 10;
				extraH += footerH;
			}
			int pad = 16;
			if (getBorder() != null)
			{
				Insets in = getBorder().getBorderInsets(this);
				pad = in.top + in.bottom;
			}
			setCardHeight(Math.max(collapsed ? 48 : 72, pad + summaryH + extraH));
		}

		private void hideLine(JLabel label)
		{
			label.setVisible(false);
			label.setMinimumSize(new Dimension(0, 0));
			label.setPreferredSize(new Dimension(0, 0));
			label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
		}

		private void setCardHeight(int height)
		{
			setPreferredSize(new Dimension(1, height));
			setMinimumSize(new Dimension(1, height));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		}

		private CompoundBorder cardBorder(Color accent, boolean empty)
		{
			int padY = empty ? 6 : 8;
			return BorderFactory.createCompoundBorder(
				new MatteBorder(0, 3, 0, 0, accent),
				new EmptyBorder(padY, 8, padY, 8)
			);
		}

		private void bindInspect(int itemId)
		{
			if (itemClick != null)
			{
				icon.removeMouseListener(itemClick);
				name.removeMouseListener(itemClick);
				itemClick = null;
			}
			if (itemId <= 0)
			{
				icon.setCursor(Cursor.getDefaultCursor());
				name.setCursor(Cursor.getDefaultCursor());
				return;
			}
			itemClick = inspectSlot(itemId);
			Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
			icon.setCursor(hand);
			name.setCursor(hand);
			icon.addMouseListener(itemClick);
			name.addMouseListener(itemClick);
		}
	}

	private MouseAdapter inspectSlot(int itemId)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() != MouseEvent.BUTTON1)
				{
					return;
				}
				showItemPage();
				if (lookupItem != null)
				{
					lookupItem.accept(itemId);
				}
			}
		};
	}

	private static void clearTooltips(Component root)
	{
		walk(root, c ->
		{
			if (c instanceof JComponent)
			{
				((JComponent) c).setToolTipText(null);
			}
		});
	}

	private static void walk(Component root, java.util.function.Consumer<Component> fn)
	{
		fn.accept(root);
		if (root instanceof Container)
		{
			for (Component kid : ((Container) root).getComponents())
			{
				walk(kid, fn);
			}
		}
	}

	static final class SlotView
	{
		final int index;
		final int itemId;
		final String name;
		final String stateLabel;
		final boolean listed;
		final boolean buy;
		final int qtyFilled;
		final int qtyTotal;
		final long priceEach;
		final long leftGp;
		final SlotMargin.Health health;
		final String healthLabel;
		final Integer instantBuy;
		final Integer instantSell;
		final Long paperEach;
		final String tooltip;
		final int idleMinutes;
		final String idleLabel;
		final boolean stalled;

		private SlotView(
			int index,
			int itemId,
			String name,
			String stateLabel,
			boolean listed,
			boolean buy,
			int qtyFilled,
			int qtyTotal,
			long priceEach,
			long leftGp,
			SlotMargin.Health health,
			String healthLabel,
			Integer instantBuy,
			Integer instantSell,
			Long paperEach,
			String tooltip,
			int idleMinutes,
			String idleLabel,
			boolean stalled)
		{
			this.index = index;
			this.itemId = itemId;
			this.name = name;
			this.stateLabel = stateLabel;
			this.listed = listed;
			this.buy = buy;
			this.qtyFilled = qtyFilled;
			this.qtyTotal = qtyTotal;
			this.priceEach = priceEach;
			this.leftGp = leftGp;
			this.health = health;
			this.healthLabel = healthLabel;
			this.instantBuy = instantBuy;
			this.instantSell = instantSell;
			this.paperEach = paperEach;
			this.tooltip = tooltip;
			this.idleMinutes = idleMinutes;
			this.idleLabel = idleLabel;
			this.stalled = stalled;
		}

		static SlotView empty(int index)
		{
			return new SlotView(
				index, 0, "", "Empty", false, false, 0, 0, 0, 0,
				SlotMargin.Health.NEUTRAL, "", null, null, null, "Empty slot",
				0, "", false);
		}

		static SlotView of(
			int index,
			int itemId,
			String name,
			String stateLabel,
			boolean listed,
			boolean buy,
			int qtyFilled,
			int qtyTotal,
			long priceEach,
			long leftGp,
			SlotMargin margin,
			Integer instantBuy,
			Integer instantSell,
			Long paperEach,
			int idleMinutes,
			String idleLabel,
			boolean stalled)
		{
			String tip = margin.tooltip;
			if (!idleLabel.isEmpty())
			{
				tip = idleLabel + " — " + tip;
			}
			return new SlotView(
				index,
				itemId,
				name,
				stateLabel,
				listed,
				buy,
				qtyFilled,
				qtyTotal,
				priceEach,
				leftGp,
				margin.health,
				margin.health.label,
				instantBuy,
				instantSell,
				paperEach,
				tip,
				idleMinutes,
				idleLabel,
				stalled);
		}
	}

	static final class NamedItem
	{
		final int id;
		final String name;

		NamedItem(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	static final class SessionPnl
	{
		final long realized;
		final long taxPaid;
		final long gpPerHour;

		SessionPnl(long realized, long taxPaid, long gpPerHour)
		{
			this.realized = realized;
			this.taxPaid = taxPaid;
			this.gpPerHour = gpPerHour;
		}
	}

	static final class StatsPnl
	{
		final String range;
		final long realized;
		final long taxPaid;
		final long gpPerHour;
		final long buySpend;
		final long sellProceeds;
		final long fillCount;
		final long buyCount;
		final long sellCount;
		final long buyQty;
		final long sellQty;
		final long winCount;
		final double hours;
		final Long paperDump;
		final Long paperPatient;

		StatsPnl(
			String range,
			long realized,
			long taxPaid,
			long gpPerHour,
			long buySpend,
			long sellProceeds,
			long fillCount,
			long buyCount,
			long sellCount,
			long buyQty,
			long sellQty,
			long winCount,
			double hours,
			Long paperDump,
			Long paperPatient)
		{
			this.range = range;
			this.realized = realized;
			this.taxPaid = taxPaid;
			this.gpPerHour = gpPerHour;
			this.buySpend = buySpend;
			this.sellProceeds = sellProceeds;
			this.fillCount = fillCount;
			this.buyCount = buyCount;
			this.sellCount = sellCount;
			this.buyQty = buyQty;
			this.sellQty = sellQty;
			this.winCount = winCount;
			this.hours = hours;
			this.paperDump = paperDump;
			this.paperPatient = paperPatient;
		}
	}

	static final class NextFlip
	{
		final int id;
		final String name;
		final int profit;
		final int qtyAfford;
		final long cashProfit;

		NextFlip(int id, String name, int profit, int qtyAfford, long cashProfit)
		{
			this.id = id;
			this.name = name;
			this.profit = profit;
			this.qtyAfford = qtyAfford;
			this.cashProfit = cashProfit;
		}
	}

	static final class FillView
	{
		final String side;
		final int qty;
		final long priceEach;
		final Integer realized;
		final long filledAtMs;

		FillView(String side, int qty, long priceEach, Integer realized, long filledAtMs)
		{
			this.side = side == null ? "buy" : side;
			this.qty = qty;
			this.priceEach = priceEach;
			this.realized = realized;
			this.filledAtMs = filledAtMs;
		}
	}

	static final class ItemPage
	{
		final int itemId;
		final String name;
		final boolean buy;
		final boolean sell;
		final boolean canSetPrice;
		final String offerLine;
		final SlotMargin margin;
		final int geLimit;
		final int allQty;

		ItemPage(
			int itemId,
			String name,
			boolean buy,
			boolean sell,
			boolean canSetPrice,
			String offerLine,
			SlotMargin margin,
			int geLimit,
			int allQty)
		{
			this.itemId = itemId;
			this.name = name;
			this.buy = buy;
			this.sell = sell;
			this.canSetPrice = canSetPrice;
			this.offerLine = offerLine;
			this.margin = margin;
			this.geLimit = geLimit;
			this.allQty = allQty;
		}
	}
}
