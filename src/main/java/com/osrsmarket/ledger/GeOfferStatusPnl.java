package com.osrsmarket.ledger;

import java.awt.Rectangle;

final class GeOfferStatusPnl
{
	static final int LINE_H = 13;

	private GeOfferStatusPnl()
	{
	}

	static boolean isNameLine(Rectangle bounds)
	{
		return bounds != null && bounds.width > 8 && bounds.height > 6 && bounds.height <= 24;
	}

	static boolean isOfferIcon(Rectangle bounds, Rectangle details)
	{
		if (bounds == null || details == null)
		{
			return false;
		}
		if (bounds.width < 24 || bounds.width > 48 || bounds.height < 24 || bounds.height > 48)
		{
			return false;
		}
		return bounds.y < details.y + details.height / 2
			&& bounds.x < details.x + details.width / 2;
	}

	static Rectangle slot(Rectangle name, Rectangle icon, Rectangle details, int lines)
	{
		int h = Math.max(lines, 1) * LINE_H + 2;
		int w = 220;
		if (isNameLine(name))
		{
			return new Rectangle(name.x, name.y + name.height + 2, Math.max(name.width, w), h);
		}
		if (icon != null && icon.width >= 16)
		{
			return new Rectangle(icon.x + icon.width + 8, icon.y + 15, w, h);
		}
		if (details != null && details.width > 0)
		{
			return new Rectangle(details.x + 78, details.y + 50, w, h);
		}
		return null;
	}
}
