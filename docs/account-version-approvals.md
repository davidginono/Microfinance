# Chart of accounts versions and approvals

The 2026-10-18 user mandate supersedes direct-only account setup. Each account has a permanent ID and a current version; creation, metadata changes, deactivation and reactivation use separate proposals. Institution metadata remains shared across branches. Monetary posting and lending approvals are unchanged.

## Modes and access

Settings → Account approval configuration selects automatic or independent approval for each of the four operations, across the institution. Unconfigured institutions retain automatic approval (policy version 0) for compatibility. Saving a complete valid version automatically approves and applies it in one transaction when automatic mode applies; Save draft never activates an incomplete proposal. Independent mode freezes the submitted version until a different authorized reviewer approves or rejects it. The existing account remains in effect, and new accounts are absent from posting lookups until approved.

The existing COA VIEW/CREATE/UPDATE permissions remain necessary. New claims are available in the access matrix, without assignment to role bundles or existing staff:

- `ACCOUNTING_ACCOUNT_APPROVAL_VIEW`: institution-wide proposal review visibility.
- `ACCOUNTING_ACCOUNT_APPROVAL_APPROVE` / `ACCOUNTING_ACCOUNT_APPROVAL_REJECT`: independent decisions, also requiring institution review visibility. Creators cannot review their own requests.
- `ACCOUNTING_ACCOUNT_RULES_VIEW` / `ACCOUNTING_ACCOUNT_RULES_CONFIGURE`: read and change the institution policy.

All services recheck current claims and active institution, branch and staff access. Without institution review visibility, the queue contains only the current actor's requests in their current branch. Approved account facts and baselines remain visible through the institution chart. Unpublished history is filtered to the owner/branch or explicit institution reviewers.

## Versions and evidence

`ChartOfAccountsPageController / AccountVersionController → GeneralLedgerService → GeneralLedgerRepository`. The existing institution setup lock serializes these low-volume configuration changes. Short transactions include the version, live metadata, permanent event history and shared audit record. No network calls, jobs or executors are added. Registers fetch size+1 (10/25/30/50), history/event pages fetch 26 for a 25-row page, and parent suggestions remain bounded. The 1,000-RPS architectural target is unmeasured.

V74 adds immutable policy revisions, account versions, permanent audit events, a current-version pointer and a journal-line account-version reference. Submitted snapshots retain before/proposed facts, request reason, creator and branch, policy version and approval mode. Decisions retain their real reviewer, reason and timestamp. Automatic approvals explicitly have no second reviewer. PostgreSQL prevents submitted content changes, evidence updates/deletes, mismatched live account snapshots and journal references to a different account's version. Account code, classification, purpose, balance type and hierarchy keep their original immutability protections. Existing accounts are never deleted.

Drafts may be edited with their expected revision. Pending requests must be withdrawn before correction; rejected and withdrawn proposals remain preserved and can be copied into the next version. Only one open proposal is allowed per account, and open new-account codes are reserved. Request keys make creation retries idempotent; stale draft saves and concurrent decisions fail rather than overwrite. Review rechecks the current base, active parent, protected main groups and active children. A policy change does not silently approve old requests: withdraw and submit a new version against the new policy.

Existing accounts receive an explicitly labelled migration baseline, not invented historical approval. Existing journal lines are untouched and retain a NULL version reference because their historical account labels cannot be proven. New journal lines capture the current approved account version within posting. Existing core vouchers already preserve their own account name/code snapshots. This increment does not rewrite old financial history, balances or journals.

## Routes

- `/finance/accounts`: current effective accounts; selected-row Create new version and Version history.
- `/finance/accounts/groups/new` and `/posting/new`: Save draft / Save version creates an account proposal.
- `/finance/accounts/versions`: requests and approval queue.
- `/finance/accounts/versions/{id}`: before/after comparison, decisions, version and audit history.
- `/finance/accounts/versions/{id}/edit` and `/revise`: draft editing and replacement proposals.
- `/finance/accounts/approval-configuration`: institution policy, under Settings.

The existing account deactivate/reactivate POST URLs remain CSRF- and permission-protected entry points to the corresponding version form; they no longer change availability immediately.

All mutations retain session authentication and CSRF; request models allowlist editable metadata. Page labels are English/Kiswahili and use the shared responsive JSP shell and resource tables.

## Verification on 2026-10-18

- Final changed Java sources compiled with Java 25. Direct JUnit Platform runs passed 57 distinct checks with zero failures, aborts or skips: 16 real PostgreSQL version/approval checks, six version MVC checks, 12 existing COA MVC checks, the six-page Jasper compilation check, and 22 existing claim/workspace security checks. Jasper compiled all six pages again after the final UTF-8 and form-spacing corrections.
- A fresh disposable PostgreSQL 18 database applied all 94 migrations through V74, then validated their checksums on subsequent runs. Tests cover automatic/manual creation, incomplete drafts, immutable submitted content, rejected/withdrawn revisions, stale bases/policies, active children/main-group protection, tenant/branch/claim isolation, no automatic permission grants, concurrent requests/reviews, audit rollback and posted journal/voucher label retention across a rename.
- Playwright exercised the real controllers/JSPs/services against disposable synthetic accounts: draft completion with automatic activation, independent creation, rejection/revision/approval, automatic deactivation/reactivation and selection reset. All 32 queue/configuration/details/form checks in English/Kiswahili at 360/768/1366/1920 pixels had contained document widths and zero page JavaScript errors. Screenshots were inspected. The Browser plugin was unavailable; an optional in-app surface inventory also timed out. A fixture static-resource mapping error was corrected before the final rendered checks.
- JavaScript syntax, 83 matching English/Kiswahili labels and Git whitespace checks passed. Artifacts remain under ignored `target/`. The fixture omits production profile-image/notification services and the full production sign-in/navigation context; this is not a claim of operational login/logout, policy acceptance, deployment or load capacity. No operational data was changed.
- The initial focused Maven invocation was stopped during broad test compilation; final evidence comes from isolated Java 25 compilation and the direct JUnit runs above. The full repository suite, packaging and throughput benchmarks were not run. Restart/build the application to apply V74 and explicitly assign configuration/reviewer claims before using those workspaces.
