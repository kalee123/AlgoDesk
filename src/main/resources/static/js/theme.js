// Loaded in <head>, before the page draws, so a saved theme applies without a flash of the other one.
// No saved choice = follow the system (handled by CSS).
(function () {
    try {
        const saved = localStorage.getItem("algodesk.theme");
        if (saved === "light" || saved === "dark") {
            document.documentElement.dataset.theme = saved;
        }
    } catch (e) {
        // storage blocked: just follow the system
    }
})();
