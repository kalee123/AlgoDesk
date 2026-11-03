const REFRESH_MS = 60 * 1000;
const IST_OFFSET_SECONDS = 5.5 * 60 * 60;

// "+₹1,200.00" / "-₹800.00": P&L figures always show their sign
function signedMoney(value) {
    const n = Number(value) || 0;
    return (n > 0 ? "+" : "") + money(n);
}

// Percent can be null (e.g. nothing held overnight); show a dash instead of a misleading 0%
function percentOrDash(value) {
    return value === null || value === undefined ? "—" : percent(value);
}

function chartLink(exchange, symbol) {
    const params = new URLSearchParams({ exchange, symbol, interval: "5minute" });
    return "/market-watch?" + params;
}

// Shown in a tile/table whose data the server couldn't fetch (summary.errors)
function unavailable(reason) {
    return `<span class="muted">Unavailable${reason ? " · " + escapeHtml(reason) : ""}</span>`;
}

// ---------- KPI tiles ----------

function renderTodayPnl(t, error) {
    const el = document.getElementById("kpi-today");
    if (!t) {
        el.innerHTML = `<p class="label">Today's P&amp;L</p>${unavailable(error)}`;
        return;
    }
    el.innerHTML = `
        <p class="label">Today's P&amp;L</p>
        <div class="big ${pnlClass(t.value)}">${signedMoney(t.value)}</div>
        <div class="kpi-change ${pnlClass(t.value)}">${percentOrDash(t.percent)}</div>
        <dl>
            <dt>Holdings</dt><dd class="${pnlClass(t.holdings)}">${signedMoney(t.holdings)}</dd>
            <dt>Positions</dt><dd class="${pnlClass(t.positions)}">${signedMoney(t.positions)}</dd>
        </dl>`;
}

function renderPortfolio(p, error) {
    const el = document.getElementById("kpi-portfolio");
    if (!p) {
        el.innerHTML = `<p class="label">Portfolio value</p>${unavailable(error)}`;
        return;
    }
    el.innerHTML = `
        <p class="label">Portfolio value</p>
        <div class="big">${money(p.value)}</div>
        <div class="kpi-change ${pnlClass(p.pnl)}">${signedMoney(p.pnl)} (${percentOrDash(p.pnlPercent)})</div>
        <dl>
            <dt>Invested</dt><dd>${money(p.invested)}</dd>
            <dt>Current</dt><dd>${money(p.value)}</dd>
        </dl>`;
}

function renderMargin(m, error) {
    const el = document.getElementById("kpi-margin");
    if (!m) {
        el.innerHTML = `<p class="label">Available margin</p>${unavailable(error)}`;
        return;
    }
    const commodity = m.commodity === null || m.commodity === undefined
        ? ""
        : `<dt>Commodity</dt><dd>${money(m.commodity)}</dd>`;
    el.innerHTML = `
        <p class="label">Available margin</p>
        <div class="big">${money(m.value)}</div>
        <div class="kpi-change muted">Used ${money(m.used)}</div>
        <dl>
            <dt>Cash</dt><dd>${money(m.cash)}</dd>
            ${commodity}
        </dl>`;
}

function renderOrderCounts(o, error) {
    const el = document.getElementById("kpi-orders");
    if (!o) {
        el.innerHTML = `<p class="label">Orders today</p>${unavailable(error)}`;
        return;
    }
    el.innerHTML = `
        <p class="label">Orders today</p>
        <div class="big">${o.open} <span class="muted kpi-unit">open</span></div>
        <div class="kpi-change muted">${o.total} placed today</div>
        <dl>
            <dt>Executed</dt><dd class="pos">${o.executed}</dd>
            <dt>Rejected</dt><dd class="${o.rejected ? "neg" : ""}">${o.rejected}</dd>
            <dt>Cancelled</dt><dd>${o.cancelled}</dd>
        </dl>`;
}

// ---------- Tables ----------

function instrumentCell(exchange, symbol) {
    return `<a class="symbol-link" href="${chartLink(exchange, symbol)}">
                <span class="symbol">${escapeHtml(symbol)}</span> <span class="muted">${escapeHtml(exchange)}</span>
            </a>`;
}

function unavailableRow(columns, error) {
    return `<tr><td colspan="${columns}" style="text-align:center">${unavailable(error)}</td></tr>`;
}

function renderMovers(movers, error) {
    const body = document.getElementById("movers-body");
    if (!movers) {
        body.innerHTML = unavailableRow(4, error);
        return;
    }
    body.innerHTML = tableRows(movers, 4, "No holdings", m => `
        <tr>
            <td>${instrumentCell(m.exchange, m.symbol)}</td>
            <td class="num">${money(m.ltp)}</td>
            <td class="num ${pnlClass(m.dayChangePercent)}">${percent(m.dayChangePercent)}</td>
            <td class="num ${pnlClass(m.dayPnl)}">${signedMoney(m.dayPnl)}</td>
        </tr>`);
}

function orderStatusClass(o) {
    if (o.status === "COMPLETE") return "pos";
    if (o.status === "REJECTED" || o.status === "CANCELLED") return "neg";
    return "muted";
}

function renderRecentOrders(orders, error) {
    const body = document.getElementById("orders-body");
    if (!orders) {
        body.innerHTML = unavailableRow(5, error);
        return;
    }
    body.innerHTML = tableRows(orders, 5, "No orders today", o => {
        const side = escapeHtml(o.side);
        return `
            <tr>
                <td class="muted">${escapeHtml(o.time)}</td>
                <td><span class="tag ${side.toLowerCase()}">${side}</span></td>
                <td>${instrumentCell(o.exchange, o.symbol)}</td>
                <td class="num">${number(o.filled)} / ${number(o.quantity)}</td>
                <td class="${orderStatusClass(o)}">${escapeHtml(o.status)}</td>
            </tr>`;
    });
}

function renderPositions(positions, error) {
    const body = document.getElementById("positions-body");
    if (!positions) {
        body.innerHTML = unavailableRow(6, error);
        return;
    }
    body.innerHTML = tableRows(positions, 6, "No open positions", p => `
        <tr>
            <td>${instrumentCell(p.exchange, p.symbol)}</td>
            <td>${escapeHtml(p.product)}</td>
            <td class="num ${p.quantity < 0 ? "neg" : ""}">${number(p.quantity)}</td>
            <td class="num">${money(p.ltp)}</td>
            <td class="num ${pnlClass(p.m2m)}">${signedMoney(p.m2m)}</td>
            <td class="num ${pnlClass(p.pnl)}">${signedMoney(p.pnl)}</td>
        </tr>`);
}

function renderSummary(s) {
    const kpis = s.kpis || {};
    const errors = s.errors || {};
    renderTodayPnl(kpis.todayPnl, errors.holdings || errors.positions);
    renderPortfolio(kpis.portfolio, errors.holdings);
    renderMargin(kpis.availableMargin, errors.margins);
    renderOrderCounts(kpis.orders, errors.orders);
    renderMovers(s.movers, errors.holdings);
    renderRecentOrders(s.recentOrders, errors.orders);
    renderPositions(s.positions, errors.positions);

    const asOf = s.asOf ? new Date(s.asOf) : new Date();
    lastUpdatedText = "Updated " + asOf.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
    document.getElementById("updated").textContent = lastUpdatedText;
}

let lastUpdatedText = "";

// ---------- Index strip ----------

// Small inline SVG line of today's 5-minute closes
function sparkline(closes, cls) {
    if (closes.length < 2) {
        return "";
    }
    const w = 90, h = 28;
    const min = Math.min(...closes), max = Math.max(...closes);
    const range = max - min || 1;
    const points = closes.map((c, i) =>
        `${(i / (closes.length - 1) * w).toFixed(1)},${(h - 2 - (c - min) / range * (h - 4)).toFixed(1)}`
    ).join(" ");
    return `<svg class="spark ${cls}" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" aria-hidden="true">
                <polyline points="${points}" fill="none" stroke="currentColor" stroke-width="1.5"/>
            </svg>`;
}

// Only the latest trading day's candles (the API returns several days)
function lastDayCloses(candles) {
    if (!candles.length) {
        return [];
    }
    const day = c => Math.floor((c.time + IST_OFFSET_SECONDS) / 86400);
    const lastDay = day(candles[candles.length - 1]);
    return candles.filter(c => day(c) === lastDay).map(c => c.close);
}

async function loadSparkline(index, slot) {
    try {
        const candles = await api(`/api/instruments/${encodeURIComponent(index.exchange)}/`
            + `${encodeURIComponent(index.symbol)}/candles?interval=5minute`);
        slot.innerHTML = sparkline(lastDayCloses(candles), pnlClass(index.change));
    } catch (e) {
        slot.innerHTML = "";   // the numbers matter more than the line; fail quietly
    }
}

async function loadIndices(withSparklines) {
    const strip = document.getElementById("indices");
    try {
        const indices = await api("/api/market/indices");
        strip.innerHTML = indices.map((i, n) => `
            <a class="index-item" href="${chartLink(i.exchange, i.symbol)}">
                <span class="index-name">${escapeHtml(i.label)}</span>
                <span class="index-price">${number(i.ltp)}</span>
                <span class="index-change ${pnlClass(i.change)}">
                    ${i.change > 0 ? "+" : ""}${number(i.change)} (${percentOrDash(i.changePercent)})
                </span>
                <span class="index-spark" id="spark-${n}"></span>
            </a>`).join("");
        if (withSparklines) {
            indices.forEach((i, n) => loadSparkline(i, document.getElementById("spark-" + n)));
        }
    } catch (e) {
        strip.innerHTML = `<span class="muted small">Market indices unavailable</span>`;
    }
}

// ---------- Load + auto refresh ----------

async function refresh() {
    if (document.visibilityState !== "visible") {
        return;   // don't spend Kite calls on a background tab
    }
    loadIndices(false);
    try {
        renderSummary(await api("/api/dashboard/summary"));
    } catch (e) {
        // Keep showing the last good numbers, but say they're stale
        document.getElementById("updated").textContent = lastUpdatedText + " · refresh failed";
    }
}

loadIndices(true);
loadPage(async () => renderSummary(await api("/api/dashboard/summary")));
setInterval(refresh, REFRESH_MS);
