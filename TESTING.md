# Testing And Review Notes

This project has a business-sensitive workflow, so tests should read as executable scenarios rather than isolated implementation checks.

## Current Automated Coverage

- Security and access checks: `src/test/java/com/sacco/mvp/security`
- Controller security checks: `src/test/java/com/sacco/mvp/web`
- Loan workflow and review rules: `src/test/java/com/sacco/mvp/service/LoanWorkflowServiceTest.java`, `BoardServiceTest.java`, `ManagerServiceTest.java`
- Admin, SACCO registry, station access, loan product settings, and support flows: `AdminServiceTest.java`, `SaccoRegistryServiceTest.java`
- Financial, repayment, analytics, payment sync, and presentation helpers: the remaining service tests

## Scenario Comment Style

For workflow tests, use short comments before the setup block:

```java
// Scenario: plain-language business rule under test.
// Given the important starting state
// When the user or system action happens
// Then the protected outcome must hold.
```

Keep these comments focused on business behavior. Avoid repeating every mock or assertion in prose.

## Manual Smoke Checklist

Run these after workflow, security, or JSP changes:

- Login and logout for member and staff users
- `/app/dashboard`, `/app/loan-products`, `/app/loan-applications`, `/app/guarantee-requests`
- `/manager/loan-applications`
- `/board/assigned`
- `/admin/dashboard`, `/admin/users`, `/admin/settings-controls`
- Sidebar/mobile navigation, profile dropdown, notification dropdown, and modal overlays
- Common mobile, laptop, and wide desktop widths with no horizontal overflow

## Production Readiness Gaps To Cover Next

- Add PostgreSQL-backed integration tests for Flyway migrations and critical repository queries.
- Add MockMvc end-to-end tests for the highest-risk role flows: member submit, guarantor approve, manager review, board quorum, and admin station suspension.
- Add load/performance checks for dashboard and registry paths that currently aggregate large data sets.
