document.querySelectorAll('[data-repayment-form]').forEach(function (form) {
    form.addEventListener('submit', function () {
        var button = form.querySelector('[data-repayment-submit]');
        if (button) {
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
        }
    });
});
document.querySelectorAll('[data-repayment-print]').forEach(function (button) {
    button.addEventListener('click', function () { window.print(); });
});
window.addEventListener('pageshow', function () {
    document.querySelectorAll('[data-repayment-submit]').forEach(function (button) {
        button.disabled = false;
        button.removeAttribute('aria-busy');
    });
});
