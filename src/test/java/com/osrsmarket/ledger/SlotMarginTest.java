package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlotMarginTest
{
	@Test
	public void buyOnBuyEdgeSuggestsOverbid()
	{
		WikiQuotes.Quote quote = new WikiQuotes.Quote(19_342_268, 18_846_745);
		SlotMargin margin = SlotMargin.analyze(true, false, 18_705_000, 0, 2, quote, 0);
		assertEquals(SlotMargin.Health.GOOD, margin.health);
		assertEquals(Integer.valueOf(19_342_268), margin.instantBuy);
		assertEquals(Integer.valueOf(18_846_745), margin.instantSell);
		assertTrue(margin.adjust.contains("On the buy edge"));
		assertTrue(margin.adjust.contains("18,846,746"));
	}

	@Test
	public void buyAboveInstantBuyIsOutOfMargin()
	{
		WikiQuotes.Quote quote = new WikiQuotes.Quote(100, 90);
		SlotMargin margin = SlotMargin.analyze(true, false, 100, 0, 1, quote, 0);
		assertEquals(SlotMargin.Health.BAD, margin.health);
	}

	@Test
	public void taxCapsPastMaxCash()
	{
		assertEquals(5_000_000L, GeTax.tax(3_000_000_000L));
	}

	@Test
	public void sellOnSellEdgeSuggestsUndercut()
	{
		WikiQuotes.Quote quote = new WikiQuotes.Quote(100, 90);
		SlotMargin margin = SlotMargin.analyze(false, true, 100, 0, 1, quote, 0);
		assertEquals(SlotMargin.Health.GOOD, margin.health);
		assertTrue(margin.adjust.contains("On the sell edge"));
		assertTrue(margin.adjust.contains("99"));
	}
}
