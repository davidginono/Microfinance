# AGENTS.md

This file gives coding agents project-specific guidance for working in `IAA_SACCOS`.

## Project Overview

- Project: `SACCO Loan Workflow MVP`
- Stack:
  - Spring Boot 3.x
  - Java
  - Spring MVC + JSP/JSTL
  - Spring Security
  - Spring Data JPA + Hibernate
  - PostgreSQL
  - Tailwind CSS via CDN in JSP pages
- Auth model:
  - Session-based authentication
  - Form login
  - CSRF enabled
  - Not JWT-based

## Run And Build

- Development run:
  - `mvn spring-boot:run`
- Compile:
  - `mvn -DskipTests compile`
- Full test run:
  - `mvn test`
- Packaged run:
  - `mvn -DskipTests package`

Default local URL:
- `http://localhost:8080`

## Key Application Areas

- Member pages:
  - `src/main/webapp/WEB-INF/jsp/app`
- Admin pages:
  - `src/main/webapp/WEB-INF/jsp/admin`
- Manager pages:
  - `src/main/webapp/WEB-INF/jsp/manager`
- Board pages:
  - `src/main/webapp/WEB-INF/jsp/board`
- Chairperson pages:
  - `src/main/webapp/WEB-INF/jsp/chairperson`
- Shared layout fragments:
  - `src/main/webapp/WEB-INF/jsp/fragments`

## Important Backend Files

- Security:
  - `src/main/java/com/sacco/mvp/config/SecurityConfig.java`
  - `src/main/java/com/sacco/mvp/security/AppUserDetailsService.java`
  - `src/main/java/com/sacco/mvp/security/AppUserPrincipal.java`
  - `src/main/java/com/sacco/mvp/security/AuthzService.java`
- Main member workflow:
  - `src/main/java/com/sacco/mvp/web/AppController.java`
  - `src/main/java/com/sacco/mvp/service/LoanWorkflowService.java`
- Manager workflow:
  - `src/main/java/com/sacco/mvp/web/ManagerController.java`
  - `src/main/java/com/sacco/mvp/service/ManagerService.java`
- Board workflow:
  - `src/main/java/com/sacco/mvp/web/BoardController.java`
  - `src/main/java/com/sacco/mvp/service/BoardService.java`
- Admin:
  - `src/main/java/com/sacco/mvp/web/AdminController.java`
  - `src/main/java/com/sacco/mvp/service/AdminService.java`

## Workflow Notes

- Loan workflow logic is sensitive. Do not change statuses or transitions casually.
- Current loan flow includes:
  - draft
  - sent to guarantors
  - all guarantors approved
  - review by manager
  - review by board
  - approval / rejection paths
- Guarantor and board rules are business-critical.
- Board review quorum and guarantor approval logic must remain consistent with service-layer rules.

## Security Notes

- The app uses Spring Security form login, not JWT.
- CSRF is enabled and used in JSP forms and AJAX requests.
- Role access is enforced in:
  - `SecurityConfig`
  - `@PreAuthorize`
  - `AuthzService`
- Do not weaken route or ownership checks without explicit instruction.

## UI Conventions

- Shared shell styling lives primarily in:
  - `src/main/webapp/WEB-INF/jsp/fragments/header.jspf`
  - `src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf`
- Treat the sidebar as the only global navigation for admin, member, manager, board, and chairperson pages.
- Do not add duplicate navigation actions inside page bodies for destinations that already exist in the sidebar.
- Keep dashboard routes focused on summary cards and high-level KPIs. Do not place registry tables, cross-workspace listings, or alternate route launchers on dashboards unless explicitly requested.
- Keep portfolio views and registry views separate when both exist:
  - `SACCOs` should remain a focused portfolio/workspace view
  - `SACCO Registry` should be its own sidebar destination and view
- Keep visual changes consistent with the current ERP-style admin/member layout.
- Keep panel-header and toolbar action buttons compact by default. Do not stretch actions such as Restore Default Permissions to full width unless a specific mobile form flow explicitly calls for it.
- Use one shared modal language across the app. Default to the shared modal shell with calm white surfaces, modest system-aligned corner radii, thin grey borders, light shadows, right-aligned footer actions, and a simple top-right close icon.
- Reduce short-term memory overload:
  - keep page-header explanations brief
  - keep card-header helper text brief or omit it when the title already explains the section
  - avoid stacking multiple instructional sentences when one short line is enough
- Prefer plain-language labels for non-technical SACCO staff:
  - avoid exposing internal codes or technical identifiers unless they are truly needed for the task
  - choose wording a layperson can understand at first glance
- Preserve responsiveness across:
  - small mobile screens
  - tablets
  - laptops
  - ultra-wide screens
- If changing modal behavior, ensure overlays are appended to `document.body` and cover the full viewport.

## Editing Rules

- Prefer minimal, targeted changes.
- After completing and verifying a user-requested change, automatically create a local Git commit unless the user explicitly asks not to commit.
  - stage only files changed for the current request; never include unrelated or pre-existing worktree changes
  - use a concise commit message that describes the completed change
  - do not amend an existing commit and do not push unless the user explicitly requests it
  - if the current change cannot be separated safely from unrelated edits, leave it uncommitted and clearly report why
- Always consider RESPONSIVENESS FOR MOBILE DEVICES.
- Reuse existing services and controller flows instead of duplicating logic.
- Avoid changing database structure unless explicitly required.
- Avoid changing seeded business rules unless explicitly requested.
- Keep labels and UI wording aligned with existing domain terminology:
  - `Loan ID`
  - `Awaiting Guarantors`
  - `All Guarantors Approved`
  - `On Review By Manager`
  - `On Review By Board`
- Treat station scope as the default tenant boundary for workspace roles:
  - when a workflow, registry, report, member list, incident list, or loan listing is filtered by SACCO for a workspace admin or station-bound role, prefer filtering to the selected station as well
  - `LoanApplication.stationId` is now the first-class station reference for loan workflow, reporting, archive, queue, and loan-document visibility paths; prefer it over rebuilding station scope from the applicant member record
  - if a path cannot be fully station-scoped because the current data model still lacks a first-class station reference for that record type, call that out clearly before widening access

## Testing Checklist

After workflow or UI changes, validate as applicable:

- Member:
  - `/app/dashboard`
  - `/app/loan-products`
  - `/app/loan-applications`
  - `/app/guarantee-requests`
- Admin:
  - `/admin/dashboard`
  - `/admin/users`
  - `/admin/settings-controls`
- Manager:
  - `/manager/loan-applications`
- Board:
  - `/board/assigned`

Also check:
- login still works
- logout still works
- sidebar/mobile nav still works
- notification and profile dropdowns still behave correctly
- no horizontal overflow appears on common screen sizes
- responsiveness for mobile devices

## Practical Guidance For Agents

- If the user asks about authentication, answer in terms of session-based Spring Security unless the code has been changed.
- If the user asks about a workflow status, inspect `LoanWorkflowService` first.
- If the user asks about a page layout issue, inspect shared fragments before editing individual JSPs.
- If a modal is visually broken, check whether it is trapped inside the page shell instead of being attached to `document.body`.

## Safe Defaults

- Assume business logic should remain unchanged unless the user explicitly asks for a workflow change.
- Assume responsiveness matters for every UI change.
- Assume user-facing IDs and labels should remain readable and domain-friendly.
- Assume production changes must preserve a path toward at least 1,000 requests per second:
  - never add unbounded `findAll()` or SACCO-wide list loading on request paths
  - prefer tenant/station-scoped `Pageable`, `count`, `exists`, `top`, and aggregate queries
  - keep loan submit and review transactions short, indexed, and free of avoidable external or file work
  - avoid hot-row locks where a small allocation block, queue, or async worker preserves correctness
  - keep outbox/report/notification work batchable and configurable
