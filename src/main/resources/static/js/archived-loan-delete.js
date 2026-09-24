(() => {
    document.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-check-delete]');
        if (!button) return;
        const form = button.closest('[data-archive-delete]');
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        const pending = window.showToast('info', 'Checking Foresight…');
        try {
            const response = await fetch(form.dataset.checkUrl, {
                headers: {Accept: 'application/json'},
                credentials: 'same-origin',
                cache: 'no-store',
                signal: AbortSignal.timeout(15000)
            });
            if (response.status === 401 || response.redirected) throw new Error('Your session expired. Please sign in again.');
            if (response.status === 403) throw new Error('You do not have permission to delete this loan.');
            if (!response.ok) throw new Error('The loan check failed on the server. Please retry or contact support.');
            const result = await response.json();
            if (result.eligible === true) form.requestSubmit();
            else window.showToast('error', result.message);
        } catch (error) {
            window.showToast('error', error.name === 'TimeoutError'
                ? 'The loan check timed out. Please retry.'
                : error instanceof TypeError || error instanceof SyntaxError
                    ? 'Unable to reach the loan check. Check your connection and retry.' : error.message);
        } finally {
            pending?.dismiss();
            button.disabled = false;
            button.removeAttribute('aria-busy');
        }
    });
})();
