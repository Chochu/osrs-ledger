package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class GeHistoryExtractorTest
{
	@Test
	public void oldestFirstReversesNewestFirst()
	{
		GeHistoryExtractor.Fill a = new GeHistoryExtractor.Fill(1, "buy", 1, 100);
		GeHistoryExtractor.Fill b = new GeHistoryExtractor.Fill(2, "sell", 2, 200);
		List<GeHistoryExtractor.Fill> out = GeHistoryExtractor.oldestFirst(Arrays.asList(a, b));
		assertEquals(2, out.size());
		assertEquals(2, out.get(0).itemId);
		assertEquals(1, out.get(1).itemId);
	}

	@Test
	public void parseSideRecognizesBoughtAndSold()
	{
		assertEquals("buy", GeHistoryExtractor.parseSide("Bought"));
		assertEquals("sell", GeHistoryExtractor.parseSide("Sold"));
		assertEquals("sell", GeHistoryExtractor.parseSide("Sold:"));
		assertEquals("buy", GeHistoryExtractor.parseSide("Bought:"));
		assertEquals(null, GeHistoryExtractor.parseSide("Other"));
		assertEquals(null, GeHistoryExtractor.parseSide("Dragon arrowtips x 13,000"));
	}

	@Test
	public void priceEachPrefersTaxPreTotal()
	{
		// 26,900,000 - 520,000 → listed 1345 each for qty 20000
		assertEquals(1345, GeHistoryExtractor.priceEach("26,380,000 coins (26,900,000 - 520,000)", 20_000));
	}

	@Test
	public void qtyFromNamePrefersTextOverIcon()
	{
		assertEquals(16585, GeHistoryExtractor.qtyFromName("16,585 x Venator tooth", 20_000));
		assertEquals(13_000, GeHistoryExtractor.qtyFromName("Dragon arrowtips x 13,000", 20_000));
		assertEquals(20_000, GeHistoryExtractor.qtyFromName("Venator tooth", 20_000));
	}

	@Test
	public void leftoverSoldSpriteDoesNotInventDartTip()
	{
		int dartTip = 11232;
		int arrowtips = 11237;
		List<GeHistoryExtractor.Slot> row = Arrays.asList(
			new GeHistoryExtractor.Slot("Sold:", dartTip, 20_000),
			new GeHistoryExtractor.Slot("Dragon arrowtips x 13,000", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", arrowtips, 13_000),
			new GeHistoryExtractor.Slot("24,713,000 coins (25,207,000 - 494,000) = 1,901 each", 0, 0)
		);
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(row);
		assertEquals(1, fills.size());
		assertEquals(arrowtips, fills.get(0).itemId);
		assertEquals("sell", fills.get(0).side);
		assertEquals(13_000, fills.get(0).qty);
		assertEquals(1939, fills.get(0).priceEach);
	}

	@Test
	public void extraSpacerBetweenSoldAndItemStillReads()
	{
		int arrowtips = 11237;
		List<GeHistoryExtractor.Slot> row = Arrays.asList(
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("Dragon arrowtips x 13,000", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", arrowtips, 13_000),
			new GeHistoryExtractor.Slot("24,713,000 coins (25,207,000 - 494,000) = 1,901 each", 0, 0)
		);
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(row);
		assertEquals(1, fills.size());
		assertEquals(arrowtips, fills.get(0).itemId);
		assertEquals(13_000, fills.get(0).qty);
	}

	@Test
	public void nameWithoutQtyUsesIconStack()
	{
		int arrowtips = 11237;
		List<GeHistoryExtractor.Slot> row = Arrays.asList(
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("Dragon arrowtips", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", arrowtips, 13_000),
			new GeHistoryExtractor.Slot("24,713,000 coins (25,207,000 - 494,000) = 1,901 each", 0, 0)
		);
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(row);
		assertEquals(1, fills.size());
		assertEquals(arrowtips, fills.get(0).itemId);
		assertEquals(13_000, fills.get(0).qty);
		assertEquals(1939, fills.get(0).priceEach);
	}

	@Test
	public void twoRealRowsBothExtract()
	{
		int arrowtips = 11237;
		int fang = 22316;
		List<GeHistoryExtractor.Slot> rows = Arrays.asList(
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("Dragon arrowtips x 13,000", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", arrowtips, 13_000),
			new GeHistoryExtractor.Slot("24,713,000 coins (25,207,000 - 494,000) = 1,901 each", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("Venator fang x 13,000", 0, 0),
			new GeHistoryExtractor.Slot("Bought:", 0, 0),
			new GeHistoryExtractor.Slot("", 0, 0),
			new GeHistoryExtractor.Slot("", fang, 13_000),
			new GeHistoryExtractor.Slot("23,907,000 coins 1,839 each", 0, 0)
		);
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(rows);
		assertEquals(2, fills.size());
		assertEquals(arrowtips, fills.get(0).itemId);
		assertEquals("sell", fills.get(0).side);
		assertEquals(fang, fills.get(1).itemId);
		assertEquals("buy", fills.get(1).side);
		assertEquals(13_000, fills.get(1).qty);
		assertEquals(1839, fills.get(1).priceEach);
	}

	@Test
	public void rowContainersKeepEveryTradeWhenFlattenWouldDropThem()
	{
		int holy = 13149;
		int orb = 24511;
		List<GeHistoryExtractor.Slot> holyRow = Arrays.asList(
			new GeHistoryExtractor.Slot("Holy book page set", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", holy, 1),
			new GeHistoryExtractor.Slot("12,917 coins (13,180 - 263)", 0, 0)
		);
		List<GeHistoryExtractor.Slot> orbRow = Arrays.asList(
			new GeHistoryExtractor.Slot("Harmonised orb", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("", orb, 1),
			new GeHistoryExtractor.Slot("470,000,000 coins (475,000,000 - 5,000,000)", 0, 0)
		);
		List<GeHistoryExtractor.Fill> grouped = GeHistoryExtractor.fromRowGroups(
			Arrays.asList(holyRow, orbRow));
		assertEquals(2, grouped.size());
		assertEquals(holy, grouped.get(0).itemId);
		assertEquals(orb, grouped.get(1).itemId);
		assertEquals(475_000_000, grouped.get(1).priceEach);

		List<GeHistoryExtractor.Slot> columnMajor = new ArrayList<>();
		columnMajor.addAll(Arrays.asList(
			new GeHistoryExtractor.Slot("Holy book page set", 0, 0),
			new GeHistoryExtractor.Slot("Harmonised orb", 0, 0)));
		columnMajor.addAll(Arrays.asList(
			new GeHistoryExtractor.Slot("Sold:", 0, 0),
			new GeHistoryExtractor.Slot("Sold:", 0, 0)));
		columnMajor.addAll(Arrays.asList(
			new GeHistoryExtractor.Slot("", holy, 1),
			new GeHistoryExtractor.Slot("", orb, 1)));
		columnMajor.addAll(Arrays.asList(
			new GeHistoryExtractor.Slot("12,917 coins (13,180 - 263)", 0, 0),
			new GeHistoryExtractor.Slot("470,000,000 coins (475,000,000 - 5,000,000)", 0, 0)));
		assertEquals(1, GeHistoryExtractor.fromSlots(columnMajor).size());
	}

	@Test
	public void jagexHistoryIsSoldThenNameThenIconThenPrice()
	{
		int holy = 13149;
		int orb = 24511;
		int arrowtips = 11237;
		int fang = 33661;
		int ags = 26233;
		List<GeHistoryExtractor.Slot> rows = new ArrayList<>();
		rows.addAll(jagexHistoryRow("Sold:", "Holy book page set", holy, 1,
			"12,917 coins (13,180 - 263)"));
		rows.addAll(jagexHistoryRow("Sold:", "Harmonised orb", orb, 1,
			"470,000,000 coins (475,000,000 - 5,000,000)"));
		rows.addAll(jagexHistoryRow("Sold:", "Dragon arrowtips x 4,007", arrowtips, 4007,
			"7,617,307 coins (7,769,573 - 152,266)"));
		rows.addAll(jagexHistoryRow("Bought:", "Harmonised orb", orb, 1,
			"462,500,000 coins"));
		rows.addAll(jagexHistoryRow("Bought:", "Venator fang x 4,007", fang, 4007,
			"7,368,873 coins = 1,839 each"));
		rows.addAll(jagexHistoryRow("Bought:", "Ancient godsword", ags, 1,
			"33,700,000 coins"));
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(rows);
		assertEquals(6, fills.size());
		assertEquals(holy, fills.get(0).itemId);
		assertEquals("sell", fills.get(0).side);
		assertEquals(13_180, fills.get(0).priceEach);
		assertEquals(orb, fills.get(1).itemId);
		assertEquals(475_000_000, fills.get(1).priceEach);
		assertEquals(arrowtips, fills.get(2).itemId);
		assertEquals(4007, fills.get(2).qty);
		assertEquals(1939, fills.get(2).priceEach);
		assertEquals("buy", fills.get(3).side);
		assertEquals(462_500_000, fills.get(3).priceEach);
		assertEquals(fang, fills.get(4).itemId);
		assertEquals(1839, fills.get(4).priceEach);
		assertEquals(ags, fills.get(5).itemId);
		assertEquals(33_700_000, fills.get(5).priceEach);
	}

	private static List<GeHistoryExtractor.Slot> jagexHistoryRow(
		String side, String name, int itemId, int qty, String price)
	{
		return Arrays.asList(
			new GeHistoryExtractor.Slot("", -1, 0),
			new GeHistoryExtractor.Slot("", -1, 0),
			new GeHistoryExtractor.Slot(side, -1, 0),
			new GeHistoryExtractor.Slot(name, -1, 0),
			new GeHistoryExtractor.Slot("", itemId, qty),
			new GeHistoryExtractor.Slot(price, -1, 0)
		);
	}

	@Test
	public void leftoverEmptyHistorySlotsDoNotInventFills()
	{
		List<GeHistoryExtractor.Slot> rows = new ArrayList<>();
		rows.addAll(jagexHistoryRow("Bought:", "Venator fang x 18,489", 33661, 18_489,
			"34,056,738 coins = 1,842 each"));
		rows.addAll(jagexHistoryRow("Sold:", "Holy book page set", 13149, 1,
			"12,917 coins (13,180 - 263)"));
		for (int i = 0; i < 12; i++)
		{
			rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		}
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(rows);
		assertEquals(2, fills.size());
		assertEquals(18_489, fills.get(0).qty);
		assertEquals(1_842, fills.get(0).priceEach);
		assertEquals(13149, fills.get(1).itemId);
	}

	@Test
	public void hiddenLeftoverHistoryRowIsIgnored()
	{
		List<GeHistoryExtractor.Slot> rows = new ArrayList<>();
		rows.addAll(jagexHistoryRow("Bought:", "Venator fang x 18,489", 33661, 18_489,
			"34,056,738 coins = 1,842 each"));
		rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("Bought:", -1, 0, true));
		rows.add(new GeHistoryExtractor.Slot("Venator fang x 12,000", -1, 0, true));
		rows.add(new GeHistoryExtractor.Slot("", 33661, 12_000, true));
		rows.add(new GeHistoryExtractor.Slot("22,092,000 coins = 1,841 each", -1, 0, true));
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(rows);
		assertEquals(1, fills.size());
		assertEquals(18_489, fills.get(0).qty);
		assertEquals(1_842, fills.get(0).priceEach);
	}

	@Test
	public void leftoverSoldWithoutNameInStrideIsSkipped()
	{
		List<GeHistoryExtractor.Slot> rows = new ArrayList<>();
		rows.addAll(jagexHistoryRow("Bought:", "Venator fang x 18,489", 33661, 18_489,
			"34,056,738 coins = 1,842 each"));
		rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("Bought:", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("", -1, 0));
		rows.add(new GeHistoryExtractor.Slot("", 33661, 12_000));
		rows.add(new GeHistoryExtractor.Slot("22,092,000 coins = 1,841 each", -1, 0));
		List<GeHistoryExtractor.Fill> fills = GeHistoryExtractor.fromSlots(rows);
		assertEquals(1, fills.size());
		assertEquals(18_489, fills.get(0).qty);
	}

	@Test
	public void looksLikePriceRequiresAPriceLine()
	{
		assertEquals(true, GeHistoryExtractor.looksLikePrice("20,399,550 coins 1,230 each"));
		assertEquals(true, GeHistoryExtractor.looksLikePrice("5,560 coins (5,673 - 113)"));
		assertEquals(false, GeHistoryExtractor.looksLikePrice("Venator tooth"));
		assertEquals(false, GeHistoryExtractor.looksLikePrice("Bought"));
	}
}
