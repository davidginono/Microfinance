/* Group the visible amount; keep the existing numeric control authoritative for scripts and transport. */
(() => {
    'use strict';
    const selector = 'input[type="number"][data-amount-input="true"]';
    const states = new Map(), valueProperty = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    let serial = 0;
    const plain = value => value.replace(/,/g, '').trim();
    const valid = value => /^-?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value);
    const grouped = value => value.replace(/^(\-?\d+)(.*)$/, (_, whole, fraction) => whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + fraction);
    const invalidMessage = () => document.getElementById('amount-input-labels')?.dataset.invalid || '';

    function enhance(native) {
        if (states.has(native)) return;
        // Row templates can contain a cloned presentation, but never its listeners or state.
        let wrapper = native.closest('.erp-amount-control');
        if (wrapper) wrapper.querySelectorAll('[data-amount-display]').forEach(input => input.remove());
        else {
            wrapper = document.createElement('span'); wrapper.className = 'erp-amount-control';
            native.before(wrapper); wrapper.append(native);
        }
        const input = document.createElement('input'); input.type = 'text'; input.inputMode = 'decimal';
        input.autocomplete = 'off'; input.dataset.amountDisplay = '';
        input.id = `erp-amount-${++serial}-display`;
        const oldDisplayId = native.dataset.amountDisplayId;
        document.querySelectorAll('label[for]').forEach(label => {
            if ((native.id && label.htmlFor === native.id) || (label.htmlFor === oldDisplayId && (label.contains(wrapper) || !document.getElementById(oldDisplayId)))) label.htmlFor = input.id;
        });
        native.dataset.amountDisplayId = input.id;
        wrapper.prepend(input); native.classList.add('erp-amount-native');
        native.tabIndex = -1; native.setAttribute('aria-hidden', 'true');
        let emitting = false, lastValue, invalidText = false;
        function sync() {
            if (!emitting && native.value !== lastValue) {input.value = grouped(native.value); invalidText = false; lastValue = native.value;}
            input.className = native.className.replace(/\berp-amount-native\b/g, '').trim();
            input.disabled = native.disabled; input.readOnly = native.readOnly; input.required = native.required;
            input.placeholder = native.placeholder;
            for (const key of ['aria-label','aria-labelledby','aria-describedby','aria-invalid','title']) {
                if (native.hasAttribute(key)) input.setAttribute(key, native.getAttribute(key)); else input.removeAttribute(key);
            }
            input.setCustomValidity(invalidText ? invalidMessage() : '');
        }
        function commit(type, event) {
            event.stopPropagation();
            if (event.isComposing) return;
            const raw = plain(input.value), caret = input.selectionStart, before = input.value.slice(0, caret).replace(/,/g, '').length;
            invalidText = input.value.trim() !== '' && !valid(raw);
            emitting = true;
            native.value = invalidText ? '' : raw.replace(/\.$/, '');
            lastValue = native.value;
            if (!invalidText) {
                input.value = grouped(raw);
                let position = 0, digits = 0;
                while (position < input.value.length && digits < before) if (input.value[position++] !== ',') digits++;
                input.setSelectionRange(position, position);
            }
            input.setCustomValidity(invalidText ? invalidMessage() : '');
            native.dispatchEvent(new Event(type, {bubbles:true}));
            emitting = false;
        }
        input.addEventListener('input', event => commit('input', event));
        input.addEventListener('compositionend', event => commit('input', event));
        input.addEventListener('change', event => commit('change', event));
        // Backspace/Delete beside a separator must remove a digit instead of reinserting the same comma.
        input.addEventListener('beforeinput', event => {
            if (input.selectionStart !== input.selectionEnd) return;
            const caret = input.selectionStart, backward = event.inputType === 'deleteContentBackward', forward = event.inputType === 'deleteContentForward';
            if (backward && input.value[caret - 1] === ',') input.setSelectionRange(caret - 1, caret);
            else if (forward && input.value[caret] === ',') input.setSelectionRange(caret, caret + 1);
            else return;
            event.preventDefault();
            const start = backward ? Math.max(0, caret - 2) : caret, end = backward ? caret : caret + 2;
            input.setRangeText('', start, end, 'start'); input.dispatchEvent(new Event('input', {bubbles:true}));
        });
        for (const type of ['input','change']) native.addEventListener(type, () => {if (!emitting) sync();});
        native.addEventListener('invalid', event => {event.preventDefault(); input.focus();});
        Object.defineProperty(native, 'value', {configurable:true, get() {return valueProperty.get.call(this);}, set(value) {
            valueProperty.set.call(this, value); if (!emitting) {lastValue = undefined; invalidText = false; sync();}
        }});
        native.focus = options => input.focus(options);
        const observer = new MutationObserver(sync);
        observer.observe(native, {attributes:true, attributeFilter:['value','min','max','step','disabled','readonly','required','class','aria-label','aria-labelledby','aria-describedby','aria-invalid','title','placeholder']});
        states.set(native, {sync, observer, reset() {lastValue = undefined; invalidText = false; sync();}}); sync();
    }
    function scan(root = document) {
        if (root.matches?.(selector)) enhance(root);
        root.querySelectorAll?.(selector).forEach(enhance);
    }
    function ready() {
        scan();
        new MutationObserver(records => {
            for (const record of records) record.addedNodes.forEach(node => {if (node.nodeType === 1) scan(node);});
            for (const [native, state] of states) if (!native.isConnected) {state.observer.disconnect(); states.delete(native);}
        }).observe(document.body, {childList:true, subtree:true});
    }
    document.addEventListener('reset', event => setTimeout(() => {
        if (!event.defaultPrevented) for (const [native, state] of states) if (native.form === event.target) state.reset();
    }, 0));
    window.addEventListener('pageshow', () => states.forEach(state => state.sync()));
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once:true});
    else ready();
    window.AmountInputs = {refresh:scan};
})();
