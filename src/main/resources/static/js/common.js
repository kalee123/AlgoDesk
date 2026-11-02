// Shared helpers for every page: nav bar, API calls, formatting.

const NAV_ITEMS = [
    { page: "dashboard", label: "Dashboard", href: "/dashboard" },
    { page: "orders",    label: "Orders",    href: "/orders" },
    { page: "holdings",  label: "Holdings",  href: "/holdings" },
    { page: "positions", label: "Positions", href: "/positions" },
    { page: "market-watch", label: "Market Watch", href: "/market-watch" },
];

// Fills <header id="app-nav"> and highlights the link matching <body data-page="...">
function renderNav() {
    const current = document.body.dataset.page;
    const links = NAV_ITEMS.map(item =>
        `<a href="${item.href}" class="${item.page === current ? "active" : ""}">${item.label}</a>`
    ).join("");
    document.getElementById("app-nav").innerHTML = `
        <a class="brand" href="/dashboard">AlgoDesk</a>
        <nav>${links}</nav>
        <form class="logout" method="post" action="/logout">
            <button type="submit" class="button-ghost">Log out</button>
        </form>`;
}

// GET a JSON API. On 401 (session expired, Kite resets tokens at 6 AM IST) go back to login.
async function api(path) {
    const res = await fetch(path, { headers: { Accept: "application/json" } });
    if (res.status === 401) {
        window.location.href = "/login";
        return new Promise(() => {});   // never resolves; the page is navigating away
    }
    if (!res.ok) {
        throw new Error("HTTP " + res.status);
    }
    return res.json();
}

// Runs a page's loader and swaps the "Loading…" message for the content (or an error)
async function loadPage(loader) {
    const status = document.getElementById("status");
    const content = document.getElementById("content");
    try {
        await loader();
        status.hidden = true;
        content.hidden = false;
    } catch (e) {
        status.innerHTML = 'Could not load data. <a href="">Try again</a>';
    }
}

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
const plain = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });

function money(value) {
    return inr.format(Number(value) || 0);
}

function number(value) {
    return plain.format(Number(value) || 0);
}

function percent(value) {
    const n = Number(value) || 0;
    return (n > 0 ? "+" : "") + n.toFixed(2) + "%";
}

// CSS class for profit / loss colouring
function pnlClass(value) {
    const n = Number(value) || 0;
    return n > 0 ? "pos" : n < 0 ? "neg" : "";
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, c => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
}

// Builds a table body; shows a single "empty" row when there's nothing to list
function tableRows(items, columnCount, emptyText, rowHtml) {
    if (!items || items.length === 0) {
        return `<tr><td colspan="${columnCount}" class="muted" style="text-align:center">${emptyText}</td></tr>`;
    }
    return items.map(rowHtml).join("");
}

renderNav();
