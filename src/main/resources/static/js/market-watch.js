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
    const params = new URLSearchParams(window.location.search);
    return params.get("exchange") === i.exchange && params.get("symbol") === i.tradingsymbol;
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

// Right panel. The selection lives in the URL (?exchange=NSE&symbol=RELIANCE) so refresh keeps it,
// and the chart can later load from the same two values.
function showSelected(exchange, symbol, name) {
    document.getElementById("chart-empty").hidden = true;
    document.getElementById("chart-view").hidden = false;
    document.getElementById("chart-symbol").textContent = symbol;
    document.getElementById("chart-exchange").textContent = exchange;
    document.getElementById("chart-name").textContent = name || "";
    document.title = `${symbol} · Market Watch · AlgoDesk`;
}

function select(i) {
    const params = new URLSearchParams({ exchange: i.exchange, symbol: i.tradingsymbol });
    history.replaceState(null, "", "?" + params);
    showSelected(i.exchange, i.tradingsymbol, i.name);
    renderResults();   // move the highlight
}

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
        select(results[Number(item.dataset.index)]);
    }
});

// Restore the selection after a refresh
const initial = new URLSearchParams(window.location.search);
if (initial.get("exchange") && initial.get("symbol")) {
    showSelected(initial.get("exchange"), initial.get("symbol"));
}
