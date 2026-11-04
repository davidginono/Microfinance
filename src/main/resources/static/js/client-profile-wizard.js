/* Keep the native profile form and its server-owned draft/submission commands. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn, {once:true});
        else fn();
    });
    ready(() => document.querySelectorAll('[data-profile-wizard]').forEach(wizard => {
        if (wizard.dataset.wizardReady) return;
        const form = wizard.closest('form'), labels = form.querySelector('[data-profile-wizard-labels]')?.dataset;
        if (!labels) return;
        const registration = wizard.hasAttribute('data-profile-registration');
        const keys = registration ? ['personal', 'finances', 'review'] : ['personal', 'finances', 'documents', 'review'];
        const reviewIndex = keys.indexOf('review'), documentsIndex = keys.indexOf('documents');
        const sections = [...wizard.querySelectorAll('[data-profile-section]')];
        const previous = form.querySelector('[data-profile-previous]'), next = form.querySelector('[data-profile-next]');
        const send = form.querySelector('[data-profile-send]');
        if (!previous || !next || !send) return;
        wizard.dataset.wizardReady = 'true';wizard.classList.add('kyc-profile-wizard');
        const node = (tag, text, className) => {
            const el = document.createElement(tag);
            if (text !== undefined) el.textContent = text;
            if (className) el.className = className;
            return el;
        };
        const nav = node('nav'), list = node('ol', undefined, 'kyc-profile-steps'), main = node('div');
        nav.setAttribute('aria-label', labels.navigation);nav.append(list);
        const buttons = [], panels = [];
        keys.forEach((key, index) => {
            const item = node('li'), button = node('button'), panel = node('div', undefined, 'kyc-profile-step');
            const heading = node('h2', labels[key]);heading.id = 'profile-step-' + key + '-heading';heading.tabIndex = -1;
            panel.id = 'profile-step-' + key;panel.setAttribute('aria-labelledby', heading.id);panel.append(heading);
            button.type = 'button';button.setAttribute('aria-controls', panel.id);
            button.append(node('small', labels.step + ' ' + (index + 1)), node('span', labels[key]));
            item.append(button);list.append(item);main.append(panel);buttons.push(button);panels.push(panel);
        });
        // Move live controls, never clone inputs or persist their values in browser storage.
        sections.forEach(section => panels[keys.indexOf(section.dataset.profileSection)].append(section));
        wizard.replaceChildren(nav, main);
        const breadcrumb = wizard.closest('.portal-entry')?.querySelector(':scope > .erp-page-path');
        const measureSticky = () => {
            wizard.style.setProperty('--kyc-step-nav-height', nav.offsetHeight + 'px');
            if (breadcrumb) wizard.style.setProperty('--kyc-breadcrumb-height', breadcrumb.offsetHeight + 'px');
        };
        measureSticky();
        if (window.ResizeObserver) {
            const observer = new ResizeObserver(measureSticky);
            observer.observe(nav);
            if (breadcrumb) observer.observe(breadcrumb);
        }
        const storageKey = 'client-profile-step:v1:' + location.pathname.replace(/\/edit$/, '');
        let active = 0, furthest = 0, invalidPending = false;
        const fieldText = field => {
            const label = field.querySelector(':scope > legend,:scope > span,:scope > label');
            if (label) return label.textContent.trim().replace(/\s*\*$/, '');
            const copy = field.cloneNode(true);copy.querySelectorAll('input,select,textarea,.erp-field-error').forEach(el => el.remove());
            return copy.textContent.trim();
        };
        const valueText = field => {
            const controls = [...field.querySelectorAll('input:not([type="hidden"]),select,textarea')]
                .filter(control => !control.matches(':disabled') && !control.hasAttribute('data-date-display') && !control.hasAttribute('data-amount-display'));
            if (!controls.length) return null;
            if (field.matches('.kyc-income-choices')) return controls.filter(control => control.checked).map(control => fieldText(control.closest('label'))).join(', ') || labels.empty;
            const control = controls[0];
            if (control.type === 'checkbox') return control.checked ? labels.yes : labels.no;
            if (control.tagName === 'SELECT' && (control.value || control.name === 'profile.groupId')) return control.selectedOptions[0]?.textContent.trim() || labels.empty;
            return control.value || labels.empty;
        };
        const review = () => {
            const panel = panels[reviewIndex];panel.querySelectorAll('.kyc-profile-review').forEach(section => section.remove());
            keys.slice(0, reviewIndex).forEach((key, step) => sections.filter(section => section.dataset.profileSection === key && !section.hidden).forEach(section => {
                const title = section.querySelector('h2,h3').textContent.trim();
                const summary = node('section', undefined, 'erp-resource-section kyc-profile-review');
                const header = node('div', undefined, 'kyc-profile-review-heading'), edit = node('button', labels.edit, 'app-btn btn-neutral');
                edit.type = 'button';edit.setAttribute('aria-label', labels.edit + ': ' + title);edit.addEventListener('click', () => show(step));
                header.append(node('h3', title), edit);summary.append(header);
                const facts = node('dl');
                const add = (name, value) => {const fact = node('div');fact.append(node('dt', name), node('dd', value));facts.append(fact);};
                section.querySelectorAll('.kyc-field,.kyc-checkbox,.kyc-income-choices').forEach(field => {
                    if (section.contains(field.closest('[hidden]')) || field.matches('.kyc-checkbox') && field.closest('.kyc-income-choices')) return;
                    const value = valueText(field);if (value !== null) add(fieldText(field), value);
                });
                if (section.querySelector('[data-profile-document]') || section.querySelector('[name="declarationAccepted"]')) {
                    const documents = [...section.querySelectorAll('[data-profile-document]')]
                        .map(doc => (doc.querySelector('[data-profile-file-name]') || doc).textContent.trim());
                    const fact = node('div');fact.append(node('dt', title), node('dd', documents.join('\n') || labels.noDocuments));facts.prepend(fact);
                }
                summary.append(facts);panel.insertBefore(summary, panel.querySelector('[data-profile-section="review"]'));
            }));
        };
        function show(index, focus = true) {
            active = index;furthest = Math.max(furthest, index);
            panels.forEach((panel, i) => panel.hidden = i !== index);
            buttons.forEach((button, i) => {
                button.disabled = i > furthest;
                if (i === index) button.setAttribute('aria-current', 'step');else button.removeAttribute('aria-current');
            });
            previous.hidden = index === 0;next.hidden = index === reviewIndex;send.hidden = index !== reviewIndex;
            if (index === reviewIndex) review();
            if (!registration) {
                try {sessionStorage.setItem(storageKey, keys[index]);} catch {/* Navigation also works without storage. */}
            }
            if (focus) {const heading = panels[index].querySelector('h2');heading.focus({preventScroll:true});heading.scrollIntoView?.({block:'start'});}
        }
        const advance = () => {
            if (active === documentsIndex && !form.dispatchEvent(new CustomEvent('profile:before-review', {cancelable:true}))) return;
            const invalid = [...panels[active].querySelectorAll('input,select,textarea')].find(control => control.willValidate && !control.checkValidity());
            if (invalid) {invalid.reportValidity();return;}
            if (active < reviewIndex) show(active + 1);
        };
        previous.addEventListener('click', () => show(Math.max(0, active - 1)));
        next.addEventListener('click', advance);
        buttons.forEach((button, index) => button.addEventListener('click', () => show(index)));
        form.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.isComposing && active < reviewIndex &&
                panels[active].contains(event.target) && event.target.matches('input[name]:not([type="hidden"],[type="checkbox"],[type="radio"])')) {
                event.preventDefault();advance();
            }
        });
        form.addEventListener('invalid', event => {
            if (invalidPending) return;
            const index = panels.findIndex(panel => panel.contains(event.target));
            if (index >= 0) {invalidPending = true;show(index, false);setTimeout(() => invalidPending = false, 0);}
        }, true);
        form.addEventListener('submit', event => {
            if (event.submitter === send && active !== reviewIndex) {event.preventDefault();show(reviewIndex);}
        });
        const documentErrors = [labels.documentsError, labels.documentExpiryError, labels.acknowledgementError, labels.noticeChangedError];
        const errorStep = () => {
            const field = [...wizard.querySelectorAll('[aria-invalid="true"],.field-error:not(:empty),.coa-error:not(:empty)')].find(field => !field.hidden);
            if (field) return panels.findIndex(panel => panel.contains(field));
            const error = form.querySelector('[data-workspace-form-error]');
            if (error) return documentsIndex >= 0 && documentErrors.some(text => error.textContent.includes(text)) ? documentsIndex : reviewIndex;
            return -1;
        };
        document.addEventListener('workspace:form-settled', event => {
            if (event.detail.form === form && form.isConnected) {const index = errorStep();if (index >= 0) show(index, false);}
        });
        let saved;
        if (!registration) {
            try {saved = sessionStorage.getItem(storageKey);} catch {/* Start with personal details. */}
        }
        const error = errorStep();show(error >= 0 ? error : Math.max(0, keys.indexOf(saved)), false);
    }));
})();
