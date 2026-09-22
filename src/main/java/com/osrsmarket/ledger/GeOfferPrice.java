package com.osrsmarket.ledger;

import net.runelite.api.Client;

final class GeOfferPrice
{
	private GeOfferPrice()
	{
	}

	/** Types into “Set a price for each item”. Does not confirm or set the offer. */
	static boolean apply(Client client, int price)
	{
		if (price <= 0 || GeOfferInput.kind(client) != GeOfferInput.Kind.PRICE)
		{
			return false;
		}
		return GeOfferInput.type(client, price);
	}
}
