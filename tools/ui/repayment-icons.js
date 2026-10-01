import { createIcons, Receipt, Search, Check, Undo2, Printer } from 'lucide';

function renderRepaymentIcons() {
    createIcons({
        icons: { Receipt, Search, Check, Undo2, Printer },
        attrs: { width: 16, height: 16, 'aria-hidden': 'true', focusable: 'false' }
    });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', renderRepaymentIcons, { once: true });
} else {
    renderRepaymentIcons();
}
