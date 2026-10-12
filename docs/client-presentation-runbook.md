# Client presentation runbook

Verified on 12 October 2026 against an isolated local demonstration at http://localhost:8080. All borrowers, staff, products, evidence references and transactions described here are synthetic. This is presentation readiness for the accountant recording path, not certification of an institution's accounting policy or complete statutory reporting.

## Open the prepared demonstration

- URL: http://localhost:8080/login?tab=staff
- Institution: **Presentation Demo - Synthetic Data**; branch: **Synthetic B1** (header B1).
- Accountant staff number: **RECORDING-ACCOUNTANT**.
- Demo-only password: **RecordingTest-2026!**.
- Completed loan: **100001**, client **RECORDING-CLIENT / Synthetic MEMBER**.
- Loan details: http://localhost:8080/finance/loan-records/c7e03f16-2291-4eeb-bfd8-7eebaf183eef
- Client statement: http://localhost:8080/finance/client-ledgers/91000000-0000-0000-0000-000000000002

The local server is left running on port 8080 against a separate PostgreSQL demo database on loopback port 55461. It does not use the configured operational datasource. SMS, outbox scheduling and routine retention cleanup are disabled for this demonstration launch. Real transfers are not made by these postings; the accountant records actual movement evidence in real operations.

The existing loan is already Paid. Use it to explain the completed process, or record a fresh synthetic loan to demonstrate the buttons live. References below have already been used: use a new suffix for a new transaction. Never attempt to overwrite the completed loan or reuse its transaction references.

## Start with the chart of accounts

In the sidebar open **Accounting Setup > Chart of accounts**. Accounts belong to the institution; loan transactions and visibility retain their branch scope.

The five main groups are already provisioned:

| Main group | Classification | Usual normal balance |
| --- | --- | --- |
| 100000 Assets | ASSET | Debit |
| 200000 Liabilities | LIABILITY | Credit |
| 300000 Equity | EQUITY | Credit |
| 400000 Expenses | EXPENSE | Debit |
| 500000 Income | INCOME | Credit |

Create children in order: select a main-group row, **Add subgroup**; select the resulting subgroup, **Add subgroup** to create its account family; select the family, **Create posting account**. The hierarchy is Main group > Subgroup > Account family > Posting/control account. Only the final level carries transaction amounts. Account kind and purpose matter; the name alone does not make an account valid for lending.

For example: 100000 Assets > 110000 Current assets > 111000 Cash and bank > 111002 Operating bank account. The six-digit code follows the parent family. Heading groups are not posting destinations. New accounts activate directly after validation, without an approval queue. If these codes already exist, use them rather than creating duplicates.

These are the accounts prepared in the demo:

| Code and name | Parents below main group | Kind | Purpose | Normal balance | Use |
| --- | --- | --- | --- | --- | --- |
| 111001 Branch cash | 110000 Current assets > 111000 Cash and bank | POSTING | CASH | Debit | Cash disbursements/collections |
| 111002 Operating bank account | 110000 > 111000 | POSTING | BANK | Debit | Demo disbursement and repayments |
| 111003 Mobile money wallet | 110000 > 111000 | POSTING | MOBILE_MONEY | Debit | Wallet movements when applicable |
| 112001 Loan principal control | 110000 > 112000 Loan receivables | CONTROL | LOAN_PRINCIPAL | Debit | Principal owed, tied to loan subledger |
| 211001 Interest collections clearing | 210000 Current liabilities > 211000 Collection clearing | POSTING | CLEARING | Credit | Collected interest in this recording path |
| 311001 Owner capital | 310000 Owner equity > 311000 Owner capital | POSTING | CAPITAL | Credit | Demonstration owner's funding |
| 411001 Office expenses | 410000 Operating expenses > 411000 Branch expenses | POSTING | EXPENSE | Debit | Ordinary expense voucher example |
| 411002 Office rent | 410000 > 411000 | POSTING | EXPENSE | Debit | Account created successfully through the UI |
| 511001 Loan interest income | 510000 Lending income > 511000 Loan income | POSTING | INCOME | Credit | Separate approved income-recognition configuration |

For the bank-based loan demonstration, **111002, 112001 and 211001** are the essential loan accounts. Add **311001** if demonstrating owner funding. Cash/mobile-money accounts are required only for the channels actually used. Expense and income accounts broaden the accounting demonstration; they are not prerequisites for opening the loan record.

Choose one money account for each real channel/account. Owner capital is equity; borrowed funding needs the appropriate liability account under the institution's policy. Creating an account does not create its opening balance. Existing institutional balances need evidence-backed cutover/opening work; never invent an opening amount to make the report balance.

**Interest collections clearing is deliberately distinct from interest income.** The repayment command requires an ordinary clearing account for interest. It does not automatically accrue interest or recognize revenue. Do not select 511001 instead of 211001 merely because both names mention interest.

Activities/Transactions configuration stores accounting definitions. It is not a prerequisite for this direct loan path, and saving a definition does not itself post money.

## Activities and transaction codes

The demo library was initially empty because only accounts and direct financial transactions were prepared. On 12 October 2026 the following three activities and four transaction codes were created through the accountant UI, with active posting templates verified in the register and database.

Open **Accounting Setup > Transactions Config > Activities**. An activity groups related transaction definitions. Use **Add New Activity**, enter Code and Name, create, then close the success modal. Select an activity row and use **Transactions** to view its transaction codes.

Open **Transactions > Add New Transaction**. Choose the activity code, enter the transaction code/name and accounting event, and provide the account pairs for Total alone or separate components. Create the transaction and close the success modal. Select its row and **Open** to inspect its template; **Edit posting template** changes its configured account pairs.

| Activity code and name | Transaction code | Event | Component | Debit | Credit |
| --- | --- | --- | --- | --- | --- |
| FUNDING - Institution funding | CAP-RECEIPT | Owner capital | Total | 111002 Bank | 311001 Owner capital |
| LENDING - Loan operations | LN-DISB | Loan disbursement | Principal | 112001 Principal control | 111002 Bank |
| LENDING - Loan operations | LN-REPAY | Loan repayment | Principal | 111002 Bank | 112001 Principal control |
| LENDING - Loan operations | LN-REPAY | Loan repayment | Interest | 111002 Bank | 211001 Interest collections clearing |
| EXPENSES - Operating expenses | OFFICE-EXPENSE | Expenses | Total | 411001 Office expenses | 111002 Bank |

A transaction code describes a reusable type. A document/payment reference such as DEMO-REPAY-001 identifies an individual occurrence; they are not interchangeable.

**Current implementation boundary:** the library stores definitions only. It does not automatically execute these templates in the direct loan-recording or core voucher workflows. Loan recording still uses its selected validated accounts, and core vouchers still use their entered transaction rows. A Configured template proves configuration exists, not that a posting adapter consumes it. Do not promise clients that changing a library account pair changes automatic loan postings. Integrating that behavior requires separate implementation and financial validation.

No fees, accruals, recognition, statutory approvals or additional financial movements were fabricated to populate the library. These definitions belong only to the synthetic demonstration institution. Screenshots: target/presentation/transaction-codes.jpg and target/presentation/repayment-template.jpg. Creation, success modals, the complete register, and principal/interest repayment template details were verified live. No backend code changed for this setup follow-up, so the Maven suite was not rerun.

## Suggested presentation sequence

### 1. Show ordinary funding before lending

Open **Vouchers > Receipt register > Create**. The prepared receipt RV-2026-00000001 records synthetic owner funding:

- Transaction date: 11 September 2026.
- Received from: Demo owner.
- Reference: DEMO-CAPITAL-001.
- Amount: TZS 2,000,000.00.
- Debit 111002 Operating bank account; credit 311001 Owner capital.

Preview the balanced entries and post directly. Core receipt/payment/journal vouchers have accountant direct posting, subject to validation, permissions, open periods and audit. They support multiple transaction rows. Payment vouchers can demonstrate ordinary expenditure, and journals ordinary adjustments, using eligible posting accounts.

Do not use these generic vouchers to disburse a loan or record a loan repayment: they cannot bypass principal control/subledger safeguards. The owner's funding is a new synthetic transaction, not an invented legacy opening balance.

### 2. Record the existing loan agreement

Open **Loan Accounting > Loan Records > Create**. Select an active client from the current branch and an active loan product. The demo contract is:

| Field | Value |
| --- | --- |
| Client | RECORDING-CLIENT / Synthetic MEMBER |
| Product | Synthetic Business Loan |
| Application date | 10 September 2026 |
| Requested principal | TZS 1,000,000.00 |
| Contractual principal | TZS 1,000,000.00 |
| Tenure | 1 month |
| First instalment due | 12 October 2026 |
| Purpose | Synthetic business stock purchase |
| Product pricing | 24% per year, reducing balance, monthly; no fees |

Preview the server-calculated schedule. It shows one instalment of **TZS 1,020,000.00**, comprising principal **1,000,000.00** and contractual interest **20,000.00**. The example rate is synthetic, not a recommended institutional price.

Save the record. Status is **Recorded**. No receivable, money movement or repayment exists merely because the record was saved. Contract terms are frozen; changing the product later does not reprice a posted contract. Supporting information remains declared/documented/unavailable as actually entered; a reference is not proof that underwriting has occurred.

### 3. Record the actual disbursement

Open the recorded loan's disbursement action, or **Disbursements > Create** and select it. Enter:

- Effective disbursement date: 12 September 2026.
- Full contractual principal: TZS 1,000,000.00.
- Money account: 111002 Operating bank account.
- Loan principal control: 112001 Loan principal control.
- Reference: DEMO-DISBURSE-001.
- Supporting reference: synthetic agreement and payment evidence.

Preview and post. The disbursement opens the loan subledger and posts **Dr principal control 1,000,000 / Cr bank 1,000,000** atomically. Status becomes Disbursed; outstanding principal becomes TZS 1,000,000. Download the disbursement PDF and show the posted document.

Disbursement must match the recorded principal, be on/after application, before the first due date, and not future-dated. Use an open accounting date. No silent change to assessed terms is permitted.

### 4. Record a partial repayment on its due date

Open **Repayments > Create** and select the loan. Record the received payment:

- Effective payment date: 12 October 2026.
- Amount: TZS 200,000.00.
- Money account: 111002 Operating bank account.
- Interest collections clearing: 211001.
- Reference: DEMO-REPAY-001.
- Supporting reference: synthetic bank collection evidence.

Preview first. The system applies **20,000.00 to due interest**, then **180,000.00 to principal**, leaving **820,000.00 principal**. Post and open the repayment receipt.

The journal is **Dr bank 200,000 / Cr principal control 180,000 / Cr interest collections clearing 20,000**. The receipt retains the balance immediately after this posting even when a later payment changes today's balance.

Payments in this path apply only to instalments already due on the effective date. A future instalment cannot be paid by pretending a schedule refresh received money. Advances and early settlement need their own implementation.

### 5. Show the client statement, then finish repayment

Open **Client Ledgers**, select the client, and show the principal, due interest, overdue amount and future interest separately. The saved partial-payment statement shows principal 820,000 and due interest zero.

Record a second received repayment of **TZS 820,000.00** dated 12 October 2026, reference **DEMO-REPAY-002**, using 111002. Preview: principal 820,000, interest zero, balance zero. Post. The loan becomes **Paid** because the ledger was cleared, not because its final due date passed.

The final posting is **Dr bank 820,000 / Cr principal control 820,000**. The final client statement shows outstanding principal and due interest zero and all three chronological movements.

If presenting on a later day, the prepared dates remain historical. Keep them accurate; do not future-date payments or alter computer time. A fresh demonstration needs a distinct loan and new references, and its payment date must be an eligible due date in an open period.

### 6. Export the reports

| Screen | Output and expected prepared-demo result |
| --- | --- |
| Loan record details | Download PDF / Print: contract and schedule |
| Disbursement details | Download PDF / Print: posted TZS 1,000,000 movement |
| Repayment details | Download PDF / Print: immutable receipt and allocation |
| Client Loan Statement | Download PDF / Print: current balances and movements |
| Disbursements register | **Download Report PDF / Print Report**: one posted disbursement, TZS 1,000,000 total |
| Repayments register, from/through 12 October 2026 | **Download Report PDF / Print Report**: two posted payments, TZS 1,020,000 total |

The register's selection-dependent PDF/Print buttons export one selected record. The independent **Report** links export the filtered register. Click **Filter** after changing dates/status/search before downloading. The report links carry the applied filters and sort. Output is **PDF and print only**. Narrow filters if an export would exceed 500 records/movements. Print views were opened and verified; physical printer output was not tested.

Verified downloaded evidence is stored in the ignored local directory **target/presentation/pdf/**. There are PDFs for the loan record, disbursement, partial receipt, partial statement, final statement, and both registers. Screenshots are under **target/presentation/**. Keep these as a fallback if live navigation takes longer than expected.

## What the accounting totals mean

After the prepared synthetic sequence:

| Account | Net position from the demo entries |
| --- | --- |
| 111002 Bank | Debit TZS 2,020,000.00 |
| 112001 Principal control | TZS 0.00 |
| 211001 Interest collections clearing | Credit TZS 20,000.00 |
| 311001 Owner capital | Credit TZS 2,000,000.00 |

The loan subledger independently records principal advanced 1,000,000, principal repaid 1,000,000 and interest collected 20,000. It agrees with the principal control journal. The 20,000 clearing credit is not automatic recognized profit. Actual bank-statement reconciliation was not performed because the evidence is synthetic.

## Keep credit approval separate

This rehearsed accountant flow records an existing agreement and money movements directly. It does not create loan approval, verification, maker/checker or release evidence.

For new borrower origination, use the existing client loan application and the selected product's enabled approval path: draft/submission, guarantors where required, then the configured staff review stages. Guarantor, manager, board and other review requirements come from the product and service rules. Approval alone does not disburse funds. Do not promise that the direct recording path is an automated conversion of every approved borrower application; the existing historical/borrower-originated workflows have their own safeguards and coverage. A complete multi-role credit-approval rehearsal was outside this accountant demonstration.

## Readiness and limits to state honestly

**Verified now:** six-digit COA creation, ordinary funding receipt, record without moving money, full disbursement, due-date partial/final repayment, Paid status, ledger-to-principal-control agreement, document PDFs, client statements, filtered register PDFs and print views.

**Formal financial reports remain conditional:** trial balance/account activity rely on approved ledger policy for the reporting date, and report coverage must disclose missing reviewed openings or unbridged sources. Income recognition, financial statements, closing/reconciliation and statutory report release require the institution's actual approved configuration and evidence. The demo has no fabricated policy approval or legacy openings. Do not advertise its operational exports as an authoritative institution-wide P&L, balance sheet or regulatory return.

**Direct loan recording boundaries:** TZS, full ordinary disbursement, scheduled due-interest/principal payments, no application/processing/insurance fees. Fee-bearing products, advances, early settlement, top-ups, restructuring, write-offs, disbursement cancellation and legacy opening migration need separate supported accounting work. Refresh recalculates projections; it never receives money.

**Corrections:** a different authorized accountant may reverse the latest unreversed repayment with a date/reason. Original receipts and journals remain retained with linked reversal history. The original accountant cannot reverse their own receipt. The original-actor button was verified disabled live; independent correction and concurrency protections were tested in disposable PostgreSQL fixtures. Corrections are not demonstrated by editing or deleting a posted record.

For actual institution pricing, use approved policy and current official requirements, including the [Bank of Tanzania fee guidelines](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf) and [official regulations directory](https://bot.go.tz/BankSupervision/Regulations). This runbook does not certify regulatory compliance.

## Verification and implementation evidence

- Full Maven suite passed: **1,272 tests, 0 failures, 0 errors, 258 opt-in skips**. Log: target/presentation-full-test-final.log. This run includes the workspace-table contract correction and precedes the final report-link UI addition.
- Disposable PostgreSQL regression passed: **61 tests, no failures/errors/skips**, including loan recording, vouchers, GL, reports and repayment ledger. Log: target/presentation-postgres-test.log.
- After the final report-link addition: **8 focused JSP/document/table tests passed**, no failures/errors/skips. Log: target/presentation-final-ui-test.log.
- Live browser verified account creation, ordinary receipt, loan schedule preview/save, disbursement, both repayment allocations, current client balances, PDFs, filtered report totals and print views.
- Report toolbar checked at **360, 768, 1,366 and 1,920 pixels** without document-width overflow. Institution/branch authorization, idempotency, closed-period behavior, audit rollback and concurrent financial commands are covered by the regression fixtures; this is not a live cross-branch UI penetration test.
- Fresh demo schema validated/applied **77 Flyway migrations through V58**. No historical schema checksums were repaired or financial guards disabled.
- No 1,000 requests/second benchmark was performed. Existing scoped pagination/export limits and transactional posting architecture remain intact.

Implementation for this request is deliberately small: expose existing scoped report endpoints in the disbursement/repayment register using existing localized labels and export permissions; correct the ERP workspace-table test's classification of the separate A4 client-statement print document. No backend financial formulas, route permissions, schema or seeded business policies changed. The statement's own document/PDF tests continue to cover its print contract.

## If the local demo needs restarting

Keep the prepared application running for the presentation. The bootstrap script and database are local temporary artifacts, not production configuration:

- Directory: C:/Users/USER/AppData/Local/Temp/microfinance-presentation-20261012
- Database: microfinance_presentation_demo, PostgreSQL 17, 127.0.0.1:55461.
- App script: start-app.ps1 in that directory; app.log and app.err contain startup output.

If PostgreSQL is stopped, start only this isolated cluster with:

~~~powershell
& 'C:/Program Files/PostgreSQL/17/bin/pg_ctl.exe' -D 'C:/Users/USER/AppData/Local/Temp/microfinance-presentation-20261012/data' -l 'C:/Users/USER/AppData/Local/Temp/microfinance-presentation-20261012/postgres-restart.log' start
~~~

Once port 8080 is free and this cluster is running, launch the prepared application with:

~~~powershell
& 'C:/Users/USER/AppData/Local/Temp/microfinance-presentation-20261012/start-app.ps1'
~~~

The script uses Java 25, existing compiled target/classes and a resolved runtime classpath. Rebuilding or deleting temporary files can require regenerating that classpath. Do not start a second application on 8080 while the current one is running, and do not point this fixture at operational data. Normal mvn spring-boot:run uses the repository configuration and is not equivalent to this isolated demo launch.
