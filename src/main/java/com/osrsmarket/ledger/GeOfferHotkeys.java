package com.osrsmarket.ledger;

import java.awt.event.KeyEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;

@Singleton
final class GeOfferHotkeys implements KeyListener
{
	private final Client client;
	private final ClientThread clientThread;
	private final OsrsMarketLedgerConfig config;

	private volatile GeOfferInput.Kind kind = GeOfferInput.Kind.NONE;
	private volatile int wikiBuy;
	private volatile int wikiSell;
	private volatile int geLimit;
	private volatile int allQty;

	@Inject
	GeOfferHotkeys(Client client, ClientThread clientThread, OsrsMarketLedgerConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
	}

	void setContext(GeOfferInput.Kind kind, int wikiBuy, int wikiSell, int geLimit, int allQty)
	{
		this.kind = kind == null ? GeOfferInput.Kind.NONE : kind;
		this.wikiBuy = wikiBuy;
		this.wikiSell = wikiSell;
		this.geLimit = geLimit;
		this.allQty = allQty;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		GeOfferInput.Kind open = kind;
		if (open == GeOfferInput.Kind.NONE)
		{
			return;
		}
		int value = 0;
		if (open == GeOfferInput.Kind.PRICE)
		{
			if (config.wikiBuyHotkey().matches(e))
			{
				value = wikiBuy;
			}
			else if (config.wikiSellHotkey().matches(e))
			{
				value = wikiSell;
			}
		}
		else if (open == GeOfferInput.Kind.QUANTITY)
		{
			if (config.geLimitHotkey().matches(e))
			{
				value = geLimit;
			}
			else if (config.allQtyHotkey().matches(e))
			{
				value = allQty;
			}
		}
		if (value <= 0)
		{
			return;
		}
		e.consume();
		int typed = value;
		clientThread.invoke(() -> GeOfferInput.type(client, typed));
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}
}
