# Loan cancellation

Staff can open a loan by **Loan ID**, then choose **Cancel loan** from its details or the Loan Records Actions menu. This corrects a mistaken loan; it does not forgive debt, recover money from a payment provider, or erase history.

## Permissions and outcome

- Unfunded recorded, submitted, rejected and ready-for-disbursement loans require `LOAN_RECORDING_VIEW` and the new explicit `LOAN_RECORDING_CANCEL` claim. Confirmation and a reason are mandatory. Cancellation takes effect immediately, retaining the internal `WITHDRAWN` status, shown as **Cancelled**.
- Verified ordinary funded loans also require `LOAN_ADJUSTMENTS_VIEW` and `LOAN_ADJUSTMENTS_CREATE`. Staff provide a correction date and evidence that funds no longer remain with the client. The existing disbursement reversal request requires independent approval under `LOAN_ADJUSTMENTS_APPROVE`, including its original-disburser exclusion and existing group-access checks. Approval creates the linked accounting reversal and terminal `DISBURSEMENT_REVERSED` state. Rejection moves no money.
- No defaults, existing assignments or financial permissions are granted by this increment. Assign the new claim deliberately through the existing access matrix.
- Any posted repayment blocks cancellation permanently, including reversed receipts, charge-only receipts and internal settlements. Funding deductions are part of the original disbursement, and supported deductions/charge assessments are reversed by the existing correction service.
- Pending adjustments, posted allowances/write-offs and linked active top-ups retain their safeguards. Funded top-ups use their separate correction process. Missing or unverified legacy funding is not proof of zero repayments.

## Integrity and history

The new GET/POST `/finance/loan-records/{id}/cancel` routes use session authentication, CSRF and live institution/branch permissions. The service locks the recorded source, ledger if present, and loan in the existing order before checking current state, terms and repayment history. Request keys make retries idempotent; audit failure rolls back the command.

Migration `V168__loan_cancellation_without_repayments.sql` generalizes the existing immutable withdrawal evidence for individual loans without renaming its compatibility table. It preserves the existing group withdrawal command. It also adds shared eligibility/history checks and rechecks repayment history when a disbursement reversal posts, including receipts posted and subsequently reversed while review was pending. It does not remove contracts, decisions, documents, schedules, journals or repayment protections.

Cancelled loans leave the default working register and actionable queues. Select **Cancelled** or **Disbursement reversed** in the status filter to find them. Their details retain a **Cancellation** link to the reason/staff/date or the existing financial correction decision. Historical financial reports continue to use the original posting and dated reversal, rather than suppressing past transactions.

Apply the migration during a backed-up deployment. Verification uses an isolated build directory and disposable PostgreSQL database; do not clean an output directory used by a running app. This change makes no 1,000 requests/second capacity claim.

## Verification

The focused suites are `LoanCancellationPostgresTest` and `LoanCancellationMvcTest`. They cover lifecycle states, scope/current permissions, immutable evidence, duplicate requests, audit rollback, strict repayment protection, rejection/resubmission, and races against funding and repayment. Existing loan recording/group approval regressions and Jasper compilation cover the surviving flows and affected JSPs.

Verification on 18/11/2026 used Java 25 and a new disposable PostgreSQL 17.9 database. All 189 migrations applied and validated; all 163 selected database tests passed, including the 12 cancellation cases. The latest results across the selected suites total 224 tests with no failures, errors or skips, including loan review/MVC checks, permission-screen rendering, record-property compatibility and four-page Jasper compilation. JavaScript syntax checks also passed. The cancellation view has a dedicated Jakarta EL accessor check because JSP precompilation alone does not evaluate record properties. The temporary database server was stopped after verification.

The running application was not redeployed, and no live browser layout pass is claimed. Deployment must apply V168 and explicitly assign the cancellation claim before staff can use the new action.
