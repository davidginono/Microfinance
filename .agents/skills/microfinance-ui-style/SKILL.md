---
name: microfinance-ui-style
description: Build or restyle Microfinance JSP interfaces using the existing compact ERP shell, shared CSS, forms, controls, and modals. Use for rendered UI changes without introducing a new frontend framework.
---

# Microfinance UI Style

## Start With The Shared Shell

Read the relevant definitions before changing individual pages:

- `src/main/webapp/WEB-INF/jsp/fragments/header.jspf`
- `src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf`
- `src/main/resources/static/css/shell.css`
- `src/main/resources/static/css/console-components.css`

The current interface is a compact operational ERP, not a lending marketing site. Preserve its predictable topbar/sidebar and shared controls. Existing `sacco-*` and `aws-*` CSS names are implementation details; do not mass-rename them during a terminology change.

## Surfaces And Typography

- Reuse shared tokens and ERP classes for workspace canvas, neutral surfaces, borders, text, and status colors. Keep navigation and institution identity distinct from the data workspace.
- Prefer unframed page sections and a restrained white surface for genuinely framed forms, tables, or dialogs. Do not nest cards or turn every section into a floating card.
- Use modest corners, normally at most 8px, compact headings, ordinary letter spacing, and the existing local font. Do not introduce oversized headings, viewport-scaled type, decorative gradients, or visual effects behind financial data.
- Use semantic status colors with readable labels or icons; color alone must not communicate approval, delinquency, or reversal.
- Institution logos and names belong in the shared shell. Do not add SACCO/cooperative branding or decorative stock imagery to operational loan screens.

## Controls And Forms

- Reuse `app-btn`, `btn-primary`, `btn-neutral`, and `erp-icon-btn` rather than creating page-specific button systems.
- Use the available Lucide integration for new tool icons. Give icon-only controls an accessible name and a tooltip when their purpose is not obvious; retain short verb labels for financial commands.
- Use selects for option sets, segmented controls for modes, checkboxes/toggles for binary choices, and suitable numeric inputs for amounts and rates. Keep currency, rate basis, and repayment frequency adjacent to their values.
- Group fields by identity, business/employment, cash flow, guarantors, collateral, and documents when relevant. Preserve field values and inline errors across failed submissions.
- Arrange related controls with wrapping flex or responsive grid tracks and `min-width: 0`. Keep command buttons compact, allowing labels to wrap when necessary without clipping.

## Shared Modal Contract

- Reuse the existing modal implementation and `app-modal-panel` / `app-modal-actions` styling. Keep a white surface, thin neutral border, modest corners, simple close icon, and right-aligned footer commands.
- Mount overlays under `document.body` so fixed headers and shell overflow do not clip them. Keep the dialog within the viewport and let its body scroll rather than hiding its actions.
- Provide a dialog name, sensible initial focus, focus containment, and focus restoration. Support Escape where cancellation is safe; do not dismiss an in-flight financial command as if it had not been submitted.
- Keep success, failure, and pending feedback concise. Never imply a payment succeeded before the server confirms posting.

## Responsive And Accessible Checks

- Check representative widths of 360px, 768px, 1366px, and 1920px, plus long institution/client names and both languages.
- Only a table's scroll viewport may overflow horizontally. Page content, menus, dialogs, and toolbars must fit without clipped labels or overlapping actions.
- Preserve the mobile sidebar, profile/notification dropdowns, visible focus, label associations, and touch-friendly controls. Keep the key loan context near the action at every width.
- Capture rendered screenshots and exercise affected interactions when browser access is available. If the application cannot be run, report that visual verification is pending rather than claiming it passed.
- JSP changes need runtime rendering checks; Java compilation alone does not prove JSP correctness. Keep any temporary screenshots or diagnostic helpers out of production commits.
