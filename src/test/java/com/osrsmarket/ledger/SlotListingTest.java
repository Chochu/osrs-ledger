package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlotListingTest
{
	@Test
	public void roundTrip()
	{
		SlotListing saved = new SlotListing(4151, 1000, 50, 1_700_000_000_000L, 1_700_000_100_000L);
		SlotListing parsed = SlotListing.parse(saved.encode());
		assertNotNull(parsed);
		assertTrue(parsed.sameOffer(4151, 1000, 50));
		assertEquals(saved.listedAt, parsed.listedAt);
		assertEquals(saved.lastFillAt, parsed.lastFillAt);
	}

	@Test
	public void differentOfferDoesNotMatch()
	{
		SlotListing saved = new SlotListing(4151, 1000, 50, 100L, 0L);
		assertFalse(saved.sameOffer(4151, 1001, 50));
		assertFalse(saved.sameOffer(4151, 1000, 49));
		assertFalse(saved.sameOffer(11802, 1000, 50));
	}

	@Test
	public void rejectsGarbage()
	{
		assertNull(SlotListing.parse(null));
		assertNull(SlotListing.parse(""));
		assertNull(SlotListing.parse("1:2:3"));
		assertNull(SlotListing.parse("nope"));
		assertNull(SlotListing.parse("0:1:1:100"));
	}

	@Test
	public void slotKeysAreStable()
	{
		assertEquals("listed.0", SlotListing.key(0));
		assertEquals("listed.7", SlotListing.key(7));
	}
}
