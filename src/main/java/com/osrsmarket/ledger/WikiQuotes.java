package com.osrsmarket.ledger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
final class WikiQuotes
{
	private static final String LATEST = "https://prices.runescape.wiki/api/v1/osrs/latest";
	private static final long TTL_MS = 30_000;

	static final class Quote
	{
		final Integer high;
		final Integer low;

		Quote(Integer high, Integer low)
		{
			this.high = high;
			this.low = low;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Map<Integer, Quote> quotes = new ConcurrentHashMap<>();
	private final AtomicLong fetchedAt = new AtomicLong(0);
	private final AtomicBoolean inFlight = new AtomicBoolean(false);
	private volatile Runnable onUpdated;

	@Inject
	WikiQuotes(OkHttpClient http, Gson gson, ScheduledExecutorService executor)
	{
		this.http = http;
		this.gson = gson;
		this.executor = executor;
	}

	Quote get(int itemId)
	{
		return quotes.get(itemId);
	}

	void setOnUpdated(Runnable onUpdated)
	{
		this.onUpdated = onUpdated;
	}

	void refreshIfStale()
	{
		if (System.currentTimeMillis() - fetchedAt.get() < TTL_MS)
		{
			return;
		}
		if (!inFlight.compareAndSet(false, true))
		{
			return;
		}
		executor.execute(this::fetch);
	}

	private void fetch()
	{
		Request request = new Request.Builder()
			.url(LATEST)
			.header("User-Agent", "OSRS-Ledger/1.0")
			.header("Accept", "application/json")
			.build();
		try (Response response = http.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.debug("Wiki latest HTTP {}", response.code());
				return;
			}
			JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
			if (root == null)
			{
				return;
			}
			JsonObject data = root.getAsJsonObject("data");
			if (data == null)
			{
				return;
			}
			Map<Integer, Quote> next = new ConcurrentHashMap<>();
			for (Map.Entry<String, JsonElement> e : data.entrySet())
			{
				int id;
				try
				{
					id = Integer.parseInt(e.getKey());
				}
				catch (NumberFormatException ex)
				{
					continue;
				}
				if (!e.getValue().isJsonObject())
				{
					continue;
				}
				JsonObject row = e.getValue().getAsJsonObject();
				next.put(id, new Quote(intOrNull(row, "high"), intOrNull(row, "low")));
			}
			quotes.clear();
			quotes.putAll(next);
			fetchedAt.set(System.currentTimeMillis());
			Runnable cb = onUpdated;
			if (cb != null)
			{
				cb.run();
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Wiki latest failed: {}", e.getMessage());
		}
		finally
		{
			inFlight.set(false);
		}
	}

	private static Integer intOrNull(JsonObject row, String key)
	{
		if (!row.has(key) || row.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			int n = row.get(key).getAsInt();
			return n > 0 ? n : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
