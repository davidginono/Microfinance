/* Shared JSP selects. Cloudscape caret/check geometry is Apache-2.0;
   see vendor/cloudscape-icons.LICENSE and vendor/cloudscape-icons.NOTICE. */
(function () {
        if (window.__neoSelectEnhancerLoaded) {
            return;
        }
        window.__neoSelectEnhancerLoaded = true;

        const enhancedStates = new WeakMap();
        let globalObserver = null;
        let globalHandlersBound = false;
        let selectSerial = 0;

        function enableSearch(state) {
            if (state.search) return;
            const controls = document.createElement('div'); controls.className = 'neo-select-search'; controls.dataset.neoSelectControls = 'true';
            const search = document.createElement('input'); search.type = 'search'; search.maxLength = 100;
            search.placeholder = document.body.dataset.selectSearch; search.setAttribute('aria-label',document.body.dataset.selectSearch);
            search.setAttribute('aria-controls',state.list.id);
            controls.append(search); state.menu.prepend(controls); state.search = search;
            search.addEventListener('input', () => renderEnhancedSelect(state.select));
            search.addEventListener('keydown', event => {
                if (event.key === 'Enter') event.preventDefault();
                if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {event.preventDefault();focusOption(state,event.key === 'ArrowDown' ? 'first' : 'last');}
                if (event.key === 'Escape') {event.preventDefault();event.stopPropagation();closeEnhancedSelect(state);state.button.focus();}
            });
        }

        window.NeoSelect = { search(select) {
            enhanceSelect(select); const state = enhancedStates.get(select); if (!state) return;
            enableSearch(state);
            if (!state.footer) {
                state.footer = document.createElement('button'); state.footer.type = 'button'; state.footer.className = 'neo-select-more';
                state.footer.textContent = document.body.dataset.selectLoadMore; state.footer.hidden = true; state.menu.append(state.footer);
            }
            renderEnhancedSelect(select);
            return {input:state.search, more:state.footer, setLoading(loading) {state.loading=loading;state.list.setAttribute('aria-busy',String(loading));renderEnhancedSelect(select);}, setError(message) {state.lookupError=message;renderEnhancedSelect(select);}};
        }};

        function selectOpen(state) {state.select.dispatchEvent(new CustomEvent('neo-select:open'));}

        function closeEnhancedSelect(state) {
            if (!state || !state.wrapper.classList.contains('neo-select--open')) {
                return;
            }
            state.wrapper.classList.remove('neo-select--open');
            state.button.setAttribute('aria-expanded', 'false');
            state.menu.classList.remove('is-open');
            state.menu.hidden = true;
            state.select.dispatchEvent(new CustomEvent('neo-select:close'));
        }

        function closeAllEnhancedSelects(exceptState) {
            document.querySelectorAll('.neo-select').forEach(function (wrapper) {
                const nativeSelect = wrapper.querySelector('select[data-neo-enhanced="true"]');
                if (!nativeSelect) {
                    return;
                }
                const state = enhancedStates.get(nativeSelect);
                if (state && state !== exceptState) {
                    closeEnhancedSelect(state);
                }
            });
        }

        function focusOption(state, direction) {
            const items = Array.from(state.menu.querySelectorAll('.neo-select-option:not(.is-disabled)'));
            if (!items.length) {
                return;
            }
            const selectedItem = state.menu.querySelector('.neo-select-option.is-selected:not(.is-disabled)');
            let index = items.indexOf(document.activeElement);
            if (index < 0) index = selectedItem ? items.indexOf(selectedItem) : -1;
            if (direction === 'first') {
                index = 0;
            } else if (direction === 'last') {
                index = items.length - 1;
            } else if (direction === 'next') {
                index = Math.min(index + 1, items.length - 1);
            } else if (direction === 'prev') {
                index = Math.max(index - 1, 0);
            }
            const target = items[Math.max(0,index)];
            if (target) {
                target.focus({ preventScroll: true });
                target.scrollIntoView({ block: 'nearest' });
            }
        }

        function dispatchSelectionEvents(select) {
            select.dispatchEvent(new Event('input', { bubbles: true }));
            select.dispatchEvent(new Event('change', { bubbles: true }));
        }

        function renderEnhancedSelect(select) {
            const state = enhancedStates.get(select);
            if (!state || !select.isConnected) {
                return;
            }

            const options = Array.from(select.options);
            const selectedOption = options[select.selectedIndex] || options.find(function (option) {
                return option.selected;
            }) || options[0] || null;
            const selectedLabel = selectedOption ? selectedOption.textContent.trim() : '';
            const placeholderSelected = selectedOption != null && selectedOption.value === '' && !select.value && selectedOption.dataset.emptyOption !== 'true';
            const query = (state.search?.value || '').trim().toLocaleLowerCase();
            const visibleOptions = state.loading ? [] : options.filter(function (option) {
                return !option.hidden && option.dataset.placeholder !== 'true'
                    && (!state.search || option.value !== '' || option.dataset.emptyOption === 'true')
                    && (!query || option.textContent.toLocaleLowerCase().includes(query) || option.dataset.searchQuery === query);
            });

            state.button.classList.toggle('is-placeholder', placeholderSelected);
            state.button.classList.toggle('is-disabled', select.disabled);
            state.button.disabled = select.disabled;
            if (select.disabled) closeEnhancedSelect(state);
            state.buttonText.textContent = selectedLabel || document.body.dataset.shellSelect || 'Select an option';
            state.button.title = selectedLabel || document.body.dataset.shellSelect || 'Select an option';
            state.list.replaceChildren();
            state.menu.querySelector('.neo-select-loading')?.remove();
            if(state.loading) {
                const loading=document.createElement('div');loading.className='neo-select-loading';loading.setAttribute('role','status');
                loading.setAttribute('aria-label',document.body.dataset.shellLoading);
                const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.setAttribute('aria-hidden','true');loading.append(spinner);
                state.list.after(loading);
            }
            select.dispatchEvent(new CustomEvent('neo-select:render', { detail: { menu: state.menu } }));

            if (state.lookupError && !state.loading) {
                const error = document.createElement('div');error.className = 'neo-select-empty';error.setAttribute('role','alert');error.textContent = state.lookupError;state.list.append(error);
                if (state.wrapper.classList.contains('neo-select--open')) positionMenu(state);
                return;
            }

            if (!visibleOptions.length && !state.loading) {
                const emptyState = document.createElement('div');
                emptyState.className = 'neo-select-empty';
                emptyState.setAttribute('role','status');
                emptyState.textContent = state.loading ? document.body.dataset.shellLoading : query ? document.body.dataset.selectNoMatches : document.body.dataset.shellNoOptions;
                state.list.appendChild(emptyState);
                if (state.wrapper.classList.contains('neo-select--open')) {
                    positionMenu(state);
                }
                return;
            }

            visibleOptions.forEach(function (option) {
                const index = options.indexOf(option);
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'neo-select-option';
                item.setAttribute('role', 'option');
                item.setAttribute('data-option-index', String(index));
                const label=document.createElement('span');label.className='neo-select-option-label';label.textContent=option.textContent.trim();item.append(label);
                const check = document.createElement('span'); check.className = 'neo-select-check'; check.setAttribute('aria-hidden','true');
                check.innerHTML = '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M1 9L5 13L15 2"/></svg>'; item.appendChild(check);
                item.disabled = option.disabled || !!option.parentElement.disabled;
                if (item.disabled) {
                    item.classList.add('is-disabled');
                }
                if (option.selected) {
                    item.classList.add('is-selected');
                    item.setAttribute('aria-selected', 'true');
                } else {
                    item.setAttribute('aria-selected', 'false');
                }
                item.addEventListener('click', function () {
                    if (item.disabled || state.loading || !option.isConnected || option.closest('select') !== select) {
                        return;
                    }
                    select.value = option.value;
                    state.wrapper.classList.remove('neo-select--invalid');
                    renderEnhancedSelect(select);
                    dispatchSelectionEvents(select);
                    closeEnhancedSelect(state);
                    state.button.focus({ preventScroll: true });
                });
                item.addEventListener('keydown', function (event) {
                    if (event.key === 'ArrowDown') {
                        event.preventDefault();
                        focusOption(state, 'next');
                    } else if (event.key === 'ArrowUp') {
                        event.preventDefault();
                        focusOption(state, 'prev');
                    } else if (event.key === 'Home') {
                        event.preventDefault();
                        focusOption(state, 'first');
                    } else if (event.key === 'End') {
                        event.preventDefault();
                        focusOption(state, 'last');
                    } else if (event.key === 'Escape') {
                        event.preventDefault();
                        event.stopPropagation();
                        closeEnhancedSelect(state);
                        state.button.focus({ preventScroll: true });
                    }
                });
                state.list.appendChild(item);
            });

            if (state.wrapper.classList.contains('neo-select--open')) {
                positionMenu(state);
            }
        }

        function positionMenu(state) {
            if (!state || !state.wrapper.isConnected || !state.button.isConnected) {
                return;
            }

            const viewportPadding = 8;
            const maxVisibleRows = 6;
            const compactRowHeight = 36;
            const rect = state.button.getBoundingClientRect();
            const viewportTop = window.visualViewport?.offsetTop || 0;
            const viewportBottom = viewportTop + (window.visualViewport?.height || window.innerHeight);
            const availableBelow = Math.max(0, viewportBottom - rect.bottom - viewportPadding);
            const controls = state.menu.querySelector('[data-neo-select-controls]');
            const optionCount = state.list.children.length;
            const visibleRows = Math.max(1, Math.min(maxVisibleRows, optionCount || 1));
            const rowsHeight=[...state.list.children].slice(0,visibleRows).reduce((height,item)=>height+Math.max(compactRowHeight,item.getBoundingClientRect().height),0);
            const desiredHeight = rowsHeight + (controls ? controls.offsetHeight || 48 : 0) + (state.loading ? 40 : 0) + (state.footer?.hidden === false ? 36 : 0) + 2;
            const availableAbove = Math.max(0, rect.top - viewportTop - viewportPadding);
            const upward = availableBelow < Math.min(desiredHeight,150) && availableAbove > availableBelow;
            const menuHeight = Math.min(desiredHeight, upward ? availableAbove : availableBelow);

            state.menu.style.width = Math.min(rect.width, window.innerWidth - 16) + 'px';
            state.menu.style.left = Math.max(8, Math.min(rect.left, window.innerWidth - rect.width - 8)) + 'px';
            state.menu.style.top = (upward ? rect.top - menuHeight : rect.bottom) + 'px';
            state.menu.style.height = menuHeight + 'px';
            state.menu.style.maxHeight = menuHeight + 'px';
        }

        function positionAllOpenMenus() {
            document.querySelectorAll('.neo-select.neo-select--open').forEach(function (wrapper) {
                const nativeSelect = wrapper.querySelector('select[data-neo-enhanced="true"]');
                if (!nativeSelect) {
                    return;
                }
                const state = enhancedStates.get(nativeSelect);
                if (state) {
                    positionMenu(state);
                }
            });
        }

        function openEnhancedSelect(state) {
            if (!state || state.button.disabled) {
                return;
            }
            closeAllEnhancedSelects(state);
            state.wrapper.classList.add('neo-select--open');
            state.button.setAttribute('aria-expanded', 'true');
            state.menu.classList.add('is-open');
            state.menu.hidden = false;
            selectOpen(state);
            window.requestAnimationFrame(function () {
                positionMenu(state);
                if (state.search) state.search.focus({preventScroll:true});
                else focusOption(state, 'selected');
            });
        }

        function toggleEnhancedSelect(state) {
            if (!state) {
                return;
            }
            if (state.wrapper.classList.contains('neo-select--open')) {
                closeEnhancedSelect(state);
                return;
            }
            openEnhancedSelect(state);
        }

        function enhanceSelect(select) {
            if (!select || select.dataset.neoEnhanced === 'true' || select.multiple || select.size > 1 || select.closest('.neo-select') || select.type === 'hidden' || select.dataset.nativeSelect === 'true') {
                return;
            }

            const wrapper = document.createElement('div');
            wrapper.className = 'neo-select';
            select.parentNode.insertBefore(wrapper, select);
            wrapper.appendChild(select);

            select.dataset.neoEnhanced = 'true';
            select.classList.add('neo-select-native');
            select.tabIndex = -1;

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'neo-select-button';
            button.setAttribute('aria-haspopup', 'listbox');
            button.setAttribute('aria-expanded', 'false');
            button.innerHTML =
                '<span class="neo-select-button-text"></span>' +
                '<svg class="neo-select-chevron" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">' +
                    '<path d="M8 11L12 5H4L8 11Z"></path>' +
                '</svg>';

            const menu = document.createElement('div');
            menu.className = 'neo-select-menu';
            menu.setAttribute('data-neo-select-menu', 'true');
            menu.hidden = true;
            const list = document.createElement('div'); list.className = 'neo-select-options'; list.setAttribute('role','listbox');
            list.id = 'neo-select-options-' + (++selectSerial); button.setAttribute('aria-controls',list.id);
            const label = select.getAttribute('aria-label') || Array.from(select.labels).map(label => Array.from(label.childNodes).filter(node=>node.nodeType===3).map(node=>node.textContent).join('').trim()).filter(Boolean).join(' ') || select.name;
            button.setAttribute('aria-label',label); list.setAttribute('aria-label',label);
            menu.append(list);

            wrapper.appendChild(button);
            document.body.appendChild(menu);

            const state = {
                select: select,
                wrapper: wrapper,
                button: button,
                buttonText: button.querySelector('.neo-select-button-text'),
                menu: menu, list: list, loading: false
            };
            enhancedStates.set(select, state);
            menu.addEventListener('keydown', event => {
                if (event.key === 'Tab') {closeEnhancedSelect(state);button.focus();}
            });

            if (select.id) {
                document.querySelectorAll('label[for="' + select.id + '"]').forEach(function (label) {
                    label.addEventListener('click', function (event) {
                        if (event.target.closest('.neo-select')) {
                            return;
                        }
                        event.preventDefault();
                        if (!button.disabled) {
                            button.focus({ preventScroll: true });
                            toggleEnhancedSelect(state);
                        }
                    });
                });
            }

            button.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleEnhancedSelect(state);
            });
            button.addEventListener('keydown', function (event) {
                if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    openEnhancedSelect(state);
                } else if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    openEnhancedSelect(state);
                    window.requestAnimationFrame(function () {
                        focusOption(state, 'last');
                    });
                } else if (event.key === 'Escape') {
                    event.preventDefault();
                    if (state.wrapper.classList.contains('neo-select--open')) event.stopPropagation();
                    closeEnhancedSelect(state);
                }
            });

            select.addEventListener('change', function () {
                state.wrapper.classList.remove('neo-select--invalid');
                renderEnhancedSelect(select);
            });
            select.addEventListener('focus', () => button.focus());
            select.addEventListener('invalid', function (event) {
                event.preventDefault();
                state.wrapper.classList.add('neo-select--invalid');
                state.button.focus({ preventScroll: true });
            });

            const observer = new MutationObserver(function () {
                if (!state.search && select.options.length >= 8) enableSearch(state);
                renderEnhancedSelect(select);
            });
            observer.observe(select, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['disabled', 'label', 'selected', 'value']
            });
            state.observer = observer;

            if (select.options.length >= 8 || select.dataset.searchable === 'true' || select.hasAttribute('data-choice-select')) enableSearch(state);
            renderEnhancedSelect(select);
        }

        function initEnhancedSelects(root) {
            const scope = root || document;
            scope.querySelectorAll('select').forEach(function (select) {
                enhanceSelect(select);
            });
        }

        function bindGlobalHandlers() {
            if (globalHandlersBound) {
                return;
            }
            globalHandlersBound = true;

            document.addEventListener('click', function (event) {
                if (!event.target.closest('.neo-select') && !event.target.closest('[data-neo-select-menu="true"]')) {
                    closeAllEnhancedSelects();
                }
            });

            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    closeAllEnhancedSelects();
                }
            });

            window.addEventListener('resize', positionAllOpenMenus);
            window.addEventListener('scroll', positionAllOpenMenus, true);
            window.visualViewport?.addEventListener('resize',positionAllOpenMenus);
            window.visualViewport?.addEventListener('scroll',positionAllOpenMenus);
        }

        function observeNewSelects() {
            if (globalObserver || !document.body) {
                return;
            }
            globalObserver = new MutationObserver(function (records) {
                records.forEach(function (record) {
                    record.addedNodes.forEach(function (node) {
                        if (!(node instanceof HTMLElement)) {
                            return;
                        }
                        if (node.matches && node.matches('select')) {
                            enhanceSelect(node);
                        }
                        if (node.querySelectorAll) {
                            initEnhancedSelects(node);
                        }
                    });
                    record.removedNodes.forEach(function (node) {
                        if (!(node instanceof HTMLElement)) return;
                        const selects = node.matches('select') ? [node] : Array.from(node.querySelectorAll('select'));
                        selects.forEach(select => {const state=enhancedStates.get(select);if(state && !select.isConnected){closeEnhancedSelect(state);state.observer.disconnect();state.menu.remove();enhancedStates.delete(select);}});
                    });
                });
            });
            globalObserver.observe(document.body, { childList: true, subtree: true });
        }

        window.refreshEnhancedSelects = function (root) {
            initEnhancedSelects(root || document);
            document.querySelectorAll('select[data-neo-enhanced="true"]').forEach(function (select) {
                renderEnhancedSelect(select);
            });
        };

        const onReady = window.WorkspaceNavigation?.onReady || (callback => document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', callback, {once:true}) : callback());
        onReady( function () {
            bindGlobalHandlers();
            initEnhancedSelects(document);
            observeNewSelects();
        });
    }());
