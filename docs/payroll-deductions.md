# Payroll deduction exports

Implemented scope: employer-specific loan deduction requests, registration/profile employee numbers, frozen PDF/print schedules and a delivery/download audit. Exporting, downloading or marking a schedule as sent never posts a repayment, creates a journal or changes a loan balance. Employer result imports and remittance reconciliation remain separate work.

## Setup and use

1. Clients may enter **Employee number** during registration or edit it under **Profile**. It is optional for ordinary business clients and required before payroll enrollment. It preserves leading zeros and accepts up to 50 letters, digits, spaces, periods, hyphens, underscores and slashes. It is separate from the institution's Staff Number/login identifier. A self-entered number is a declaration, not verified employment.
2. An authorized staff member opens **Payroll employers** in the sidebar and records the employer name and agreement reference for the selected branch.
3. In **Deduction instructions**, choose the employer and a disbursed loan with an existing local ledger. Record the authorized monthly limit for that loan, effective dates and references to the borrower's deduction mandate and employer evidence. The employee number is read from the client's current profile, not a hidden form field. Staff must check the evidence; recording a reference does not establish legal approval.
4. In **Payroll deductions**, create a batch for this or next month and select its expected deduction date. Preview all instruction results, using the server pager. Earlier unpaid instalments are included only when explicitly selected. Confirm any exclusions before freezing.
5. Open the frozen batch to download PDF or print it. The employer document combines a client's included loans into one employee deduction, with a stable deduction reference. It contains employee number/name, reference and requested TZS amount, not loan balances, authorization evidence, unrelated finances or client identifiers.
6. **Mark as sent** records the staff member's actual delivery reference. It does not transmit the file. Download history remains separate. Cancelling a sent schedule requires a recorded employer withdrawal reference and explicit confirmation. Create a linked replacement from the cancelled batch; historical rows and documents remain unchanged. Send/withdrawal actions require real supporting evidence.

## Amounts and exceptions

The proposed amount is the smaller of the instruction's per-loan monthly limit and the unpaid contractual principal plus scheduled interest on instalments due by the deduction date. Unless arrears inclusion is selected, only instalments in the payroll month are included. Paid amounts, future-interest exclusions and recorded interest waivers reduce their corresponding obligations. This reads ledger instalment counters; it does not reprice contracts or use borrower JSON estimates as posted payments. Fees/charges and future-principal collection requests are outside this export.

Amounts reflect the records available when calculated, not a reconstructed historical balance. Payments after freezing do not rewrite an employer instruction. Staff must withdraw/cancel and replace an obsolete schedule before payroll processing. The per-loan limits do not establish a salary-wide affordability or statutory deduction ceiling; the institution must retain the applicable authorized limits and evidence for each arrangement.

Expired/not-yet-effective authorizations, changed/missing employee numbers, inactive clients, unsupported loans, overlapping active batches and no unpaid instalments are explicit exclusions. Changing an employee number requires stopping and replacing its old instruction. A loan cannot have two active instructions; an employer employee number cannot map to different clients. Stopping an instruction affects future batches, not a schedule already frozen.

## Security, storage and limits

- Routes start at `/finance/payroll`. Registration/profile retain session authentication and CSRF. Profile updates change only the authenticated active client's employee number, without overwriting concurrent access changes.
- Explicitly assign `PAYROLL_DEDUCTIONS_VIEW`, `PAYROLL_DEDUCTIONS_CREATE`, `PAYROLL_DEDUCTIONS_UPDATE` and `PAYROLL_DEDUCTIONS_EXPORT` through the existing access matrix. None is granted automatically. Current claims, active staff/institution/branch and exact tenant/branch scope are checked again in the service, including PDF/print downloads.
- V81 adds the optional member field, employer/instruction records, linked instruction stops, frozen batch headers/lines, permanent batch events and retained PDF artifacts. V81.1 indexes loan overlap lookups. Financial-history triggers protect these records; deferred checks require complete frozen rows and totals. No existing loan opening, posting or approval history is changed.
- Registers and details use 25-row server pages; choices use 25 plus lookahead. Calculation/export is capped at 1,000 active instructions per employer. Larger employers need a separately implemented partitioned/batched export. No throughput claim is made.
- Generation rejects stale previews, duplicate active employer/month batches and changed retries. Request and employer locks serialize competing commands. Snapshots include source identity, dates, amounts, exceptions, actor and a SHA-256 fingerprint. Each language's first PDF is retained; subsequent PDF/print downloads return the same bytes and checksum and append a download event.
- Outputs are PDF/print only. No CSV/Excel export, employer API, automated dispatch, salary product, salary evidence verification, repayment posting or channel reconciliation is introduced. Existing voucher approval settings and specialized repayment safeguards remain with their owning services.

## Verification

2026-10-19: all 31 focused tests passed, including eight PostgreSQL tests and Jasper compilation of all five new views plus registration/profile. All 102 migrations validated through V81.1. The database was a disposable loopback instance; the operational datasource was not used. Coverage includes calculations/caps, partial-paid obligations, optional arrears, mapping changes, exclusions, tenant/branch isolation, duplicate instructions, stale previews, idempotent concurrent generation, immutable documents/history, cancellation/replacement, audit rollback and bounded/sorted lists. MVC checks exercise claims, CSRF, allowlisted binding and matching PDF/print bytes/headers; profile checks cover ownership, validation and concurrent deactivation. A final eight-test PostgreSQL rerun also passed with multiline evidence references.

Authenticated synthetic browser checks covered employee-number saving, employer/instruction creation, searchable choices, paginated preview, explicit exclusions, freezing, batch details, selection reset on server sorting and a real PDF download. English registration/profile and payroll pages, plus Kiswahili payroll forms/details, were checked at 360/768/1366/1920px without document overflow. Mobile sidebar, profile/notification dropdowns and login/logout were exercised. English/Kiswahili A4 multipage PDF samples were rendered and inspected with repeated headings and intact rows. New payroll routes explicitly participate in the existing locale interceptor. `mvn -DskipTests compile`, the focused tests, JavaScript syntax/diff checks and the final `mvn -DskipTests package` passed; incremental compiler mode was disabled for the focused/package runs to avoid unnecessary rebuilds in the shared checkout.

The full `mvn test` run completed with 1,253 tests, 17 failures, zero errors and 202 skipped opt-in tests. The failures were outside the changed payroll paths: existing loan-document expectations, audit date formatting, workflow-progress expectations and legacy UI/source contracts (including approval configuration tables and a management-report derived accessor). They were not changed under this request. This is focused feature verification, not a clean full-suite or throughput claim.

Run the opt-in database checks only against a disposable database named `microfinance_payroll_test`, with user `microfinance_test` and a dedicated loopback port:

```powershell
$env:MICROFINANCE_PAYROLL_TEST_URL = 'jdbc:postgresql://127.0.0.1:PORT/microfinance_payroll_test'
mvn '-Dtest=PayrollPostgresTest,PayrollPdfTest,PayrollMvcTest,EmployeeProfileServiceTest' test
```
