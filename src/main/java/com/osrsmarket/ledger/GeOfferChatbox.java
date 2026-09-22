package com.osrsmarket.ledger;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.QuantityFormatter;

@Singleton
final class GeOfferChatbox
{
	private static final int LINK = 0x800000;
	private static final int HOVER = 0xFFFFFF;
	private static final int MUTED = 0xAA2222;

	private final Client client;
	private final OsrsMarketLedgerConfig config;

	private Widget line1;
	private Widget line2;
	private Widget hint;

	@Inject
	GeOfferChatbox(Client client, OsrsMarketLedgerConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void sync(GeOfferInput.Kind kind, Integer wikiBuy, Integer wikiSell, int geLimit, int allQty)
	{
		if (!config.chatboxPriceLinks() || kind == GeOfferInput.Kind.NONE)
		{
			line1 = line2 = hint = null;
			return;
		}
		Widget parent = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (parent == null || parent.isHidden())
		{
			line1 = line2 = hint = null;
			return;
		}
		if (!live(line1, parent) || !live(line2, parent) || !live(hint, parent))
		{
			line1 = child(parent, WidgetTextAlignment.LEFT, WidgetPositionMode.ABSOLUTE_TOP, 6, 10);
			line2 = child(parent, WidgetTextAlignment.LEFT, WidgetPositionMode.ABSOLUTE_TOP, 20, 10);
			hint = child(parent, WidgetTextAlignment.CENTER, WidgetPositionMode.ABSOLUTE_BOTTOM, 6, 0);
		}
		if (kind == GeOfferInput.Kind.PRICE)
		{
			link(line1, wikiBuy, wikiBuy != null && wikiBuy > 0 ? "set wiki instant buy: " + gp(wikiBuy) : "", wikiBuy == null ? 0 : wikiBuy, "wiki instant buy not loaded yet");
			link(line2, wikiSell, wikiSell != null && wikiSell > 0 ? "set wiki instant sell: " + gp(wikiSell) : "", wikiSell == null ? 0 : wikiSell, "wiki instant sell not loaded yet");
			hint.setText("J wiki buy  ·  N wiki sell  ·  click a line to type it");
			hint.setTextColor(LINK);
			clearClick(hint);
			return;
		}
		link(line1, geLimit, geLimit > 0 ? "set GE limit: " + QuantityFormatter.formatNumber(geLimit) : "", geLimit, "this item has no GE limit");
		link(line2, allQty, allQty > 0 ? "set all: " + QuantityFormatter.formatNumber(allQty) : "", allQty, "no inventory / cash for all");
		hint.setText("L GE limit  ·  A all  ·  click a line to type it");
		hint.setTextColor(LINK);
		clearClick(hint);
	}

	private void link(Widget widget, Integer value, String okText, int typed, String missing)
	{
		if (value == null || value <= 0)
		{
			widget.setText(missing);
			widget.setTextColor(MUTED);
			clearClick(widget);
			return;
		}
		widget.setText(okText);
		widget.setTextColor(LINK);
		widget.setAction(0, "Set");
		widget.setOnOpListener((JavaScriptCallback) ev -> GeOfferInput.type(client, typed));
		widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(HOVER));
		widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(LINK));
	}

	private static void clearClick(Widget widget)
	{
		widget.setAction(0, "");
		widget.setOnOpListener((Object[]) null);
		widget.setOnMouseRepeatListener((Object[]) null);
		widget.setOnMouseLeaveListener((Object[]) null);
	}

	private static Widget child(Widget parent, int xAlign, int yMode, int y, int x)
	{
		Widget widget = parent.createChild(-1, WidgetType.TEXT);
		widget.setTextColor(LINK);
		widget.setFontId(FontID.VERDANA_11_BOLD);
		widget.setYPositionMode(yMode);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalHeight(16);
		widget.setXTextAlignment(xAlign);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		widget.setHasListener(true);
		widget.revalidate();
		return widget;
	}

	private static boolean live(Widget widget, Widget parent)
	{
		if (widget == null || parent == null)
		{
			return false;
		}
		try
		{
			return widget.getParent() == parent;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static String gp(int n)
	{
		return QuantityFormatter.formatNumber(n) + " gp";
	}
}
