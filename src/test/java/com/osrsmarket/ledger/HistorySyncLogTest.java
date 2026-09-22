package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class HistorySyncLogTest
{
	@Test
	public void unseenPrefixImportsOnlyRowsAboveTheLastWindow()
	{
		GeHistoryExtractor.Fill tipsNew = new GeHistoryExtractor.Fill(11237, "sell", 25420, 1940);
		GeHistoryExtractor.Fill fangNew = new GeHistoryExtractor.Fill(33661, "buy", 36615, 1841);
		GeHistoryExtractor.Fill ags = new GeHistoryExtractor.Fill(26233, "sell", 1, 35999999);
		GeHistoryExtractor.Fill fangOld = new GeHistoryExtractor.Fill(33661, "buy", 9000, 1841);
		GeHistoryExtractor.Fill tipsOld = new GeHistoryExtractor.Fill(11237, "sell", 11784, 1940);
		GeHistoryExtractor.Fill shard = new GeHistoryExtractor.Fill(11818, "buy", 1, 153942);
		GeHistoryExtractor.Fill tooth = new GeHistoryExtractor.Fill(33663, "buy", 9865, 1285);
		GeHistoryExtractor.Fill beef = new GeHistoryExtractor.Fill(2132, "buy", 1000, 47);
		List<String> stored = Arrays.asList(
			fangOld.tupleKey(), tipsOld.tupleKey(), shard.tupleKey());
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(
			tipsNew, fangNew, ags, fangOld, tipsOld, shard, tooth, beef);
		List<GeHistoryExtractor.Fill> prefix = HistorySyncLog.unseenPrefix(
			newest, stored, Collections.emptySet());
		assertEquals(3, prefix.size());
		assertEquals(25420, prefix.get(0).qty);
		assertEquals(36615, prefix.get(1).qty);
		assertEquals(26233, prefix.get(2).itemId);
	}

	@Test
	public void unseenPrefixIsEmptyWhenHistoryDidNotMove()
	{
		GeHistoryExtractor.Fill a = new GeHistoryExtractor.Fill(1, "buy", 1, 10);
		GeHistoryExtractor.Fill b = new GeHistoryExtractor.Fill(2, "sell", 2, 20);
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(a, b);
		List<String> stored = Arrays.asList(a.tupleKey(), b.tupleKey());
		assertEquals(0, HistorySyncLog.unseenPrefix(newest, stored, Collections.emptySet()).size());
	}

	@Test
	public void unseenPrefixKeepsARepeatedTupleWhenItIsActuallyNew()
	{
		GeHistoryExtractor.Fill buy = new GeHistoryExtractor.Fill(33661, "buy", 8000, 1841);
		GeHistoryExtractor.Fill other = new GeHistoryExtractor.Fill(1, "sell", 1, 50);
		List<String> stored = Arrays.asList(buy.tupleKey(), other.tupleKey());
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(buy, buy, other);
		List<GeHistoryExtractor.Fill> prefix = HistorySyncLog.unseenPrefix(
			newest, stored, Collections.emptySet());
		assertEquals(1, prefix.size());
		assertEquals(8000, prefix.get(0).qty);
	}

	@Test
	public void unseenPrefixStopsAtKnownTupleWhenSnapshotIsMissing()
	{
		GeHistoryExtractor.Fill tipsNew = new GeHistoryExtractor.Fill(11237, "sell", 25420, 1940);
		GeHistoryExtractor.Fill fangOld = new GeHistoryExtractor.Fill(33661, "buy", 9000, 1841);
		GeHistoryExtractor.Fill tooth = new GeHistoryExtractor.Fill(33663, "buy", 9865, 1285);
		Set<String> known = new HashSet<>(Collections.singletonList(fangOld.tupleKey()));
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(tipsNew, fangOld, tooth);
		List<GeHistoryExtractor.Fill> prefix = HistorySyncLog.unseenPrefix(
			newest, Collections.emptyList(), known);
		assertEquals(1, prefix.size());
		assertEquals(25420, prefix.get(0).qty);
	}

	@Test
	public void unseenPrefixIsEmptyWhenALiveCompleteRacedAhead()
	{
		GeHistoryExtractor.Fill complete = new GeHistoryExtractor.Fill(33661, "buy", 8000, 1841);
		GeHistoryExtractor.Fill a = new GeHistoryExtractor.Fill(1, "buy", 1, 10);
		GeHistoryExtractor.Fill b = new GeHistoryExtractor.Fill(2, "sell", 2, 20);
		List<String> stored = Arrays.asList(complete.tupleKey(), a.tupleKey(), b.tupleKey());
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(a, b);
		assertEquals(0, HistorySyncLog.unseenPrefix(newest, stored, Collections.emptySet()).size());
	}

	@Test
	public void unseenPrefixStopsAtLastJournalRowAfterNewerTrades()
	{
		GeHistoryExtractor.Fill fangNew = new GeHistoryExtractor.Fill(33661, "buy", 27447, 1842);
		GeHistoryExtractor.Fill tipsNew = new GeHistoryExtractor.Fill(11237, "sell", 40256, 1940);
		GeHistoryExtractor.Fill water = new GeHistoryExtractor.Fill(555, "buy", 150000, 6);
		GeHistoryExtractor.Fill air = new GeHistoryExtractor.Fill(556, "buy", 150000, 6);
		GeHistoryExtractor.Fill tipsOld = new GeHistoryExtractor.Fill(11237, "sell", 25420, 1940);
		GeHistoryExtractor.Fill fangOld = new GeHistoryExtractor.Fill(33661, "buy", 36615, 1841);
		Set<String> known = new HashSet<>(Collections.singletonList(tipsOld.tupleKey()));
		List<GeHistoryExtractor.Fill> newest = Arrays.asList(
			fangNew, tipsNew, water, air, tipsOld, fangOld);
		List<GeHistoryExtractor.Fill> prefix = HistorySyncLog.unseenPrefix(
			newest, Collections.emptyList(), known);
		assertEquals(4, prefix.size());
		assertEquals(27447, prefix.get(0).qty);
		assertEquals(40256, prefix.get(1).qty);
		assertEquals(true, HistorySyncLog.journalAligns(newest.size(), prefix.size(), true));
		assertEquals(false, HistorySyncLog.journalAligns(6, 6, true));
	}
}
