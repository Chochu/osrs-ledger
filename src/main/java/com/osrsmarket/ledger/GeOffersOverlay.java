package com.osrsmarket.ledger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.QuantityFormatter;

@Singleton
final class GeOffersOverlay extends Overlay
{
	private static final Color GOLD = new Color(255, 203, 61);
	private static final Color PROFIT = new Color(0, 255, 0);
	private static final Color LOSS = new Color(255, 80, 80);

	private final Client client;
	private final OsrsMarketLedgerConfig config;
	private final WikiQuotes quotes;
	private final CostBasis costBasis;

	@Inject
	GeOffersOverlay(
		Client client,
		OsrsMarketLedgerConfig config,
		WikiQuotes quotes,
		CostBasis costBasis)
	{
		this.client = client;
		this.config = config;
		this.quotes = quotes;
		this.costBasis = costBasis;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		setPriority(PRIORITY_HIGH);
		setResettable(false);
		drawAfterInterface(InterfaceID.GE_OFFERS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget root = client.getWidget(InterfaceID.GeOffers.CONTENTS);
		if (root == null)
		{
			return null;
		}

		quotes.refreshIfStale();
		Widget details = client.getWidget(InterfaceID.GeOffers.DETAILS);
		boolean detailsOpen = details != null && !details.isHidden();
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		boolean setupOpen = setup != null && !setup.isHidden();

		if (!detailsOpen && !setupOpen)
		{
			renderSlotTotal(graphics);
			if (config.enhancedSlots())
			{
				renderSlots(graphics);
			}
		}
		if (config.offerStatusPnl() && detailsOpen)
		{
			hideFeeInfo(details);
			renderOfferStatus(graphics, details);
		}
		return null;
	}

	private void renderSlotTotal(Graphics2D g)
	{
		Widget title = findGrandExchangeTitle();
		if (title == null || title.isHidden())
		{
			return;
		}
		Rectangle bounds = title.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int nameW = fm.stringWidth(stripTags(title.getText()));
		int nameX = titleTextX(title, bounds, nameW);
		int x = nameX + nameW + 8;
		int y = bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2;
		OverlayUtil.renderTextLocation(g, new Point(x, y), QuantityFormatter.formatNumber(leftoverSlotGp()), GOLD);
	}

	private Widget findGrandExchangeTitle()
	{
		Widget found = findExactText(client.getWidget(InterfaceID.GeOffers.FRAME), "Grand Exchange");
		if (found != null)
		{
			return found;
		}
		return findExactText(client.getWidget(InterfaceID.GeOffers.CONTENTS), "Grand Exchange");
	}

	private static Widget findExactText(Widget root, String want)
	{
		if (root == null)
		{
			return null;
		}
		ArrayList<Widget> texts = new ArrayList<>();
		collectText(root, texts);
		for (Widget widget : texts)
		{
			if (want.equalsIgnoreCase(stripTags(widget.getText())))
			{
				return widget;
			}
		}
		return null;
	}

	private static int titleTextX(Widget title, Rectangle bounds, int nameW)
	{
		int align = title.getXTextAlignment();
		if (align == WidgetTextAlignment.CENTER)
		{
			return bounds.x + Math.max(0, (bounds.width - nameW) / 2);
		}
		if (align == WidgetTextAlignment.RIGHT)
		{
			return bounds.x + Math.max(0, bounds.width - nameW);
		}
		return bounds.x;
	}

	private long leftoverSlotGp()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return 0;
		}
		long total = 0;
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null || offer.getItemId() <= 0)
			{
				continue;
			}
			GrandExchangeOfferState state = offer.getState();
			if (state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.SELLING)
			{
				continue;
			}
			int left = Math.max(offer.getTotalQuantity() - offer.getQuantitySold(), 0);
			total += (long) left * offer.getPrice();
		}
		return total;
	}

	private void renderSlots(Graphics2D g)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int i = 0; i < 8; i++)
		{
			Widget slot = client.getWidget(InterfaceID.GeOffers.INDEX_0 + i);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			Rectangle bounds = slot.getBounds();
			if (bounds == null || bounds.width <= 0)
			{
				continue;
			}
			GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
			if (offer == null || offer.getItemId() <= 0 || !isActive(offer.getState()))
			{
				continue;
			}

			int avg = costBasis.avgCost(offer.getItemId());
			SlotMargin margin = SlotMargin.analyze(offer, quotes.get(offer.getItemId()), avg);
			Color c = margin.health.color;
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 14));
			g.fillRoundRect(bounds.x + 3, bounds.y + 3, bounds.width - 7, bounds.height - 7, 10, 10);
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
			g.setStroke(new BasicStroke(1.6f));
			g.drawRoundRect(bounds.x + 3, bounds.y + 3, bounds.width - 7, bounds.height - 7, 10, 10);
		}
	}

	private void hideFeeInfo(Widget details)
	{
		Widget fee = client.getWidget(InterfaceID.GeOffers.DETAILS_FEE);
		Rectangle feeBox = hideTree(fee);
		hideTree(client.getWidget(InterfaceID.GeOffers.DETAILS_GRAPHIC3));
		if (details == null)
		{
			return;
		}
		hideFeeIcons(details, feeBox);
	}

	private static void hideFeeIcons(Widget widget, Rectangle feeBox)
	{
		if (widget == null)
		{
			return;
		}
		String text = stripTags(widget.getText());
		if (text.toLowerCase().contains("convenience fee") || text.toLowerCase().contains("fee:"))
		{
			widget.setHidden(true);
			if (widget.getBounds() != null)
			{
				feeBox = widget.getBounds();
			}
		}
		Rectangle bounds = widget.getBounds();
		if (feeBox != null && bounds != null
			&& (widget.getText() == null || widget.getText().isEmpty())
			&& bounds.width > 0 && bounds.width <= 28
			&& bounds.height > 0 && bounds.height <= 28
			&& near(feeBox, bounds))
		{
			widget.setHidden(true);
		}
		Widget[] kids = widget.getStaticChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				hideFeeIcons(kid, feeBox);
			}
		}
		kids = widget.getDynamicChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				hideFeeIcons(kid, feeBox);
			}
		}
	}

	private static boolean near(Rectangle fee, Rectangle icon)
	{
		Rectangle padded = new Rectangle(fee.x - 6, fee.y - 8, fee.width + 36, fee.height + 16);
		return padded.intersects(icon);
	}

	private static Rectangle hideTree(Widget widget)
	{
		if (widget == null)
		{
			return null;
		}
		widget.setHidden(true);
		if (widget.getText() != null && !widget.getText().isEmpty())
		{
			widget.setText("");
		}
		Widget[] kids = widget.getStaticChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				hideTree(kid);
			}
		}
		kids = widget.getDynamicChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				hideTree(kid);
			}
		}
		return widget.getBounds();
	}

	private void renderOfferStatus(Graphics2D g, Widget details)
	{
		GrandExchangeOffer offer = selectedOffer();
		if (offer == null || offer.getItemId() <= 0)
		{
			return;
		}

		boolean sell = offer.getState() == GrandExchangeOfferState.SELLING
			|| offer.getState() == GrandExchangeOfferState.SOLD
			|| offer.getState() == GrandExchangeOfferState.CANCELLED_SELL;
		int filled = offer.getQuantitySold();
		int qty = filled > 0 ? filled : Math.max(offer.getTotalQuantity(), 1);
		long price = offer.getPrice();
		long taxEach = sell ? GeTax.tax(price) : 0;
		long taxTotal = taxEach * qty;
		int avg = costBasis.avgCost(offer.getItemId());
		int breakeven = avg > 0 ? GeTax.breakeven(avg) : 0;
		Long profitEach = null;
		if (sell && avg > 0)
		{
			profitEach = GeTax.afterTax(price) - avg;
		}
		else if (!sell)
		{
			WikiQuotes.Quote q = quotes.get(offer.getItemId());
			if (q != null && q.high != null)
			{
				profitEach = GeTax.afterTax(q.high) - price;
			}
		}

		ArrayList<String> texts = new ArrayList<>();
		ArrayList<Color> colors = new ArrayList<>();
		texts.add(breakeven > 0 ? "Breakeven: " + gp(breakeven) : "Breakeven: n/a");
		colors.add(GOLD);
		if (sell)
		{
			texts.add("Tax: " + compact(taxTotal) + " (" + QuantityFormatter.formatNumber(taxEach) + "/ea)");
			colors.add(GOLD);
		}
		if (profitEach != null)
		{
			long total = (long) profitEach * qty;
			String label = sell ? "Profit: " : "Paper: ";
			texts.add(label + signedGp(total) + " (" + signedCompact(profitEach) + "/ea)");
			colors.add(profitEach >= 0 ? PROFIT : LOSS);
		}

		String itemName = "";
		try
		{
			itemName = client.getItemDefinition(offer.getItemId()).getName();
		}
		catch (RuntimeException ignored)
		{
			/* item defs are only valid on the client thread */
		}
		hideExamine(client.getWidget(InterfaceID.GeOffers.DETAILS_DESC), itemName);
		Anchor anchor = locateAnchor(details, itemName, offer.getItemId());
		Rectangle slot = GeOfferStatusPnl.slot(anchor.name, anchor.icon, details.getBounds(), texts.size());
		if (slot == null || slot.width <= 0)
		{
			return;
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int x = slot.x;
		int y = slot.y + fm.getAscent();

		for (int i = 0; i < texts.size(); i++)
		{
			OverlayUtil.renderTextLocation(g, new Point(x, y), texts.get(i), colors.get(i));
			y += GeOfferStatusPnl.LINE_H;
		}
	}

	private static final class Anchor
	{
		final Rectangle name;
		final Rectangle icon;

		Anchor(Rectangle name, Rectangle icon)
		{
			this.name = name;
			this.icon = icon;
		}
	}

	private static Anchor locateAnchor(Widget details, String itemName, int itemId)
	{
		Holder holder = new Holder();
		walkAnchor(details, itemName == null ? "" : itemName.trim(), itemId, details.getBounds(), holder);
		return new Anchor(holder.name, holder.icon);
	}

	private static final class Holder
	{
		Rectangle name;
		Rectangle icon;
	}

	private static void walkAnchor(Widget widget, String name, int itemId, Rectangle details, Holder out)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}
		Rectangle bounds = widget.getBounds();
		if (bounds != null && widget.getItemId() > 0
			&& (widget.getItemId() == itemId)
			&& GeOfferStatusPnl.isOfferIcon(bounds, details))
		{
			out.icon = bounds;
		}
		String plain = stripTags(widget.getText());
		if (!name.isEmpty() && bounds != null
			&& (plain.equalsIgnoreCase(name) || plain.startsWith(name))
			&& GeOfferStatusPnl.isNameLine(bounds))
		{
			out.name = bounds;
		}
		Widget[] kids = widget.getStaticChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				walkAnchor(kid, name, itemId, details, out);
			}
		}
		kids = widget.getDynamicChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				walkAnchor(kid, name, itemId, details, out);
			}
		}
		try
		{
			kids = widget.getNestedChildren();
			if (kids != null)
			{
				for (Widget kid : kids)
				{
					walkAnchor(kid, name, itemId, details, out);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			/* nested children are not on every widget type */
		}
	}

	private static void hideExamine(Widget desc, String itemName)
	{
		if (desc == null || desc.isHidden())
		{
			return;
		}
		ArrayList<Widget> texts = new ArrayList<>();
		collectText(desc, texts);
		Widget title = null;
		String name = itemName == null ? "" : itemName.trim();
		for (Widget widget : texts)
		{
			String raw = widget.getText();
			String plain = stripTags(raw);
			boolean isName = !name.isEmpty()
				&& (plain.equalsIgnoreCase(name) || plain.startsWith(name));
			if (isName && title == null)
			{
				title = widget;
				if (raw != null && (indexOfBr(raw) >= 0 || plain.length() > name.length() + 2))
				{
					widget.setText(name);
				}
				continue;
			}
			if (widget == desc)
			{
				widget.setText("");
			}
			else
			{
				widget.setHidden(true);
			}
		}
	}

	private static void collectText(Widget widget, ArrayList<Widget> out)
	{
		if (widget == null)
		{
			return;
		}
		String text = widget.getText();
		if (text != null && !text.isEmpty())
		{
			out.add(widget);
		}
		Widget[] kids = widget.getStaticChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				collectText(kid, out);
			}
		}
		kids = widget.getDynamicChildren();
		if (kids != null)
		{
			for (Widget kid : kids)
			{
				collectText(kid, out);
			}
		}
		try
		{
			kids = widget.getNestedChildren();
			if (kids != null)
			{
				for (Widget kid : kids)
				{
					collectText(kid, out);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			/* nested children are not on every widget type */
		}
	}

	private static String stripTags(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
	}

	private static int indexOfBr(String text)
	{
		return text.toLowerCase().indexOf("<br");
	}

	private GrandExchangeOffer selectedOffer()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		int selected = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1;
		if (selected >= 0 && offers != null && selected < offers.length)
		{
			GrandExchangeOffer offer = offers[selected];
			if (offer != null && offer.getItemId() > 0)
			{
				return offer;
			}
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId > 0 && offers != null)
		{
			for (GrandExchangeOffer o : offers)
			{
				if (o != null && o.getItemId() == itemId && o.getState() != GrandExchangeOfferState.EMPTY)
				{
					return o;
				}
			}
		}
		return null;
	}

	private static boolean isActive(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD;
	}

	private static String gp(int n)
	{
		return QuantityFormatter.formatNumber(n) + " gp";
	}

	private static String compact(long n)
	{
		long a = Math.abs(n);
		if (a < 1_000)
		{
			return QuantityFormatter.formatNumber(n);
		}
		if (a < 1_000_000)
		{
			return trimDecimal(n / 1_000.0) + "k";
		}
		if (a < 1_000_000_000)
		{
			return trimDecimal(n / 1_000_000.0) + "M";
		}
		return trimDecimal(n / 1_000_000_000.0) + "B";
	}

	private static String trimDecimal(double n)
	{
		String s = String.format(java.util.Locale.US, "%.1f", n);
		if (s.endsWith(".0"))
		{
			return s.substring(0, s.length() - 2);
		}
		return s;
	}

	private static String signedGp(long n)
	{
		String body = QuantityFormatter.formatNumber(Math.abs(n)) + " gp";
		return n > 0 ? "+" + body : n < 0 ? "-" + body : body;
	}

	private static String signedCompact(long n)
	{
		String body = compact(Math.abs(n));
		return n > 0 ? "+" + body : n < 0 ? "-" + body : body;
	}
}
