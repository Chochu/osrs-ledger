package com.osrsmarket.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;

/**
 * Reads completed offers from the in-game Grand Exchange History interface.
 * LIST is a flat 240-child widget: each trade is 6 leaves
 * (pad, pad, Bought/Sold, name, item icon, price).
 */
final class GeHistoryExtractor
{
	private static final Pattern DIGITS = Pattern.compile("(\\d[\\d,]*)");
	private static final Pattern EACH = Pattern.compile("([\\d,]+)\\s*(?:coins?\\s*)?each", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAX_TOTAL = Pattern.compile("\\(([\\d,]+)\\s*-");
	private static final Pattern QTY_BEFORE_X = Pattern.compile("(\\d[\\d,]*)\\s*x\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern QTY_AFTER_X = Pattern.compile("\\bx\\s*(\\d[\\d,]*)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SIDE_ONLY = Pattern.compile("^(bought|sold)\\s*:?$", Pattern.CASE_INSENSITIVE);

	/** One dynamic child of the GE History list. */
	static final class Slot
	{
		final String text;
		final int itemId;
		final int itemQuantity;
		final boolean hidden;

		Slot(String text, int itemId, int itemQuantity)
		{
			this(text, itemId, itemQuantity, false);
		}

		Slot(String text, int itemId, int itemQuantity, boolean hidden)
		{
			this.text = text == null ? "" : text;
			this.itemId = itemId;
			this.itemQuantity = itemQuantity;
			this.hidden = hidden;
		}
	}

	static final class Fill
	{
		final int itemId;
		final String side;
		final int qty;
		final int priceEach;

		Fill(int itemId, String side, int qty, int priceEach)
		{
			this.itemId = itemId;
			this.side = side;
			this.qty = qty;
			this.priceEach = priceEach;
		}

		String tupleKey()
		{
			return itemId + ":" + side + ":" + qty + ":" + priceEach;
		}
	}

	private GeHistoryExtractor()
	{
	}

	static List<Fill> fromWidgets(Widget[] widgets)
	{
		return fromSlots(slotsFromWidgets(widgets));
	}

	static List<Fill> fromWidgetTree(Widget root)
	{
		return bestFromWidget(root, 0);
	}

	/**
	 * History rows are nested containers. Flattening the whole tree can put every
	 * "Sold" in one band and every price in another, so only the newest line matches.
	 * Prefer splitting child containers when that finds more fills.
	 */
	private static List<Fill> bestFromWidget(Widget w, int depth)
	{
		if (w == null || depth > 8)
		{
			return new ArrayList<>();
		}
		List<Fill> flat = fromSlots(slotsFromWidgetTree(w));
		List<Fill> split = new ArrayList<>();
		int sub = 0;
		for (Widget kid : childWidgets(w))
		{
			if (!widgetHasKids(kid))
			{
				continue;
			}
			sub++;
			split.addAll(bestFromWidget(kid, depth + 1));
		}
		if (sub == 0 || split.size() <= flat.size())
		{
			return flat;
		}
		return split;
	}

	static List<Fill> fromRowGroups(List<List<Slot>> groups)
	{
		List<Fill> split = new ArrayList<>();
		if (groups == null)
		{
			return split;
		}
		List<Slot> flat = new ArrayList<>();
		for (List<Slot> group : groups)
		{
			if (group == null)
			{
				continue;
			}
			split.addAll(fromSlots(group));
			flat.addAll(group);
		}
		List<Fill> flattened = fromSlots(flat);
		return split.size() > flattened.size() ? split : flattened;
	}

	private static Widget[] childWidgets(Widget w)
	{
		List<Widget> out = new ArrayList<>();
		addChildren(out, nestedChildren(w));
		addChildren(out, w.getDynamicChildren());
		addChildren(out, w.getStaticChildren());
		return out.toArray(new Widget[0]);
	}

	private static void addChildren(List<Widget> out, Widget[] widgets)
	{
		if (widgets == null)
		{
			return;
		}
		for (Widget w : widgets)
		{
			if (w != null)
			{
				out.add(w);
			}
		}
	}

	private static boolean widgetHasKids(Widget w)
	{
		return len(nestedChildren(w)) > 0
			|| len(w.getDynamicChildren()) > 0
			|| len(w.getStaticChildren()) > 0;
	}

	static List<Slot> slotsFromWidgetTree(Widget root)
	{
		List<Slot> rows = new ArrayList<>();
		collectWidget(root, rows);
		return rows;
	}

	static List<Slot> slotsFromWidgets(Widget[] widgets)
	{
		List<Slot> rows = new ArrayList<>();
		collectArray(widgets, rows);
		return rows;
	}

	static String debugSlots(List<Slot> rows, int limit)
	{
		if (rows == null || rows.isEmpty())
		{
			return "total=0";
		}
		StringBuilder sb = new StringBuilder();
		int n = Math.min(Math.max(0, limit), rows.size());
		for (int i = 0; i < n; i++)
		{
			Slot s = rows.get(i);
			String t = plain(s.text);
			if (t.length() > 36)
			{
				t = t.substring(0, 36) + "…";
			}
			sb.append(i).append(":{id=").append(s.itemId)
				.append(" q=").append(s.itemQuantity)
				.append(" t=").append(t).append("} ");
		}
		sb.append("total=").append(rows.size());
		return sb.toString();
	}

	static String debugFills(List<Fill> fills)
	{
		if (fills == null || fills.isEmpty())
		{
			return "fills=0";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("fills=").append(fills.size());
		int n = Math.min(fills.size(), 40);
		for (int i = 0; i < n; i++)
		{
			Fill f = fills.get(i);
			sb.append(" [").append(i).append("] ")
				.append(f.side)
				.append(" id=").append(f.itemId)
				.append(" q=").append(f.qty)
				.append(" @").append(f.priceEach);
		}
		return sb.toString();
	}

	/** One-shot dump of LIST children so we can see why only one History row parses. */
	static String debugLayout(Widget root)
	{
		if (root == null)
		{
			return "root=null";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("root dyn=").append(len(root.getDynamicChildren()))
			.append(" nest=").append(len(nestedChildren(root)))
			.append(" stat=").append(len(root.getStaticChildren()))
			.append(" hidden=").append(root.isHidden());
		Widget[] kids = childWidgets(root);
		sb.append(" kids=").append(kids.length);
		int n = Math.min(kids.length, 20);
		for (int i = 0; i < n; i++)
		{
			Widget k = kids[i];
			List<Slot> slots = slotsFromWidgetTree(k);
			List<Fill> fills = fromSlots(slots);
			sb.append("\n kid").append(i)
				.append(" type=").append(k.getType())
				.append(" dyn=").append(len(k.getDynamicChildren()))
				.append(" nest=").append(len(nestedChildren(k)))
				.append(" stat=").append(len(k.getStaticChildren()))
				.append(" hidden=").append(k.isHidden())
				.append(" slots=").append(slots.size())
				.append(" fills=").append(fills.size())
				.append(" ").append(debugSlots(slots, 10));
		}
		sb.append("\nflat ").append(debugSlots(slotsFromWidgetTree(root), 48));
		sb.append("\ntree ").append(debugTree(root, 0, 3, 80, new int[] {0}));
		return sb.toString();
	}

	private static String debugTree(Widget w, int depth, int maxDepth, int maxNodes, int[] count)
	{
		if (w == null || depth > maxDepth || count[0] >= maxNodes)
		{
			return "";
		}
		count[0]++;
		String t = plain(w.getText());
		if (t.length() > 28)
		{
			t = t.substring(0, 28) + "…";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(depth).append(":{ty=").append(w.getType())
			.append(" id=").append(w.getItemId())
			.append(" q=").append(w.getItemQuantity())
			.append(" d=").append(len(w.getDynamicChildren()))
			.append(" n=").append(len(nestedChildren(w)))
			.append(" s=").append(len(w.getStaticChildren()))
			.append(" t=").append(t).append("} ");
		for (Widget kid : childWidgets(w))
		{
			sb.append(debugTree(kid, depth + 1, maxDepth, maxNodes, count));
		}
		return sb.toString();
	}

	private static void collectArray(Widget[] widgets, List<Slot> rows)
	{
		if (widgets == null)
		{
			return;
		}
		for (Widget w : widgets)
		{
			collectWidget(w, rows);
		}
	}

	private static void collectWidget(Widget w, List<Slot> rows)
	{
		if (w == null)
		{
			return;
		}
		Widget[] nested = nestedChildren(w);
		Widget[] dyn = w.getDynamicChildren();
		Widget[] stat = w.getStaticChildren();
		boolean hasKids = len(nested) > 0 || len(dyn) > 0 || len(stat) > 0;
		if (!hasKids)
		{
			rows.add(toSlot(w));
			return;
		}
		if (hasLeafContent(w))
		{
			rows.add(toSlot(w));
		}
		collectArray(nested, rows);
		collectArray(dyn, rows);
		collectArray(stat, rows);
	}

	private static Widget[] nestedChildren(Widget w)
	{
		try
		{
			return w.getNestedChildren();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static int len(Widget[] widgets)
	{
		if (widgets == null)
		{
			return 0;
		}
		int n = 0;
		for (Widget w : widgets)
		{
			if (w != null)
			{
				n++;
			}
		}
		return n;
	}

	private static boolean hasLeafContent(Widget w)
	{
		if (w.getItemId() > 0)
		{
			return true;
		}
		return !plain(w.getText()).isEmpty();
	}

	private static Slot toSlot(Widget w)
	{
		return new Slot(w.getText(), w.getItemId(), w.getItemQuantity(), w.isHidden());
	}

	static List<Fill> fromSlots(List<Slot> rows)
	{
		if (rows == null || rows.isEmpty())
		{
			return new ArrayList<>();
		}
		if (looksLikeJagexHistory(rows))
		{
			List<Fill> strided = new ArrayList<>();
			for (int i = 0; i + 6 <= rows.size(); i += 6)
			{
				Fill fill = fromJagexGroup(rows.subList(i, i + 6));
				if (fill != null)
				{
					strided.add(fill);
				}
			}
			if (!strided.isEmpty())
			{
				return strided;
			}
		}
		return matchWalking(rows);
	}

	private static boolean looksLikeJagexHistory(List<Slot> rows)
	{
		if (rows.size() < 6 || rows.size() % 6 != 0)
		{
			return false;
		}
		return parseSide(plain(rows.get(2).text)) != null;
	}

	/**
	 * Live History row: pad, pad, Bought/Sold, name, icon, price.
	 * Leftover empty or hidden slots must not become a fill.
	 */
	private static Fill fromJagexGroup(List<Slot> group)
	{
		if (group == null || group.size() != 6)
		{
			return null;
		}
		for (int i = 2; i <= 5; i++)
		{
			if (group.get(i).hidden)
			{
				return null;
			}
		}
		String side = parseSide(plain(group.get(2).text));
		if (side == null)
		{
			return null;
		}
		String nameText = plain(group.get(3).text);
		if (nameText.isEmpty() || parseSide(nameText) != null || looksLikePrice(nameText))
		{
			return null;
		}
		Slot item = group.get(4);
		if (item.itemId <= 0)
		{
			return null;
		}
		if (!looksLikePrice(group.get(5).text))
		{
			return null;
		}
		int namedQty = qtyInName(nameText);
		int qty = namedQty > 0 ? namedQty : item.itemQuantity;
		if (qty <= 0)
		{
			return null;
		}
		if (namedQty > 0 && item.itemQuantity > 1 && namedQty != item.itemQuantity)
		{
			return null;
		}
		int price = priceEach(plain(group.get(5).text), qty);
		if (price <= 0)
		{
			return null;
		}
		return new Fill(item.itemId, side, qty, price);
	}

	private static List<Fill> matchWalking(List<Slot> rows)
	{
		List<Fill> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); )
		{
			Match match = matchAt(rows, i);
			if (match != null)
			{
				out.add(match.fill);
				i = Math.max(i + 1, match.end);
			}
			else
			{
				i++;
			}
		}
		return out;
	}

	private static String nearbyName(List<Slot> rows, int from, int to, int step)
	{
		if (step == 0)
		{
			return "";
		}
		for (int j = from; step > 0 ? j <= to : j >= to; j += step)
		{
			if (j < 0 || j >= rows.size())
			{
				break;
			}
			String t = plain(rows.get(j).text);
			if (t.isEmpty())
			{
				continue;
			}
			if (parseSide(t) != null)
			{
				if (step > 0)
				{
					break;
				}
				continue;
			}
			if (looksLikePrice(t))
			{
				continue;
			}
			return t;
		}
		return "";
	}

	private static final class Match
	{
		final Fill fill;
		final int end;

		Match(Fill fill, int end)
		{
			this.fill = fill;
			this.end = end;
		}
	}

	/**
	 * Live GE History is Bought/Sold then name then icon then price. Older
	 * layouts put the name behind Sold; leftover Sold sprites still need a name.
	 */
	private static Match matchAt(List<Slot> rows, int sideIdx)
	{
		String side = parseSide(plain(rows.get(sideIdx).text));
		if (side == null)
		{
			return null;
		}
		String nameText = nearbyName(rows, sideIdx + 1, Math.min(rows.size() - 1, sideIdx + 4), 1);
		if (nameText.isEmpty())
		{
			nameText = nearbyName(rows, sideIdx - 1, Math.max(0, sideIdx - 3), -1);
		}
		if (nameText.isEmpty())
		{
			return null;
		}
		Slot item = null;
		int itemIdx = -1;
		String priceText = "";
		int priceIdx = -1;
		int last = Math.min(rows.size() - 1, sideIdx + 6);
		for (int j = sideIdx + 1; j <= last; j++)
		{
			Slot s = rows.get(j);
			if (item == null && s.itemId > 0)
			{
				item = s;
				itemIdx = j;
			}
			if (priceIdx < 0 && looksLikePrice(s.text))
			{
				priceText = plain(s.text);
				priceIdx = j;
			}
			if (item != null && priceIdx >= 0)
			{
				break;
			}
		}
		if (item == null || priceIdx < 0)
		{
			return null;
		}
		int namedQty = qtyInName(nameText);
		int qty = namedQty > 0 ? namedQty : item.itemQuantity;
		if (qty <= 0)
		{
			return null;
		}
		if (namedQty > 0 && item.itemQuantity > 1 && namedQty != item.itemQuantity)
		{
			return null;
		}
		int price = priceEach(priceText, qty);
		if (price <= 0)
		{
			return null;
		}
		int end = Math.max(itemIdx, priceIdx) + 1;
		return new Match(new Fill(item.itemId, side, qty, price), end);
	}

	/** History is newest-first; reverse so FIFO lots land in trade order. */
	static List<Fill> oldestFirst(List<Fill> newestFirst)
	{
		List<Fill> out = new ArrayList<>(newestFirst.size());
		for (int i = newestFirst.size() - 1; i >= 0; i--)
		{
			out.add(newestFirst.get(i));
		}
		return out;
	}

	static int qtyFromName(String text, int iconQty)
	{
		int named = qtyInName(text);
		return named > 0 ? named : iconQty;
	}

	static int qtyInName(String text)
	{
		String t = plain(text);
		Matcher after = QTY_AFTER_X.matcher(t);
		if (after.find())
		{
			long n = parseNumber(after.group(1));
			if (n > 0 && n <= Integer.MAX_VALUE)
			{
				return (int) n;
			}
		}
		Matcher before = QTY_BEFORE_X.matcher(t);
		if (before.find())
		{
			long n = parseNumber(before.group(1));
			if (n > 0 && n <= Integer.MAX_VALUE)
			{
				return (int) n;
			}
		}
		return 0;
	}

	static boolean looksLikePrice(String text)
	{
		String t = plain(text).toLowerCase();
		if (t.isEmpty())
		{
			return false;
		}
		return t.contains("each") || t.contains("coin") || (t.contains("(") && t.contains("-"));
	}

	static String parseSide(String text)
	{
		String t = plain(text);
		if (t.isEmpty())
		{
			return null;
		}
		Matcher m = SIDE_ONLY.matcher(t);
		if (!m.matches())
		{
			return null;
		}
		return t.toLowerCase().startsWith("bought") ? "buy" : "sell";
	}

	static int priceEach(String text, int qty)
	{
		String t = plain(text);
		if (t.isEmpty() || qty <= 0)
		{
			return 0;
		}
		Matcher tax = TAX_TOTAL.matcher(t);
		if (tax.find())
		{
			long total = parseNumber(tax.group(1));
			return total > 0 ? (int) (total / qty) : 0;
		}
		Matcher each = EACH.matcher(t);
		if (each.find())
		{
			return (int) parseNumber(each.group(1));
		}
		Matcher digits = DIGITS.matcher(t);
		if (digits.find())
		{
			long n = parseNumber(digits.group(1));
			if (n <= 0)
			{
				return 0;
			}
			if (qty > 1 && n % qty == 0 && n > qty)
			{
				return (int) (n / qty);
			}
			return (int) n;
		}
		return 0;
	}

	static String plain(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replaceAll("<[^>]+>", " ").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static long parseNumber(String raw)
	{
		if (raw == null)
		{
			return 0;
		}
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < raw.length(); i++)
		{
			char c = raw.charAt(i);
			if (c >= '0' && c <= '9')
			{
				s.append(c);
			}
		}
		if (s.length() == 0)
		{
			return 0;
		}
		try
		{
			return Long.parseLong(s.toString());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
