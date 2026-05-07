---
name: saccos-lms-ui-governance
description: UI and navigation governance for SACCOS_LMS. Use when generating or editing JSP, HTML, controller-backed page flows, shared fragments, dashboards, workspaces, forms, review screens, or navigation patterns in this repository. Enforce single-source navigation in the sidebar, reduce UI memory load by avoiding explanatory text, and apply Shneiderman and Nielsen usability rules while staying within the existing ERP-style system.
---

# SACCOS LMS UI Governance

## Navigation

- Keep navigations in one place: the sidebar.
- Do not repeat the same workspace navigation links inside page content areas.
- Do not add route-launch buttons such as `Open SACCOs`, `Back To Dashboard`, `Open Event Log`, or `Open Registry` inside page bodies when those destinations already exist in the sidebar.
- Do not add duplicate "Back to Queue", "Go to Dashboard", "Open Archive", or similar links inside cards or action sections when the sidebar already provides that route.
- Use modern sidebar behavior by default:
  - parent menu groups with subitems should collapse by default and expand only after explicit user action
  - use clear chevrons, `aria-expanded`, and persistent open/closed state when helpful
  - when a submenu item is selected, render that destination as its own focused view instead of stacking sibling destinations on one page
- When a portfolio dashboard and a registry both exist, keep them as separate sidebar destinations instead of combining cards and registry tables on one page.
- Use content-area actions only for record-specific tasks:
  - submit
  - approve
  - reject
  - print
  - download
  - request OTP
- If a route must be reachable outside the sidebar, justify it only when it is record-specific and not a global workspace destination.

## Cognitive Load

- Do not add explanatory paragraphs to the UI by default.
- Prefer short labels, short helper text, and strong visual grouping over descriptive copy.
- Remove repeated status explanations, process explanations, and instructional text when the interface already makes the action clear.
- Do not add mini dashboard stat cards or summary-number strips to non-dashboard pages.
- Keep counts, totals, and KPI summaries on dedicated dashboard routes only unless the user explicitly asks for inline analytics on that page.
- Keep dashboard routes card-focused by default. Do not place operational registry tables on dashboards unless the user explicitly asks for a combined view.
- Keep headings concise and direct.
- Use one clear prompt for risky actions instead of surrounding the action with multiple warnings.
- Favor recognition over recall:
  - show the needed values near the action
  - avoid making the user remember prior screen details

## Shneiderman Rules

- Strive for consistency in labels, buttons, spacing, card structure, and status treatments.
- Enable frequent users to use shortcuts:
  - keep common actions predictable
  - avoid putting alternate navigations of any of the sidebar menu items in the main content.
  - avoid moving primary actions between pages
- Offer informative feedback:
  - use concise success, error, and pending states
  - avoid verbose explanations
- Design dialogs to yield closure:
  - confirmations should make the consequence clear
  - success states should feel final and calm
- Prevent errors:
  - hide actions that are not valid for the current status
  - enforce the same rule in the service layer
- Permit easy reversal only where the business rules explicitly allow it.
- Support internal locus of control:
  - let the user feel in charge through clear choices and confirmations
  - avoid surprising state changes
- Reduce short-term memory load:
    - Do not put unnecessary subtitle explanations, and elaborations.
    - keep related facts on the same card
    - avoid duplicate navigation and duplicate explanation blocks

## Nielsen Heuristics

- Make system status visible with compact badges, alerts, and action feedback.
- Match the real world:
  - use SACCO domain terms already present in the app
  - keep wording plain and operational
- Preserve user control and freedom:
  - use confirmations for destructive or high-impact actions
  - do not expose actions that cannot succeed
- Maintain consistency and standards across member, manager, board, and admin pages.
- Prevent errors before they happen:
  - disable or hide invalid actions
  - do not rely on explanatory text to compensate for weak interaction design
- Favor recognition rather than recall:
  - surface the current status, record ID, and key context near decisions
- Keep layouts efficient and minimalist:
  - every line of text must earn its place
  - remove decorative or repetitive copy
- Help users recognize and recover from errors with short, specific messages.

## Project-Specific Defaults

- Use the shared shell and existing ERP card language from:
  - `src/main/webapp/WEB-INF/jsp/fragments/header.jspf`
  - `src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf`
- Keep page content focused on the current record or task, not on cross-workspace navigation.
- On list, report, form, review, archive, and detail pages, avoid dashboard-style metric summaries above the main content.
- When editing a page with repeated navigation links in the body, remove them and rely on the sidebar unless the link is record-specific.
- When a route named `SACCOs` exists, treat it as the portfolio/workspace view. Put registry tables under a separate `SACCO Registry` sidebar item and separate page.
- Use one shared modal style across the product:
  - calm white modal surface
  - modest system-aligned corners, not pill-shaped
  - thin neutral border and soft shadow
  - simple close icon in the top-right
  - action buttons grouped at the bottom-right
- When adding helper text, keep it to one short sentence only if the action would otherwise be ambiguous.
- Prefer concise confirmation dialogs over long inline explanations.
- Preserve responsiveness and avoid horizontal overflow on mobile, tablet, laptop, and wide screens.

## Pre-Ship Check

- Check that sidebar navigation is the only global navigation.
- Check that grouped sidebar items behave like modern collapsible menus, not always-open indented lists.
- Check that one submenu route maps to one focused content view unless the user explicitly asked for a combined dashboard.
- Check that body content does not duplicate sidebar destinations.
- Check that non-dashboard pages do not contain mini dashboard metric cards or summary strips.
- Check that explanatory text is trimmed to the minimum needed.
- Check that destructive actions use a clear confirmation.
- Check that invalid actions are hidden or blocked.
- Check that the page still feels calm, brief, and readable.

## Performance
- Large lists (>50 items): virtualize (`virtua`, `content-visibility: auto`)
- No layout reads in render (`getBoundingClientRect`, `offsetHeight`, `offsetWidth`, `scrollTop`)
- Batch DOM reads/writes; avoid interleaving
- Prefer uncontrolled inputs; controlled inputs must be cheap per keystroke
- Add `<link rel="preconnect">` for CDN/asset domains
- Critical fonts: `<link rel="preload" as="font">` with `font-display: swap`

