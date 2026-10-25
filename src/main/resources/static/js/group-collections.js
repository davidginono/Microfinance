(() => {
    const initialize = () => {
        const form = document.querySelector('[data-group-collection]');
        if (!form || form.dataset.collectionReady) return;
        form.dataset.collectionReady = 'true';
        const values = () => JSON.stringify([...new FormData(form)].filter(([name]) => !['previewHash','_csrf','action'].includes(name)));
        const baseline = values();
        ['input','change'].forEach(type => form.addEventListener(type, () => {
            if (values() === baseline) return;
            form.elements.previewHash.value = '';
            const preview = form.querySelector('[data-collection-preview]');
            if (preview) preview.hidden = true;
            const post = form.querySelector('[data-collection-post]');
            if (post) post.disabled = true;
        }));
    };
    if (window.WorkspaceNavigation?.onReady) window.WorkspaceNavigation.onReady(initialize);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',initialize,{once:true});
    else initialize();
})();
