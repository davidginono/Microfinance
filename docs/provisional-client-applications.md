# Provisional client profiles and applications

This increment supersedes the retired onboarding loan-request UI. Profile onboarding remains profile-only. Applications recorded for provisional clients now create the normal `LoanApplication`, using the existing product, quote, affordability, guarantor and approval services.

## Entry points

- `/client-portal`: public client-number entry and contact OTP.
- `/client-portal/register`: first-time profile registration without a borrower user account.
- `/client-portal/workspace/profile`: the authenticated client's own profile, documents, submission and review feedback.
- `/client-portal/workspace/applications`: that client's applications, recording, documents and handoff after guarantor approval.
- `/finance/clients/onboarding`: staff-assisted provisional profiles and independent profile verification.
- `/finance/loan-records` and `/finance/loan-records/new`: the accountant's existing loan register and Record Loan Application form own staff recording, including provisional intake drafts and supporting evidence. A profile's Record application action carries its client ID into this form. Old `/finance/client-applications` links redirect into this workspace.

Staff access reuses explicit `CLIENT_ONBOARDING_VIEW` and `CLIENT_ONBOARDING_UPDATE` claims. Profile verification retains the separate `CLIENT_ONBOARDING_REVIEW` claim and independent-person rule. No claims are assigned by this increment. Staff cannot edit or upload a profile for a client with an enabled borrower account. Staff user creation remains restricted to staff roles. Full borrower users continue to self-register, complete their profiles and apply in their borrower workspace.

## One identity, several applications

Both registration paths use `MemberRegistrationService.registerAssisted` and the same atomic `client_number_seq` as full borrower registration. A provisional identity has no username, login password, invented signature or settings account. Duplicate contact checks run under the existing registration contact locks. Retrying a consumed registration challenge cannot register another identity.

A returning provisional client enters their existing client number privately; there is no public client directory. Staff use a branch-scoped selector. Application drafts and later applications remain linked to the original client and number. Profile corrections and renewals retain existing versioned KYC evidence. A new profile is not required for another application. Existing active-application and product limits remain authoritative.

## OTP and verification

Registration verifies the delivery contact before creating the provisional identity and initial profile draft. Profile access can be used while verification is pending. Loan access requires a currently verified profile both when issuing the challenge and on every application request. Revocation, expiry, a changed contact, an inactive identity or inactive branch invalidates access.

Challenges expire after five minutes, allow five failed attempts, use hashed codes, and are consumed under a row lock. Contact/IP limits bound issuance and verification. Delivery happens after the short database transaction using the branch's configured email/SMS delivery policy. Phone-only self-service therefore needs SMS delivery configured for that branch. Existing-client responses do not disclose whether a number exists or how it is contacted.

Successful verification rotates the HTTP session ID and establishes a restricted principal with no normal borrower/staff authorities. A filter blocks access to other workspaces, documents and authenticated routes. Portal access has a 15-minute maximum lifetime. CSRF remains enabled. Profile verification enqueues a scoped notification with the client number and a portal link; clients request a fresh OTP when they return. Notification links use the existing `APP_BASE_URL` configuration.

Consumed challenges immediately discard their code hash and registration payload. A bounded cleanup removes challenges and rate buckets older than a day, 500 of each per minute by default. `app.client-portal.cleanup-ms` controls the interval.

## Shared client profile

Public provisional registration, staff-assisted registration and the borrower Complete profile flow reuse the same profile-field fragment: bio data, contact/address, business, spouse and bank details. Public registration retains these details through contact OTP verification into the initial profile draft, using the same draft validation before sending a code. Private group choices, photos and supporting documents become available after contact verification; their tabs explain this before verification. Profile submission, document checks and independent staff verification remain unchanged. A provisional identity still has no borrower account, and subsequent applications reuse its client number.

The profile parity correction passed 15 distinct focused cases: real staff/client JSP rendering, Jasper compilation, portal MVC/security and PostgreSQL tests, and the full Spring Boot registration/OTP/loan flow. The public form renders every applicable shared profile field; supplied business, spouse, bank and other profile details survive OTP and database storage. Browser checks confirmed tab value preservation, upload guidance, no console errors and no page overflow at 360, 768, 1366 and 1920 pixels in English and Kiswahili. Delivery was mocked; no live SMS/email provider was exercised.

## Recording and evidence

The accountant's recording form retains its configured/custom terms, quote, charges and save controls. Its supporting section also captures the intake evidence. Staff drafts reopen in that same form. Borrower and restricted portal commands render through the shared accounting page family with their existing ownership and approval safeguards. Fields include application date, product, amount, explicit repayment period, officer preference, purpose/use of funds, business, declared monthly income/expenses/debt, guarantor relationship/activity/contact, and repeated collateral names, numbers, types, descriptions, owners, contacts, locations and declared values.

Rates, fees, repayment calculations and approval stages come from the product. A saved draft is explicitly unassessed; missing expenses/debt are never filled with zero. Submission requires current verified KYC, the declaration and an uploaded signed application. Signed paper evidence is retained as evidence, never converted into a fabricated digital signature or an OTP signature event. Existing generated-document signature gates remain in force.

Attachments have names and purposes (signed application, business/income/debt evidence, collateral photos/ownership, guarantor photos, other), and can satisfy named product requirements. PDF/PNG/JPEG files are limited to 5 MB each, 10 per save, 25 MB combined and 40 stored attachments. Downloads recheck record scope and ownership. Normal borrower forms also capture application date, intended use and collateral identification/ownership/contact details, and name additional attachments.

Commands have request keys, payload/file hashes and optimistic versions. Transaction-local request/client locks serialize recording; immutable command rows retain the real actor and branch. Duplicate retries return the original application; changed payloads and stale versions fail. The full normal product submission runs in the same transaction as its recording command. Recording/submission posts no journal or repayment.

## Verification and capacity

Focused tests cover registration/activation compatibility, profile review, OTP attempts/reuse/concurrency, contact changes, CSRF, session fixation and isolation, recording idempotency, real product submission, private attachments and repeat applications. The full-application PostgreSQL test uses a synthetic fixture and mocked OTP delivery; it does not contact delivery providers. The rendered profile regression uses actual JSPs and HTTP multipart forms.

Queries use scoped exact lookups and bounded 25-row pages; guarantor labels use a bounded batch. These correctness/rendering tests are not evidence of 1,000 requests/second. No throughput claim is made. Production capacity still needs a declared route mix, representative database, delivery latency, CPU/pool settings, p95/p99 and error gates, and sustained/soak measurements.

### Recorded checks (2026-11-02)

- 269 focused regression cases passed with no failures or errors, covering staff account creation, profile ownership/review, portal security, OTP concurrency, lending, review handoff, documents, localization and actual Jasper compilation.
- The full Spring Boot/JSP/PostgreSQL test passed: public registration and OTP login, staff-assisted recording, client continuation/submission to the configured review stage, private attachments, repeat applications with the same client number, English/Kiswahili pages and an HTTP draft save.
- In-app-browser checks covered public registration (optional email/middle name), OTP login, product loading and server estimates, collateral and named attachments, and a rejected save retaining entered values and the selected file while restoring controls. Form initialization and tab visibility were checked after a cold start. Widths 360, 768, 1366 and 1920 had no page-level horizontal overflow.
- JavaScript syntax checks and UTF-8 source checks passed. The WAR package built successfully. SMS/email provider delivery was mocked for these tests; live provider delivery was not exercised.
