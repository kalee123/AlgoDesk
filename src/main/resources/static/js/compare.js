// Compare page: run several strategies on the same candles in one request
// (POST /api/strategies/backtest?codes=orb,pdb) and show their results side by side.

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

const form = document.getElementById("setup");
const searchInput = document.getElementById("search");
const searchResults = document.getElementById("search-results");
const status = document.getElementById("status");
const content = document.getElementById("content");
const runButton = document.getElementById("run");
const formError = document.getElementById("form-error");

let selected = null;      // { exchange, symbol }
let results = [];
let debounceTimer;
let latestSearch = 0;
let strategies = {};      // code -> {code, name, description}
let lastData = {};        // code -> {candles, trades, summary}, as returned by the server
let activeTab = null;

// ---------- Instrument search ----------

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

// ---------- Strategy checkboxes ----------

async function loadStrategies(checkedCodes) {
    const list = await api("/api/strategies");
    strategies = Object.fromEntries(list.map(s => [s.code, s]));
    document.getElementById("strategies").innerHTML = list.map(s => `
        <label class="check-item" title="${escapeHtml(s.description)}">
            <input type="checkbox" value="${escapeHtml(s.code)}" ${checkedCodes.includes(s.code) ? "checked" : ""}>
            <span>${escapeHtml(s.name)}</span>
            <span class="tag">${escapeHtml(s.code)}</span>
        </label>`).join("");
}

function checkedCodes() {
    return [...document.querySelectorAll("#strategies input:checked")].map(c => c.value);
}

// ---------- Interval + period ----------

let currentInterval = "5minute";
let rangeDays = 90;   // days back from today, or "custom"

function pick(groupId, attr, value) {
    document.querySelectorAll(`#${groupId} .interval`).forEach(b =>
        b.classList.toggle("active", b.dataset[attr] === String(value)));
}

function isoDate(d) {
    return d.toISOString().slice(0, 10);
}

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

function showFormError(text) {
    formError.textContent = text;
    formError.hidden = !text;
}

form.addEventListener("submit", e => {
    e.preventDefault();
    const codes = checkedCodes();
    if (!selected) {
        showFormError("Pick an instrument from the search list first");
        return;
    }
    if (codes.length === 0) {
        showFormError("Tick at least one strategy");
        return;
    }
    const p = period();
    if (!p.from || !p.to) {
        showFormError("Pick both dates");
        return;
    }
    showFormError("");
    saveSetup(codes);
    run({ exchange: selected.exchange, symbol: selected.symbol, interval: currentInterval, from: p.from, to: p.to }, codes);
});

async function run(body, codes) {
    runButton.disabled = true;
    status.textContent = `Running ${codes.length} strateg${codes.length === 1 ? "y" : "ies"}…`;
    status.hidden = false;
    content.hidden = true;
    const started = performance.now();
    try {
        const res = await fetch(`/api/strategies/backtest?codes=${codes.map(encodeURIComponent).join(",")}`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify(body),
        });
        if (res.status === 401) {
            window.location.href = "/login";
            return;
        }
        const data = await res.json();
        if (!res.ok) {
            showFormError(data.error || "Compare failed");
            status.hidden = true;
            return;
        }
        const ms = Math.round(performance.now() - started);
        lastData = data;
        document.getElementById("title").textContent = `${body.exchange}:${body.symbol} · ${body.interval}`;
        document.getElementById("timing").textContent = `Round trip ${ms} ms`;
        renderSummary(codes);
        renderTabs(codes);
        status.hidden = true;
        content.hidden = false;
    } catch (e) {
        showFormError("Could not run the comparison");
        status.hidden = true;
    } finally {
        runButton.disabled = false;
    }
}

// ---------- Summary: one row per requested strategy ----------

function signed(value, digits = 2) {
    const n = Number(value) || 0;
    return (n > 0 ? "+" : "") + n.toFixed(digits);
}

function strategyName(code) {
    return strategies[code] ? strategies[code].name : code;
}

// A code we asked for but got nothing back for: on the server its thread failed (see the log)
function renderSummary(codes) {
    const extra = Object.keys(lastData).filter(c => !codes.includes(c));   // returned but not asked for
    document.getElementById("summary-body").innerHTML = [...codes, ...extra].map(code => {
        const r = lastData[code];
        if (!r) {
            return `<tr>
                <td><span class="symbol">${escapeHtml(strategyName(code))}</span> <span class="tag">${escapeHtml(code)}</span></td>
                <td colspan="6" class="neg small">No result returned. Check the server log</td>
            </tr>`;
        }
        const s = r.summary;
        const pf = s.profitFactor == null ? "—" : Number(s.profitFactor).toFixed(2);
        const exits = Object.entries(s.exitReasons || {})
            .map(([k, v]) => `${v} ${k.toLowerCase().replace("_", " ")}`).join(" · ") || "—";
        return `<tr>
            <td><span class="symbol">${escapeHtml(strategyName(code))}</span> <span class="tag">${escapeHtml(code)}</span></td>
            <td class="num">${s.trades}</td>
            <td class="num">${Number(s.winRate).toFixed(1)}%</td>
            <td class="num ${pnlClass(s.totalPnl)}">${signed(s.totalPnl)}</td>
            <td class="num ${s.profitFactor > 1 ? "pos" : s.profitFactor != null ? "neg" : ""}">${pf}</td>
            <td class="num ${s.maxDrawdown > 0 ? "neg" : ""}">${Number(s.maxDrawdown).toFixed(2)}</td>
            <td class="muted small">${escapeHtml(exits)}</td>
        </tr>`;
    }).join("");
}

// ---------- Trades: a tab per strategy that came back ----------

function renderTabs(codes) {
    const returned = [...codes.filter(c => lastData[c]), ...Object.keys(lastData).filter(c => !codes.includes(c))];
    document.getElementById("trade-tabs").innerHTML = returned.map(code =>
        `<button type="button" class="interval" role="tab" data-code="${escapeHtml(code)}">${escapeHtml(code)}</button>`
    ).join("");
    showTab(returned.includes(activeTab) ? activeTab : returned[0]);
}

function hhmm(epoch) {
    return new Date(epoch * 1000).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Asia/Kolkata" });
}

function showTab(code) {
    activeTab = code;
    pick("trade-tabs", "code", code);
    const trades = code ? lastData[code].trades : [];
    document.getElementById("trades-body").innerHTML = tableRows(trades, 7, "No trades in this period", t => `
        <tr title="Levels ${number(t.rangeLow)} – ${number(t.rangeHigh)} · stop ${number(t.stopLoss)} · target ${number(t.target)}">
            <td>${escapeHtml(t.date)}</td>
            <td><span class="tag ${t.direction === "LONG" ? "buy" : "sell"}">${t.direction}</span></td>
            <td>${hhmm(t.entryTime)} <span class="muted">@</span> ${number(t.entryPrice)}</td>
            <td>${hhmm(t.exitTime)} <span class="muted">@</span> ${number(t.exitPrice)}</td>
            <td class="muted small">${escapeHtml(t.exitReason.toLowerCase().replace("_", " "))}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnl)}</td>
            <td class="num ${pnlClass(t.pnl)}">${signed(t.pnlPercent)}%</td>
        </tr>`);
}

document.getElementById("trade-tabs").addEventListener("click", e => {
    const b = e.target.closest(".interval");
    if (b) {
        showTab(b.dataset.code);
    }
});

// ---------- Remember the setup per browser ----------

const SETUP_KEY = "algodesk.compare";

function saveSetup(codes) {
    try {
        localStorage.setItem(SETUP_KEY, JSON.stringify({
            selected, codes, interval: currentInterval, range: rangeDays,
            from: document.getElementById("from").value, to: document.getElementById("to").value,
        }));
    } catch (e) { /* storage blocked: the page still works, it just won't remember */ }
}

function loadSetup() {
    try { return JSON.parse(localStorage.getItem(SETUP_KEY)) || {}; } catch (e) { return {}; }
}

async function init() {
    const saved = loadSetup();
    try {
        await loadStrategies(Array.isArray(saved.codes) ? saved.codes : ["orb", "pdb"]);
    } catch (e) {
        showFormError("Could not load the strategy list");
        return;
    }
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
    }
}

init();
