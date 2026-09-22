package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.Rectangle;
import org.junit.Test;

public class GeOfferStatusPnlTest
{
	@Test
	public void sitsUnderAOneLineItemName()
	{
		Rectangle name = new Rectangle(120, 80, 90, 14);
		Rectangle slot = GeOfferStatusPnl.slot(name, null, new Rectangle(40, 40, 400, 300), 3);
		assertEquals(120, slot.x);
		assertEquals(96, slot.y);
	}

	@Test
	public void ignoresATallDescriptionBoxAndUsesTheIcon()
	{
		Rectangle details = new Rectangle(40, 40, 400, 300);
		Rectangle tallDesc = new Rectangle(40, 40, 200, 80);
		Rectangle icon = new Rectangle(50, 70, 36, 32);
		Rectangle slot = GeOfferStatusPnl.slot(tallDesc, icon, details, 3);
		assertEquals(94, slot.x);
		assertEquals(85, slot.y);
		assertFalse(slot.x < 70 && slot.y < 60);
	}

	@Test
	public void skipsTheSellOfferHeaderWhenNothingElseIsFound()
	{
		Rectangle details = new Rectangle(40, 40, 400, 300);
		Rectangle slot = GeOfferStatusPnl.slot(null, null, details, 3);
		assertTrue(slot.x >= details.x + 70);
		assertTrue(slot.y >= details.y + 40);
	}
}
