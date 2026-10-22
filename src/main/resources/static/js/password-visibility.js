(() => {
    const eyePath = "M2.2 10s2.8-5 7.8-5 7.8 5 7.8 5-2.8 5-7.8 5-7.8-5-7.8-5Zm7.8 2.4a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8Z";
    const eyeOffPath = "m3 3 14 14M8.6 5.2A8.9 8.9 0 0 1 10 5c5 0 7.8 5 7.8 5a14.2 14.2 0 0 1-2.5 3.1M6.3 6.3A14.8 14.8 0 0 0 2.2 10S5 15 10 15c1.2 0 2.3-.3 3.3-.8M8.6 8.6a2 2 0 0 0 2.8 2.8";

    const icon = (button) => {
        let svg = button.querySelector("svg");
        if (svg) {
            return svg;
        }
        svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("viewBox", "0 0 20 20");
        svg.setAttribute("fill", "none");
        svg.setAttribute("stroke", "currentColor");
        svg.setAttribute("stroke-width", "1.8");
        svg.setAttribute("stroke-linecap", "round");
        svg.setAttribute("stroke-linejoin", "round");
        svg.setAttribute("aria-hidden", "true");
        button.replaceChildren(svg);
        return svg;
    };

    const update = (button, revealed) => {
        const label = button.dataset[revealed ? "hideLabel" : "showLabel"] || (revealed ? "Hide" : "Show");
        icon(button).innerHTML = "<path d=\"" + (revealed ? eyeOffPath : eyePath) + "\"/>";
        button.setAttribute("aria-label", label + " password");
        button.setAttribute("title", label + " password");
        button.setAttribute("aria-pressed", String(revealed));
    };

    const initialize = () => {
        document.querySelectorAll("[data-password-visibility]").forEach((button) => update(button, false));
        document.addEventListener("click", (event) => {
            const button = event.target.closest("[data-password-visibility]");
            if (!button) {
                return;
            }
            const input = document.getElementById(button.getAttribute("aria-controls"));
            if (!input || (input.type !== "password" && input.type !== "text")) {
                return;
            }
            const revealed = input.type === "password";
            input.type = revealed ? "text" : "password";
            update(button, revealed);
            input.focus({preventScroll: true});
        });
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initialize, {once: true});
    } else {
        initialize();
    }
})();
