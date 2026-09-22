package com.osrsmarket.ledger;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.List;
import net.runelite.http.api.item.ItemPrice;
import org.junit.Test;

public class ItemSearchRankTest
{
	@Test
	public void exactAndPrefixBeatContains()
	{
		ItemPrice just = price(1, "Justiciar faceguard");
		ItemPrice face = price(2, "Faceguard");
		ItemPrice ring = price(3, "Ring of suffering (i)");
		List<ItemPrice> ranked = OsrsMarketLedgerPanel.rankHits(
			"just",
			Arrays.asList(ring, just, face));
		assertEquals("Justiciar faceguard", ranked.get(0).getName());
	}

	private static ItemPrice price(int id, String name)
	{
		ItemPrice p = new ItemPrice();
		p.setId(id);
		p.setName(name);
		return p;
	}
}
