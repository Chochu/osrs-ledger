package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlotIdleTest
{
	@Test
	public void freshOfferIsNotStalled()
	{
		long listed = 1_000_000L;
		long now = listed + 3 * 60_000L;
		assertFalse(SlotIdle.stalled(now, listed, 0, 0, true));
		assertEquals("listed 3m", SlotIdle.label(now, listed, 0, 0, true));
	}

	@Test
	public void noFillsForTenMinutesIsNotMoving()
	{
		long listed = 1_000_000L;
		long now = listed + SlotIdle.STALL_FRESH_MS;
		assertTrue(SlotIdle.stalled(now, listed, 0, 0, true));
		assertEquals("listed 10m · not moving", SlotIdle.label(now, listed, 0, 0, true));
	}

	@Test
	public void fillResetsIdleUntilFifteenMinutes()
	{
		long listed = 1_000_000L;
		long fill = listed + 2 * 60_000L;
		long now = fill + 5 * 60_000L;
		assertFalse(SlotIdle.stalled(now, listed, fill, 4, true));
		assertEquals("fill 5m ago", SlotIdle.label(now, listed, fill, 4, true));
		long stalledAt = fill + SlotIdle.STALL_AFTER_FILL_MS;
		assertTrue(SlotIdle.stalled(stalledAt, listed, fill, 4, true));
		assertEquals("fill 15m ago · stalled", SlotIdle.label(stalledAt, listed, fill, 4, true));
	}

	@Test
	public void emptyOrUnlistedHasNoLabel()
	{
		assertEquals("", SlotIdle.label(10, 1, 0, 0, false));
		assertFalse(SlotIdle.stalled(10, 1, 0, 0, false));
	}
}
