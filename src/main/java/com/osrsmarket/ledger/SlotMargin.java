package com.osrsmarket.ledger;

import java.awt.Color;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.util.QuantityFormatter;

final class SlotMargin
{
	enum Health
	{
		GOOD(new Color(55, 240, 70), "On market"),
		WARN(new Color(230, 150, 30), "In spread"),
		BAD(new Color(230, 30, 30), "Update"),
		NEUTRAL(new Color(120, 120, 120), "");

		final Color color;
		final String label;

		Health(Color color, String label)
		{
			this.color = color;
			this.label = label;
		}
	}

	final Health health;
	final String tooltip;
	final String adjust;
	final Integer instantBuy;
	final Integer instantSell;
	final Integer spreadAfterTax;
	final Integer ifSellAtIb;
	final int avgCost;
	final int breakeven;
	final Integer vsBook;

	private SlotMargin(
		Health health,
		String tooltip,
		String adjust,
		Integer instantBuy,
		Integer instantSell,
		Integer spreadAfterTax,
		Integer ifSellAtIb,
		int avgCost,
		int breakeven,
		Integer vsBook)
	{
		this.health = health;
		this.tooltip = tooltip;
		this.adjust = adjust;
		this.instantBuy = instantBuy;
		this.instantSell = instantSell;
		this.spreadAfterTax = spreadAfterTax;
		this.ifSellAtIb = ifSellAtIb;
		this.avgCost = avgCost;
		this.breakeven = breakeven;
		this.vsBook = vsBook;
	}

	static SlotMargin empty()
	{
		return new SlotMargin(Health.NEUTRAL, "Empty slot", "", null, null, null, null, 0, 0, null);
	}

	static SlotMargin analyze(GrandExchangeOffer offer, WikiQuotes.Quote quote, int avgCost)
	{
		if (offer == null || offer.getItemId() <= 0 || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return empty();
		}
		boolean buy = offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.BOUGHT
			|| offer.getState() == GrandExchangeOfferState.CANCELLED_BUY;
		boolean sell = offer.getState() == GrandExchangeOfferState.SELLING
			|| offer.getState() == GrandExchangeOfferState.SOLD
			|| offer.getState() == GrandExchangeOfferState.CANCELLED_SELL;
		return analyze(
			buy,
			sell,
			offer.getPrice(),
			offer.getQuantitySold(),
			offer.getTotalQuantity(),
			quote,
			avgCost);
	}

	static SlotMargin analyze(
		boolean buy,
		boolean sell,
		int price,
		int qtySold,
		int qtyTotal,
		WikiQuotes.Quote quote,
		int avgCost)
	{
		Integer high = quote == null ? null : quote.high;
		Integer low = quote == null ? null : quote.low;
		Integer spread = high != null && low != null ? GeTax.afterTax(high) - low : null;
		Integer ifSellAtIb = buy && high != null && price > 0 ? GeTax.afterTax(high) - price : null;
		int breakeven = avgCost > 0 ? GeTax.breakeven(avgCost) : 0;
		Integer vsBook = sell && avgCost > 0 && price > 0 ? GeTax.afterTax(price) - avgCost : null;

		StringBuilder html = new StringBuilder("<html>");
		html.append(esc(buy ? "Buy" : sell ? "Sell" : "Offer"));
		if (qtyTotal > 0)
		{
			html.append(' ')
				.append(qtySold)
				.append(" / ")
				.append(qtyTotal);
		}
		if (price > 0)
		{
			html.append(" @ ").append(gp(price));
		}
		html.append("<br>");
		if (high != null)
		{
			html.append("Buy price (instant-buy): ").append(gp(high)).append("<br>");
		}
		if (low != null)
		{
			html.append("Sell price (instant-sell): ").append(gp(low)).append("<br>");
		}
		if (spread != null)
		{
			html.append("After-tax spread: ").append(signed(spread)).append("<br>");
		}

		Health health;
		String adjust;
		if (high == null || low == null)
		{
			health = Health.NEUTRAL;
			adjust = "No live wiki quote yet.";
		}
		else if (price <= 0)
		{
			health = Health.NEUTRAL;
			adjust = "Set a price to see margin.";
		}
		else if (buy)
		{
			html.append("If you sell at buy price: ").append(signed(ifSellAtIb)).append("/ea<br>");
			if (ifSellAtIb == null || ifSellAtIb <= 0 || price >= high)
			{
				health = Health.BAD;
				adjust = "Out of margin. Drop buy to " + gp(low) + " (sell price / buy edge).";
			}
			else if (price <= low)
			{
				health = Health.GOOD;
				adjust = "On the buy edge. Hold, or overbid to " + gp(low + 1) + " to front the queue.";
			}
			else
			{
				health = Health.WARN;
				adjust = "Above the buy edge. Drop to " + gp(low) + " for a patient buy, or keep bidding to fill faster.";
			}
		}
		else if (sell)
		{
			if (avgCost > 0)
			{
				html.append("Avg cost: ").append(gp(avgCost)).append("<br>");
				html.append("Breakeven: ").append(gp(breakeven)).append("<br>");
				html.append("Vs book: ").append(signed(vsBook)).append("/ea<br>");
			}
			boolean belowCost = avgCost > 0 && vsBook != null && vsBook < 0;
			if (belowCost || price <= low)
			{
				health = Health.BAD;
				adjust = belowCost
					? "Below breakeven " + gp(breakeven) + ". Raise or abort."
					: "At/under dump price (chart sell). Raise toward " + gp(high) + " (chart buy).";
			}
			else if (price >= high)
			{
				health = Health.GOOD;
				adjust = "On the sell edge. Hold, or undercut to " + gp(Math.max(1, high - 1)) + ".";
			}
			else
			{
				health = Health.WARN;
				adjust = "Under the sell edge. Raise to " + gp(high) + " to sit on the market.";
			}
		}
		else
		{
			health = Health.NEUTRAL;
			adjust = "";
		}

		html.append("<br>").append(esc(adjust)).append("<br>");
		html.append("<br>Green = on the edge (buy at sell / sell at buy)<br>");
		html.append("Yellow = inside the spread, not at the edge<br>");
		html.append("Red = off-market / out of margin — update the offer");
		html.append("</html>");
		return new SlotMargin(
			health,
			html.toString(),
			adjust,
			high,
			low,
			spread,
			ifSellAtIb,
			avgCost,
			breakeven,
			vsBook);
	}

	static String gp(int n)
	{
		return QuantityFormatter.formatNumber(n) + " gp";
	}

	static String signed(int n)
	{
		String body = QuantityFormatter.formatNumber(Math.abs(n)) + " gp";
		if (n > 0)
		{
			return "+" + body;
		}
		if (n < 0)
		{
			return "-" + body;
		}
		return body;
	}

	private static String esc(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
