package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeOfferInputTest
{
	@Test
	public void detectsPricePrompt()
	{
		assertEquals(GeOfferInput.Kind.PRICE, GeOfferInput.kindFromTitle("Set a price for each item:"));
	}

	@Test
	public void detectsQuantityPrompts()
	{
		assertEquals(GeOfferInput.Kind.QUANTITY, GeOfferInput.kindFromTitle("How many do you wish to buy?"));
		assertEquals(GeOfferInput.Kind.QUANTITY, GeOfferInput.kindFromTitle("How many do you wish to sell?"));
	}

	@Test
	public void ignoresSearchAndEmpty()
	{
		assertEquals(GeOfferInput.Kind.NONE, GeOfferInput.kindFromTitle("What would you like to buy?"));
		assertEquals(GeOfferInput.Kind.NONE, GeOfferInput.kindFromTitle(""));
		assertEquals(GeOfferInput.Kind.NONE, GeOfferInput.kindFromTitle(null));
	}
}
