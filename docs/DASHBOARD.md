# Dashboard

The dashboard answers "how am I doing today, and what needs attention?" at a glance.
Account details moved out of the page into a navbar menu.

```
┌ Navbar ─────────────────────────────────────────────────── [KJ] QEX463 ▾ ┐
│ NIFTY 50 23,810 +1.58% ╱╲╱   BANK NIFTY 51,000 -0.97% ╲╱   SENSEX ...     │  index strip
│ ┌ Today's P&L ┐ ┌ Portfolio value ┐ ┌ Available margin ┐ ┌ Orders today ┐ │  KPI tiles
│ ┌ Today's movers ─────────────┐ ┌ Recent orders ───────────────────┐      │
│ ┌ Open positions ──────────────────────────────────────────────────┐      │
└───────────────────────────────────────────────────────────────────────────┘
```

## APIs

| Endpoint | Returns | Why it is separate |
|---|---|---|
| `GET /api/dashboard/summary` | KPIs, movers, recent orders, open positions | One call for everything that changes about once a minute |
| `GET /api/market/indices` | NIFTY 50, BANK NIFTY, SENSEX: price, change, % | Changes every second; will become live through the ticker later |
| `GET /api/profile` | Name, user ID, email, broker, exchanges | Every page's navbar needs it, not just the dashboard |

The old `GET /api/dashboard` (profile + raw margins) was removed.

### `/api/dashboard/summary`

```json
{
  "asOf": "2026-11-04T11:42:05+05:30",
  "kpis": {
    "todayPnl":        { "value": 150.00, "percent": 0.81, "holdings": 200.00, "positions": -50.00 },
    "portfolio":       { "value": 13000.00, "invested": 12000.00, "pnl": 1000.00, "pnlPercent": 8.33 },
    "availableMargin": { "value": 5000.00, "used": 1000.00, "cash": 6000.00, "commodity": null },
    "orders":          { "open": 2, "executed": 1, "rejected": 1, "cancelled": 1, "total": 5 }
  },
  "movers":       [ { "exchange": "NSE", "symbol": "RELIANCE", "ltp": 1300, "dayChangePercent": 1.56, "dayPnl": 200.00 } ],
  "recentOrders": [ { "time": "11:40", "side": "BUY", "exchange": "NSE", "symbol": "SBIN", "quantity": 20,
                      "filled": 0, "price": 810.00, "status": "OPEN", "open": true } ],
  "positions":    [ { "exchange": "NFO", "symbol": "NIFTY26NOVFUT", "product": "NRML", "quantity": 75,
                      "ltp": 23600, "pnl": 5000.00, "m2m": 1200.00 } ],
  "errors": {}
}
```

- Field names are ours, not Kite's: the browser never depends on Kite's response format.
- `null` means "could not be calculated" (e.g. a percent with nothing invested); the UI shows `—`.
- If one Kite call fails, only the sections that need it are `null`, and `errors` says why
  (e.g. `"errors": { "orders": "Could not load orders" }`). The response is still `200`.
- Only when **all** calls fail does the request fail (`502`).

## How the numbers are calculated

| KPI | Formula | Kite fields |
|---|---|---|
| Today's P&L | Σ holdings `(last_price − close_price) × qty` + Σ positions `m2m` | `close_price` = yesterday's close |
| Today's P&L % | total ÷ Σ `close_price × qty` (yesterday's holdings value) | |
| Portfolio value | Σ `last_price × qty` | |
| Invested | Σ `average_price × qty` | |
| Available margin | `equity.net`; used = `equity.utilised.debits`; cash = `equity.available.cash` | |
| Open orders | status not in COMPLETE / REJECTED / CANCELLED | OPEN, TRIGGER PENDING, AMO... |
| Movers | holdings sorted by size of today's % move, top 5 | |
| Open positions | net positions with `quantity != 0` | |
| Index change | `last_price − ohlc.close` | in a quote, `ohlc.close` is the previous close |

`qty` for holdings is always `quantity + t1_quantity` (shares bought yesterday are not settled yet but are still owned).

Decisions worth remembering:
- **Positions use `m2m`, not `pnl`.** For a position carried from yesterday, `pnl` counts from the buy price;
  `m2m` counts from yesterday's close, which is "today" only.
- **Money is `BigDecimal`, never `double`.** `double` cannot represent amounts like 0.1 exactly, so sums drift.

## Backend design

```
DashboardController  GET /api/dashboard/summary
  └─ DashBoardService.getDetailedSummary()
       1. read token from RqInfo (request thread only!)
       2. start 4 Kite calls in parallel  ── holdings, positions, orders, margins
       3. wait for each (5 s timeout); failure → null + errors entry
       4. pure static calculations:  todayPnl · portfolio · availableMargin · orderCounts
                                     topMovers · recentOrders · openPositions
```

- **Parallel calls:** `CompletableFuture.supplyAsync(..., pool).orTimeout(5s)`. The page waits for the slowest
  call (~300 ms), not the sum of all four.
- **Own thread pool** (8 threads, shut down with `@PreDestroy`): HTTP calls block, and the shared
  `ForkJoinPool` is meant for CPU work.
- **`RqInfo` is request-scoped**, so it does not work on the pool's threads. The token is read once on the
  request thread and passed in.
- **I/O separated from logic:** fetching happens in one place; every calculation is a `static` method that takes
  JSON and returns JSON. That is why they can be tested without Spring, Kite or a network.
- **Errors** go through `ApiExceptionHandler` (`@RestControllerAdvice`): controllers have no try/catch.

## Frontend

| File | What |
|---|---|
| `static/dashboard.html` | Page structure: index strip, 4 tiles, movers, recent orders, open positions |
| `static/js/dashboard.js` | Renders the summary; per-section "Unavailable" when `errors` has an entry; sparklines; auto refresh |
| `static/js/common.js` | Navbar account menu (`<details>` dropdown) with name, ID, email, exchanges and Log out |
| `static/css/app.css` | Tiles, index strip, panels, account menu |

- **Sparklines** in the index strip are drawn as small inline SVGs from today's 5-minute candles
  (the existing candles API). They load after the numbers and fail quietly.
- **Auto refresh** every 60 s, only while the tab is visible (no Kite calls for a background tab).
  If a refresh fails, the last numbers stay and the header says "refresh failed".
- **Profile is cached** in `sessionStorage` for the browser tab and cleared on logout, so pages don't call
  Kite for it on every navigation.
- Instruments in movers, orders, positions and the index strip link to their chart in Market Watch.

## Code

| Layer | Files |
|---|---|
| Controllers | `DashboardController`, `MarketController` (new), `UserContoller` (now `/api/profile`) |
| Services | `DashBoardService`, `MarketService` (new) |
| Constants | `UrlConstants.KITE_QUOTE_OHLC_PATH` (new) |
| Errors | `exception/ApiExceptionHandler` |
| Tests | `DashBoardServiceTest` (20), `MarketServiceTest` (2), `ApiExceptionHandlerTest` (4) |

Run the tests:

```bash
./mvnw test -Dtest='DashBoardServiceTest,MarketServiceTest,ApiExceptionHandlerTest'
```

The summary tests fake `KiteApiClient` with Mockito, including a failing call (partial result) and all calls
failing (error).

## Known limitations

- **Selling a holding today** may be counted twice in Today's P&L (holding day change + position m2m).
  Compare with the Kite app on such a day and adjust the formula if it differs.
- **Index strip and summary are polled**, not live. Phase 2 (ticker + SSE) can push index prices instead.
- **`UserProfileService.getMarginDetails()`** is no longer used by anything; it can be removed.
- The Kite quote API (`/quote/ohlc`) must be included in the Kite Connect plan, like historical data.

## Checklist after restarting the app

- [ ] Index strip shows NIFTY 50, BANK NIFTY, SENSEX with sparklines
- [ ] Today's P&L matches the Kite app's day P&L
- [ ] Portfolio value and margin match Kite's Holdings and Funds pages
- [ ] Navbar menu shows your name and ID; Log out works from it
- [ ] Clicking a mover opens its chart in Market Watch
