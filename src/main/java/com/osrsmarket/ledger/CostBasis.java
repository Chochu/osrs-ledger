package com.osrsmarket.ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
final class CostBasis
{
	private static final class Lot
	{
		int qty;
		final int costEach;

		Lot(int qty, int costEach)
		{
			this.qty = qty;
			this.costEach = costEach;
		}
	}

	private final Map<Integer, List<Lot>> lots = new HashMap<>();
	private final Map<Integer, Integer> lastAvg = new HashMap<>();

	synchronized void replaceAll(Map<Integer, int[]> positions)
	{
		lots.clear();
		if (positions == null)
		{
			return;
		}
		for (Map.Entry<Integer, int[]> e : positions.entrySet())
		{
			int itemId = e.getKey() == null ? 0 : e.getKey();
			int[] qc = e.getValue();
			if (itemId <= 0 || qc == null || qc.length < 2)
			{
				continue;
			}
			int qty = qc[0];
			int avgCost = qc[1];
			if (qty <= 0 || avgCost <= 0)
			{
				continue;
			}
			List<Lot> list = new ArrayList<>();
			list.add(new Lot(qty, avgCost));
			lots.put(itemId, list);
			lastAvg.put(itemId, avgCost);
		}
	}

	synchronized void addBuy(int itemId, int qty, int priceEach)
	{
		if (itemId <= 0 || qty <= 0 || priceEach <= 0)
		{
			return;
		}
		lots.computeIfAbsent(itemId, k -> new ArrayList<>()).add(new Lot(qty, priceEach));
		lastAvg.put(itemId, avgCost(itemId));
	}

	synchronized void addSell(int itemId, int qty)
	{
		if (itemId <= 0 || qty <= 0)
		{
			return;
		}
		int before = avgCost(itemId);
		if (before > 0)
		{
			lastAvg.put(itemId, before);
		}
		List<Lot> list = lots.get(itemId);
		if (list == null)
		{
			return;
		}
		int left = qty;
		Iterator<Lot> it = list.iterator();
		while (it.hasNext() && left > 0)
		{
			Lot lot = it.next();
			int take = Math.min(lot.qty, left);
			lot.qty -= take;
			left -= take;
			if (lot.qty <= 0)
			{
				it.remove();
			}
		}
		if (list.isEmpty())
		{
			lots.remove(itemId);
		}
		else
		{
			lastAvg.put(itemId, avgCost(itemId));
		}
	}

	synchronized int remaining(int itemId)
	{
		List<Lot> list = lots.get(itemId);
		if (list == null)
		{
			return 0;
		}
		int n = 0;
		for (Lot lot : list)
		{
			n += lot.qty;
		}
		return n;
	}

	synchronized int avgCost(int itemId)
	{
		List<Lot> list = lots.get(itemId);
		if (list == null || list.isEmpty())
		{
			return lastAvg.getOrDefault(itemId, 0);
		}
		long cost = 0;
		int qty = 0;
		for (Lot lot : list)
		{
			cost += (long) lot.qty * lot.costEach;
			qty += lot.qty;
		}
		return qty == 0 ? lastAvg.getOrDefault(itemId, 0) : (int) (cost / qty);
	}

	synchronized void clear()
	{
		lots.clear();
		lastAvg.clear();
	}
}
