# OSRS Ledger (RuneLite)

Grand Exchange slot colors, offer P&L, and fill sync into your [OSRS Ledger](https://osrsledger.com) account.

## What it does

- **Fill sync** — imports completed trades from **GE History** when you open that screen (one History row = one ledger fill).
- **Offer slots** — syncs the 8 GE slots (item, side, price, qty, progress) to the ledger page.
- **GE overlay** — slot colors, offer status P&L, wiki hotkeys, chatbox price links.

## Setup

1. Sign in on [osrsledger.com](https://osrsledger.com) → **Settings**.
2. Create a **plugin key** (shown once — copy it).
3. Install this plugin from the RuneLite Plugin Hub (or `./gradlew run` while developing).
4. In RuneLite config **OSRS Ledger**, paste your plugin key.

The plugin connects to [osrsledger.com](https://osrsledger.com) automatically. For local API dev, change `OsrsMarketUrls.ORIGIN` in the plugin source.

The sidebar panel (blue **GE** icon) is a live GE desk: 8 slots with fill progress, wiki instant-buy/sell, leftover GP, and paper P&L per item. Buy offers are blue, sells are orange. The left stripe matches overlay health (green = on market, yellow = in spread, red = update the offer). Hover a slot for the suggested price; click the card to open `/item/{id}`. **Ledger** / **Tracker** open those pages on the site.

Toggle these under plugin config: **Color GE slots**, **Offer status P&L**, and **Sync GE History**. Profit uses leftover lots from the ledger (fetched on login) plus fills imported from History.

## Features (config)

- **Slot colors** — green = still on market / in margin, yellow = in the spread but off the edge, red = out of margin (update the offer).
- **Offer status P&L** — estimated profit on open offers using wiki prices and your ledger cost basis.
- **Sync GE History** — open Grand Exchange History to import completed trades (up to 32).

## Develop

```
./gradlew test
./gradlew run
```
