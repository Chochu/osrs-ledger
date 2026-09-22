package com.osrsmarket.ledger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeLastSearchTest
{
	@Test
	public void detectsBuyAndSellSearch()
	{
		assertTrue(GeLastSearch.isSearchTitle("What would you like to buy?"));
		assertTrue(GeLastSearch.isSearchTitle("What would you like to sell?"));
		assertTrue(GeLastSearch.isSearchTitle("What would you like to buy? *"));
		assertTrue(GeLastSearch.isSearchTitle("<col=000000>What would you like to buy?</col>"));
	}

	@Test
	public void ignoresOtherPrompts()
	{
		assertFalse(GeLastSearch.isSearchTitle("Set a price for each item:"));
		assertFalse(GeLastSearch.isSearchTitle("How many do you wish to buy?"));
		assertFalse(GeLastSearch.isSearchTitle(""));
		assertFalse(GeLastSearch.isSearchTitle(null));
	}
}
