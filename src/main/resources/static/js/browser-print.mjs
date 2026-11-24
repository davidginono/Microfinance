const vendor = new URL('../vendor/pdfjs/', import.meta.url);
let releasePrevious;

async function loadPdf(data) {
    const pdfjs = await import(new URL('pdf.min.mjs', vendor).href);
    pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdf.worker.min.mjs', vendor).href;
    return pdfjs.getDocument({
        data,
        cMapUrl: new URL('cmaps/', vendor).href,
        cMapPacked: true,
        standardFontDataUrl: new URL('standard_fonts/', vendor).href,
        wasmUrl: new URL('wasm/', vendor).href,
        isEvalSupported: false
    });
}

// Prepare a regular HTML print document so printing does not depend on the PDF plug-in.
async function renderPdf(blob, document, signal, objectUrls) {
    const data = await blob.arrayBuffer();
    signal.throwIfAborted();
    const loading = await loadPdf(data);
    let renderTask;
    const cancel = () => {
        renderTask?.cancel();
        void loading.destroy().catch(() => {});
    };
    signal.addEventListener('abort', cancel, {once: true});
    // PDF.js registers embedded fonts in the active document. Its canvas must use those fonts.
    const canvas = window.document.createElement('canvas');
    const style = document.createElement('style');
    style.textContent = 'html,body{margin:0;padding:0} img{display:block;break-inside:avoid} img+img{break-before:page}';
    const blankFooter = '@bottom-left{content:"";width:100%;height:1px}';
    document.head.append(style);
    const sizes = new Map();
    try {
        signal.throwIfAborted();
        const pdf = await loading.promise;
        for (let number = 1; number <= pdf.numPages; number++) {
            signal.throwIfAborted();
            const page = await pdf.getPage(number);
            const original = page.getViewport({scale: 1});
            const {width, height} = original;
            const scale = Math.min(2, 3000 / Math.max(width, height), Math.sqrt(4000000 / (width * height)));
            const viewport = page.getViewport({scale});
            canvas.width = Math.ceil(viewport.width);
            canvas.height = Math.ceil(viewport.height);
            renderTask = page.render({canvasContext: canvas.getContext('2d'), viewport, intent: 'print'});
            await renderTask.promise;
            renderTask = null;
            page.cleanup();
            const imageBlob = await new Promise(resolve => canvas.toBlob(resolve));
            signal.throwIfAborted();
            if (!imageBlob) throw new Error('Print page could not be rendered');
            const url = URL.createObjectURL(imageBlob);
            objectUrls.push(url);
            const size = `${width.toFixed(3)}pt ${height.toFixed(3)}pt`;
            if (!sizes.has(size)) {
                const name = `print-page-${sizes.size}`;
                style.textContent += `@page ${name}{size:${size};margin:0;${blankFooter}}`;
                if (!sizes.size) style.textContent += `@page{size:${size};margin:0;${blankFooter}}`;
                sizes.set(size, name);
            }
            const image = document.createElement('img');
            image.alt = '';
            image.style.cssText = `width:${width}pt;height:${height}pt;page:${sizes.get(size)}`;
            image.src = url;
            document.body.append(image);
            await image.decode();
        }
    } finally {
        canvas.width = canvas.height = 0;
        signal.removeEventListener('abort', cancel);
        await loading.destroy();
    }
}

function mountPrintDocument(printDocument, isPdf) {
    const container = document.createElement('div');
    container.setAttribute('data-export-print-root', 'true');
    container.style.display = 'none';
    const content = printDocument.documentElement.cloneNode(true);
    content.querySelectorAll('base, script').forEach(node => node.remove());
    const stylesheets = isPdf ? [] : Array.from(content.querySelectorAll('link[rel="stylesheet"]'));
    const originals = printDocument.querySelectorAll('link[rel="stylesheet"]');
    const loaded = stylesheets.map((link, index) => {
        link.href = originals[index].href;
        return new Promise((resolve, reject) => {
            link.addEventListener('load', resolve, {once: true});
            link.addEventListener('error', () => reject(new Error('Print stylesheet could not be loaded')), {once: true});
        });
    });
    const images = Array.from(content.querySelectorAll('img'));
    const originalImages = printDocument.querySelectorAll('img');
    images.forEach((image, index) => { image.src = originalImages[index].src; });
    const blankMargins = '@top-left{content:"";width:100%;height:1px}@bottom-left{content:"";width:100%;height:1px}';
    const pageRules = Array.from(printDocument.styleSheets).flatMap(sheet =>
        Array.from(sheet.cssRules).filter(rule => rule.type === CSSRule.PAGE_RULE)
            .map(rule => rule.cssText + `\n@page ${rule.selectorText}{${blankMargins}}`));
    const style = document.createElement('style');
    style.media = 'print';
    style.textContent = pageRules.join('\n') + (isPdf ? `@page { size: ${parseFloat(images[0]?.style.width) > parseFloat(images[0]?.style.height) ? 'landscape' : 'portrait'} !important; margin: 0 !important; }` : '') + `
        @page {
            @top-left { content: ""; width: 100%; height: 1px; }
            @bottom-left { content: ""; width: 100%; height: 1px; }
        }
        html, body { display: block !important; position: static !important; margin: 0 !important; padding: 0 !important; width: auto !important; height: auto !important; min-height: 0 !important; max-height: none !important; overflow: visible !important; background: #fff !important; }
        body > :not([data-export-print-root]) { display: none !important; }
        [data-export-print-root] { display: block !important; }
        [data-export-print-root] > img { display: block !important; width: 100% !important; height: auto !important; max-height: 100vh !important; object-fit: contain; object-position: left top; max-width: none !important; margin: 0 !important; padding: 0 !important; border: 0 !important; break-inside: avoid; }
        [data-export-print-root] > img + img { break-before: page; }
    `;
    if (isPdf) {
        images.forEach(image => { image.style.page = 'auto'; });
        container.append(...images);
    }
    else container.attachShadow({mode: 'open'}).append(content);
    document.head.append(style);
    document.body.append(container);
    return {container, style, ready: Promise.all([...loaded, ...images.map(image => image.decode())])};
}

export async function printDocument(blob, target, signal) {
    const type = blob.type.split(';')[0];
    const isPdf = type === 'application/pdf';
    const footerStyle = new URL('../css/print-footer.css', import.meta.url);
    let source = `<!doctype html><html><head><title></title><link rel="stylesheet" href="${footerStyle}"></head><body></body></html>`;
    if (!isPdf) {
        if (type !== 'text/html') throw new Error('Unsupported print response');
        const printable = new DOMParser().parseFromString(await blob.text(), 'text/html');
        if (!printable.body.hasAttribute('data-print-document')) throw new Error('Print document unavailable');
        printable.querySelectorAll('script, base, [data-loan-print], [data-management-print], .statement-print-tools')
            .forEach(node => node.remove());
        const base = printable.createElement('base');
        base.href = target.href;
        printable.head.prepend(base);
        source = '<!doctype html>' + printable.documentElement.outerHTML;
    }
    signal.throwIfAborted();
    releasePrevious?.();
    const objectUrls = [];
    const frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.setAttribute('data-export-print-frame', 'true');
    frame.setAttribute('sandbox', 'allow-same-origin allow-modals');
    frame.tabIndex = -1;
    frame.title = 'Print';
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:1px;height:1px;border:0;opacity:0;pointer-events:none';
    let printRoot;
    const cleanup = () => {
        signal.removeEventListener('abort', cleanup);
        window.removeEventListener('pagehide', cleanup);
        window.removeEventListener('afterprint', cleanup);
        frame.remove();
        printRoot?.container.remove();
        printRoot?.style.remove();
        objectUrls.splice(0).forEach(url => URL.revokeObjectURL(url));
        if (releasePrevious === cleanup) releasePrevious = undefined;
    };
    releasePrevious = cleanup;
    window.addEventListener('pagehide', cleanup, {once: true});
    signal.addEventListener('abort', cleanup, {once: true});
    try {
        await new Promise((resolve, reject) => {
            frame.addEventListener('load', resolve, {once: true, signal});
            signal.addEventListener('abort', () => reject(signal.reason), {once: true});
            frame.srcdoc = source;
            document.body.append(frame);
        });
        const printWindow = frame.contentWindow;
        const printDocument = frame.contentDocument;
        if (!printWindow || !printDocument) throw new Error('Print document unavailable');
        if (isPdf) {
            printDocument.title = document.title;
            await renderPdf(blob, printDocument, signal, objectUrls);
        } else {
            await printDocument.fonts.ready;
        }
        signal.throwIfAborted();
        printRoot = mountPrintDocument(printDocument, isPdf);
        await printRoot.ready;
        await document.fonts.ready;
        signal.throwIfAborted();
        signal.removeEventListener('abort', cleanup);
        window.addEventListener('afterprint', cleanup, {once: true});
        document.head.append(printRoot.style);
        window.print();
    } catch (error) {
        cleanup();
        throw error;
    }
}
