(() => {
    document.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-check-delete]');
        if (!button) return;
        const form = button.closest('[data-archive-delete]');
        const status = form.querySelector('[data-delete-status]');
        button.disabled = true;
        status.textContent = 'Checking Foresight…';
        try {
            const response = await fetch(form.dataset.checkUrl, {
                headers: {Accept: 'application/json'},
                credentials: 'same-origin',
                cache: 'no-store',
                signal: AbortSignal.timeout(15000)
            });
            if (!response.ok) throw new Error('Check failed');
            const result = await response.json();
            status.textContent = result.message;
            if (result.eligible === true) form.requestSubmit();
        } catch (_) {
            status.textContent = 'Unable to check Foresight. Please retry.';
        } finally {
            button.disabled = false;
        }
    });
})();
