package com.osrsmarket.ledger;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * Puts an item on the GE "Previous search" / quick-select row
 * (the same slot that remembers Masori mask after you search it).
 */
final class GeLastSearch
{
	private GeLastSearch()
	{
	}

	static boolean isSearchTitle(String title)
	{
		if (title == null)
		{
			return false;
		}
		String plain = title.replaceAll("<[^>]*>", "");
		return plain.contains("What would you like to buy?")
			|| plain.contains("What would you like to sell?");
	}

	static void prime(Client client, int itemId)
	{
		int id = unnoted(client, itemId);
		if (id <= 0)
		{
			return;
		}
		int[] varps = client.getVarps();
		if (varps == null || VarPlayerID.GE_LAST_SEARCHED >= varps.length)
		{
			return;
		}
		varps[VarPlayerID.GE_LAST_SEARCHED] = id;
		try
		{
			client.queueChangedVarp(VarPlayerID.GE_LAST_SEARCHED);
		}
		catch (RuntimeException ignored)
		{
			/* older clients still keep the varp write */
		}
		if (!searchOpen(client))
		{
			return;
		}
		rebuild(client);
		paint(client, id);
	}

	private static int unnoted(Client client, int itemId)
	{
		if (itemId <= 0)
		{
			return -1;
		}
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def != null && def.getNote() != -1)
			{
				int linked = def.getLinkedNoteId();
				if (linked > 0)
				{
					return linked;
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return itemId;
		}
		return itemId;
	}

	private static boolean searchOpen(Client client)
	{
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (title == null || title.isHidden())
		{
			return false;
		}
		return isSearchTitle(title.getText());
	}

	private static void rebuild(Client client)
	{
		Widget contents = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		if (run(client, contents != null ? contents.getOnLoadListener() : null))
		{
			return;
		}
		if (contents != null && run(client, contents.getOnVarTransmitListener()))
		{
			return;
		}
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (run(client, layer != null ? layer.getOnLoadListener() : null))
		{
			return;
		}
		try
		{
			client.runScript(
				ScriptID.GE_ITEM_SEARCH,
				InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS,
				InterfaceID.Chatbox.MES_LAYER_SCROLLBAR,
				0);
		}
		catch (RuntimeException ignored)
		{
			/* search widgets vary by client revision */
		}
	}

	private static boolean run(Client client, Object[] listener)
	{
		if (listener == null || listener.length == 0)
		{
			return false;
		}
		try
		{
			client.runScript(listener);
			return true;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static void paint(Client client, int itemId)
	{
		Widget contents = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		if (contents == null)
		{
			return;
		}
		String name;
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			name = def == null ? null : def.getName();
		}
		catch (RuntimeException e)
		{
			return;
		}
		if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return;
		}
		paintKids(contents.getDynamicChildren(), itemId, name);
		paintKids(contents.getStaticChildren(), itemId, name);
	}

	private static void paintKids(Widget[] kids, int itemId, String name)
	{
		if (kids == null)
		{
			return;
		}
		boolean row = false;
		for (Widget kid : kids)
		{
			if (kid == null)
			{
				continue;
			}
			String text = kid.getText();
			if (text != null && text.contains("Previous search"))
			{
				row = true;
				continue;
			}
			if (!row)
			{
				continue;
			}
			if (text != null && text.contains("Start typing"))
			{
				break;
			}
			if (kid.getType() == WidgetType.GRAPHIC || kid.getItemId() > 0)
			{
				kid.setItemId(itemId);
				kid.setItemQuantity(1);
				kid.setHidden(false);
				continue;
			}
			if (text != null && !text.isEmpty() && !text.contains("Previous search"))
			{
				kid.setText(name);
			}
		}
	}
}
