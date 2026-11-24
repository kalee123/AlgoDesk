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
const runAgainButton = document.getElementById("run-again");
const toggleSetupButton = document.getElementById("toggle-setup");
const setupDialog = document.getElementById("setup-dialog");
const emptyState = document.getElementById("empty");

let selected = null;      // { exchange, symbol }
let results = [];
let debounceTimer;
let latestSearch = 0;
let chart, candleSeries;
let lastResult = null;
let markerTrades = new Map();   // chart time -> [{ trade, kind: "entry" | "exit" }], for the hover legend

const LEGEND_HINT = "Hover a candle for OHLC · hover an arrow for the trade";

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

const strategyDropdown = document.getElementById("strategy-dropdown");
const strategyList = document.getElementById("strategies");
let strategies = {};          // code -> {code, name, description}
let currentStrategy = null;

async function loadStrategies() {
    const list = await api("/api/strategies");
    strategies = Object.fromEntries(list.map(s => [s.code, s]));
    strategyList.innerHTML = list.map(s => `
        <li><button type="button" role="option" data-strategy="${escapeHtml(s.code)}">${escapeHtml(s.name)}</button></li>`
    ).join("");
    currentStrategy = list.length ? list[0].code : null;
}

// Shows the name in the closed box, marks the option, shows the description, updates the address bar
function selectStrategy(code) {
    currentStrategy = code;
    const s = strategies[code];
    document.getElementById("strategy-value").textContent = s ? s.name : "Choose a strategy";
    strategyList.querySelectorAll("button").forEach(b =>
        b.classList.toggle("selected", b.dataset.strategy === code));
    document.getElementById("strategy-hint").textContent = s ? s.description : "";
    updateIntervalHint();
    history.replaceState(null, "", `/backtest/${encodeURIComponent(code)}`);
}

strategyList.addEventListener("click", e => {
    const b = e.target.closest("button");
    if (b) {
        selectStrategy(b.dataset.strategy);
        strategyDropdown.open = false;
    }
});

// Close when clicking anywhere else, like the account menu
document.addEventListener("click", e => {
    if (strategyDropdown.open && !strategyDropdown.contains(e.target)) {
        strategyDropdown.open = false;
    }
});

// ---------- Interval + period buttons ----------

let currentInterval = "5minute";
let rangeDays = 90;   // number of days back from today, or "custom"

function pick(groupId, attr, value) {
    document.querySelectorAll(`#${groupId} .interval`).forEach(b =>
        b.classList.toggle("active", b.dataset[attr] === String(value)));
}

// Opening-range strategies use the first candle of the day as the range, so its length is the candle interval
const INTERVAL_MINUTES = { minute: 1, "3minute": 3, "5minute": 5, "15minute": 15, "30minute": 30 };
const INTERVAL_LABELS = { minute: "1m", "3minute": "3m", "5minute": "5m", "15minute": "15m", "30minute": "30m" };

function updateIntervalHint() {
    const hint = document.getElementById("interval-hint");
    const minutes = INTERVAL_MINUTES[currentInterval];
    const isOrb = currentStrategy && currentStrategy.startsWith("orb");
    hint.hidden = !isOrb || !minutes;
    if (!hint.hidden) {
        const end = 9 * 60 + 15 + minutes;
        const hhmmEnd = `${String(Math.floor(end / 60)).padStart(2, "0")}:${String(end % 60).padStart(2, "0")}`;
        hint.textContent = `Opening range = first ${minutes}-min candle (09:15 – ${hhmmEnd})`;
    }
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
        updateIntervalHint();
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
    if (text) {
        setSetupOpen(true);   // the error lives in the form, so make sure it's visible
    }
}

// The setup is a modal: open on the first visit / "New backtest" / "Edit setup", closed once a run succeeds
function setSetupOpen(open) {
    if (open && !setupDialog.open) {
        setupDialog.showModal();
    } else if (!open && setupDialog.open) {
        setupDialog.close();
    }
}

toggleSetupButton.addEventListener("click", () => setSetupOpen(true));
document.getElementById("new-backtest").addEventListener("click", () => setSetupOpen(true));
document.getElementById("close-setup").addEventListener("click", () => setSetupOpen(false));
document.getElementById("cancel-setup").addEventListener("click", () => setSetupOpen(false));

// A click on the dimmed backdrop lands on the <dialog> itself (the card fills it), so close
setupDialog.addEventListener("click", e => {
    if (e.target === setupDialog) {
        setSetupOpen(false);
    }
});

form.addEventListener("submit", e => {
    e.preventDefault();
    submitSetup();
});

runAgainButton.addEventListener("click", submitSetup);

// Shared by the form's Run button and the run bar's Run again
function submitSetup() {
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
        from: p.from, to: p.to, strategy: currentStrategy,
    };
    saveSetup();
    run(params);
}

// The address bar only shows /backtest/<strategy>; the rest of the setup is remembered per browser
const SETUP_KEY = "algodesk.backtest";

function saveSetup() {
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
    emptyState.hidden = true;
}

// Back to the results, or to the "No backtest yet" state if nothing has run successfully
function hideStatus() {
    status.hidden = true;
    emptyState.hidden = !!lastResult;
}

function setRunning(running) {
    runButton.disabled = running;
    runButton.textContent = running ? "Running…" : "Run backtest";
    runAgainButton.disabled = running;
    runAgainButton.textContent = running ? "Running…" : "Run again";
    content.classList.toggle("is-loading", running);
}

// "NIFTY 50 · Opening Range Breakout · 5m · 26 Aug 2026 – 24 Nov 2026"
function renderRunBar(p) {
    const s = strategies[p.strategy];
    document.getElementById("chart-title").textContent = `${p.exchange}:${p.symbol}`;
    document.getElementById("run-meta").textContent = [s ? s.name : p.strategy,
        INTERVAL_LABELS[p.interval] || p.interval, `${pretty(p.from)} – ${pretty(p.to)}`].join(" · ");
}

async function run(p) {
    setRunning(true);
    // First run: a "Running…" placeholder. Re-runs: keep the old results on screen, dimmed.
    if (!lastResult) {
        showStatus("Running…");
    }
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
            hideStatus();
            return;
        }
        lastResult = data;
        hideStatus();
        content.hidden = false;
        setSetupOpen(false);
        renderRunBar(p);
        renderStats(data.summary);
        renderChart(data, p);
        resetTradeFilters();
        renderTrades();
        showTab("summary");       // every new run opens on the summary
    } catch (e) {
        showFormError("Could not run the backtest");
        hideStatus();
    } finally {
        setRunning(false);
    }
}

// ---------- Tabs: Summary / Chart / Trades, one view at a time ----------

let pendingChartView = null;   // what the chart should show next time its tab opens (it can't size while hidden)

function showTab(name) {
    document.querySelectorAll("#tabs .interval").forEach(b =>
        b.classList.toggle("active", b.dataset.tab === name));
    document.querySelectorAll(".bt-panel").forEach(panel =>
        panel.hidden = panel.dataset.panel !== name);
    if (name === "chart" && pendingChartView) {
        const view = pendingChartView;
        pendingChartView = null;
        // A hidden chart has no size; it resizes itself (autoSize) a frame after the tab shows, so wait for that
        requestAnimationFrame(() => requestAnimationFrame(view));
    }
}

document.getElementById("tabs").addEventListener("click", e => {
    const b = e.target.closest(".interval");
    if (b) {
        showTab(b.dataset.tab);
    }
});

// ---------- Stats ----------

function signed(value, digits = 2) {
    const n = Number(value) || 0;
    return (n > 0 ? "+" : "") + n.toFixed(digits);
}

// Summary tab: headline numbers on the first row, the detail on the second
function renderStats(s) {
    const pf = s.profitFactor == null ? "—" : Number(s.profitFactor).toFixed(2);
    const winPercent = s.trades ? (s.wins / s.trades) * 100 : 0;
    const tiles = [
        { label: "Net P&L", value: signed(s.totalPnl) + " pts", cls: pnlClass(s.totalPnl),
          sub: `${signed(s.totalPnlPercent)}% summed · ${s.trades} trades` },
        { label: "Win rate", value: Number(s.winRate).toFixed(1) + "%", cls: s.winRate >= 50 ? "pos" : "",
          sub: `${s.wins} won · ${s.losses} lost`,
          extra: `<div class="bt-winbar"><span style="width:${winPercent.toFixed(1)}%"></span></div>` },
        { label: "Profit factor", value: pf, cls: s.profitFactor > 1 ? "pos" : s.profitFactor != null ? "neg" : "",
          sub: "gross profit ÷ gross loss" },
        { label: "Max drawdown", value: Number(s.maxDrawdown).toFixed(2) + " pts", cls: s.maxDrawdown > 0 ? "neg" : "",
          sub: "peak to trough" },
        { label: "Avg win / loss", html: `<span class="pos">${signed(s.avgWin)}</span> / <span class="neg">${signed(s.avgLoss)}</span>`,
          sub: "points per trade" },
        { label: "Best / worst", html: `<span class="pos">${signed(s.bestTrade)}</span> / <span class="neg">${signed(s.worstTrade)}</span>`,
          sub: "single trade" },
        { label: "Exits", wide: true, extra: exitsBar(s.exitReasons || {}) },
    ];
    document.getElementById("stats").innerHTML = tiles.map(t => `
        <div class="card stat ${t.wide ? "stat-wide" : ""}">
            <p class="label">${t.label}</p>
            ${t.value !== undefined ? `<div class="stat-value ${t.cls || ""}">${escapeHtml(String(t.value))}</div>` : ""}
            ${t.html ? `<div class="stat-value">${t.html}</div>` : ""}
            ${t.sub ? `<div class="muted small">${escapeHtml(t.sub)}</div>` : ""}
            ${t.extra || ""}
        </div>`).join("");
}

// Exit reasons as one stacked bar: target (green), stop loss (red), anything else like square off (grey)
function exitsBar(reasons) {
    const entries = Object.entries(reasons).filter(([, count]) => count > 0);
    const total = entries.reduce((sum, [, count]) => sum + count, 0);
    if (!total) {
        return `<div class="stat-value">—</div>`;
    }
    const cls = reason => reason === "TARGET" ? "exit-target" : reason === "STOP_LOSS" ? "exit-stop" : "exit-other";
    const name = reason => reason.toLowerCase().replace("_", " ");
    return `
        <div class="bt-exitbar">${entries.map(([r, count]) =>
            `<span class="${cls(r)}" style="width:${(count / total * 100).toFixed(1)}%" title="${count} ${name(r)}"></span>`).join("")}</div>
        <div class="bt-exit-legend">${entries.map(([r, count]) =>
            `<span><i class="${cls(r)}"></i>${count} ${escapeHtml(name(r))}</span>`).join("")}</div>`;
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

    // Markers have no text (labels pile up with many trades), so the trade details show here on hover
    const legend = document.getElementById("chart-legend");
    legend.textContent = LEGEND_HINT;
    chart.subscribeCrosshairMove(param => {
        const c = param.seriesData && param.seriesData.get(candleSeries);
        if (!c) {
            legend.textContent = LEGEND_HINT;
            return;
        }
        const ohlc = ["O", "H", "L", "C"].map((k, idx) =>
            `<span class="muted">${k}</span> ${number([c.open, c.high, c.low, c.close][idx])}`).join(" ");
        const trades = (markerTrades.get(param.time) || []).map(tradeText).join(" · ");
        legend.innerHTML = trades ? `${ohlc} <span class="bt-legend-trade">${trades}</span>` : ohlc;
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

// Entry arrow below/above the bar, exit on the other side, coloured by the trade result. No text: see the legend.
function markersFor(trades) {
    const markers = [];
    for (const t of trades) {
        const isLong = t.direction === "LONG";
        const result = t.pnl > 0 ? cssVar("--pos") : t.pnl < 0 ? cssVar("--neg") : cssVar("--muted");
        markers.push({
            time: t.entryTime + IST_OFFSET_SECONDS,
            position: isLong ? "belowBar" : "aboveBar",
            shape: isLong ? "arrowUp" : "arrowDown",
            color: cssVar("--accent"),
        });
        markers.push({
            time: t.exitTime + IST_OFFSET_SECONDS,
            position: isLong ? "aboveBar" : "belowBar",
            shape: isLong ? "arrowDown" : "arrowUp",
            color: result,
        });
    }
    // The library needs markers sorted by time
    return markers.sort((a, b) => a.time - b.time);
}

// chart time -> trades that enter or exit on that candle
function indexMarkerTrades(trades) {
    const index = new Map();
    const add = (time, entry) => index.set(time, [...(index.get(time) || []), entry]);
    for (const t of trades) {
        add(t.entryTime + IST_OFFSET_SECONDS, { trade: t, kind: "entry" });
        add(t.exitTime + IST_OFFSET_SECONDS, { trade: t, kind: "exit" });
    }
    return index;
}

// "LONG entry @ 24,371.35 · stop 24,300 · target 24,513" or "LONG exit @ 24,309.50 · stop loss · -61.85"
function tradeText({ trade: t, kind }) {
    if (kind === "entry") {
        return escapeHtml(`${t.direction} entry @ ${number(t.entryPrice)} · stop ${number(t.stopLoss)} · target ${number(t.target)}`);
    }
    return `${escapeHtml(`${t.direction} exit @ ${number(t.exitPrice)} · ${t.exitReason.toLowerCase().replace("_", " ")} ·`)}
        <span class="${pnlClass(t.pnl)}">${signed(t.pnl)}</span>`;
}

const INITIAL_VIEW_DAYS = 10;   // first view: the last ~2 weeks, readable without squeezing everything in

function renderChart(data, p) {
    if (!chart) {
        createChart();
    }
    markerTrades = indexMarkerTrades(data.trades);
    candleSeries.setData(data.candles.map(c => ({
        time: c.time + IST_OFFSET_SECONDS, open: c.open, high: c.high, low: c.low, close: c.close,
    })));
    candleSeries.setMarkers(markersFor(data.trades));

    // Positioned when the Chart tab opens (see showTab): a hidden chart has no width to fit candles into
    const barsPerDay = 375 / (INTERVAL_MINUTES[p.interval] || 5);
    const lastIndex = data.candles.length - 1;
    pendingChartView = () => chart.timeScale().setVisibleLogicalRange({
        from: Math.max(0, lastIndex - INITIAL_VIEW_DAYS * barsPerDay), to: lastIndex + 3,
    });
}

// Show the whole trading day around one trade
function focusTrade(t) {
    const dayStart = t.entryTime - ((t.entryTime + IST_OFFSET_SECONDS) % 86400) + IST_OFFSET_SECONDS;
    chart.timeScale().setVisibleRange({ from: dayStart + 9 * 3600, to: dayStart + 15.75 * 3600 });
}

// ---------- Trade table: filter, search, CSV ----------

const tradeSearch = document.getElementById("trade-search");
let tradeFilter = "all";

function hhmm(epoch) {
    return new Date(epoch * 1000).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Asia/Kolkata" });
}

// "STOP_LOSS" -> "Stop Loss"
function reasonLabel(reason) {
    return reason.toLowerCase().split("_").map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
}

function reasonClass(reason) {
    return reason === "TARGET" ? "pos" : reason === "STOP_LOSS" ? "neg" : "bt-reason-other";
}

const FILTERS = {
    all: () => true,
    winners: t => t.pnl > 0,
    losers: t => t.pnl < 0,
    long: t => t.direction === "LONG",
    short: t => t.direction === "SHORT",
};

// Trades that pass the filter button and the search box. Each keeps its position in the full list:
// that's its trade number (#) and what a row click uses to find it.
function visibleTrades() {
    const query = tradeSearch.value.trim().toLowerCase();
    return lastResult.trades
        .map((t, index) => ({ t, index }))
        .filter(({ t }) => FILTERS[tradeFilter](t))
        .filter(({ t }) => !query || [t.date, t.direction, reasonLabel(t.exitReason),
            number(t.entryPrice), number(t.exitPrice), signed(t.pnl)].join(" ").toLowerCase().includes(query));
}

const EYE_ICON = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/>
    <circle cx="12" cy="12" r="3"/></svg>`;

function renderTrades() {
    const all = lastResult.trades;
    const rows = visibleTrades();
    document.getElementById("trades-count").textContent = all.length;
    document.getElementById("trades-shown").textContent =
        rows.length === all.length ? `${all.length} trades` : `Showing ${rows.length} of ${all.length}`;
    const emptyText = all.length ? "No trades match" : "No trades in this period";
    document.getElementById("trades-body").innerHTML = tableRows(rows, 9, emptyText, ({ t, index }) => `
        <tr class="trade-row" data-index="${index}"
            title="Levels ${number(t.rangeLow)} – ${number(t.rangeHigh)} · stop ${number(t.stopLoss)} · target ${number(t.target)}">
            <td class="muted">#${index + 1}</td>
            <td>${escapeHtml(t.date)}</td>
            <td><span class="tag ${t.direction === "LONG" ? "long" : "short"}">${t.direction}</span></td>
            <td><span class="muted">${hhmm(t.entryTime)} @</span> <b>${number(t.entryPrice)}</b></td>
            <td><span class="muted">${hhmm(t.exitTime)} @</span> <b>${number(t.exitPrice)}</b></td>
            <td class="${reasonClass(t.exitReason)}">${escapeHtml(reasonLabel(t.exitReason))}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnl)}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnlPercent)}%</td>
            <td class="num"><button type="button" class="bt-eye" aria-label="Show trade #${index + 1} on the chart">${EYE_ICON}</button></td>
        </tr>`);
}

// A new run starts with every trade visible
function resetTradeFilters() {
    tradeFilter = "all";
    tradeSearch.value = "";
    pick("trade-filters", "filter", "all");
}

document.getElementById("trade-filters").addEventListener("click", e => {
    const b = e.target.closest(".interval");
    if (b && lastResult) {
        tradeFilter = b.dataset.filter;
        pick("trade-filters", "filter", tradeFilter);
        renderTrades();
    }
});

tradeSearch.addEventListener("input", () => {
    if (lastResult) {
        renderTrades();
    }
});

// Whole row (or its eye button) opens the Chart tab on that trade's day
document.getElementById("trades-body").addEventListener("click", e => {
    const row = e.target.closest(".trade-row");
    if (row && lastResult) {
        const trade = lastResult.trades[Number(row.dataset.index)];
        pendingChartView = () => focusTrade(trade);
        showTab("chart");
    }
});

// Downloads the rows currently shown (filter + search applied), built in the browser
document.getElementById("trades-csv").addEventListener("click", () => {
    if (!lastResult) {
        return;
    }
    const cell = value => `"${String(value).replace(/"/g, '""')}"`;
    const header = ["#", "Date", "Side", "Entry time", "Entry price", "Exit time", "Exit price", "Reason", "P&L", "P&L %"];
    const lines = visibleTrades().map(({ t, index }) => [index + 1, t.date, t.direction, hhmm(t.entryTime), t.entryPrice,
        hhmm(t.exitTime), t.exitPrice, reasonLabel(t.exitReason), t.pnl, t.pnlPercent].map(cell).join(","));
    const blob = new Blob([[header.map(cell).join(","), ...lines].join("\n")], { type: "text/csv" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `trades-${document.getElementById("chart-title").textContent.replace(/[^A-Za-z0-9]+/g, "_")}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);   // revoking at once can cancel the download
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
    selectStrategy(strategies[pathStrategy] ? pathStrategy : currentStrategy);

    const saved = loadSetup();
    if (document.querySelector(`#intervals [data-interval="${saved.interval}"]`)) {
        currentInterval = saved.interval;
        pick("intervals", "interval", currentInterval);
    }
    updateIntervalHint();
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
                  from: p.from, to: p.to, strategy: currentStrategy });
            return;
        }
    }
    setSetupOpen(true);   // nothing to re-run: start with the setup
}

init();
