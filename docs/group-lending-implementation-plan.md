# Group lending: image analysis and implementation plan

Planning date: 23 October 2026. Status: proposal; no group-lending implementation, migration, policy approval or financial posting is delivered by this document.

## 1. Recommendation and scope

Implement one group registry and lending-cycle concept, with three explicitly different contractual structures. Reuse the modular Spring Boot 4.1.0 / Java 25 / PostgreSQL application, session authentication, CSRF, product workflows, decimal calculator, loan ledger and JSP shell.

Recommended delivery order: common foundation → Model 2 (individual loans with group guarantees) → shared borrower support and Model 3 (organization borrower) → Model 1 (one shared facility with member allocations). Model 2 preserves individual loan ownership and is the smallest complete financial increment. Model 1 requires an additional component-attribution layer and the most difficult conservation rules. Institution priorities can change this order, but not the dependencies.

The image is a discovery reference, not an approved policy, legal agreement, accounting standard or instruction to enable every pictured integration. Its TZS amounts and five-person groups are examples, not product limits. No savings prerequisite, group deposit product, native mobile app, payment provider, identity integration or automatic recovery authority follows from the illustration.

“Group loan account” means a lender's loan/subledger record here. It is not a deposit account or permission for the lender to hold members' pooled savings. The group's actual bank/mobile-money destination is a separate verified payment relationship.

## 2. What the three pictures actually mean

| Question | Model 1: shared loan | Model 2: individual loans | Model 3: organization loan |
| --- | --- | --- | --- |
| Who owes the lender? | The parties identified in the shared agreement: a legally supported group/joint-borrower arrangement | Each individual borrower on their own agreement | The organization, where its capacity to borrow is established |
| Authoritative debt records | One facility and loan ledger | One loan and ledger per borrower | One organization loan and ledger |
| Member-level amounts | Allocations of the same debt, with contractual meaning explicitly defined | Actual individual debts | Participation/contributions unless separately contracted |
| Repayment source | Members, representative or other authorized payer against the shared loan | Each borrower or an identified payer covering a named borrower's loan | Organization/project income or an identified third-party payer |
| Group total | Shared-loan balance | Aggregate of linked individual loans, with no extra group receivable | Organization-loan balance |
| Guarantee | Separate from being a joint borrower; only where contracted | Explicit directed member/group guarantees, never inferred from membership | Separate member/other guarantees only where agreed |
| Arrears unit | Shared contractual loan, plus internal allocation shortfalls | Individual debt plus separately defined group/regulatory risk treatment | Organization loan |
| Approval unit | Shared agreement and frozen allocations/obligors | Each loan; cycle coordination does not approve every child loan | Organization agreement, mandate and signatories |

All models can share capabilities, but they cannot share every decision rule or necessarily the same stage sequence. The illustration's common workflow needs product-selected reviews and model-specific gates.

### Model 1: TZS 5,000,000 allocated to five members

The diagram's five TZS 1,000,000 allocations total the one TZS 5,000,000 loan. They do not create another TZS 5,000,000 of receivables. A joint debtor and a guarantor are different legal roles; the label “joint liability (all members)” does not establish which role the agreement creates, or whether liability is capped, proportional or joint and several.

Required business rules:

1. Identify every actual obligor, signatory and representative. Record who may apply, accept the agreement, receive funds and view shared information.
2. Freeze eligible participants, gross allocation amounts, charge shares, net payouts and accepted liabilities with the loan revision. Equal allocation must be a deliberate choice; unequal allocations must work.
3. Preserve one authoritative repayment schedule. Component shares and final-cent rules must reconcile to it. Independently pricing five small loans can produce different rounding and would be a different structure.
4. Separate allocation from money delivery. A TZS 1,000,000 allocation is not proof that a member received TZS 1,000,000. Store beneficiary, actual destination, payout reference, amount and outcome.
5. Record contributions with both “Paid by” and “Applied to.” A member can pay their share or cover another member, but the lender must reduce the shared loan exactly once.
6. Do not automatically create a new lender receivable against the covering member or the defaulting member. Any recourse between members is a separate legal relationship and is not lender income.
7. Changes in membership or leadership affect future participation. They do not erase existing liabilities or rewrite an accepted contract. Releases and substitutions need explicit authorized amendments.
8. Settlement of an allocation is not automatically settlement of the facility or release from joint liability. Show these separately.

The illustration omits a major repayment choice: the relationship between the shared-loan waterfall and member attribution. Suppose five members are each scheduled for TZS 100,000 principal and TZS 10,000 interest. The loan is due TZS 550,000; four members remit TZS 440,000. A parent-loan interest-first allocation pays TZS 50,000 interest and TZS 390,000 principal. Four independent member waterfalls would pay TZS 40,000 interest and TZS 400,000 principal. These are different accounting results.

Recommended design: calculate the authoritative shared-loan components first using the existing engine, then attribute those exact components to members under an approved, disclosed policy. Show cross-member coverage where it occurs. If the institution requires independently serviced individual debts, use Model 2. Do not hide the difference behind an “equal split” button.

### Model 2: separate loans A–E with group guarantees

Each person remains the borrower. Loan amounts, terms, approval outcomes, payment dates and balances can differ where the product allows. The group cycle links them for coordinated processing and risk monitoring.

Required business rules:

1. Each child loan passes its own identity, cash-flow, debt, security, consent and product workflow checks. Group acceptance cannot override an individual's failed assessment.
2. Define whether cycle participation is all-or-none or whether eligible members can proceed independently. Reducing a frozen cohort may change guarantee coverage and require renewed consent/review.
3. Record directed guarantees: who guarantees whose loan, which principal/interest/charges are covered, the cap, duration, trigger, consent revision and remaining commitment. Membership alone creates no guarantee.
4. A group can guarantee a loan only if its authority and capacity to give that guarantee are established. Otherwise record the actual consenting individuals.
5. Reuse individual consent delivery where appropriate, but a group leader cannot consent for everyone without a verified legal mandate. A signature image or OTP alone does not prove that the correct liability terms were accepted.
6. Underwrite own debt plus existing/new guarantee commitments. Do not count the same household or shared business income repeatedly. Reciprocal guarantees do not manufacture independent repayment capacity.
7. Five people guaranteeing all other members creates up to 20 directed relationships. Larger cohorts must be bounded; one loan approval count is insufficient to represent coverage.
8. A guarantor payment is applied to the defaulting borrower's loan with the guarantor identified as payer. A promise to cover is not cash and cannot clear arrears.
9. The group balance is a query over linked loans; do not post a group receivable in addition to the individual loans.

For a combined remittance, first identify exact amounts assigned to each loan; then each loan's existing waterfall applies. Never allocate the entire remittance independently to every selected loan. No automatic sweep from one person's money to another loan without the required authority.

The current branch policy can prohibit a guarantor from having an active loan. That can make reciprocal guarantees impossible once the first child loan is funded. The institution must explicitly approve a compatible product/qualification rule and capacity assessment before enabling this model; do not switch the existing branch restriction off globally to make the demonstration work.

### Model 3: TZS 10,000,000 to a group organization

The organization borrows for a shared enterprise. Its members are not automatically personal borrowers or guarantors.

Required business rules:

1. Verify the organization's identity, legal form, capacity to borrow, governing documents, borrowing resolution, beneficial ownership/control where applicable, authorized signatories and verified payout mandate.
2. Assess project/business income, expenses, existing debt and repayment timing. Individual salaries are not automatically organizational income. Shared project income cannot also be counted in full for every member.
3. Disburse to the verified authorized beneficiary destination. A leader's personal account is not a safe default for the organization's account.
4. Post one organization receivable. Member contributions are payer attribution, not separate borrower debts.
5. Project sales are business activity of the borrower, not lender revenue. The lender records the repayment it actually receives and its allocation.
6. Member guarantees, collateral pledges and personal liability require their own accepted terms. Changing officers does not transfer the loan to the new officer.
7. An agricultural project example does not establish seasonal lending support. Current repayment frequencies are WEEKLY and MONTHLY; harvest-linked, fortnightly, grace-period and irregular schedules need separate calculator, product and policy work.

## 3. Missing logic in the workflow and relationship view

| Image element | Missing distinction | Implementation consequence |
| --- | --- | --- |
| Group registration | Group identity versus membership versus a particular borrowing cohort | Effective-dated membership plus an immutable loan-cycle participant snapshot |
| Leadership roles | Group chairperson versus institution Chairperson/Board/Credit Committee | Group roles confer only scoped delegated actions; never staff claims |
| Screening | Declared, evidenced, verified, unavailable and stale data | Evidence source/date/reviewer; missing history never means a clean record |
| Digital consent | Group mandate, borrower acceptance, guarantee acceptance and staff approval | Separate records with exact revision and liability disclosure |
| Approval | Cycle coordination versus individual/facility decisions | Reuse product stages and quorum; invalidate affected approvals after material changes |
| Disbursement | Authorized instruction, provider acceptance, confirmed payment and failed payout | Explicit payout attempts/outcomes; no loan funding based solely on approval |
| Repayment tracking | Cash event, recipient loan, financial components and payer attribution | Immutable linked records; balances derived from owning posted sources |
| Missed-payment coverage | Shortfall detection, notice, guarantee invocation, promise and actual payment | A guarantee case state is distinct from a payment state |
| Overdue/default | Unpaid due amount, days past due, regulatory classification and collection action | Separate metrics and states; no automatic debt transfer or write-off |
| Accounting/reporting | Operational clearing versus mapped GL versus externally reconciled money | Identify source coverage and reconciliation status; do not claim full financial completeness |
| Channels | Portal, mobile app, mobile money and bank gateways | Independent capabilities, provider contracts, identity controls and delivery evidence |
| Group/member balances | Same debt shown at several levels | Reconciliation identities and de-duplicated portfolio measures |

The linear diagram also needs send-back, rejection, expired consent, changed amount, withdrawn participant, failed/retried payout, short/duplicate remittance, partial collection, reversal, early repayment and closed-period recovery paths. Do not put all of these into one new LoanStatus enum.

Separate state dimensions: group lifecycle; membership; cycle preparation; existing loan review; agreement/consent; guarantee commitment/call; payout; payment posting; arrears/classification. Use existing loan statuses where their meaning fits. New group states require explicit transition rules and tests.

## 4. Current-system evidence and reuse boundary

These are source observations, not new runtime verification or a claim that the current whole-project suite passes.

Java paths in the table are relative to `src/main/java/com/sacco/mvp/`; JSP paths are relative to `src/main/webapp/WEB-INF/jsp/`.

| Current source | Observed behavior | Reuse or change |
| --- | --- | --- |
| `pom.xml` | Spring Boot 4.1.0, Java 25, WAR/JSP stack | Preserve stack; no frontend migration |
| `domain/LoanApplication.java:39` | Required `applicant_member_id`; institution and first-class branch; product and financial/policy snapshots | Suitable for Model 2; shared/organization borrowers need typed identity and migration |
| `domain/LoanLedger.java:18` and `service/LoanRepaymentLedgerService.java:63` | Ledger also requires/copies individual applicant identity | Changing only the application form cannot enable Models 1/3 |
| `domain/LoanProductSetting.java`; `service/LoanProductWorkflowService.java` | Product pricing, guarantors, stage order and committee/board rules; workflow snapshots | Add an explicit structure capability; retain existing review semantics |
| `service/LoanAmortizationCalculator.java`; `service/EligibilityService.java` | Decimal schedules; required cash-flow inputs; assessment snapshot says DECLARED_NOT_VERIFIED | Reuse arithmetic; add model-specific verified assessments and exposure checks |
| `service/LoanQualificationPolicyService.java:90,243` | Legacy amount-named guarantee setting is treated as a count; active/default/count checks are not monetary guarantee underwriting | Add explicit money caps and reservation accounting; do not repurpose the legacy field |
| `domain/GuarantorRequest.java`; `service/LoanWorkflowService.java:1425` | Requests retain amounts/signatures; readiness principally counts approved guarantors plus eligibility | Reuse consent access/delivery, extend to immutable coverage and capacity; count alone cannot prove a group guarantee |
| `accounting/loans/service/LoanRecordingService.java:206,228,458` | Saving staff-recorded loans starts review; funding requires READY_FOR_DISBURSEMENT; snapshot includes STAFF_REVIEW_V1 | Follow actual current workflow, despite older direct-recording prose |
| `accounting/loans/service/LoanRecordingService.java:68,77` | Receipt locks and previews enforce a single client across loans | Add an explicit group collection use case; do not remove the client ownership guard globally |
| `accounting/service/LoanReceiptService.java:57` | One immutable receipt envelope, 1–50 distinct loan rows; delegates to loan-owned posting in one transaction | Reuse atomic envelope pattern; add payer/group/beneficiary links and update database guards |
| `accounting/batch/service/ReceiptBatchWorkerService.java:25–50` | New transaction per receipt item, frozen-plan recheck; batch can finish PARTIAL | Batch processing is not one atomic group remittance |
| `accounting/loans/service/LoanProductAccountService.java` | Frozen per-loan principal/interest account routing | Reuse approved product accounts; no GL account per member/group |
| `accounting/reconciliation/*`; `docs/sl-gl-reconciliation.md` | Focused discrepancy-only SL/GL report; not bank/provider matching | Extend supported group attribution diagnostics, keep channel reconciliation separate |
| `accounting/approval/*`; `docs/voucher-approval-workspaces.md` | Latest rules govern ordinary branch/type/amount individual or batch vouchers; specialized loan receipts remain separate | Group functionality must retain lending/loan posting safeguards, not assume ordinary voucher approval authorizes them |
| JSP `accounting/vouchers/form.jsp`, loan views and shared fragments | Existing receipt purpose/client/loan controls, resource tables and finance navigation | Extend existing task flows and shared UI components |

No dedicated group-lending model/service or `group_id`/`groupId` implementation was identified in the searched production Java and migration sources. A form JSON field or the recording form's CONFIGURED/CUSTOM loanMode would not supply these missing relationships.

The conversion checklist and early increment documents contain historical capability gaps. Later charge, prepayment, account-link, adjustment and workflow increments supersede parts of them. Do not implement from an old “not supported” sentence without checking current owning code.

## 5. Proposed domain and persistence changes

Names below are design candidates, not existing classes or applied migrations. Model 2 can ship without generalizing every historical individual borrower. Before Models 1/3, approve additive Flyway changes; never edit applied migrations or insert fake individual borrowers to satisfy non-null constraints.

| Record/relationship | Minimum contents and invariants |
| --- | --- |
| Lending group | Institution, owning branch, readable group number/name, purpose, status, identity/authority evidence, version; deactivation preserves history |
| Group membership and office mandate | Client, group, role, effective dates, appointment/exit evidence; no overlapping duplicate active membership; no retroactive contract edits |
| Group loan cycle | Group, explicit model, product/policy revision, participant snapshot, preparation state and coordination rule; financial totals reference loans rather than create debt |
| Cycle participation/loan link | Frozen client/role/assessment/consent; Model 2 links one child loan per participating borrower for the cycle; Models 1/3 link the one facility |
| Typed borrower identity | Individual, organization or legally supported joint arrangement; tenant-owned identity linked to existing Member or group identity, with explicit contractual obligors |
| Loan obligor/signatory links | Borrower/co-borrower roles and accepted liability, separately from representative/signatory mandate and staff creator; one authoritative borrower identity per loan |
| Member allocation (Model 1) | Facility, participant, gross principal share, terms, charge attribution and component schedule shares; totals conserve the parent contract |
| Guarantee agreement/commitment | Guarantor party, covered loan(s), coverage components/cap, accepted revision, capacity reservation, effective/release dates, consent evidence |
| Guarantee call/coverage case | Underlying unpaid obligation, requested amount, remaining callable cap, notices and decisions; promise/paid/cancelled states; links to actual payment allocations |
| Collection envelope and payer attribution | Institution/branch, actual payer/representative, bank/provider reference, effective date, account, total, target loan splits, optional member/coverage attribution, request identity and immutable component links |
| Payout instruction/beneficiary results | Approved beneficiary/destination, gross/net breakdown, attempt identity, provider/evidence reference, status and actual amount; allocation alone is not confirmation |

Use a minimal typed borrower identity shared by application and ledger rather than two independently editable borrower fields. Existing individuals retain `Member` identifiers. Add/backfill identity links without generating balances, new contracts or historical approvals. For new group loans, remove the individual-only non-null assumption only alongside model-specific database checks and every dependent reader's compatibility work. Keep the submitting staff/client and authorized representatives in separate fields.

Enforce institution/branch consistency on every cross-reference. Foreign keys and deferred conservation checks should protect child totals, receipt components and immutable links at commit. Required scope cannot be supplied solely by hidden form fields. Version editable preparation records; submitted agreement/participant/consent revisions are immutable. Monetary sources are append-only; corrections reference their originals.

A group is not itself a borrowing cycle. The same group can have successive cycles, changed membership and different supported products. Prevent duplicate borrower/cycle loan creation and assess overlapping cycles without assuming either that all overlap is forbidden or that it is harmless.

## 6. Application architecture and money boundaries

Add a cohesive `com.sacco.mvp.grouplending` feature with only the controller, DTO/view, service, repository and model layers needed for delivered slices. Controllers bind allowlisted requests; services own scope, validation, transactions and orchestration. Integrate through owning lending/accounting service use cases rather than accessing another component's repositories from controllers.

Likely use cases: register group; appoint representative; prepare cycle; assess participants/project; freeze agreement; accept liability; reserve guarantee capacity; submit each applicable loan; prepare payout; record confirmed funding; preview/post group collection; open/cancel coverage case; view group statement. Avoid a new generic workflow engine or parallel calculator.

### Financial invariants

1. Model 1: sum of original member principal allocations = shared original principal. Sum of member-attributed principal movements/balances = parent movements/balance. Interest, supported charges, exclusions, waivers and write-offs reconcile separately.
2. Model 2: group outstanding = sum of distinct linked loan balances. Guarantee commitments are a separate exposure measure, not extra outstanding principal. Count each loan once even when linked through multiple guarantee edges.
3. Model 3: member contributions + other identified payers = recorded organization cash receipts. Contributions never create member loan principal automatically.
4. Collection total = exact sum of target allocations, each of which equals its owning financial components. A receipt header is not another journal.
5. Every GL journal balances; original plus linked reversals reconciles to source components at the same effective date and recorded cutoff.
6. Principal/interest/charges/arrears/future interest are distinct. Arrears is a subset of debt already shown, never an extra addend to settlement.
7. Payer contribution and debt beneficiary attribution are two views of one payment. Summing both would double-count cash.

### Disbursement

Model 2 keeps full disbursement per approved individual loan. A cycle records which loans are funded, failed or awaiting funds; “cycle approved” is not “all paid.” Model 3 begins with one verified organization payout. Model 1 initially requires a defined complete funding event with proven beneficiary totals; do not emulate unsupported partial disbursement by repeatedly calling the existing full-disbursement command.

Multiple external payouts are not atomically reversible with a database transaction. Store durable instructions, use stable provider keys, confirm each outcome and recover unknown outcomes before retry. If one member's payout succeeds and another fails, show that real state. Tranches, partial debt recognition, provider clearing and suspense need their own approved posting design before enabling this variation. A staff-confirmed transfer can be the first supported channel; an unverified upload is not automatic confirmation.

### Repayment and concurrency

Keep the existing individual receipt flow intact. Add an explicit group mode/use case that validates target loans against the frozen cycle and participant/guarantee relationships, as well as institution/branch and current claims. A scoped group picker is context, not authority.

For Model 2, resolve user/payer-authorized loan splits, lock distinct loans in stable ID order, refresh the accepted preview under lock and invoke the existing repayment/charge use cases. For Model 1, aggregate the intended payment to the parent loan once, then persist member component attribution in the same transaction. Current receipt rows prohibit repeated Loan IDs: member rows must be a new attribution layer, not five duplicate rows for the shared Loan ID. For Model 3, one organization loan receives the payment, with actual payer attribution retained.

Reuse the existing interest/charge/principal allocation and retained prepayment terms, including dated earned interest and non-cash future-interest exclusions. Do not add a “principal first” UI setting or collect future interest in advance. An early contributor in Model 1 cannot silently rewrite the other participants' obligations; the contract's allocation/coverage policy governs attribution.

Use institution/branch/channel event uniqueness and actor/payload-bound request keys. Reserve monetary guarantee capacity transactionally, including concurrent approvals across different loans. Do not rely on an unlocked sum-then-insert. Release reservations through audited expiry/rejection/amendment rules, with no automatic release from an informal group exit. Avoid a single institution-wide posting lock.

One physical remittance covering a bounded group must have one atomic posting envelope: all target loan postings and attribution commit or none do. Existing background receipt batches can retain independently committed items and PARTIAL outcomes, but must not be labelled atomic group remittances. If a group exceeds the initial bound, reject/narrow the operation or deliver a separately designed staged cash/clearing mechanism; never silently break the guarantee of one receipt.

Keep external calls and document generation outside financial transactions. Notifications use a committed outbox and stable delivery identities. Failed receipt PDF generation allows a document retry without re-posting.

### Corrections, excess money and arrears

Preserve the existing independent, latest-unreversed correction restrictions. A whole group receipt correction must restore every linked loan component, payer/member attribution, coverage payment, exclusion and journal together. Later activity on one included loan can block the entire correction: show the precise dependency. Do not offer partial envelope reversal or silent reallocation as a workaround. A more flexible historical reallocation facility is separate work.

Do not auto-post an ambiguous or excess payment against an arbitrary member. The current engine does not support unapplied credit/refunds. Before an inbound channel can accept such money, define and implement an approved identified liability/suspense, matching, reallocation and refund lifecycle. A confirmed bank credit must not disappear because loan posting failed. Until that exists, restrict the pilot to confirmed, exactly attributable, supported amounts and retain an explicit external exception procedure.

Calculate unpaid due amounts and oldest unpaid due date from actual allocations. Preserve “member shortfall,” “facility arrears,” “guarantee called,” “coverage promised” and “coverage received” as distinct facts. In Model 2, determine applicable regulatory group treatment without changing unrelated individual contract terms or falsely marking every borrower defaulted. Show contractual and regulatory risk views separately where required. A late coverage payment can cure current arrears while leaving historical delinquency visible. Write-off does not mean payment or forgiveness.

## 7. Accounting event matrix

Illustrative entries below assume no charges and the existing mapped collection basis. The institution accountant must approve exact account purpose and recognition. A scheduled interest amount is not automatically a GL interest receivable.

| Event | Debit | Credit | Detail retained |
| --- | --- | --- | --- |
| Group/cycle creation, consent, approval, allocation proposal | None | None | Identity, revision, actor, evidence; no cash or receivable |
| Model 1 confirmed TZS 5,000,000 funding | Loan principal posting account 5,000,000 | Actual money account 5,000,000 | One facility; five allocation/payout links; no extra member receivable journals |
| Model 2 five confirmed TZS 1,000,000 loans | Loan principal 1,000,000 per loan | Actual money 1,000,000 per loan | Five owning loan postings; cycle totals are aggregation |
| Model 3 confirmed TZS 10,000,000 funding | Loan principal 10,000,000 | Actual money 10,000,000 | Organization borrower and authorized beneficiary |
| TZS 110,000 collection: 10,000 interest + 100,000 principal | Actual money 110,000 | Retained interest account 10,000; loan principal 100,000 | Underlying loan, actual payer, receipt, exact components |
| Guarantor makes that payment | Same as ordinary loan collection | Same as ordinary loan collection | Original borrower's loan, guarantor payer and called commitment; no second receivable |
| Member-to-member promise or payment that never reaches lender | None | None | Evidence/case information only; lender balance unchanged |
| Loan payment reversal | Original credits become debits | Original debit becomes credit | Linked independent reversal, original accounts and matching subledger restoration |
| Early-settlement future-interest exclusion | No invented cash entry | No invented cash entry | Separate exclusion evidence; preserve the approved original contract |

Reuse existing charge assessment/collection rules when enabled: management assessment to deferred liability, insurance to payable, collection against assessed receivable, and gross principal versus net funding. Do not treat deductions as a second cash receipt. For one shared loan, member fee shares partition its actual charge total; the five allocations cannot trigger five copies of a single facility management fee. Separate Model 2 agreements need their own approved fee treatment. Do not add a new “group guarantee fee.”

Product accounts remain the GL routing dimension. Groups, cycles, member allocations and guarantees are subledger/report dimensions; avoid creating a GL account for every group/member. Principal account bindings and approved account-version evidence remain frozen for historical postings.

New product accounts may remain pending under the institution's account-version approval configuration. Show that prerequisite and keep group funding unavailable until the required account versions are effective; generating account proposals does not approve them or give a group officer account-review authority.

Extend the discrepancy report to detect missing/duplicate loan source links, missing group attribution, component-total mismatches and GL differences. Show attribution discrepancies separately from monetary book differences. Matching SL/GL numbers do not prove that a bank/provider settled the money. Full channel reconciliation, historical openings, interest-accrual recognition and retired accounting workspaces are separate scope; do not recreate them under this plan.

## 8. Accounting UX proposal

These are requirements for future implementation, grounded in current source/templates. They are not claims of a screenshot-based audit of the running accounting UI.

### A. Group record and lending preparation

Add **Groups** under the existing lending navigation. One compact branch-scoped register: Group No., Name, Branch, Members and Status, with filters and native blue identifying links. Group roles stay within that record. Do not add a second global navigation menu or put the register on a dashboard.

Group details use an identity/mandate summary and distinct in-place sections/tabs for Members, Loans, Guarantees and History. Model 1 adds Allocations; Model 3 includes organization/project evidence. Do not show empty technical tabs for unsupported capabilities.

Cycle preparation is a focused form: Group and Product → Participants/borrower and amounts → Evidence and liabilities → Review. The selected product fixes the permitted model. Show the borrower and liability meaning before acceptance; do not let a presentation toggle silently convert existing loans to another model. Require fresh accepted evidence after material changes.

### B. Group collections inside Receipt vouchers

Retain the explicit Loan repayment purpose. Add a simple **Individual / Group** choice only where group capability and claims are enabled. Capture Group/Cycle, **Paid by**, Date, Reference, Receiving account and Amount. Keep GL routing derived from the loans; staff should not manually choose a principal/interest pair for each member.

For Model 2, show a compact editable grid of Client, Loan ID, Due amount and Amount received; show the server preview's Interest, Charges, Principal and Balance after. For Model 1, show member contribution/allocation attribution beneath the one facility context; for Model 3, show the organization loan and optional identified contributors. Make **Own payment / Cover another member** explicit when relevant and contractually allowed.

Always reconcile Total received, Total allocated and Difference across the entire server-owned draft, not just the visible page. A mismatch blocks posting with a field-linked error. Never replace the actual received amount with the expected collection total. Preview must show any cross-member benefit produced by the Model 1 parent waterfall.

Use Preview and the authorized final Post action. Recheck mutable balances and preview identity on Post. Preserve entered rows on failure; show the affected loan and a short remedy such as “Balance changed. Preview again.” After commit, display one receipt and the authoritative result. Keep payment, approval and posting states separate; do not route specialized collections through ordinary voucher approvals by accident.

Example preview for Model 2, no charges:

| Paid by | Applied to | Received TZS | Interest TZS | Principal TZS | Coverage |
| --- | --- | ---: | ---: | ---: | --- |
| Asha | Asha / Loan A | 110,000 | 10,000 | 100,000 | Own payment |
| Neema | Juma / Loan B | 110,000 | 10,000 | 100,000 | Covers Juma |
| Total | Two loans | 220,000 | 20,000 | 200,000 | One cash event |

The eventual workspace aligns all headings/values at the leading edge; the Markdown table above is only a planning example. Asha/Juma/Neema are fictional.

### C. Group and individual statements

Group statement summary: borrower/model, currency, as-of date, original principal, principal outstanding, due/earned interest, assessed charges, overdue amount and next due date. Show future interest separately and provide a dated settlement amount only from the owning engine. Do not add overdue amount to principal/interest/charges a second time.

Model 1 shows allocation shares, contributions, beneficiary applications and outstanding attribution. Model 2 shows separate borrower loans and an aggregate once. Model 3 shows the organization debt and payer contributions, without inventing personal balances.

Keep schedules separate from posted transaction history. Show a compact trace from receipt to payer/target allocation, owning loan posting and authorized GL lines. Include reversals and coverage history; inaccessible borrower/GL details remain protected. PDF/print must preserve the same financial meaning. Group operational reports do not inherit the financial-statement or payroll Excel exceptions.

### D. Coverage and exceptions

Use a focused group/loan collection view with Due date, Overdue amount, Days late, Guarantor, Remaining cover and case state. Open a selected shortfall to show notice evidence, liability terms and actual received cover. A promise has no paid indicator. Avoid sending the full group's personal credit histories to every member or exposing delinquency in bulk SMS.

Keep row-specific errors close to the row; selection-based registers can show concise details in the shared split pane. Unknown ledger opening, missing accounts, revoked consent/mandate, changed balance and unavailable channel evidence are actionable restrictions, not zero amounts.

Prioritize a read-only allocation/SL/GL discrepancy view before expanding channel automation. A matching badge must state the supported comparison rather than claim external reconciliation.

### E. Shared UX and accessibility contract

Use the existing navy topbar, white text sidebar, light gray workspace, framed white sections, compact controls, local fonts and resource-table components. No visual redesign or framework migration is needed. Use familiar English/Kiswahili labels, minimal routine copy, essential liability/pricing disclosures and clear errors.

Keep independent Create separate from selected-record commands. Registers use single selection, one Actions menu, native authorized blue ID links, real server sorting/paging and reset selection after query/history restoration. Editable allocation rows retain their form controls and draft ownership. Tabs/fetches stay within the document; styles and initialization precede reveal. Failed loads retain values and allow local retry.

At 360/768/1366/1920px, contain horizontal scrolling inside the table; let forms/toolbars wrap without hiding financial amounts. Keep amount/difference summaries and final action reachable, dialogs body-mounted, focus visible, errors associated and pending/posted/reversed states text-labelled. Validate both languages and keyboard flow using the existing signed-in session during implementation.

## 9. Security, data access and performance

Proposed explicit capabilities: group view/manage; cycle prepare/submit; group contract/guarantee consent; group collection view/post/reverse; coverage manage; group report view/export. Final names follow the existing UserClaim registry. They supplement existing loan/accounting claims; no automatic grants or group-leader-to-staff privilege escalation.

Every query/action/download enforces institution and owning branch. New group records have a first-class branch. Keep LoanApplication.stationId authoritative for loans even if a client/group changes branch later. Transfers need an explicit policy; default to blocking incompatible new-cycle participation rather than widening access. A guarantee/exposure aggregate across branches requires separate authorization and minimal returned data, never cross-branch registry access.

Members see their own loans, accepted liabilities and necessary shared-facility facts. Representatives receive only mandate-appropriate group views. Model 1 shared liability may require disclosure of facility amounts, but it does not grant access to every member's identity documents or unrelated loans. Model 3 representatives act for the organization, not by impersonating another person's login.

Use bounded server projections, aggregates and deterministic ordering. Retain current receipt limits initially; propose a maximum 50 participants/targets per interactive pilot operation, subject to product policy and measurement. Do not load full groups/portfolios for dropdowns, client-side pagination or totals. Use a constant number of batched lookup queries instead of per-member HTTP/SQL loops; financial row writes remain bounded by the transaction cap.

Candidate indexes, to justify using representative EXPLAIN plans: group registry `(sacco_id, station_id, status, id)`; effective member/group lookup; cycle and participant uniqueness; loan links by scoped cycle; commitments by scoped guarantor/state; receipt/reference uniqueness; pending payout/case state and age. Align actual index leading columns with predicates and ordering, check existing overlap, and measure write cost. Do not blanket-index every field.

Keep transactions short and locks local to affected loans/commitments in a consistent order. Use bounded background workers and durable leases for external payouts/notifications only when needed; no request-created threads, unbounded queues or remote calls while holding loan locks. Existing batch workers must remain semantically distinct from atomic remittances.

The 1,000-RPS target remains an architectural constraint and is unmeasured for group lending. Agree a route mix, read/write ratio, cohort sizes, dataset, hardware, p95/p99 and error budget; benchmark with query counts, Hikari/DB lock waits and worker backlog. Do not claim 1,000 group financial postings/second from a read-only benchmark.

## 10. Phased delivery and acceptance gates

| Phase | Complete vertical increment | Acceptance gate / dependency |
| --- | --- | --- |
| 0. Policy and verified baseline | Final structure/obligor, guarantee, repayment attribution, group-risk and payout decisions; current-route/test inventory; approved posting matrix and contract changes | Institution credit/accounting/compliance decisions recorded; unresolved capabilities disabled, not guessed |
| 1. Group registry and cycles | Scoped group/member/mandate history, bounded screens, immutable cycle participant snapshot, product model capability, explicit claims | Cross-tenant/branch and privacy tests; exit/leadership history; retries/version conflicts; no financial effect |
| 2. Model 2 lending | Individual applications linked to cycle, per-member assessment, directed guarantee terms/capacity reservation, consent/review rules, individual confirmed funding | Own/guaranteed exposure race tests; no self-guarantees; no group approval bypass; known active-loan-guarantor policy conflict handled through approved product policy |
| 3. Model 2 collections and accounting UX | Authorized multi-client group receipt, payer/beneficiary evidence, preview/recheck, atomic owning postings, full independent correction, group statement and diagnostics | Exact cash/loan/GL conservation; one-row failure rollback; retries/races/late activity; complete receipt/PDF/privacy verification |
| 4. Typed borrower and Model 3 | Additive identity migration covering application/ledger/queries/documents/security; verified organization/mandate; project assessment, one-loan funding/collection | No fake Member; individual regression; mandate revocation; no automatic member debt/guarantees; reconciliation and organization documents |
| 5. Model 1 allocations | Shared obligors/agreement, parent schedule, exact member component attribution and payout evidence, coverage and shared settlement | Parent/member cent conservation across partial/early payments, exclusions/waivers/write-offs/reversals; approved waterfall example; no duplicate Loan ID receipt workaround |
| 6. Collections and integration expansion | Lawful notices/cases, confirmed provider events, unmatched/excess money and settlement support only under separately approved design | Provider timeout/unknown/duplicate/out-of-order recovery, inbound cash preservation, privacy and reconciliation; remaining regulatory/reporting decisions resolved |

Model 2 lending and collections form the first end-to-end pilot; do not call a group registry or approval-only slice completed group lending. Model 3/1 delivery gates also include all affected statement, report, download and client ownership paths, not just creation forms. Prefer controlled institution/product enablement and small verified pilot cohorts before broader activation. Disabling new group originations must leave servicing/history available for existing group loans.

No reliable calendar estimate is assigned before phase 0 confirms policy, migration surface and channel scope. This sequence is dependency-based, not a promise that organization identity or shared allocation will be small changes.

## 11. Required verification scenarios

1. Model 1 unequal allocations, tiny amounts/final cents and five-member partial contribution example; original and remaining components always reconcile to the facility. Never sum the parent and its allocations as portfolio debt.
2. Model 2 different loan amounts/terms and outcomes; one excluded participant; expired/withdrawn guarantor; own-loan plus guarantee exposure; concurrent commitments on different loans; circular guarantees/common income; no self-guarantees or duplicated edges.
3. Model 3 invalid legal capacity/mandate, changed signatory, unauthorized destination, missing project cash flow, organization payment by a member, and no fabricated individual debt.
4. Membership changes before/after freeze; leader replacement; death/exit/deactivation; overlapping cycles; branch transfers; exact history and no automatic release.
5. Same/changed-payload retry, duplicate external reference under different group/receipt, two staff posting concurrently, serialization/deadlock recovery, revoked claims and stale preview.
6. Partial amounts, interest/charge-only collection, arbitrary supported cents, extra principal, full early repayment with future-interest exclusion, waivers, date boundaries and original terms/account routing preserved after product edits.
7. Backdated/out-of-order/closed-period payment, source-coverage unknown, failed audit/outbox row, one invalid target causing total envelope rollback, and stored cutoff/effective-date consistency.
8. Whole-receipt independent reversal, original actor denied, latest-payment dependency on one target, rejected partial reversal, exactly restored attribution/coverage/exclusions and original accounts.
9. Actual funds received but unmatched/over-limit; provider pending/unknown/failure/success; duplicate/out-of-order callback; a failed PDF/SMS never causes a second financial posting. Do not test by disabling database protections.
10. SL versus GL versus attribution differences, duplicate/missing links, unavailable historical openings, group risk without duplicate principal, moved clients and as-of history. Confirm matching books do not imply provider settlement.
11. MockMvc claims/CSRF/ownership, PostgreSQL constraints and transactional races in disposable databases, Jasper rendering, English/Kiswahili receipt/PDF/print, keyboard/mobile tables/forms, dirty-form recovery and failed local fetches.
12. Regress affected client, guarantor, manager, board, accountant/disbursement routes and login/logout, sidebar/profile/notifications. Run relevant suites and document known failures/skips; measure the agreed performance workload before capacity claims.

## 12. Decisions to resolve before dependent implementation

| Decision | Responsible owner | Safe planning position |
| --- | --- | --- |
| First commercial model and supported group legal forms | Institution credit lead and counsel | Model 2 first; other models disabled until supported |
| Actual obligors and liability limits | Counsel/credit lead | No liability inferred from membership, office or an image label |
| Group size, overlapping cycles, cross-branch participation | Credit/risk and operations | Bounded, same-branch pilot; no scope widening |
| All-or-none participation and effect of rejected/withdrawn members | Credit lead | Reassess affected commitments and consent; no automatic partial approval |
| Guarantee monetary caps and stressed capacity | Credit/risk lead | New explicit rules; retain legacy count semantics |
| Model 1 parent waterfall/member-benefit attribution | Credit lead and institution accountant | Parent components conserved; disclose who covers whom |
| Group-wide delinquency/regulatory treatment of Model 2 | Compliance and risk | Separate contract facts and regulatory classification; verify applicable perimeter |
| Organization/project repayment evidence | Credit lead and operations | Verified project capacity; no duplicated income assumptions |
| Multi-beneficiary funding/tranches and provider confirmation | Operations and institution accountant | Complete supported funding only; no invented atomic external payout |
| Unknown/excess receipts and refunds | Accountant/operations | No arbitrary allocation or lost bank credit; separate capability gate |
| Charge sharing, recognition and accounts | Institution accountant/compliance | Reuse frozen supported charges; no new group fee or speculative accrual |
| Contract/form changes and regulatory submission | Institution compliance | Record approved revisions and required regulator evidence before activation |

## 13. Official-source checks for policy design

Reviewed online on 23 October 2026. These sources inform design and identify human applicability decisions; this plan does not certify compliance or authorize new fees/recovery actions.

- [BoT published 2019 Mainland Tier 2 regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf): regulation 21 includes group lending. Regulation 42 addresses effective-date crediting, allocation priority and early repayment. Regulation 44(3) addresses the whole group loan becoming past due when a member defaults and others do not cover the amount. Regulation 55 requires written guarantor liability disclosure. Establish applicability to each proposed contract structure, particularly Model 2.
- [BoT financial consumer protection amendments, 2025](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2025061918261266.pdf), regulation 6 amending principal regulation 11(4): specifies reducing-balance interest. New group products need the applicable approved pricing, while legacy agreements/history remain preserved. Consumer-protection recovery and disclosure requirements also need review.
- [BoT fees and charges guidelines, 2024](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf), guidelines 13–17: review management-fee composition, actual third-party/insurance costs and prohibited charges. Guideline 15's settlement-fee wording must be reconciled by compliance with the repayment right and the project's no-penalty implementation; do not enable a fee from that sentence alone.
- [BoT digital lending guidance, 2024](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024082813141188.pdf), sections 3–5: includes no-objection requirements, clear pricing, Kiswahili-default operation, customer privacy and guarantor consent/disclosure. Determine applicable digital-lending approvals for the delivered channels; do not assume the portal or a recorded OTP establishes regulatory approval.
- [BoT submission guidance, 2026](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2026092118400398.pdf), sections 19–20 and Forms 13–14: describes requests for changes to lending policy and application/agreement forms. Several forms refer to 2026 non-deposit-taking regulations. The [BoT regulations index](https://www.bot.go.tz/Publications/Filter/38?lang=en) inspected still listed the 2019 Mainland text. Obtain/confirm the applicable gazetted text and directions before treating the older numbering as the final release baseline. The guide explicitly says it does not amend or supersede regulations.

## 14. Evidence and limits of this planning task

Inspected the attached image, project instructions/maintained UI skills, current core Java entities and owning loan/guarantee/recording/receipt/batch/workflow services, accounting JSP/shared navigation sources and the linked increment/checklist documents. Searched production Java, test filenames and migrations for dedicated group models/identifiers, and checked the official sources above.

Only this planning document is added. No application code, schema, permissions, business seed, account mapping or financial data is changed. Documentation/link/whitespace checks apply; compile, runtime/browser, PostgreSQL correctness, full-suite and throughput tests were not run for a documentation-only deliverable. The 1,000-RPS capacity target remains unproven. Implementation phases require their own evidence and local commits under AGENTS.md.
