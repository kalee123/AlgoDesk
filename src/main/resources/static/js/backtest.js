// Backtest page: pick an instrument + range, run a strategy, show stats, trades on the chart and a trade table.

const IST_OFFSET_SECONDS = 5.5 * 60 * 60;   // chart shows UTC; shift so the axis reads market time
const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

const form = document.getElementById("setup");
const searchInput = document.getElementById("search");
const searchResults = document.getElementById("search-results");
const status = document.getElementById("status");
const content = document.getElementById("content");
const runButton = document.getElementById("run");

let selected = null;      // { exchange, symbol }
let results = [];
let debounceTimer;
let latestSearch = 0;
let chart, candleSeries;
let lastResult = null;

// ---------- Instrument search (small dropdown under the input) ----------

searchInput.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    selected = null;
    const query = searchInput.value.trim();
    if (query.length < MIN_QUERY_LENGTH) {
        latestSearch++;
        searchResults.hidden = true;
        return;
    }
    debounceTimer = setTimeout(() => search(query), DEBOUNCE_MS);
});

async function search(query) {
    const requestId = ++latestSearch;
    try {
        const items = await api("/api/instruments/search?q=" + encodeURIComponent(query));
        if (requestId !== latestSearch) {
            return;
        }
        results = items.slice(0, 8);
        searchResults.innerHTML = results.map((i, index) => `
            <li><button type="button" data-index="${index}">
                <span class="symbol">${escapeHtml(i.tradingsymbol)}</span>
                <span class="muted small">${escapeHtml(i.name || "")}</span>
                <span class="tag">${escapeHtml(i.exchange)}</span>
            </button></li>`).join("");
        searchResults.hidden = results.length === 0;
    } catch (e) {
        searchResults.hidden = true;
    }
}

searchResults.addEventListener("click", e => {
    const button = e.target.closest("button");
    if (button) {
        selectInstrument(results[Number(button.dataset.index)]);
    }
});

function selectInstrument(i) {
    selected = { exchange: i.exchange, symbol: i.tradingsymbol };
    searchInput.value = `${i.exchange}:${i.tradingsymbol}`;
    searchResults.hidden = true;
}

document.addEventListener("click", e => {
    if (!e.target.closest(".search-box")) {
        searchResults.hidden = true;
    }
});

// ---------- Strategies (from the server) ----------

const strategySelect = document.getElementById("strategy");
let strategies = {};   // code -> {code, name, description}

async function loadStrategies() {
    const list = await api("/api/strategies");
    strategies = Object.fromEntries(list.map(s => [s.code, s]));
    strategySelect.innerHTML = list.map(s =>
        `<option value="${escapeHtml(s.code)}">${escapeHtml(s.name)}</option>`).join("");
}

function updateHint() {
    const s = strategies[strategySelect.value];
    document.getElementById("strategy-hint").textContent = s ? s.description : "";
}

strategySelect.addEventListener("change", () => {
    updateHint();
    history.replaceState(null, "", `/backtest/${encodeURIComponent(strategySelect.value)}`);
});

// ---------- Interval + period buttons ----------

let currentInterval = "5minute";
let rangeDays = 90;   // number of days back from today, or "custom"

function pick(groupId, attr, value) {
    document.querySelectorAll(`#${groupId} .interval`).forEach(b =>
        b.classList.toggle("active", b.dataset[attr] === String(value)));
}

function isoDate(d) {
    return d.toISOString().slice(0, 10);
}

// The from/to actually sent: a preset counts back from today, Custom uses the two date inputs
function period() {
    if (rangeDays === "custom") {
        return { from: document.getElementById("from").value, to: document.getElementById("to").value };
    }
    const today = new Date();
    return { from: isoDate(new Date(today.getTime() - rangeDays * 86400 * 1000)), to: isoDate(today) };
}

function pretty(iso) {
    return iso ? new Date(iso).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "…";
}

function updateRangeText() {
    const p = period();
    document.getElementById("custom-range").hidden = rangeDays !== "custom";
    document.getElementById("range-text").textContent = `${pretty(p.from)} – ${pretty(p.to)}`;
}

document.getElementById("intervals").addEventListener("click", e => {
    const b = e.target.closest(".interval");
    if (b) {
        currentInterval = b.dataset.interval;
        pick("intervals", "interval", currentInterval);
    }
});

document.getElementById("ranges").addEventListener("click", e => {
    const b = e.target.closest(".interval");
    if (b) {
        rangeDays = b.dataset.days === "custom" ? "custom" : Number(b.dataset.days);
        pick("ranges", "days", b.dataset.days);
        updateRangeText();
    }
});
["from", "to"].forEach(id => document.getElementById(id).addEventListener("change", updateRangeText));

// ---------- Run ----------

const formError = document.getElementById("form-error");

function showFormError(text) {
    formError.textContent = text;
    formError.hidden = !text;
}

form.addEventListener("submit", e => {
    e.preventDefault();
    if (!selected) {
        showFormError("Pick an instrument from the search list first");
        return;
    }
    const p = period();
    if (!p.from || !p.to) {
        showFormError("Pick both dates");
        return;
    }
    showFormError("");
    const params = {
        exchange: selected.exchange, symbol: selected.symbol, interval: currentInterval,
        from: p.from, to: p.to, strategy: strategySelect.value,
    };
    saveSetup();
    run(params);
});

// The address bar only shows /backtest/<strategy>; the rest of the setup is remembered per browser
const SETUP_KEY = "algodesk.backtest";

function saveSetup() {
    history.replaceState(null, "", `/backtest/${encodeURIComponent(strategySelect.value)}`);
    try {
        localStorage.setItem(SETUP_KEY, JSON.stringify({
            selected, interval: currentInterval, range: rangeDays,
            from: document.getElementById("from").value, to: document.getElementById("to").value,
        }));
    } catch (e) { /* storage blocked: the page still works, it just won't remember */ }
}

function loadSetup() {
    try { return JSON.parse(localStorage.getItem(SETUP_KEY)) || {}; } catch (e) { return {}; }
}

function showStatus(text) {
    status.textContent = text;
    status.hidden = false;
    content.hidden = true;
}

async function run(p) {
    runButton.disabled = true;
    showStatus("Running…");
    try {
        const res = await fetch(`/api/strategies/${encodeURIComponent(p.strategy)}/backtest`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify({ exchange: p.exchange, symbol: p.symbol, interval: p.interval, from: p.from, to: p.to }),
        });
        if (res.status === 401) {
            window.location.href = "/login";
            return;
        }
        const data = await res.json();
        if (!res.ok) {
            showFormError(data.error || "Backtest failed");
            status.hidden = true;
            return;
        }
        lastResult = data;
        renderStats(data.summary);
        renderChart(data, p);
        renderTrades(data.trades);
        status.hidden = true;
        content.hidden = false;
    } catch (e) {
        showFormError("Could not run the backtest");
        status.hidden = true;
    } finally {
        runButton.disabled = false;
    }
}

// ---------- Stats ----------

function signed(value, digits = 2) {
    const n = Number(value) || 0;
    return (n > 0 ? "+" : "") + n.toFixed(digits);
}

function renderStats(s) {
    const pf = s.profitFactor == null ? "—" : Number(s.profitFactor).toFixed(2);
    const tiles = [
        { label: "Total P&L", value: signed(s.totalPnl) + " pts", cls: pnlClass(s.totalPnl), sub: signed(s.totalPnlPercent) + "% summed" },
        { label: "Trades", value: s.trades, sub: `${s.wins} won · ${s.losses} lost` },
        { label: "Win rate", value: Number(s.winRate).toFixed(1) + "%", cls: s.winRate >= 50 ? "pos" : "" },
        { label: "Profit factor", value: pf, cls: s.profitFactor > 1 ? "pos" : s.profitFactor != null ? "neg" : "", sub: "gross profit ÷ gross loss" },
        { label: "Avg win / loss", value: `${signed(s.avgWin)} / ${signed(s.avgLoss)}`, sub: "points per trade" },
        { label: "Max drawdown", value: Number(s.maxDrawdown).toFixed(2) + " pts", cls: s.maxDrawdown > 0 ? "neg" : "", sub: "peak to trough" },
        { label: "Best / worst", value: `${signed(s.bestTrade)} / ${signed(s.worstTrade)}` },
        { label: "Exits", value: Object.entries(s.exitReasons || {}).map(([k, v]) => `${v} ${k.toLowerCase().replace("_", " ")}`).join(" · ") || "—", small: true },
    ];
    document.getElementById("stats").innerHTML = tiles.map(t => `
        <div class="card stat">
            <p class="label">${t.label}</p>
            <div class="${t.small ? "stat-small" : "stat-value"} ${t.cls || ""}">${escapeHtml(String(t.value))}</div>
            ${t.sub ? `<div class="muted small">${escapeHtml(t.sub)}</div>` : ""}
        </div>`).join("");
}

// ---------- Chart with trade markers ----------

function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function chartTheme() {
    return {
        layout: { background: { color: cssVar("--card") }, textColor: cssVar("--muted") },
        grid: { vertLines: { color: cssVar("--border") }, horzLines: { color: cssVar("--border") } },
        rightPriceScale: { borderColor: cssVar("--border") },
        timeScale: { borderColor: cssVar("--border"), timeVisible: true, secondsVisible: false },
    };
}

function candleColors() {
    const up = cssVar("--candle-up");
    const down = cssVar("--candle-down");
    return { upColor: up, downColor: down, wickUpColor: up, wickDownColor: down, borderVisible: false };
}

function createChart() {
    chart = LightweightCharts.createChart(document.getElementById("chart"), {
        ...chartTheme(),
        autoSize: true,
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
    });
    candleSeries = chart.addCandlestickSeries(candleColors());

    const legend = document.getElementById("chart-legend");
    chart.subscribeCrosshairMove(param => {
        const c = param.seriesData && param.seriesData.get(candleSeries);
        legend.innerHTML = c ? ["O", "H", "L", "C"].map((k, idx) =>
            `<span class="muted">${k}</span> ${number([c.open, c.high, c.low, c.close][idx])}`).join(" ") : "";
    });

    const applyTheme = () => {
        chart.applyOptions(chartTheme());
        candleSeries.applyOptions(candleColors());
        if (lastResult) {
            candleSeries.setMarkers(markersFor(lastResult.trades));
        }
    };
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
    window.addEventListener("themechange", applyTheme);
}

// Entry arrow below/above the bar, exit on the other side, coloured by the trade result
function markersFor(trades) {
    const markers = [];
    for (const t of trades) {
        const isLong = t.direction === "LONG";
        const accent = cssVar("--accent");
        const result = t.pnl > 0 ? cssVar("--pos") : t.pnl < 0 ? cssVar("--neg") : cssVar("--muted");
        markers.push({
            time: t.entryTime + IST_OFFSET_SECONDS,
            position: isLong ? "belowBar" : "aboveBar",
            shape: isLong ? "arrowUp" : "arrowDown",
            color: accent,
            text: `${isLong ? "Buy" : "Sell"} ${number(t.entryPrice)}`,
        });
        markers.push({
            time: t.exitTime + IST_OFFSET_SECONDS,
            position: isLong ? "aboveBar" : "belowBar",
            shape: isLong ? "arrowDown" : "arrowUp",
            color: result,
            text: `${isLong ? "Sell" : "Cover"} ${number(t.exitPrice)}`,
        });
    }
    // The library needs markers sorted by time
    return markers.sort((a, b) => a.time - b.time);
}

function renderChart(data, p) {
    if (!chart) {
        createChart();
    }
    document.getElementById("chart-title").textContent = `${p.exchange}:${p.symbol} · ${p.interval}`;
    candleSeries.setData(data.candles.map(c => ({
        time: c.time + IST_OFFSET_SECONDS, open: c.open, high: c.high, low: c.low, close: c.close,
    })));
    candleSeries.setMarkers(markersFor(data.trades));
    // Start on the last trade (or the end of the data) rather than squeezing everything in
    const last = data.trades[data.trades.length - 1];
    if (last) {
        focusTrade(last);
    } else {
        chart.timeScale().fitContent();
    }
}

// Show the whole trading day around one trade
function focusTrade(t) {
    const dayStart = t.entryTime - ((t.entryTime + IST_OFFSET_SECONDS) % 86400) + IST_OFFSET_SECONDS;
    chart.timeScale().setVisibleRange({ from: dayStart + 9 * 3600, to: dayStart + 15.75 * 3600 });
}

// ---------- Trade table ----------

function hhmm(epoch) {
    return new Date(epoch * 1000).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Asia/Kolkata" });
}

function renderTrades(trades) {
    document.getElementById("trades-body").innerHTML = tableRows(trades, 7, "No trades in this period", (t, index) => `
        <tr class="trade-row" data-index="${index}"
            title="Levels ${number(t.rangeLow)} – ${number(t.rangeHigh)} · stop ${number(t.stopLoss)} · target ${number(t.target)}">
            <td>${escapeHtml(t.date)}</td>
            <td><span class="tag ${t.direction === "LONG" ? "buy" : "sell"}">${t.direction}</span></td>
            <td>${hhmm(t.entryTime)} <span class="muted">@</span> ${number(t.entryPrice)}</td>
            <td>${hhmm(t.exitTime)} <span class="muted">@</span> ${number(t.exitPrice)}</td>
            <td class="muted small">${escapeHtml(t.exitReason.toLowerCase().replace("_", " "))}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnl)}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnlPercent)}%</td>
        </tr>`);
}

document.getElementById("trades-body").addEventListener("click", e => {
    const row = e.target.closest(".trade-row");
    if (row && lastResult) {
        focusTrade(lastResult.trades[Number(row.dataset.index)]);
        document.querySelector(".bt-chart").scrollIntoView({ behavior: "smooth", block: "start" });
    }
});

// ---------- Start: strategies from the server, strategy from the path, the rest from localStorage ----------

async function init() {
    try {
        await loadStrategies();
    } catch (e) {
        showFormError("Could not load the strategy list");
        return;
    }
    const pathStrategy = decodeURIComponent(window.location.pathname.split("/")[2] || "");
    if (strategies[pathStrategy]) {
        strategySelect.value = pathStrategy;
    }
    updateHint();

    const saved = loadSetup();
    if (document.querySelector(`#intervals [data-interval="${saved.interval}"]`)) {
        currentInterval = saved.interval;
        pick("intervals", "interval", currentInterval);
    }
    if (saved.range === "custom") {
        rangeDays = "custom";
        document.getElementById("from").value = saved.from || "";
        document.getElementById("to").value = saved.to || "";
    } else if (document.querySelector(`#ranges [data-days="${saved.range}"]`)) {
        rangeDays = Number(saved.range);
    }
    pick("ranges", "days", rangeDays);
    updateRangeText();

    if (saved.selected && saved.selected.exchange && saved.selected.symbol) {
        selectInstrument({ exchange: saved.selected.exchange, tradingsymbol: saved.selected.symbol });
        const p = period();
        if (p.from && p.to) {
            run({ exchange: selected.exchange, symbol: selected.symbol, interval: currentInterval,
                  from: p.from, to: p.to, strategy: strategySelect.value });
        }
    }
}

init();
