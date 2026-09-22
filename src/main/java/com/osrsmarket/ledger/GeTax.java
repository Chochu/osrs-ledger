package com.osrsmarket.ledger;

final class GeTax
{
	static final int TAX_RATE_NUM = 2;
	static final int TAX_RATE_DEN = 100;
	static final int TAX_CAP = 5_000_000;

	private GeTax()
	{
	}

	static int tax(int sellPrice)
	{
		if (sellPrice <= 0)
		{
			return 0;
		}
		return Math.min(Math.floorDiv(sellPrice * TAX_RATE_NUM, TAX_RATE_DEN), TAX_CAP);
	}

	static int afterTax(int sellPrice)
	{
		return Math.max(0, sellPrice - tax(sellPrice));
	}

	/** Smallest sell price where after-tax proceeds cover {@code avgCost}. */
	static int breakeven(int avgCost)
	{
		if (avgCost <= 0)
		{
			return 0;
		}
		int lo = avgCost;
		int hi = avgCost + TAX_CAP + 1;
		while (lo < hi)
		{
			int mid = lo + (hi - lo) / 2;
			if (afterTax(mid) >= avgCost)
			{
				hi = mid;
			}
			else
			{
				lo = mid + 1;
			}
		}
		return lo;
	}
}
