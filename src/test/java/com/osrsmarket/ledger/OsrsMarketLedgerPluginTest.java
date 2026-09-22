package com.osrsmarket.ledger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsMarketLedgerPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsMarketLedgerPlugin.class);
		RuneLite.main(args);
	}
}
