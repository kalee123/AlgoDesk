const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

const searchInput = document.getElementById("search");
const status = document.getElementById("status");
const resultsList = document.getElementById("results");

let debounceTimer;
let latestRequest = 0;
let results = [];

function showStatus(text) {
    status.textContent = text;
    status.hidden = false;
    resultsList.hidden = true;
}

function formatExpiry(expiry) {
    if (!expiry) {
        return "";
    }
    return new Date(expiry).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" });
}

// "RELIANCE INDUSTRIES" for stocks; "26 Nov 26 · lot 30" for derivatives
function subtitle(i) {
    if (i.expiry) {
        return `${formatExpiry(i.expiry)} · lot ${number(i.lot_size)}`;
    }
    return escapeHtml(i.name);
}

function isSelected(i) {
    return selected !== null && selected.exchange === i.exchange && selected.symbol === i.tradingsymbol;
}

function renderResults() {
    if (results.length === 0) {
        showStatus("No matching instruments");
        return;
    }
    resultsList.innerHTML = results.map((i, index) => `
        <li>
            <button type="button" class="watch-item ${isSelected(i) ? "selected" : ""}" data-index="${index}">
                <span class="watch-main">
                    <span class="symbol">${escapeHtml(i.tradingsymbol)}</span>
                    <span class="muted small">${subtitle(i)}</span>
                </span>
                <span class="watch-tags">
                    <span class="tag">${escapeHtml(i.exchange)}</span>
                    <span class="tag">${escapeHtml(i.instrument_type)}</span>
                </span>
            </button>
        </li>`).join("");
    status.hidden = true;
    resultsList.hidden = false;
}

async function search(query) {
    // Responses can arrive out of order; only the newest request may update the list
    const requestId = ++latestRequest;
    try {
        const items = await api("/api/instruments/search?q=" + encodeURIComponent(query));
        if (requestId === latestRequest) {
            results = items;
            renderResults();
        }
    } catch (e) {
        if (requestId === latestRequest) {
            showStatus("Search failed. Try again.");
        }
    }
}

// ---------- Chart ----------

const INTERVALS = [
    { value: "minute", label: "1m" },
    { value: "5minute", label: "5m" },
    { value: "15minute", label: "15m" },
    { value: "60minute", label: "1h" },
    { value: "day", label: "1D" },
];

// The API sends real epoch seconds; the chart library shows times in UTC.
// Shifting by +5:30 makes the axis read Indian market time (09:15, not 03:45).
const IST_OFFSET_SECONDS = 5.5 * 60 * 60;
const VISIBLE_CANDLES = 150;   // zoom to the latest candles; scroll left for older ones

const chartStatus = document.getElementById("chart-status");
const legend = document.getElementById("chart-legend");

let selected = null;   // { exchange, symbol }
let currentInterval = "day";
let chart, candleSeries, volumeSeries;
let lastCandle = null;
let latestChartRequest = 0;

function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

// "#15803d" -> "rgba(21, 128, 61, 0.4)" for the faded volume bars
function withAlpha(hex, alpha) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}

// Chart colours come from the same CSS variables as the rest of the app, so light/dark mode match
function chartTheme() {
    return {
        layout: { background: { color: cssVar("--card") }, textColor: cssVar("--muted") },
        grid: { vertLines: { color: cssVar("--border") }, horzLines: { color: cssVar("--border") } },
        rightPriceScale: { borderColor: cssVar("--border") },
        timeScale: { borderColor: cssVar("--border"), timeVisible: true, secondsVisible: false },
    };
}

// Filled candles in the chart palette (stronger than the text colours so up/down stand apart)
function candleColors() {
    const up = cssVar("--candle-up");
    const down = cssVar("--candle-down");
    return { upColor: up, downColor: down, wickUpColor: up, wickDownColor: down, borderVisible: false };
}

function createChart() {
    chart = LightweightCharts.createChart(document.getElementById("chart"), {
        ...chartTheme(),
        autoSize: true,   // follows the container size, including window resizes
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
    });

    candleSeries = chart.addCandlestickSeries(candleColors());
    candleSeries.priceScale().applyOptions({ scaleMargins: { top: 0.1, bottom: 0.25 } });

    // Volume on its own hidden scale, squeezed into the bottom 20% under the candles
    volumeSeries = chart.addHistogramSeries({ priceFormat: { type: "volume" }, priceScaleId: "" });
    volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

    chart.subscribeCrosshairMove(param => {
        const candle = param.seriesData && param.seriesData.get(candleSeries);
        renderLegend(candle || lastCandle);
    });

    // Re-colour when the system theme changes or the navbar switch is used
    const applyTheme = () => {
        chart.applyOptions(chartTheme());
        candleSeries.applyOptions(candleColors());
        if (selected) {
            loadCandles();   // volume bar colours are per bar, so redraw them
        }
    };
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
    window.addEventListener("themechange", applyTheme);
}

function renderLegend(c) {
    if (!c) {
        legend.innerHTML = "";
        return;
    }
    const cls = pnlClass(c.close - c.open);
    legend.innerHTML = ["O", "H", "L", "C"].map((k, idx) => {
        const value = [c.open, c.high, c.low, c.close][idx];
        return `<span class="muted">${k}</span> <span class="${cls}">${number(value)}</span>`;
    }).join(" ");
}

function showChartStatus(text) {
    chartStatus.textContent = text;
    chartStatus.hidden = !text;
}

function renderIntervals() {
    document.getElementById("intervals").innerHTML = INTERVALS.map(i =>
        `<button type="button" class="interval ${i.value === currentInterval ? "active" : ""}"
                 data-interval="${i.value}">${i.label}</button>`
    ).join("");
}

async function loadCandles() {
    if (!chart) {
        createChart();
    }
    const requestId = ++latestChartRequest;
    showChartStatus("Loading…");
    try {
        const path = `/api/instruments/${encodeURIComponent(selected.exchange)}/${encodeURIComponent(selected.symbol)}`
            + `/candles?interval=${currentInterval}`;
        const data = await api(path);
        if (requestId !== latestChartRequest) {
            return;   // user picked another instrument/interval meanwhile
        }

        const candles = data.map(c => ({
            time: c.time + IST_OFFSET_SECONDS, open: c.open, high: c.high, low: c.low, close: c.close,
        }));
        const up = withAlpha(cssVar("--candle-up"), 0.4);
        const down = withAlpha(cssVar("--candle-down"), 0.4);
        // Indices have no volume (always 0); skip the bars instead of drawing a flat line
        const hasVolume = data.some(c => c.volume > 0);
        const volumes = hasVolume ? data.map(c => ({
            time: c.time + IST_OFFSET_SECONDS, value: c.volume, color: c.close >= c.open ? up : down,
        })) : [];

        candleSeries.setData(candles);
        volumeSeries.setData(volumes);
        lastCandle = candles[candles.length - 1] || null;
        renderLegend(lastCandle);

        if (candles.length > VISIBLE_CANDLES) {
            chart.timeScale().setVisibleLogicalRange({ from: candles.length - VISIBLE_CANDLES, to: candles.length + 2 });
        } else {
            chart.timeScale().fitContent();
        }
        showChartStatus(candles.length ? "" : "No candles for this interval");
    } catch (e) {
        if (requestId === latestChartRequest) {
            showChartStatus("Could not load chart");
        }
    }
}

// ---------- Selection ----------

// The selection lives in the URL (?exchange=NSE&symbol=RELIANCE&interval=5minute) so refresh keeps it
function updateUrl() {
    const params = new URLSearchParams({ exchange: selected.exchange, symbol: selected.symbol, interval: currentInterval });
    history.replaceState(null, "", "?" + params);
}

function showSelected(exchange, symbol, name) {
    selected = { exchange, symbol };
    document.getElementById("chart-empty").hidden = true;
    document.getElementById("chart-view").hidden = false;
    document.getElementById("chart-symbol").textContent = symbol;
    document.getElementById("chart-exchange").textContent = exchange;
    document.getElementById("chart-name").textContent = name || "";
    document.title = `${symbol} · Market Watch · AlgoDesk`;
    renderIntervals();
    updateUrl();
    loadCandles();
}

// ---------- Events ----------

// Wait until typing pauses so we don't hit the server on every keystroke
searchInput.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    const query = searchInput.value.trim();
    if (query.length < MIN_QUERY_LENGTH) {
        latestRequest++;   // ignore any request still in flight
        results = [];
        showStatus("Type at least 2 characters to search");
        return;
    }
    debounceTimer = setTimeout(() => search(query), DEBOUNCE_MS);
});

// One listener for the whole list instead of one per row
resultsList.addEventListener("click", e => {
    const item = e.target.closest(".watch-item");
    if (item) {
        const i = results[Number(item.dataset.index)];
        showSelected(i.exchange, i.tradingsymbol, i.name);
        renderResults();   // move the highlight
    }
});

document.getElementById("intervals").addEventListener("click", e => {
    const button = e.target.closest(".interval");
    if (button && button.dataset.interval !== currentInterval) {
        currentInterval = button.dataset.interval;
        renderIntervals();
        updateUrl();
        loadCandles();
    }
});

// Restore the selection after a refresh
const initial = new URLSearchParams(window.location.search);
if (INTERVALS.some(i => i.value === initial.get("interval"))) {
    currentInterval = initial.get("interval");
}
if (initial.get("exchange") && initial.get("symbol")) {
    showSelected(initial.get("exchange"), initial.get("symbol"));
}
