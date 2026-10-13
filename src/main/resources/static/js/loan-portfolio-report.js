(() => {
    const error = document.getElementById('portfolio-error');
    if (error) error.focus();
    const tabs = document.querySelector('[data-portfolio-tabs]');
    if (!tabs) return;
    const links = Array.from(tabs.querySelectorAll('a'));
    const panels = Array.from(document.querySelectorAll('[data-portfolio-panel]'));
    tabs.setAttribute('role', 'tablist');
    links.forEach(link => {
        link.setAttribute('role', 'tab');
        link.setAttribute('aria-controls', link.hash.slice(1));
    });
    panels.forEach(panel => {
        panel.setAttribute('role', 'tabpanel');
        panel.tabIndex = 0;
    });
    const activate = (hash, focus = false) => {
        const selected = links.find(link => link.hash === hash) || links[0];
        links.forEach(link => {
            const active = link === selected;
            link.setAttribute('aria-selected', String(active));
            link.tabIndex = active ? 0 : -1;
        });
        panels.forEach(panel => { panel.hidden = '#' + panel.id !== selected.hash; });
        if (focus) selected.focus();
    };
    links.forEach((link, index) => {
        link.addEventListener('click', event => {
            event.preventDefault();
            history.replaceState(history.state, '', link.hash);
            activate(link.hash);
        });
        link.addEventListener('keydown', event => {
            let target;
            if (event.key === 'ArrowRight') target = (index + 1) % links.length;
            if (event.key === 'ArrowLeft') target = (index + links.length - 1) % links.length;
            if (event.key === 'Home') target = 0;
            if (event.key === 'End') target = links.length - 1;
            if (target === undefined) return;
            event.preventDefault();
            history.replaceState(history.state, '', links[target].hash);
            activate(links[target].hash, true);
        });
    });
    window.addEventListener('hashchange', () => activate(location.hash));
    window.addEventListener('pageshow', () => activate(location.hash));
    activate(location.hash);
})();
