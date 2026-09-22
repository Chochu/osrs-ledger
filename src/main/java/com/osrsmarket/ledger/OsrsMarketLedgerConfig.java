package com.osrsmarket.ledger;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("osrsmarketledger")
public interface OsrsMarketLedgerConfig extends Config
{
	@ConfigItem(
		keyName = "apiKey",
		name = "Plugin key",
		description = "Plugin key from OSRS Ledger Settings (rsgelk_…). Shown once when created.",
		secret = true,
		position = 0
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat feedback",
		description = "Write a game-chat line when a fill is logged.",
		position = 1
	)
	default boolean chatFeedback()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enhancedSlots",
		name = "Color GE slots",
		description = "Color-code Grand Exchange slots by whether the offer is still in margin (green / yellow / red). Select a buy or sell to see live wiki prices in the plugin panel.",
		position = 2
	)
	default boolean enhancedSlots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "offerStatusPnl",
		name = "Offer status P&L",
		description = "Show breakeven, tax, and profit on the GE offer status screen.",
		position = 3
	)
	default boolean offerStatusPnl()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncTradeHistory",
		name = "Sync GE History",
		description = "When you open Grand Exchange History, import completed trades into your ledger (one History row = one fill).",
		position = 4
	)
	default boolean syncTradeHistory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatboxPriceLinks",
		name = "GE price links",
		description = "Clickable wiki instant-buy/sell (and quantity) lines on the Set a price / How many chatbox. Click a line to type that number into the box.",
		position = 5
	)
	default boolean chatboxPriceLinks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wikiBuyHotkey",
		name = "Type wiki instant buy",
		description = "While the GE price box is open, press this to type the wiki instant-buy price.",
		position = 6
	)
	default Keybind wikiBuyHotkey()
	{
		return new Keybind(KeyEvent.VK_J, 0);
	}

	@ConfigItem(
		keyName = "wikiSellHotkey",
		name = "Type wiki instant sell",
		description = "While the GE price box is open, press this to type the wiki instant-sell price.",
		position = 7
	)
	default Keybind wikiSellHotkey()
	{
		return new Keybind(KeyEvent.VK_N, 0);
	}

	@ConfigItem(
		keyName = "geLimitHotkey",
		name = "Type GE limit",
		description = "While the GE quantity box is open, press this to type the item's GE limit.",
		position = 8
	)
	default Keybind geLimitHotkey()
	{
		return new Keybind(KeyEvent.VK_L, 0);
	}

	@ConfigItem(
		keyName = "allQtyHotkey",
		name = "Type all quantity",
		description = "While the GE quantity box is open, press this to type inventory (sell) or how many you can afford (buy).",
		position = 9
	)
	default Keybind allQtyHotkey()
	{
		return new Keybind(KeyEvent.VK_A, 0);
	}
}
