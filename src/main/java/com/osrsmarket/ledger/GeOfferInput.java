package com.osrsmarket.ledger;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

final class GeOfferInput
{
	enum Kind
	{
		NONE,
		PRICE,
		QUANTITY
	}

	private GeOfferInput()
	{
	}

	static Kind kind(Client client)
	{
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (title == null || title.isHidden())
		{
			return Kind.NONE;
		}
		return kindFromTitle(title.getText());
	}

	static Kind kindFromTitle(String title)
	{
		if (title == null)
		{
			return Kind.NONE;
		}
		if (title.equals("Set a price for each item:"))
		{
			return Kind.PRICE;
		}
		if (title.equals("How many do you wish to buy?")
			|| title.equals("How many do you wish to sell?"))
		{
			return Kind.QUANTITY;
		}
		return Kind.NONE;
	}

	static boolean type(Client client, int value)
	{
		if (value <= 0 || kind(client) == Kind.NONE)
		{
			return false;
		}
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (input != null)
		{
			input.setText(value + "*");
		}
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(value));
		return true;
	}
}
