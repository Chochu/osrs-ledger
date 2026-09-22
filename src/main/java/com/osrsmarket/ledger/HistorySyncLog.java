package com.osrsmarket.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Newest-first prefix-diff for GE History. The server owns the stored window;
 * this copy is the shared rule used by unit tests.
 */
final class HistorySyncLog
{
	private HistorySyncLog()
	{
	}

	static List<String> tupleKeys(List<GeHistoryExtractor.Fill> newestFirst)
	{
		List<String> out = new ArrayList<>();
		if (newestFirst == null)
		{
			return out;
		}
		int n = Math.min(newestFirst.size(), 40);
		for (int i = 0; i < n; i++)
		{
			out.add(newestFirst.get(i).tupleKey());
		}
		return out;
	}

	/**
	 * Newest-first rows that appeared since {@code stored}. If the snapshot is
	 * behind a live complete that already prepended the window, the prefix is empty.
	 */
	static List<GeHistoryExtractor.Fill> unseenPrefix(
		List<GeHistoryExtractor.Fill> newestFirst,
		List<String> storedNewestFirst,
		Set<String> knownTuples)
	{
		List<GeHistoryExtractor.Fill> empty = new ArrayList<>();
		if (newestFirst == null || newestFirst.isEmpty())
		{
			return empty;
		}
		List<String> newestKeys = tupleKeys(newestFirst);
		if (storedNewestFirst != null && !storedNewestFirst.isEmpty())
		{
			int k = alignIndex(newestKeys, storedNewestFirst);
			if (k >= 0)
			{
				return new ArrayList<>(newestFirst.subList(0, k));
			}
			if (alignIndex(storedNewestFirst, newestKeys) > 0)
			{
				return empty;
			}
		}
		if (knownTuples == null || knownTuples.isEmpty())
		{
			return new ArrayList<>(newestFirst);
		}
		List<GeHistoryExtractor.Fill> out = new ArrayList<>();
		for (GeHistoryExtractor.Fill fill : newestFirst)
		{
			if (knownTuples.contains(fill.tupleKey()))
			{
				break;
			}
			out.add(fill);
		}
		return out;
	}

	/**
	 * True when a journal watermark found the overlap (or the book is empty).
	 * False means the snapshot did not line up — do not dump the full window.
	 */
	static boolean journalAligns(int newestCount, int prefixCount, boolean hasKnown)
	{
		if (prefixCount <= 0)
		{
			return true;
		}
		if (!hasKnown)
		{
			return true;
		}
		return prefixCount < newestCount;
	}

	/** Index where {@code stored} lines up under {@code newest}, or -1. */
	static int alignIndex(List<String> newest, List<String> stored)
	{
		if (newest == null || stored == null || stored.isEmpty() || newest.isEmpty())
		{
			return -1;
		}
		int need = Math.min(2, stored.size());
		for (int k = 0; k < newest.size(); k++)
		{
			int remain = newest.size() - k;
			int n = Math.min(remain, stored.size());
			if (n < need)
			{
				continue;
			}
			boolean ok = true;
			for (int i = 0; i < n; i++)
			{
				if (!newest.get(k + i).equals(stored.get(i)))
				{
					ok = false;
					break;
				}
			}
			if (ok)
			{
				return k;
			}
		}
		return -1;
	}
}
