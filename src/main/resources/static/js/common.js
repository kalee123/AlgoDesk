// Shared helpers for every page: nav bar, API calls, formatting.

const NAV_ITEMS = [
    { page: "dashboard", label: "Dashboard", href: "/dashboard" },
    { page: "orders",    label: "Orders",    href: "/orders" },
    { page: "holdings",  label: "Holdings",  href: "/holdings" },
    { page: "positions", label: "Positions", href: "/positions" },
    { page: "market-watch", label: "Market Watch", href: "/market-watch" },
    { page: "backtest", label: "Backtest", href: "/backtest" },
];

const PROFILE_CACHE_KEY = "algodesk.profile";

// Fills <header id="app-nav"> and highlights the link matching <body data-page="...">
function renderNav() {
    const current = document.body.dataset.page;
    const links = NAV_ITEMS.map(item =>
        `<a href="${item.href}" class="${item.page === current ? "active" : ""}">${item.label}</a>`
    ).join("");
    // <details> gives an open/close dropdown without any JS
    document.getElementById("app-nav").innerHTML = `
        <a class="brand" href="/dashboard">AlgoDesk</a>
        <nav>${links}</nav>
        <button type="button" class="theme-toggle" id="theme-toggle"></button>
        <details class="account-menu">
            <summary>
                <span class="avatar" id="account-avatar">·</span>
                <span class="account-id" id="account-id">Account</span>
            </summary>
            <div class="account-panel">
                <div id="account-details" class="muted">Loading…</div>
                <form method="post" action="/logout" id="logout-form">
                    <button type="submit" class="button-ghost">Log out</button>
                </form>
            </div>
        </details>`;

    document.getElementById("theme-toggle").addEventListener("click", toggleTheme);
    updateThemeButton();

    document.getElementById("logout-form").addEventListener("submit", () => {
        try { sessionStorage.removeItem(PROFILE_CACHE_KEY); } catch (e) { /* storage blocked: nothing cached */ }
    });
    // Close the menu when clicking anywhere else
    document.addEventListener("click", e => {
        const menu = document.querySelector(".account-menu");
        if (menu && menu.open && !menu.contains(e.target)) {
            menu.open = false;
        }
    });
    loadAccount();
}

// ---------- Theme ----------
// theme.js (in <head>) applies a saved choice before the page draws; this is the switch.

const THEME_KEY = "algodesk.theme";

function currentTheme() {
    return document.documentElement.dataset.theme
        || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

function updateThemeButton() {
    const button = document.getElementById("theme-toggle");
    const dark = currentTheme() === "dark";
    button.textContent = dark ? "☀" : "☾";   // shows what you'll switch TO
    button.title = dark ? "Switch to light theme" : "Switch to dark theme";
    button.setAttribute("aria-label", button.title);
}

function toggleTheme() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* not saved, still applied */ }
    updateThemeButton();
    // Charts draw with colours read from CSS at draw time, so tell them to redraw
    window.dispatchEvent(new Event("themechange"));
}

// The profile barely changes, so fetch it once per browser tab and reuse it on every page
async function loadAccount() {
    let profile = null;
    try { profile = JSON.parse(sessionStorage.getItem(PROFILE_CACHE_KEY)); } catch (e) { /* ignore */ }
    if (!profile) {
        try {
            profile = await api("/api/profile");
            try { sessionStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(profile)); } catch (e) { /* ignore */ }
        } catch (e) {
            document.getElementById("account-details").textContent = "Could not load profile";
            return;
        }
    }
    const initials = String(profile.user_name || "?").split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase();
    document.getElementById("account-avatar").textContent = initials;
    document.getElementById("account-id").textContent = profile.user_id || "Account";
    const exchanges = (profile.exchanges || []).map(e => `<span class="chip">${escapeHtml(e)}</span>`).join("");
    document.getElementById("account-details").innerHTML = `
        <div class="account-name">${escapeHtml(profile.user_name)}</div>
        <div class="muted small">${escapeHtml(profile.email)}</div>
        <div class="muted small">${escapeHtml(profile.user_id)} · ${escapeHtml(profile.broker)}</div>
        <div class="chips">${exchanges}</div>`;
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
