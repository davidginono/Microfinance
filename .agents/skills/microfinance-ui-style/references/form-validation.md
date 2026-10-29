# Form validation

Use the [Cloudscape validation pattern](https://cloudscape.design/patterns/general/errors/validation/) with the existing JSP shell. The user's supplied classic AWS screenshot controls the visual display; it does not add bucket fields or business rules. Reference reviewed 26/10/2026. Keep this implementation JSP/JavaScript based.

## Display and recovery

- Keep the field label and normal white control surface. An invalid control gets a thin red border and red focus outline. Put an outlined 16px triangle beside plain, regular-weight 14px red text immediately after the control and before any essential constraint/hint text. The error wraps on narrow screens. Do not tint whole field groups, surround inline errors with alert boxes, or use a toast or browser validation bubble as the field error display.
- Do not mark untouched fields invalid on arrival. Validate when the user leaves a field, including an empty required field; recheck errors while the user edits. On submit or wizard Next, reveal the affected section and focus/scroll to the first invalid control. Keep submission available for discovering errors; retain real permission, prerequisite and pending-command restrictions.
- Keep field-specific server errors inline, including conflicts such as an unavailable code. Editing clears the old response's display; only another server validation establishes whether the new value is acceptable. Never infer successful financial validation from a corrected browser constraint.
- Errors without a field belong in the shared error Alert immediately before the form actions. Use a specific reason and recovery action. Avoid a generic top summary or repeating every inline message. Preserve existing server notices until their sources can classify them safely; do not guess field bindings from error wording.
- Preserve all entered values, CSRF tokens, request keys and native submitters. Do not change financial policy, authorization, server validation, posting, draft completeness rules or existing confirmation handlers to implement presentation.

## Shared implementation

For platform create/edit submissions, `data-workspace-submit="recover"` retains the live form on recoverable server failures, including selected files and secret inputs kept only in memory. `platform-form-ux.js` opts in the authorized platform forms; the shared navigation layer preserves the submitter, CSRF and multipart payload and releases pending controls on failure. Place unbound errors immediately above the footer and focus them. Native redirects may retain only the explicitly allowlisted non-secret draft fields. A lost response gives an uncertain-result message and never replays the POST automatically. Page edits with `data-unchanged-url` return to their initiating page with informational feedback when unchanged. Preserve the existing submit owner on financial forms.

`fragments/header.jspf` loads `static/css/form-validation.css` and `static/js/form-validation.js`, and supplies localized strings through `#form-validation-labels`. Use the existing English/Kiswahili bundles for new messages. Keep feedback short, actionable and specific: a missing value, the invalid format, or the actual bound. Essential constraint text stays separate from the triggered error.

The script retains browser constraint validation and its submission prevention. It handles captured `invalid` events to replace browser bubbles, shows errors on `focusout`, and rechecks displayed errors on `input`/`change`. It does not set `novalidate`, call `form.submit()`, disable Submit for missing values, or intercept valid financial commands. Existing `novalidate` / `formnovalidate` draft commands retain their behavior. Custom `setCustomValidity` messages remain authoritative for their client checks.

For server-bound fields, use Spring `<form:errors path="..." cssClass="coa-error" />` beside the bound control. Spring's generated `field.errors` ID binds the message to the form element's ID/name, including indexed rows. Custom field errors can use `data-field-error-for="fieldName"`. Do not duplicate an error ID when cloning rows. Composite/group validators should retain their owning implementation's group targeting; do not guess one field when the error belongs to several controls.

The shared layer adds `erp-field-error`, preserves the message and existing hint IDs, and applies `aria-invalid="true"` plus the error ID in `aria-describedby`. Generated errors use a polite live region; the triangle is decorative. Searchable select triggers and date display inputs receive the same error state as their named native controls. Native field values, ISO dates, hidden model bindings and selector menus remain owned by their existing adapters.

Initialization uses `WorkspaceNavigation.onReady`, with a DOM-ready fallback. Dynamic row/server error insertion is observed. `window.FormValidation.refresh(root)` binds inserted server errors when an owning component needs synchronous setup; `focusFirst(form)` reveals/focuses the first bound error and returns whether it found one. Reset clears managed errors without erasing unrelated hint associations. Existing workflow validation remains responsible for its business-specific field groups and wizard transitions.

## Verification

Exercise a pristine form, blur of empty/invalid fields, live correction, multiple invalid fields on submit, nested collapsed sections, native and searchable selects, date input/calendar and bounds, required consent, radio groups, bound server errors, dynamic indexed rows and reset/reopen. Verify that Save draft can still bypass completeness validation, valid submissions reach the original handler, and errors do not disable or spin-lock the form.

Check keyboard focus, preserved `aria-describedby` hint IDs, visible error text independent of color, both languages, and wrapping at 360px, 768px, 1366px and 1920px. Run the shared JavaScript regression tests. A browser fixture verifies the component; report separately when authenticated JSP routes or live posting workflows could not be exercised.
