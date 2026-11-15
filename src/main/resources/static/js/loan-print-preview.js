(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => document.addEventListener('DOMContentLoaded', callback, {once: true}));
    ready(() => {
        const dialog = document.getElementById('loanPrintPreview');
        if (!dialog || dialog.dataset.ready) return;
        dialog.dataset.ready = 'true';
        document.body.append(dialog);
        const frame = dialog.querySelector('[data-preview-frame]'), body = dialog.querySelector('[data-preview-body]');
        const error = dialog.querySelector('[data-preview-error]'), print = dialog.querySelector('[data-preview-print]');
        let controller, objectUrl, target, trigger, overflow;
        const clear = () => {
            controller?.abort(); controller = null;
            window.AppLoading?.end(body);
            frame.removeAttribute('srcdoc'); frame.removeAttribute('src'); frame.hidden = true; print.hidden = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            objectUrl = null;
        };
        const load = async () => {
            clear(); error.hidden = true;
            const request = controller = new AbortController();
            const timeout = setTimeout(() => request.abort(), 60000);
            window.AppLoading?.begin(body);
            const finish = window.AppLoading?.action(trigger);
            try {
                const response = await fetch(target.href, {credentials: 'same-origin', redirect: 'error', signal: request.signal});
                if (!response.ok) throw new Error('Print document unavailable');
                const blob = await response.blob();
                if (controller !== request || !dialog.open) return;
                let source;
                if (blob.type.split(';')[0] === 'application/pdf') {
                    objectUrl = URL.createObjectURL(blob);
                } else if (blob.type.split(';')[0] === 'text/html') {
                    const document = new DOMParser().parseFromString(await blob.text(), 'text/html');
                    if (!document.body.hasAttribute('data-print-document')) throw new Error('Print document unavailable');
                    document.querySelectorAll('script,base,[data-loan-print]').forEach(node => node.remove());
                    const base = document.createElement('base'); base.href = target.href; document.head.prepend(base);
                    if (controller !== request || !dialog.open) return;
                    source = '<!doctype html>' + document.documentElement.outerHTML;
                } else throw new Error('Unsupported print document');
                const loaded = new Promise((resolve, reject) => {
                    frame.addEventListener('load', resolve, {once: true, signal: request.signal});
                    frame.addEventListener('error', reject, {once: true, signal: request.signal});
                    request.signal.addEventListener('abort', () => reject(new Error('Preview cancelled')), {once: true});
                });
                if (source) frame.srcdoc = source; else frame.src = objectUrl + '#view=FitH';
                frame.hidden = false;
                await loaded;
                if (source) print.hidden = false;
            } catch (failure) {
                if (controller === request && dialog.open) {frame.hidden = true; print.hidden = true; error.hidden = false;}
            } finally {
                clearTimeout(timeout); finish?.();
                if (controller === request) window.AppLoading?.end(body);
            }
        };
        document.querySelectorAll('[data-loan-print-preview]').forEach(link => link.addEventListener('click', event => {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            target = new URL(link.href, location.href);
            if (target.origin !== location.origin) return;
            event.preventDefault(); trigger = link;
            if (!dialog.open) {
                overflow = [document.body.style.overflow, document.documentElement.style.overflow];
                document.body.style.overflow = document.documentElement.style.overflow = 'hidden';
                dialog.showModal();
            }
            void load();
        }));
        dialog.querySelectorAll('[data-preview-close]').forEach(button => button.addEventListener('click', () => dialog.close()));
        dialog.querySelector('[data-preview-retry]').addEventListener('click', () => void load());
        print.addEventListener('click', () => frame.contentWindow?.print());
        dialog.addEventListener('close', () => {
            clear();
            if (overflow) [document.body.style.overflow, document.documentElement.style.overflow] = overflow;
            trigger?.focus({preventScroll: true});
        });
        window.addEventListener('pagehide', event => {
            if (dialog.open) dialog.close(); else clear();
            if (!event.persisted) dialog.remove();
        });
    });
})();
