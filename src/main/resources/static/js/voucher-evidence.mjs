// Local, client-side preview. The existing document endpoint retains its claim and scope checks.
const select = document.querySelector('[data-evidence-select]');
if (select) {
    const pane = select.closest('.approval-evidence-pane');
    const view = pane.querySelector('[data-evidence-view]');
    const canvas = pane.querySelector('[data-evidence-canvas]');
    const image = pane.querySelector('[data-evidence-image]');
    const status = pane.querySelector('[data-evidence-status]');
    const pagination = pane.querySelector('[data-evidence-pagination]');
    const link = pane.querySelector('[data-evidence-open]');
    let pdf, loading, renderTask, controller, blobUrl, zoom = 100, page = 1, generation = 0;
    image.addEventListener('error', () => { status.textContent = status.dataset.error; });
    const vendor = new URL('../vendor/pdfjs/', import.meta.url);
    let library;
    const pdfLibrary = async () => {
        library ??= import(new URL('pdf.min.mjs', vendor).href);
        const result = await library;
        result.GlobalWorkerOptions.workerSrc = new URL('pdf.worker.min.mjs', vendor).href;
        return result;
    };
    const render = async () => {
        pane.querySelector('[data-evidence-zoom-value]').textContent = zoom + '%';
        if (!pdf) {
            if (blobUrl) image.style.width = Math.max(100, view.clientWidth - 20) * zoom / 100 + 'px';
            return;
        }
        const version = ++generation;
        renderTask?.cancel();
        pane.querySelector('[data-evidence-page-value]').textContent = `${page} / ${pdf.numPages}`;
        pane.querySelector('[data-evidence-page="-1"]').disabled = page <= 1;
        pane.querySelector('[data-evidence-page="1"]').disabled = page >= pdf.numPages;
        try {
            const source = await pdf.getPage(page);
            if (version !== generation) return;
            const original = source.getViewport({scale: 1});
            const width = Math.max(100, view.clientWidth - 20) * zoom / 100;
            const cssScale = width / original.width;
            // Bound raster memory even for unusual page boxes and high DPI screens.
            const ratio = Math.min(devicePixelRatio || 1, 2, 2400 / width,
                Math.sqrt(6000000 / (width * width * original.height / original.width)));
            const viewport = source.getViewport({scale: cssScale * ratio});
            canvas.width = Math.ceil(viewport.width);
            canvas.height = Math.ceil(viewport.height);
            canvas.style.width = width + 'px';
            canvas.style.height = width * original.height / original.width + 'px';
            canvas.hidden = false;
            renderTask = source.render({canvasContext: canvas.getContext('2d'), viewport});
            await renderTask.promise;
            if (version === generation) status.textContent = '';
        } catch (error) {
            if (version === generation && error.name !== 'RenderingCancelledException') status.textContent = status.dataset.error;
        }
    };
    const show = async () => {
        const version = ++generation;
        controller?.abort();
        controller = new AbortController();
        renderTask?.cancel();
        loading?.destroy();
        pdf = null;
        if (blobUrl) URL.revokeObjectURL(blobUrl);
        blobUrl = null;
        canvas.hidden = image.hidden = pagination.hidden = true;
        image.removeAttribute('src');
        link.href = select.value;
        status.textContent = status.dataset.loading;
        try {
            const response = await fetch(select.value, {signal: controller.signal, redirect: 'error'});
            if (!response.ok) throw Error();
            const type = response.headers.get('Content-Type')?.split(';')[0];
            if (!['application/pdf', 'image/png', 'image/jpeg'].includes(type)) throw Error();
            const content = await response.arrayBuffer();
            if (version !== generation) return;
            if (type === 'application/pdf') {
                const api = await pdfLibrary();
                if (version !== generation) return;
                loading = api.getDocument({data: new Uint8Array(content), isEvalSupported: false,
                    enableXfa: false, cMapUrl: new URL('cmaps/', vendor).href, cMapPacked: true,
                    standardFontDataUrl: new URL('standard_fonts/', vendor).href,
                    wasmUrl: new URL('wasm/', vendor).href});
                const loaded = await loading.promise;
                if (version !== generation) { loaded.destroy(); return; }
                pdf = loaded;
                pagination.hidden = pdf.numPages <= 1;
            } else {
                blobUrl = URL.createObjectURL(new Blob([content], {type}));
                image.src = blobUrl;
                image.hidden = false;
                status.textContent = '';
            }
            await render();
        } catch (error) {
            if (version === generation && error.name !== 'AbortError') status.textContent = status.dataset.error;
        }
    };
    select.addEventListener('change', () => { zoom = 100; page = 1; show(); });
    pane.querySelectorAll('[data-evidence-zoom]').forEach(button => button.addEventListener('click', () => {
        zoom = Math.max(30, Math.min(200, zoom + Number(button.dataset.evidenceZoom))); render();
    }));
    pane.querySelectorAll('[data-evidence-page]').forEach(button => button.addEventListener('click', () => {
        if (!pdf) return;
        page = Math.max(1, Math.min(pdf.numPages, page + Number(button.dataset.evidencePage))); render();
    }));
    let timer;
    const resize = new ResizeObserver(() => { clearTimeout(timer); timer = setTimeout(render, 150); });
    resize.observe(view);
    window.addEventListener('pagehide', () => {
        generation++; controller?.abort(); renderTask?.cancel(); loading?.destroy();
        clearTimeout(timer); resize.disconnect();
        if (blobUrl) URL.revokeObjectURL(blobUrl);
    });
    window.addEventListener('pageshow', event => { if (event.persisted) { resize.observe(view); show(); } });
    show();
}
