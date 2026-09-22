package com.osrsmarket.ledger;

/** Time-on-market for a GE slot. Times are epoch millis; 0 means unknown. */
final class SlotIdle
{
	static final long STALL_FRESH_MS = 10 * 60 * 1000L;
	static final long STALL_AFTER_FILL_MS = 15 * 60 * 1000L;

	private SlotIdle()
	{
	}

	static long idleMs(long now, long listedAt, long lastFillAt)
	{
		if (listedAt <= 0)
		{
			return 0;
		}
		long from = lastFillAt > listedAt ? lastFillAt : listedAt;
		return Math.max(0, now - from);
	}

	static int idleMinutes(long now, long listedAt, long lastFillAt)
	{
		return (int) (idleMs(now, listedAt, lastFillAt) / 60_000L);
	}

	static boolean stalled(long now, long listedAt, long lastFillAt, int qtyFilled, boolean listed)
	{
		if (!listed || listedAt <= 0)
		{
			return false;
		}
		long idle = idleMs(now, listedAt, lastFillAt);
		if (qtyFilled <= 0)
		{
			return idle >= STALL_FRESH_MS;
		}
		return lastFillAt > 0 && idle >= STALL_AFTER_FILL_MS;
	}

	static String label(long now, long listedAt, long lastFillAt, int qtyFilled, boolean listed)
	{
		if (!listed || listedAt <= 0)
		{
			return "";
		}
		long listedMs = Math.max(0, now - listedAt);
		boolean stall = stalled(now, listedAt, lastFillAt, qtyFilled, listed);
		if (lastFillAt > listedAt)
		{
			String fill = "fill " + compactDuration(now - lastFillAt) + " ago";
			return stall ? fill + " · stalled" : fill;
		}
		String listedFor = "listed " + compactDuration(listedMs);
		if (qtyFilled <= 0 && stall)
		{
			return listedFor + " · not moving";
		}
		return stall ? listedFor + " · stalled" : listedFor;
	}

	static String compactDuration(long ms)
	{
		long sec = Math.max(0, ms / 1000L);
		if (sec < 60)
		{
			return sec + "s";
		}
		long min = sec / 60;
		if (min < 60)
		{
			return min + "m";
		}
		long hr = min / 60;
		long rem = min % 60;
		return rem == 0 ? hr + "h" : hr + "h " + rem + "m";
	}
}
