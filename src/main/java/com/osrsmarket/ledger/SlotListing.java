package com.osrsmarket.ledger;

/** Saved GE slot listing clock: item + price + qty identify the same offer. */
final class SlotListing
{
	final int itemId;
	final int price;
	final int qty;
	final long listedAt;
	final long lastFillAt;

	SlotListing(int itemId, int price, int qty, long listedAt, long lastFillAt)
	{
		this.itemId = itemId;
		this.price = price;
		this.qty = qty;
		this.listedAt = listedAt;
		this.lastFillAt = lastFillAt;
	}

	boolean sameOffer(int itemId, int price, int qty)
	{
		return this.itemId == itemId && this.price == price && this.qty == qty && listedAt > 0;
	}

	String encode()
	{
		return itemId + ":" + price + ":" + qty + ":" + listedAt + ":" + lastFillAt;
	}

	static SlotListing parse(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		String[] parts = raw.split(":");
		if (parts.length < 4)
		{
			return null;
		}
		try
		{
			int itemId = Integer.parseInt(parts[0]);
			int price = Integer.parseInt(parts[1]);
			int qty = Integer.parseInt(parts[2]);
			long listedAt = Long.parseLong(parts[3]);
			long lastFillAt = parts.length > 4 ? Long.parseLong(parts[4]) : 0L;
			if (itemId <= 0 || listedAt <= 0)
			{
				return null;
			}
			return new SlotListing(itemId, price, qty, listedAt, lastFillAt);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	static String key(int slot)
	{
		return "listed." + slot;
	}
}
