# Learning Log

My story behind AlgoDesk and a running log of what I learn while building it.

## Why AlgoDesk exists

In 2021 I started investing in mutual funds and picked up a few stocks,
which turned out to be quite profitable. Then a friend introduced me to
trading. I learned from books, docs and YouTube videos, but I kept
ending up in losses.

I only bought options, mostly the Opening Range Breakout (ORB) with a
tight stop-loss. I've had big wins and big losses on the same day.
Greed, fear, and a strong will that refused to quit. That will
felt like strength, but in trading it was a trap.

After a severe loss, I stopped trading.

That's when I realised trading is not easy money, and that my problem
wasn't only the strategy. It was me making decisions in the moment.

## Why I'm building it

I've always loved learning things deeply once my curiosity is triggered.
Instead of trading on emotion, I decided to approach it technically:
test strategies on past data, with rules, not feelings.

AlgoDesk turned out to be the perfect learning project because it
touches so many real-world problems:
- Backtesting strategies like ORB on historical candles
- Real-time market data and latency
- Threads and concurrency
- Networking, like the static IP brokers require for placing API orders
- Testing, so I can trust the numbers

I use Claude as a learning partner. It explains concepts and helps me
build, while I make the design decisions.

---

## What I've covered so far

Short notes in my own words, each linked to where it lives in AlgoDesk.

### Dependency Injection
A class doesn't create the objects it needs (`new CandleService()`). Spring creates
them and hands them in, usually through the constructor. Classes stay loosely
connected, and tests can pass in fakes.
*Like a kitchen that gets ingredients delivered instead of growing its own vegetables.*
→ [InstrumentController.java](../src/main/java/com/app/algodesk/controller/InstrumentController.java)

### Why Spring Boot doesn't need `web.xml`
Old Java web apps listed servlets and filters in an XML file. Spring Boot uses
annotations (`@RestController`, `@GetMapping`) plus auto-configuration, and runs an
embedded Tomcat inside the app. Config moved from XML into code and sensible defaults.

### Spring Security and filters
Every request passes through a chain of filters before reaching a controller,
like security checkpoints at an airport. Each one can check, change or reject it.
→ [SecurityConfig.java](../src/main/java/com/app/algodesk/authentication/SecurityConfig.java) builds the chain,
[SessionAuthFilter.java](../src/main/java/com/app/algodesk/authentication/SessionAuthFilter.java) is my own checkpoint
(`OncePerRequestFilter` = runs once per request).

### Factory pattern
One place decides which object to create from the input, so callers don't need
`if/else` everywhere.
→ [StrategyFactory.java](../src/main/java/com/app/algodesk/backtest/StrategyFactory.java) turns `"orb"` into the matching
[TradingStrategy](../src/main/java/com/app/algodesk/backtest/TradingStrategy.java). A new strategy needs no change in callers.

### Kite Connect vs Kite Ticker
- **Kite Connect**: REST API, request → response. Login, historical candles, orders.
- **Kite Ticker**: WebSocket, a connection that stays open while the server keeps pushing live prices (ticks).

*Connect = calling to ask the price. Ticker = a live TV channel showing it.*
→ [KiteTickerClient.java](../src/main/java/com/app/algodesk/broker/kite/KiteTickerClient.java)

### `@EnableScheduling`
Turns on Spring's built-in timer, so `@Scheduled` methods run automatically.
→ Enabled in [AlgoDeskApplication.java](../src/main/java/com/app/algodesk/AlgoDeskApplication.java);
[InstrumentScheduler.java](../src/main/java/com/app/algodesk/scheduler/InstrumentScheduler.java) refreshes instruments at 8:30 AM IST, Mon–Fri.

### `CompletableFuture`
Runs a task in the background on another thread, so the current request doesn't wait.
→ [AuthController.java](../src/main/java/com/app/algodesk/controller/AuthController.java): after login, the instrument refresh runs in the background.

```java
CompletableFuture.runAsync(() -> instrumentDataService.refreshIfNeeded(accessToken));
```
`() -> ...` is a **lambda**, and `runAsync` puts it on **another thread**. I've already
used both. Next I want to understand them properly.

---

## Log

### 2026-11-11 → 11-12: Networking rabbit hole

**What I tried:** ran `tcpdump` and saw live DNS lookups, logged into my home router
admin page, looked at connected devices and DHCP leases.

**What I learned (simply):**
- IP = room number, MAC = name tag, router = warden handing out rooms (DHCP)
- One webpage reload asks DNS for dozens of domains
- Data is just 1s and 0s, carried by radio waves (WiFi), electricity (copper) or light flashes (fiber)

**What confused me:** IPv6, packet internals, how the kernel captures packets.
Too much at once. Revisit slowly.

**Lesson about learning:** I forget concepts without practice. From now on:
one concept → use it in real code → commit.

**Next:** Week 1, Java Streams and Lambdas inside AlgoDesk.
