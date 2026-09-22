package com.osrsmarket.ledger;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

final class GeOfferQty
{
	private GeOfferQty()
	{
	}

	static boolean apply(Client client, int qty)
	{
		if (qty <= 0)
		{
			return false;
		}
		GeOfferInput.Kind kind = GeOfferInput.kind(client);
		if (kind == GeOfferInput.Kind.QUANTITY)
		{
			return GeOfferInput.type(client, qty);
		}
		if (kind == GeOfferInput.Kind.PRICE)
		{
			return false;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return false;
		}
		client.setVarbit(VarbitID.GE_NEWOFFER_QUANTITY, qty);
		Object[] listener = setup.getOnVarTransmitListener();
		if (listener != null && listener.length > 0)
		{
			try
			{
				client.runScript(listener);
			}
			catch (RuntimeException ignored)
			{
				/* setup widgets vary by client revision */
			}
		}
		return true;
	}
}
