# Claims language and overlap review — 29 October 2026

The requested accounting names are now **Receipt and payment vouchers**, **Journal vouchers**, and **Chart of accounts**. Their Kiswahili labels are **Hati za mapokezi na malipo**, **Hati za maingizo**, and **Orodha ya akaunti**. Only the first two needed changes; Chart of accounts already had the requested name. Stored permission codes and authorization behavior are unchanged.

This review covers all **215** `UserClaim` identifiers: **174 assignable** and **41 retired**. The assignable claims belong to **51 source features**, displayed as **50 matrix rows** because voucher workspace and decision permissions share one row. Voucher approval rules remains separate. The inventory at the end lists every identifier, including the retired ones.

This is a source-based language and permission-boundary review, not a usability study with staff, a full security audit, or proof that every operation was executed. Readability recommendations are provisional until English- and Kiswahili-speaking staff try realistic permission-assignment tasks. Shorter labels alone do not make a text-heavy permission matrix usable without reading support.

## Highest-priority findings

### R1 — Action headings sometimes describe the wrong operation

The common action grid is hiding meaningful differences. These are stronger findings than simple preferences about wording:

| Permission | Current matrix action | Actual operation | Recommended presentation |
| --- | --- | --- | --- |
| `LOAN_RECORDING_POST` | Add | Receives recorded-loan repayments and also assesses charges; the identifier is used as a POST permission. | Show the actual task, such as Record repayment. Treat charge assessment as a separate authority-design question. |
| `COLLATERAL_RELEASE` | Approve | Releases a pledged asset after service checks. | Release collateral. |
| `COLLATERAL_RECOVERY` | Initiate | Records a collateral-recovery event. | Record recovery. Do not imply that the checkbox itself completes legal recovery or receives money. |
| `GROUP_COVERAGE_LINK` | Post | Links an already posted receipt/contribution to a guarantee follow-up case. | Link payment. It does not post another receipt. |
| `GROUP_LENDING_EXECUTE` | Approve | Executes group funding operations, with further disbursement and financial checks. | Complete group funding. It is not an ordinary review decision. |
| `FINANCIAL_REPORTS_INSTITUTION` | Configure | Allows institution-wide financial statements/history. | View all branches, as a distinct scope permission. Configure suggests changing settings. |
| `GROUP_COVERAGE_OWN_RESPOND` | Edit | Allows a named person to acknowledge, promise, or dispute their own guarantee case. | Respond. Preserve the own-record boundary. |
| `NOTIFICATIONS_UPDATE` | Edit | Marks notifications read through existing notification actions. | Mark read. It does not edit the message content. |
| `OUTBOX_UPDATE` | Edit | Retries an outgoing event/message. | Retry sending. |
| `CLIENT_ONBOARDING_DOCUMENTS` | Export | Downloads a client's onboarding document. | Download documents. |

Do not rename global Add, Approve, Post, or Configure headings to fit one exceptional permission. Use accurate task labels for exceptional cells, or separate task-specific rows, while retaining the original claim values. Preserve irreversible financial boundaries and linked reversals; do not describe Reverse as a simple undo.

Evidence: [claim/action mappings](../src/main/java/com/sacco/mvp/domain/UserClaim.java), [recorded-loan authorization](../src/main/java/com/sacco/mvp/accounting/loans/service/LoanRecordingService.java), [repayment claims](../src/main/java/com/sacco/mvp/service/LoanRepaymentLedgerService.java), [charge assessment](../src/main/java/com/sacco/mvp/accounting/charges/service/LoanChargeService.java), [collateral operations](../src/main/java/com/sacco/mvp/lending/controls/service/CreditControlService.java), [coverage operations](../src/main/java/com/sacco/mvp/grouplending/service/GroupCoverageService.java), [group funding](../src/main/java/com/sacco/mvp/grouplending/service/GroupSharedLoanService.java), [statement scope](../src/main/java/com/sacco/mvp/accounting/statements/service/StatementService.java), [notification/outbox/SMS routes](../src/main/java/com/sacco/mvp/web/AdminController.java), and [onboarding downloads](../src/main/java/com/sacco/mvp/clients/onboarding/service/OnboardingService.java).

### R2 — Kiswahili claim names are incomplete

**29 of the 51 active source features lack an `access.feature.*` Kiswahili entry.** The JSP falls back to `AccessFeature.displayName`, which is English. Switching language therefore leaves many permission names in English, including client onboarding, users, loan review queues, loan products, repayment recording, notifications, and support. The final inventory marks each missing translation.

Complete both message bundles before describing the matrix as bilingual. Use familiar words consistently: client rather than member, institution rather than SACCO, and the same task name in the sidebar, screen title, matrix, and confirmation. Candidate translations below need native-speaker review; they have not been applied beyond the requested accounting names.

Evidence: [English bundle](../src/main/resources/messages_en.properties), [Kiswahili bundle](../src/main/resources/messages_sw.properties), [English fallback names](../src/main/java/com/sacco/mvp/domain/AccessFeature.java), and [matrix fallback rendering](../src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp).

### R3 — Several names hide wider authority

| Permission group | Authority beyond the apparent name | Recommendation |
| --- | --- | --- |
| Chart of accounts | The same claims also control Activities/Transactions setup and budgets; account Update is additionally required to save statement mappings. | Keep the user-requested label now. Plan separate permissions for transaction setup, budgets, and statement mappings if independent assignment is needed. A rename alone cannot separate access. |
| Financial and management reports — Export | Also authorizes voucher PDF/print/export paths, in conjunction with the voucher's own View claim. | Consider a distinct voucher-download permission or a clearly named shared reporting/download group. Do not assume it only exports the report screen. |
| Accountant loan recording — Add | The mapped POST claim also supports repayment posting and charge assessment. | Separate the visible tasks; assess whether independent charge and repayment powers are needed. |
| Disbursement Queue — Disburse | Also gates the eligible archived-loan deletion path, which has additional scope/history restrictions. | Review this authorization boundary first. A distinct archive-deletion permission would make this authority explicit if that operation remains supported. Do not imply that a disbursement grant only releases funds. |

These are broad permissions or coupled boundaries, not proven duplicate permissions. Splitting them needs an explicit assignment/migration plan, endpoint tests, and preserved historical access. No split or grant is performed by this review.

Evidence: [transaction setup](../src/main/java/com/sacco/mvp/accounting/controller/AccountingCodeLibraryController.java), [budgets](../src/main/java/com/sacco/mvp/accounting/management/service/BudgetService.java), [statement mappings](../src/main/java/com/sacco/mvp/accounting/statements/service/StatementService.java), [voucher downloads](../src/main/java/com/sacco/mvp/accounting/service/VoucherService.java), [charge assessment](../src/main/java/com/sacco/mvp/accounting/charges/service/LoanChargeService.java), [archive commands](../src/main/java/com/sacco/mvp/web/DisbursementController.java), and [archive-deletion guard](../src/main/java/com/sacco/mvp/service/ArchivedLoanDeletionService.java).

### R4 — Similar names often protect different people, records, or decisions

Keep these boundaries separate even when their names are simplified:

- Voucher approvals versus Voucher approval rules: operating/deciding versus configuring the rules. Receipt/payment/journal View claims remain additional type-specific access, not redundant checkboxes.
- Account change approvals versus account approval rules versus account editing: making, independently deciding, and configuring are different powers.
- Client registration versus Users and permissions: borrower identity/business verification is different from login and staff permission administration.
- Client loan applications versus staff loan review versus recorded-loan entry: ownership, workflow stage, and the entry route differ.
- Guarantor assessment versus My guarantee requests versus staff guarantee follow-up: capacity evidence, the guarantor's decision, and staff collection follow-up differ.
- Group lending versus group borrowing authority: loan processing differs from recording the group's authorized signers and borrowing evidence.
- Staff guarantee follow-up versus My guarantee follow-up: never merge staff-wide case access with a named person's own records.
- Board, chairperson, credit committee, manager, accountant, and loan-officer review queues: similar action names do not justify merging review roles or quorum controls.

The recent manager error illustrates a usability problem: View voucher approvals + Approve + View the relevant voucher type are all required, along with active staff status, the selected role and branch. Show these prerequisites when assigning or validating access; do not grant missing claims automatically.

Evidence: [voucher eligibility and decisions](../src/main/java/com/sacco/mvp/accounting/approval/service/VoucherWorkflowService.java), [account version decisions](../src/main/java/com/sacco/mvp/accounting/service/GeneralLedgerService.java), [loan review authorization](../src/main/java/com/sacco/mvp/security/AuthzService.java), [guarantor assessment](../src/main/java/com/sacco/mvp/lending/controls/service/CreditControlService.java), [coverage own/staff boundaries](../src/main/java/com/sacco/mvp/grouplending/service/GroupCoverageService.java), and [borrowing authority](../src/main/java/com/sacco/mvp/grouplending/service/GroupAuthorityService.java).

### R5 — Legacy voucher powers are candidates for a targeted cleanup

`VOUCHER_APPROVAL_FORCE` remains assignable, but current rule-model-2 configuration does not offer force approval; the service exposes it only for eligible historical workflows with `modelVersion < 2`. `VOUCHER_APPROVAL_ESCALATED_APPROVE` also remains assignable, while new role-based stages use `VOUCHER_APPROVAL_APPROVE`; archived stages can still contain the earlier claim.

These are **legacy-assignment candidates**, not permissions that can safely be deleted on sight. Check stored workflows, stages and existing assignments before restricting new grants or moving them behind a legacy-access section. Preserve historical decisions and recovery. Do not include Hold or Withdraw in this retirement recommendation: the current service still has those commands.

Evidence: [current stage creation, assigned-stage checks and legacy FORCE condition](../src/main/java/com/sacco/mvp/accounting/approval/service/VoucherWorkflowService.java), [retained reviewer display](../src/main/webapp/WEB-INF/jsp/accounting/approval/details.jsp), and [current versus historical workflow models](voucher-approval-workspaces.md).

### R6 — Two separate View names share a page-level authorization boundary

`/app/settings` accepts either `MEMBER_SETTINGS_VIEW` or `PAYMENT_DETAILS_VIEW`, then uses the requested section to choose language or payout details. The controller's GET does not independently gate each section with its matching View claim. Updates still use their distinct claims and payout updates use OTP checks.

This should be reviewed before promising that those two View checkboxes provide independent visibility. Either enforce the corresponding View claim per section or explicitly design a shared own-settings View permission. Do not collapse payout editing into language preferences. This is a static finding, not a tested cross-user disclosure; the displayed data is still selected for the authenticated member.

Evidence: [settings GET and distinct POST permissions](../src/main/java/com/sacco/mvp/web/AppController.java).

### R7 — The matrix needs a simpler assignment flow, not just shorter words

Twenty-one action columns require horizontal scrolling and use terms such as Validate, Initiate, Waive, and Reverse. For staff who need reading support, prefer a short task list with visible verbs and only the actions supported by the selected task. Keep one canonical permission editor; a future simpler view should edit the same original claim controls, preserve CSRF and unsaved changes, and never introduce an all-powerful shortcut.

Use a few recognizable icons alongside text, not instead of text; use the same label each time; offer spoken or assisted guidance only as an explicit product capability with privacy review. Validate with actual staff: finding receipt viewing, allowing a manager to approve without posting, configuring rules without approval authority, and denying access should all be tested in both languages. Keep the consequence visible before assigning financial or administrative powers. No such interaction redesign is implemented here.

### R8 — Earlier permission documentation has stale counts and layout descriptions

[Permission cleanup](permission-cleanup.md) and the [selectable trace](selectable-permission-trace.md) describe the 19 October catalogue of 128 active claims. The current catalogue has 174. [The earlier matrix note](access-matrix-workspace.md) describes the previous vertical feature/action/claim layout and selection pane. Use this report's dated inventory and the current source when planning cleanup; those historical descriptions are not proof that newer claims are unused or that the old layout is still rendered.

## Review of every active feature

The following covers all 51 active source features. `VOUCHER_APPROVAL` appears with `VOUCHER_WORKSPACE` because they render together. Suggested names are proposals unless marked Applied. SW suggestions with no current translation must be reviewed with Kiswahili-speaking staff before implementation. Generic action names are expanded in R1; the complete identifier/action inventory follows this table.

| Source feature(s) | Current English name | Recommendation / candidate English name | Candidate Kiswahili / decision |
| --- | --- | --- | --- |
| `CLIENT_ONBOARDING` | Client onboarding | Client registration and checks; documents and verification are separate tasks within it. | Usajili na uhakiki wa wateja. Rename and translate; not a duplicate of user administration. |
| `CREDIT_CONTROLS` | Branch credit controls | Branch lending rules. | Kanuni za mikopo za tawi. Rename; keep policy scope distinct from product terms. |
| `GUARANTOR_CAPACITY` | Guarantor capacity | Guarantor assessment. | Tathmini ya mdhamini. Keep separate from a guarantor's own acceptance. |
| `COLLATERAL` | Collateral | Loan collateral; give Release and Recovery their actual task names. | Dhamana ya mkopo. Keep; fix exceptional actions (R1). |
| `COLLECTIONS` | Collections | Overdue loan follow-up. | Ufuatiliaji wa mikopo iliyochelewa. Record contacts/tasks; do not imply receiving repayments. |
| `GROUP_COVERAGE` | Coverage cases | Staff guarantee follow-up. | Ufuatiliaji wa udhamini. Avoid insurance-like Coverage; keep linking separate from posting. |
| `GROUP_COVERAGE_OWN` | My coverage cases | My guarantee follow-up. | Ufuatiliaji wa udhamini wangu. Keep own-record permissions separate. |
| `GROUP_LENDING` | Group lending | Group loans. | Mikopo ya vikundi. Keep; funding execution needs an accurate task label. |
| `GROUP_AUTHORITY` | Group borrowing authority | Group borrowing consent and signers. | Ridhaa na watia saini wa mkopo wa kikundi. Keep distinct from loan approval. |
| `PAYROLL_DEDUCTIONS` | Payroll deductions | Salary deduction schedules. | Ratiba za makato ya mshahara. Keep import/export/post separate; export does not receive repayments. |
| `VOUCHER_WORKSPACE`, `VOUCHER_APPROVAL` | Voucher approvals | Keep the combined row; distinguish Check voucher, Send for approval, Approve, Reject and Post to ledger. | Idhini za vocha. Keep separate financial actions; review legacy powers (R5). |
| `VOUCHER_RULES` | Voucher approval rules | Keep. | Kanuni za idhini za vocha. Keep separate from deciding vouchers. |
| `VOUCHER_DOCUMENTS` | Voucher supporting documents | Voucher attachments. | Viambatisho vya vocha. Keep separate document viewing/upload permissions. |
| `USER_ACCESS` | User Access | Users and permissions. | Watumiaji na ruhusa. Distinguish creating a login, changing permissions, and eligible deletion. |
| `ADMIN_DASHBOARD` | Admin Dashboard | Admin overview. | Muhtasari wa msimamizi. Keep as a separate destination permission; not a grant of every admin action. |
| `SACCO_REGISTRY` | SACCO Registry | Institutions and branches. | Taasisi na matawi. Replace legacy branding in visible labels, retain codes. |
| `PLATFORM_SETTINGS` | Platform Settings | System settings. | Mipangilio ya mfumo. Keep scope distinct from institution settings. |
| `WORKSPACE_SETTINGS` | Workspace Settings | Institution settings. | Mipangilio ya taasisi. Explain the difference between Edit and Configure with task labels. |
| `LOAN_PRODUCTS` | Loan Products | Loan products. | Bidhaa za mikopo. Keep the domain term; clarify Edit terms versus Change product rules. |
| `APPROVAL_FLOW` | Approval Flow | Default loan review steps. | Hatua za msingi za uhakiki wa mikopo. It is loan configuration, not voucher/account approval. |
| `SMS_USAGE` | SMS Usage | SMS units and alerts. | Salio na tahadhari za SMS. Add means allocate SMS units; Configure means change alert thresholds. |
| `OUTBOX` | Outbox | Message delivery. | Utumaji wa ujumbe. Edit should say Retry sending. |
| `MEMBER_SETTINGS` | Member Settings | My language settings. | Mipangilio ya lugha yangu. Current POST changes language; review shared GET boundary (R6). |
| `PAYMENT_DETAILS` | Payment Details | My loan payout details. | Taarifa zangu za kupokea mkopo. Keep financially sensitive editing separate from language settings. |
| `LOAN_REPAYMENTS` | Repayments | Loan repayments. | Marejesho ya mikopo. Keep; distinguish recording cash, reversing a receipt and waiving interest. |
| `LOAN_RECORDING` | Accountant loan recording | Loan entry and posting, pending a task/permission split. | Uandikaji wa mikopo na miamala. Too broad and role-specific; includes payouts, repayments, charges and corrections (R1/R3). |
| `LOAN_ADJUSTMENTS` | Loan provisions and corrections | Loan loss estimates and corrections. | Makadirio ya hasara na masahihisho ya mikopo. Preserve proposal versus independent approval. |
| `ACCOUNTING_ACCOUNTS` | Chart of accounts | Applied/retained as requested. | Orodha ya akaunti. Name is standard but authority is broader than the account list (R3). |
| `ACCOUNTING_ACCOUNT_APPROVAL` | Institution account version approvals | Approve account changes. | Idhinisha mabadiliko ya akaunti. Keep independent review; Version is internal detail for most tasks. |
| `ACCOUNTING_ACCOUNT_RULES` | Institution account approval configuration | Account approval rules. | Kanuni za idhini ya akaunti. Keep separate from changing or approving an account. |
| `ACCOUNTING_JOURNALS` | Journal vouchers | Applied. | Hati za maingizo. Keep separate from receipts/payments and account setup. |
| `ACCOUNTING_BUSINESS` | Receipt and payment vouchers | Applied. | Hati za mapokezi na malipo. Keep separate from journal and account setup permissions. |
| `MEMBER_LOANS` | Member Loans | My loan applications. | Maombi yangu ya mkopo. Clarify client ownership; preserve special routing/assignment behavior. |
| `GUARANTOR_REQUESTS` | Guarantor Requests | My guarantee requests. | Maombi yangu ya udhamini. Acceptance/rejection is not staff capacity assessment. |
| `LOAN_DOCUMENTS` | Loan Documents | Loan documents. | Nyaraka za mikopo. Keep; use Download for the export task where appropriate. |
| `STAFF_ANALYTICS` | Staff Analytics | Branch loan performance. | Mwenendo wa mikopo ya tawi. It is loan/portfolio analysis, not personnel analytics. |
| `LOAN_OFFICER_QUEUE` | Loan Officer Queue | Loan officer loan review. | Uhakiki wa mikopo wa afisa mikopo. Keep role-specific assignment/review powers. |
| `MANAGER_QUEUE` | Manager Queue | Manager loan review. | Uhakiki wa mikopo wa meneja. Keep separate workflow stage. |
| `ACCOUNTANT_QUEUE` | Accountant Queue | Accountant loan review. | Uhakiki wa mikopo wa mhasibu. Not voucher approval or ordinary voucher entry. |
| `BOARD_QUEUE` | Board Queue | Board loan review. | Uhakiki wa mikopo wa bodi. Preserve board assignment/quorum. |
| `CHAIRPERSON_QUEUE` | Chairperson Queue | Chairperson loan review. | Uhakiki wa mikopo wa mwenyekiti. Do not merge with board or committee powers. |
| `PROCESSED_LOANS` | Processed Loans | Loan review history. | Historia ya uhakiki wa mikopo. Clarify that Processed does not promise disbursed or fully repaid. |
| `SACCO_CONFIGURATIONS` | SACCO Configurations | View institution and product settings. | Tazama mipangilio ya taasisi na bidhaa. It is a read-only chairperson destination, not another configuration grant. |
| `CREDIT_COMMITTEE_QUEUE` | Credit Committee Queue | Credit committee loan review. | Uhakiki wa mikopo wa kamati ya mikopo. Keep distinct from board review. |
| `DISBURSEMENT_QUEUE` | Disbursement Queue | Loan payouts; separate archive deletion as a future authority change. | Utoaji wa mikopo. Clarify archive authority before treating the name as complete (R3). |
| `LOAN_REPORTS` | Loan Reports | Loan reports. | Taarifa za mikopo. Keep; some financial/management report paths also require this View claim. |
| `NOTIFICATIONS` | Notifications | Alerts and messages. | Arifa na ujumbe. Edit means Mark read, not change content. |
| `SUPPORT` | Support | Help requests and replies. | Maombi ya msaada na majibu. Keep staff/client ownership and reply restrictions. |
| `FINANCIAL_REPORTS` | Financial and management reports | Financial reports and downloads, pending scope split. | Taarifa za fedha na upakuaji. Clarify voucher downloads and institution-wide scope (R1/R3). |
| `ACCOUNTING_RECONCILIATION` | Reconciliation | Compare loan and ledger totals. | Linganisha jumla za mikopo na leja. Current permission is View only; do not imply bank reconciliation or posting. |

## Action-language review

| Current action | Recommendation |
| --- | --- |
| View | Keep; name the records and branch/own-record scope. |
| Add / Create | Use one familiar verb for ordinary record creation. Do not merge claims merely because these words sound the same; Add currently also hides recorded-loan posting. |
| Update / Edit | Show Edit for ordinary changes; Mark read, Respond and Retry sending for those specific tasks. |
| Approve / Reject | Keep where they are actual decisions; identify whether the decision is a loan, voucher, account change, or client check. Do not use Approve for funding execution or collateral release. |
| Configure | Prefer Change rules/settings with the affected object. Institution-wide reporting needs a scope label instead. |
| Validate | Check voucher; keep it distinct from independent approval. |
| Post | Post to ledger, with the exact transaction/amount in the action context. Link payment is different. |
| Initiate | Send for approval for vouchers/group submission; Record recovery for collateral. |
| Withdraw | Withdraw request; keep the current lifecycle restrictions. |
| Hold | Put request on hold; keep the current lifecycle restrictions. |
| Force approve / Senior approval | Explain the legacy-only boundary and recovery purpose before changing availability (R5). |
| Waive interest | Keep the financial meaning; confirm the interest amount being cancelled. Do not confuse it with receiving repayment. |
| Assign | Assign reviewer/guarantor, naming the actual target. |
| Export / Import | Download / Upload with the file/document type and permitted formats. Do not introduce new export formats. |
| Disburse | Release loan funds or Record payout, according to the actual operation; preserve additional checks. |
| Reverse | Record a reversal/correction. Do not promise deletion or an unrestricted undo of posted money. |
| Delete | Name the eligible object, such as Delete draft. Do not use Delete for deactivation, and do not rename an actual delete as deactivation. |

## Redundancy decisions and next work

No currently assignable permission has been proven safe to merge or delete by this language review. Repeated role/action words are mostly distinct scopes. The evidence-backed cleanup candidates are legacy voucher assignment exposure (R5), broad reused powers (R3), and the shared settings View boundary (R6). Treat absent literal references cautiously: several features construct claim names dynamically.

Suggested order:

1. Correct misleading action labels and complete the 29 missing Kiswahili feature names, without changing assignments.
2. Review broad powers and the settings View boundary with focused authorization tests and an explicit migration/assignment decision.
3. Verify historical voucher usage before changing legacy claim availability.
4. Test a simpler task-based assignment interface with staff in both languages. Include a manager who can approve but cannot post, and a user who can change rules but cannot approve.
5. Refresh the dated permission trace and matrix architecture note after any approved behavior changes. Keep the 41 retired identifiers for stored-history compatibility.

Verification for this request is limited to the requested label changes, bundle/catalog consistency and local rendered previews. The review itself does not certify performance, compliance, independent financial acceptance, or complete route-by-route testing.

## Complete claim inventory

The following snapshot is generated from `UserClaim.values()` / `isAvailable()` and the current message bundles. Active source features are classified in the table above. All 41 retired identifiers remain unavailable; retirement does not authorize removing stored history or legacy identifiers. A missing Kiswahili name is shown as **English fallback**.

### Active permissions (174)

| Identifier | Feature name | Action | Kiswahili feature name |
| --- | --- | --- | --- |
| `ACCOUNTANT_QUEUE_APPROVE` | Accountant Queue | APPROVE | **English fallback** |
| `ACCOUNTANT_QUEUE_EXPORT` | Accountant Queue | EXPORT | **English fallback** |
| `ACCOUNTANT_QUEUE_REJECT` | Accountant Queue | REJECT | **English fallback** |
| `ACCOUNTANT_QUEUE_VIEW` | Accountant Queue | VIEW | **English fallback** |
| `ACCOUNTING_ACCOUNT_APPROVAL_APPROVE` | Institution account version approvals | APPROVE | Idhini za matoleo ya akaunti za taasisi |
| `ACCOUNTING_ACCOUNT_APPROVAL_REJECT` | Institution account version approvals | REJECT | Idhini za matoleo ya akaunti za taasisi |
| `ACCOUNTING_ACCOUNT_APPROVAL_VIEW` | Institution account version approvals | VIEW | Idhini za matoleo ya akaunti za taasisi |
| `ACCOUNTING_ACCOUNT_RULES_CONFIGURE` | Institution account approval configuration | CONFIGURE | Mipangilio ya idhini ya akaunti za taasisi |
| `ACCOUNTING_ACCOUNT_RULES_VIEW` | Institution account approval configuration | VIEW | Mipangilio ya idhini ya akaunti za taasisi |
| `ACCOUNTING_ACCOUNTS_CREATE` | Chart of accounts | CREATE | Orodha ya akaunti |
| `ACCOUNTING_ACCOUNTS_UPDATE` | Chart of accounts | UPDATE | Orodha ya akaunti |
| `ACCOUNTING_ACCOUNTS_VIEW` | Chart of accounts | VIEW | Orodha ya akaunti |
| `ACCOUNTING_BUSINESS_CREATE` | Receipt and payment vouchers | CREATE | Hati za mapokezi na malipo |
| `ACCOUNTING_BUSINESS_REVERSE` | Receipt and payment vouchers | REVERSE | Hati za mapokezi na malipo |
| `ACCOUNTING_BUSINESS_VIEW` | Receipt and payment vouchers | VIEW | Hati za mapokezi na malipo |
| `ACCOUNTING_JOURNALS_CREATE` | Journal vouchers | CREATE | Hati za maingizo |
| `ACCOUNTING_JOURNALS_REVERSE` | Journal vouchers | REVERSE | Hati za maingizo |
| `ACCOUNTING_JOURNALS_VIEW` | Journal vouchers | VIEW | Hati za maingizo |
| `ACCOUNTING_RECONCILIATION_VIEW` | Reconciliation | VIEW | Usuluhishi wa hesabu |
| `ADMIN_DASHBOARD_VIEW` | Admin Dashboard | VIEW | **English fallback** |
| `APPROVAL_FLOW_CONFIGURE` | Approval Flow | CONFIGURE | **English fallback** |
| `APPROVAL_FLOW_VIEW` | Approval Flow | VIEW | **English fallback** |
| `BOARD_QUEUE_APPROVE` | Board Queue | APPROVE | **English fallback** |
| `BOARD_QUEUE_EXPORT` | Board Queue | EXPORT | **English fallback** |
| `BOARD_QUEUE_REJECT` | Board Queue | REJECT | **English fallback** |
| `BOARD_QUEUE_VIEW` | Board Queue | VIEW | **English fallback** |
| `CHAIRPERSON_QUEUE_APPROVE` | Chairperson Queue | APPROVE | **English fallback** |
| `CHAIRPERSON_QUEUE_EXPORT` | Chairperson Queue | EXPORT | **English fallback** |
| `CHAIRPERSON_QUEUE_REJECT` | Chairperson Queue | REJECT | **English fallback** |
| `CHAIRPERSON_QUEUE_VIEW` | Chairperson Queue | VIEW | **English fallback** |
| `CLIENT_ONBOARDING_CREATE` | Client onboarding | CREATE | **English fallback** |
| `CLIENT_ONBOARDING_DOCUMENTS` | Client onboarding | EXPORT | **English fallback** |
| `CLIENT_ONBOARDING_REVIEW` | Client onboarding | APPROVE | **English fallback** |
| `CLIENT_ONBOARDING_UPDATE` | Client onboarding | UPDATE | **English fallback** |
| `CLIENT_ONBOARDING_VIEW` | Client onboarding | VIEW | **English fallback** |
| `COLLATERAL_CREATE` | Collateral | CREATE | Dhamana |
| `COLLATERAL_RECOVERY` | Collateral | INITIATE | Dhamana |
| `COLLATERAL_RELEASE` | Collateral | APPROVE | Dhamana |
| `COLLATERAL_UPDATE` | Collateral | UPDATE | Dhamana |
| `COLLATERAL_VIEW` | Collateral | VIEW | Dhamana |
| `COLLECTIONS_CREATE` | Collections | CREATE | Ufuatiliaji wa marejesho |
| `COLLECTIONS_UPDATE` | Collections | UPDATE | Ufuatiliaji wa marejesho |
| `COLLECTIONS_VIEW` | Collections | VIEW | Ufuatiliaji wa marejesho |
| `CREDIT_COMMITTEE_QUEUE_APPROVE` | Credit Committee Queue | APPROVE | **English fallback** |
| `CREDIT_COMMITTEE_QUEUE_EXPORT` | Credit Committee Queue | EXPORT | **English fallback** |
| `CREDIT_COMMITTEE_QUEUE_REJECT` | Credit Committee Queue | REJECT | **English fallback** |
| `CREDIT_COMMITTEE_QUEUE_VIEW` | Credit Committee Queue | VIEW | **English fallback** |
| `CREDIT_CONTROLS_CONFIGURE` | Branch credit controls | CONFIGURE | Udhibiti wa mikopo wa tawi |
| `CREDIT_CONTROLS_VIEW` | Branch credit controls | VIEW | Udhibiti wa mikopo wa tawi |
| `DISBURSEMENT_QUEUE_DISBURSE` | Disbursement Queue | DISBURSE | **English fallback** |
| `DISBURSEMENT_QUEUE_EXPORT` | Disbursement Queue | EXPORT | **English fallback** |
| `DISBURSEMENT_QUEUE_VIEW` | Disbursement Queue | VIEW | **English fallback** |
| `FINANCIAL_REPORTS_EXPORT` | Financial and management reports | EXPORT | Taarifa za fedha na usimamizi |
| `FINANCIAL_REPORTS_INSTITUTION` | Financial and management reports | CONFIGURE | Taarifa za fedha na usimamizi |
| `FINANCIAL_REPORTS_VIEW` | Financial and management reports | VIEW | Taarifa za fedha na usimamizi |
| `GROUP_AUTHORITY_CREATE` | Group borrowing authority | CREATE | Mamlaka ya kukopa kwa kikundi |
| `GROUP_AUTHORITY_UPDATE` | Group borrowing authority | UPDATE | Mamlaka ya kukopa kwa kikundi |
| `GROUP_AUTHORITY_VIEW` | Group borrowing authority | VIEW | Mamlaka ya kukopa kwa kikundi |
| `GROUP_COVERAGE_CONFIGURE` | Coverage cases | CONFIGURE | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_CREATE` | Coverage cases | CREATE | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_EXPORT` | Coverage cases | EXPORT | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_LINK` | Coverage cases | POST | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_REVERSE` | Coverage cases | REVERSE | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_UPDATE` | Coverage cases | UPDATE | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_VIEW` | Coverage cases | VIEW | Ufuatiliaji wa udhamini |
| `GROUP_COVERAGE_OWN_EXPORT` | My coverage cases | EXPORT | Ufuatiliaji wangu wa udhamini |
| `GROUP_COVERAGE_OWN_RESPOND` | My coverage cases | UPDATE | Ufuatiliaji wangu wa udhamini |
| `GROUP_COVERAGE_OWN_VIEW` | My coverage cases | VIEW | Ufuatiliaji wangu wa udhamini |
| `GROUP_LENDING_CONFIGURE` | Group lending | CONFIGURE | Mikopo ya vikundi |
| `GROUP_LENDING_CREATE` | Group lending | CREATE | Mikopo ya vikundi |
| `GROUP_LENDING_EXECUTE` | Group lending | APPROVE | Mikopo ya vikundi |
| `GROUP_LENDING_EXPORT` | Group lending | EXPORT | Mikopo ya vikundi |
| `GROUP_LENDING_POST` | Group lending | POST | Mikopo ya vikundi |
| `GROUP_LENDING_REVERSE` | Group lending | REVERSE | Mikopo ya vikundi |
| `GROUP_LENDING_SUBMIT` | Group lending | INITIATE | Mikopo ya vikundi |
| `GROUP_LENDING_UPDATE` | Group lending | UPDATE | Mikopo ya vikundi |
| `GROUP_LENDING_VIEW` | Group lending | VIEW | Mikopo ya vikundi |
| `GUARANTOR_CAPACITY_CREATE` | Guarantor capacity | CREATE | Uwezo wa mdhamini |
| `GUARANTOR_CAPACITY_VIEW` | Guarantor capacity | VIEW | Uwezo wa mdhamini |
| `GUARANTOR_REQUESTS_APPROVE` | Guarantor Requests | APPROVE | **English fallback** |
| `GUARANTOR_REQUESTS_REJECT` | Guarantor Requests | REJECT | **English fallback** |
| `GUARANTOR_REQUESTS_UPDATE` | Guarantor Requests | UPDATE | **English fallback** |
| `GUARANTOR_REQUESTS_VIEW` | Guarantor Requests | VIEW | **English fallback** |
| `LOAN_ADJUSTMENTS_APPROVE` | Loan provisions and corrections | APPROVE | Akiba ya hasara na masahihisho ya mikopo |
| `LOAN_ADJUSTMENTS_CREATE` | Loan provisions and corrections | CREATE | Akiba ya hasara na masahihisho ya mikopo |
| `LOAN_ADJUSTMENTS_EXPORT` | Loan provisions and corrections | EXPORT | Akiba ya hasara na masahihisho ya mikopo |
| `LOAN_ADJUSTMENTS_VIEW` | Loan provisions and corrections | VIEW | Akiba ya hasara na masahihisho ya mikopo |
| `LOAN_DOCUMENTS_EXPORT` | Loan Documents | EXPORT | **English fallback** |
| `LOAN_DOCUMENTS_VIEW` | Loan Documents | VIEW | **English fallback** |
| `LOAN_OFFICER_QUEUE_APPROVE` | Loan Officer Queue | APPROVE | **English fallback** |
| `LOAN_OFFICER_QUEUE_ASSIGN` | Loan Officer Queue | ASSIGN | **English fallback** |
| `LOAN_OFFICER_QUEUE_EXPORT` | Loan Officer Queue | EXPORT | **English fallback** |
| `LOAN_OFFICER_QUEUE_REJECT` | Loan Officer Queue | REJECT | **English fallback** |
| `LOAN_OFFICER_QUEUE_VIEW` | Loan Officer Queue | VIEW | **English fallback** |
| `LOAN_PRODUCTS_CONFIGURE` | Loan Products | CONFIGURE | **English fallback** |
| `LOAN_PRODUCTS_CREATE` | Loan Products | CREATE | **English fallback** |
| `LOAN_PRODUCTS_DELETE` | Loan Products | DELETE | **English fallback** |
| `LOAN_PRODUCTS_UPDATE` | Loan Products | UPDATE | **English fallback** |
| `LOAN_PRODUCTS_VIEW` | Loan Products | VIEW | **English fallback** |
| `LOAN_CHARGES_CUSTOM` | Accountant loan recording | CONFIGURE | **English fallback** |
| `LOAN_RECORDING_CREATE` | Accountant loan recording | CREATE | **English fallback** |
| `LOAN_RECORDING_DISBURSE` | Accountant loan recording | DISBURSE | **English fallback** |
| `LOAN_RECORDING_EXPORT` | Accountant loan recording | EXPORT | **English fallback** |
| `LOAN_RECORDING_POST` | Accountant loan recording | ADD | **English fallback** |
| `LOAN_RECORDING_REVERSE` | Accountant loan recording | REVERSE | **English fallback** |
| `LOAN_RECORDING_VIEW` | Accountant loan recording | VIEW | **English fallback** |
| `LOAN_INTEREST_WAIVE` | Repayments | WAIVE | Marejesho |
| `LOAN_REPAYMENTS_CREATE` | Repayments | CREATE | Marejesho |
| `LOAN_REPAYMENTS_REVERSE` | Repayments | REVERSE | Marejesho |
| `LOAN_REPAYMENTS_VIEW` | Repayments | VIEW | Marejesho |
| `LOAN_REPORTS_EXPORT` | Loan Reports | EXPORT | **English fallback** |
| `LOAN_REPORTS_VIEW` | Loan Reports | VIEW | **English fallback** |
| `MANAGER_QUEUE_APPROVE` | Manager Queue | APPROVE | **English fallback** |
| `MANAGER_QUEUE_EXPORT` | Manager Queue | EXPORT | **English fallback** |
| `MANAGER_QUEUE_REJECT` | Manager Queue | REJECT | **English fallback** |
| `MANAGER_QUEUE_VIEW` | Manager Queue | VIEW | **English fallback** |
| `MEMBER_LOANS_ASSIGN` | Member Loans | ASSIGN | **English fallback** |
| `MEMBER_LOANS_CREATE` | Member Loans | CREATE | **English fallback** |
| `MEMBER_LOANS_DELETE` | Member Loans | DELETE | **English fallback** |
| `MEMBER_LOANS_UPDATE` | Member Loans | UPDATE | **English fallback** |
| `MEMBER_LOANS_VIEW` | Member Loans | VIEW | **English fallback** |
| `MEMBER_SETTINGS_UPDATE` | Member Settings | UPDATE | **English fallback** |
| `MEMBER_SETTINGS_VIEW` | Member Settings | VIEW | **English fallback** |
| `NOTIFICATIONS_UPDATE` | Notifications | UPDATE | **English fallback** |
| `NOTIFICATIONS_VIEW` | Notifications | VIEW | **English fallback** |
| `OUTBOX_UPDATE` | Outbox | UPDATE | **English fallback** |
| `OUTBOX_VIEW` | Outbox | VIEW | **English fallback** |
| `PAYMENT_DETAILS_UPDATE` | Payment Details | UPDATE | **English fallback** |
| `PAYMENT_DETAILS_VIEW` | Payment Details | VIEW | **English fallback** |
| `PAYROLL_DEDUCTIONS_CREATE` | Payroll deductions | CREATE | Makato ya mshahara |
| `PAYROLL_DEDUCTIONS_EXPORT` | Payroll deductions | EXPORT | Makato ya mshahara |
| `PAYROLL_DEDUCTIONS_IMPORT` | Payroll deductions | IMPORT | Makato ya mshahara |
| `PAYROLL_DEDUCTIONS_POST` | Payroll deductions | POST | Makato ya mshahara |
| `PAYROLL_DEDUCTIONS_UPDATE` | Payroll deductions | UPDATE | Makato ya mshahara |
| `PAYROLL_DEDUCTIONS_VIEW` | Payroll deductions | VIEW | Makato ya mshahara |
| `PLATFORM_SETTINGS_UPDATE` | Platform Settings | UPDATE | **English fallback** |
| `PLATFORM_SETTINGS_VIEW` | Platform Settings | VIEW | **English fallback** |
| `PROCESSED_LOANS_VIEW` | Processed Loans | VIEW | **English fallback** |
| `SACCO_CONFIGURATIONS_VIEW` | SACCO Configurations | VIEW | **English fallback** |
| `SACCO_REGISTRY_CREATE` | SACCO Registry | CREATE | **English fallback** |
| `SACCO_REGISTRY_DELETE` | SACCO Registry | DELETE | **English fallback** |
| `SACCO_REGISTRY_UPDATE` | SACCO Registry | UPDATE | **English fallback** |
| `SACCO_REGISTRY_VIEW` | SACCO Registry | VIEW | **English fallback** |
| `SMS_USAGE_ADD` | SMS Usage | ADD | **English fallback** |
| `SMS_USAGE_CONFIGURE` | SMS Usage | CONFIGURE | **English fallback** |
| `SMS_USAGE_VIEW` | SMS Usage | VIEW | **English fallback** |
| `STAFF_ANALYTICS_EXPORT` | Staff Analytics | EXPORT | **English fallback** |
| `STAFF_ANALYTICS_VIEW` | Staff Analytics | VIEW | **English fallback** |
| `SUPPORT_CREATE` | Support | CREATE | **English fallback** |
| `SUPPORT_UPDATE` | Support | UPDATE | **English fallback** |
| `SUPPORT_VIEW` | Support | VIEW | **English fallback** |
| `USER_ACCESS_CREATE` | User Access | CREATE | **English fallback** |
| `USER_ACCESS_DELETE` | User Access | DELETE | **English fallback** |
| `USER_ACCESS_UPDATE` | User Access | UPDATE | **English fallback** |
| `USER_ACCESS_VIEW` | User Access | VIEW | **English fallback** |
| `VOUCHER_APPROVAL_APPROVE` | Voucher approval actions | APPROVE | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_ESCALATED_APPROVE` | Voucher approval actions | ESCALATED_APPROVE | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_FORCE` | Voucher approval actions | FORCE_APPROVE | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_HOLD` | Voucher approval actions | HOLD | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_INITIATE` | Voucher approval actions | INITIATE | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_REJECT` | Voucher approval actions | REJECT | Hatua za idhini za vocha |
| `VOUCHER_APPROVAL_WITHDRAW` | Voucher approval actions | WITHDRAW | Hatua za idhini za vocha |
| `VOUCHER_DOCUMENT_CREATE` | Voucher supporting documents | CREATE | Viambatisho vya vocha |
| `VOUCHER_DOCUMENT_VIEW` | Voucher supporting documents | VIEW | Viambatisho vya vocha |
| `VOUCHER_RULE_CONFIGURE` | Voucher approval rules | CONFIGURE | Kanuni za idhini za vocha |
| `VOUCHER_RULE_VIEW` | Voucher approval rules | VIEW | Kanuni za idhini za vocha |
| `VOUCHER_WORKSPACE_CREATE` | Voucher approvals | CREATE | Idhini za vocha |
| `VOUCHER_WORKSPACE_POST` | Voucher approvals | POST | Idhini za vocha |
| `VOUCHER_WORKSPACE_UPDATE` | Voucher approvals | UPDATE | Idhini za vocha |
| `VOUCHER_WORKSPACE_VALIDATE` | Voucher approvals | VALIDATE | Idhini za vocha |
| `VOUCHER_WORKSPACE_VIEW` | Voucher approvals | VIEW | Idhini za vocha |
| `WORKSPACE_SETTINGS_CONFIGURE` | Workspace Settings | CONFIGURE | **English fallback** |
| `WORKSPACE_SETTINGS_UPDATE` | Workspace Settings | UPDATE | **English fallback** |
| `WORKSPACE_SETTINGS_VIEW` | Workspace Settings | VIEW | **English fallback** |

### Retired identifiers (41)

| Retained identifier | Source feature | Historical action |
| --- | --- | --- |
| `ACCESS_MATRIX_UPDATE` | `ACCESS_MATRIX` | UPDATE |
| `ACCESS_MATRIX_VIEW` | `ACCESS_MATRIX` | VIEW |
| `ACCOUNTING_BUSINESS_APPROVE` | `ACCOUNTING_BUSINESS` | APPROVE |
| `ACCOUNTING_CASH_FLOW_APPROVE` | `ACCOUNTING_CASH_FLOW` | APPROVE |
| `ACCOUNTING_CASH_FLOW_CREATE` | `ACCOUNTING_CASH_FLOW` | CREATE |
| `ACCOUNTING_CLOSING_APPROVE` | `ACCOUNTING_CLOSING` | APPROVE |
| `ACCOUNTING_CLOSING_CREATE` | `ACCOUNTING_CLOSING` | CREATE |
| `ACCOUNTING_CLOSING_INSTITUTION` | `ACCOUNTING_CLOSING` | CONFIGURE |
| `ACCOUNTING_CLOSING_REOPEN` | `ACCOUNTING_CLOSING` | REVERSE |
| `ACCOUNTING_CLOSING_VIEW` | `ACCOUNTING_CLOSING` | VIEW |
| `ACCOUNTING_COMPLIANCE_RELEASE_APPROVE` | `ACCOUNTING_COMPLIANCE_RELEASE` | APPROVE |
| `ACCOUNTING_JOURNALS_APPROVE` | `ACCOUNTING_JOURNALS` | APPROVE |
| `ACCOUNTING_OPENINGS_APPROVE` | `ACCOUNTING_OPENINGS` | APPROVE |
| `ACCOUNTING_OPENINGS_CREATE` | `ACCOUNTING_OPENINGS` | CREATE |
| `ACCOUNTING_PERIODS_CREATE` | `ACCOUNTING_PERIODS` | CREATE |
| `ACCOUNTING_POLICIES_APPROVE` | `ACCOUNTING_POLICIES` | APPROVE |
| `ACCOUNTING_POLICIES_CREATE` | `ACCOUNTING_POLICIES` | CREATE |
| `ACCOUNTING_POLICIES_VIEW` | `ACCOUNTING_POLICIES` | VIEW |
| `ACCOUNTING_RECONCILIATION_APPROVE` | `ACCOUNTING_RECONCILIATION` | APPROVE |
| `ACCOUNTING_RECONCILIATION_CREATE` | `ACCOUNTING_RECONCILIATION` | CREATE |
| `ACCOUNTING_RELEASE_APPROVE` | `ACCOUNTING_RELEASE` | APPROVE |
| `ACCOUNTING_RELEASE_REQUEST` | `ACCOUNTING_RELEASE` | CREATE |
| `ACCOUNTING_RELEASE_VIEW` | `ACCOUNTING_RELEASE` | VIEW |
| `ACCOUNTING_RELEASE_ACCEPT` | `ACCOUNTING_STAFF_ACCEPTANCE` | APPROVE |
| `FINANCIAL_REPORTS_FINALIZE` | `FINANCIAL_REPORTS` | APPROVE |
| `STATEMENT_APPROVE` | `FINANCIAL_STATEMENTS` | APPROVE |
| `STATEMENT_DESIGN` | `FINANCIAL_STATEMENTS` | CREATE |
| `STATEMENT_EXPORT` | `FINANCIAL_STATEMENTS` | EXPORT |
| `STATEMENT_FINALIZE` | `FINANCIAL_STATEMENTS` | CONFIGURE |
| `STATEMENT_VIEW` | `FINANCIAL_STATEMENTS` | VIEW |
| `LOAN_DOCUMENTS_CREATE` | `LOAN_DOCUMENTS` | CREATE |
| `REGULATORY_SUBMISSION_APPROVE` | `REGULATORY_STATEMENTS` | APPROVE |
| `REGULATORY_SUBMISSION_CREATE` | `REGULATORY_STATEMENTS` | CREATE |
| `REGULATORY_SUBMISSION_VIEW` | `REGULATORY_STATEMENTS` | VIEW |
| `REPORT_EXPORT` | `REPORT_BUILDER` | EXPORT |
| `REPORT_RUN` | `REPORT_BUILDER` | VIEW |
| `REPORT_TEMPLATE_DESIGN` | `REPORT_BUILDER` | CREATE |
| `REPORT_TEMPLATE_PUBLISH` | `REPORT_BUILDER` | APPROVE |
| `REPORT_TEMPLATE_SHARE` | `REPORT_BUILDER` | ASSIGN |
| `REPORT_JOBS_REVIEW` | `REPORT_EXECUTION` | VIEW |
| `REPORT_RUN_APPROVE` | `REPORT_EXECUTION` | APPROVE |
