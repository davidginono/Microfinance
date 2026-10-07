(() => {
    const list = document.getElementById('library-rule-list');
    const add = document.getElementById('library-add-rule');
    if (!list || !add) return;
    const renumber = () => {
        const rows = list.querySelectorAll('.library-rule');
        rows.forEach((row, index) => row.querySelectorAll('[name]').forEach(control => {
            control.name = control.name.replace(/rules\[\d+\]/, `rules[${index}]`);
            if (control.id) control.id = control.id.replace(/rules\d+/, `rules${index}`);
        }));
        rows.forEach(row => row.querySelector('.library-remove').disabled = rows.length === 1);
        add.disabled = rows.length >= 5;
    };
    add.addEventListener('click', () => {
        if (list.children.length >= 5) return;
        const row = list.firstElementChild.cloneNode(true);
        const select = row.querySelector('select');
        const enhancedWrapper = select.closest('.neo-select');
        if (enhancedWrapper) enhancedWrapper.replaceWith(select);
        select.removeAttribute('data-neo-enhanced');
        select.classList.remove('neo-select-native');
        row.querySelectorAll('input').forEach(input => input.value = '');
        select.selectedIndex = 0;
        list.appendChild(row); renumber();
        requestAnimationFrame(() => (row.querySelector('.neo-select-button') || select).focus());
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('.library-remove');
        if (button && list.children.length > 1) {button.closest('.library-rule').remove(); renumber(); add.focus();}
    });
    renumber();
})();
