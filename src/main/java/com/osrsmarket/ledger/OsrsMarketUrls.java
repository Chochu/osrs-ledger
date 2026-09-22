package com.osrsmarket.ledger;

final class OsrsMarketUrls
{
	static final String ORIGIN = "https://osrsledger.com";

	private OsrsMarketUrls()
	{
	}

	static String trimSlash(String url)
	{
		if (url == null)
		{
			return "";
		}
		String s = url.trim();
		while (s.endsWith("/"))
		{
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	static String apiOrigin()
	{
		return ORIGIN;
	}

	static String siteOrigin()
	{
		return ORIGIN;
	}

	static String itemPage(int itemId)
	{
		return siteOrigin() + "/item/" + itemId;
	}

	static String ledgerPage()
	{
		return siteOrigin() + "/ledger";
	}

	static String trackerPage()
	{
		return siteOrigin() + "/";
	}
}
