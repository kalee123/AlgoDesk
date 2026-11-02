const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

const searchInput = document.getElementById("search");
const status = document.getElementById("status");
const content = document.getElementById("content");

let debounceTimer;
let latestRequest = 0;

function showStatus(text) {
    status.textContent = text;
    status.hidden = false;
    content.hidden = true;
}

function formatExpiry(expiry) {
    if (!expiry) {
        return "";
    }
    return new Date(expiry).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function renderResults(items) {
    document.getElementById("results-body").innerHTML = tableRows(items, 7, "No matching instruments", i => `
        <tr>
            <td class="symbol">${escapeHtml(i.tradingsymbol)}</td>
            <td class="muted">${escapeHtml(i.name)}</td>
            <td>${escapeHtml(i.exchange)}</td>
            <td><span class="tag">${escapeHtml(i.instrument_type)}</span></td>
            <td>${formatExpiry(i.expiry)}</td>
            <td class="num">${i.strike ? number(i.strike) : ""}</td>
            <td class="num">${number(i.lot_size)}</td>
        </tr>`);
    status.hidden = true;
    content.hidden = false;
}

async function search(query) {
    // Responses can arrive out of order; only the newest request may update the table
    const requestId = ++latestRequest;
    try {
        const items = await api("/api/instruments/search?q=" + encodeURIComponent(query));
        if (requestId === latestRequest) {
            renderResults(items);
        }
    } catch (e) {
        if (requestId === latestRequest) {
            showStatus("Search failed. Try again.");
        }
    }
}

// Wait until typing pauses so we don't hit the server on every keystroke
searchInput.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    const query = searchInput.value.trim();
    if (query.length < MIN_QUERY_LENGTH) {
        latestRequest++;   // ignore any request still in flight
        showStatus("Type at least 2 characters to search");
        return;
    }
    debounceTimer = setTimeout(() => search(query), DEBOUNCE_MS);
});
