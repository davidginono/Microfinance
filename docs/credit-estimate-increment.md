# Credit Estimate And Affordability Increment

Implementation date: 2026-10-01. This closes calculation and input-trust gaps, not the complete credit assessment or regulatory release requirements.

## Supported Behaviour

- Draft saves and submission refreshes calculate financial terms from the institution's product on the server. Submitted snapshot JSON cannot override principal, rate, interest, or repayment amounts.
- Quotes, estimated repayment displays, and newly generated contractual schedules share `LoanAmortizationCalculator`. Monetary arithmetic uses `BigDecimal` with DECIMAL128 intermediate precision; final monetary components use two decimal places and HALF_UP rounding.
- Reducing-balance interest uses an ordinary fixed-period annuity, annual nominal rate divided by 12 for monthly or 52 for weekly payments. Interest is computed on the remaining principal for each period. The final principal component clears the exact remaining principal.
- New weekly estimates use `ceil(months * 52 / 12)` payments. This is a documented application convention, not a claim that every calendar month contains the same number of weeks or that BoT mandates this specific formula. No daily accrual, stub-period adjustment, holidays, or business-day rolling is implemented.
- Flat-rate compatibility estimates, when the product explicitly selects them, apply the annual rate proportionally to the tenure. Explicit historical flat-interest amounts remain authoritative for legacy schedule generation. Flat-product availability still needs the approved-policy review below.
- Quotes record `DECIMAL_PERIODIC_V1`, tenure, frequency, payment count, regular payment, maximum payment, totals, and nominal rate. Disbursement checks those assessed amounts, tenure, and frequency; changes require reassessment instead of silently creating different terms.
- Monthly affordability uses the largest scheduled payment, including the final rounding adjustment. A weekly payment is normalized by `52 / 12`, rounded upward; it is not compared directly with monthly disposable income.
- When affordability is required, income must be positive, expenses and debt repayments must be explicitly declared and nonnegative, and the repayment must be positive and valid. Missing, malformed, negative, or fractional-cent values do not silently become a favourable zero. Explicit zero expenses/debt are valid.
- Assessment snapshots identify declarations as `DECLARED_NOT_VERIFIED`. Additional workflow metadata cannot overwrite assessment facts. Passing arithmetic is not evidence verification or credit approval.
- Monthly dates are anchored to the original first repayment date rather than repeatedly advancing from a shortened February date.
- The calculator rejects unsupported payment counts, invalid amounts/rates/dates, and overrides that create negative principal or settle before the last scheduled payment. The schedule is bounded to 600 payments.

## Compatibility And Limits

Existing stored schedules, posted transactions, receipts, balances, and database identifiers are not rewritten. Unversioned weekly quotes retain their legacy `months * 4` count until explicitly reassessed; newly generated legacy schedules are labelled `LEGACY_PERIOD_COUNT_DECIMAL`. They are not silently certified as the new policy.

The legacy JSON/form names and routes remain compatible. A monthly affordability amount is distinct from the actual periodic payment in review and calculator displays. New frequency/payment-basis labels and assessment feedback have English and Kiswahili messages. The wider application still has legacy language/localization gaps.

Still open before unrestricted live lending:

- Institution approval of the rate, date, rounding, grace-period and frequency conventions; controlled handling of already-approved legacy applications and accepted agreement versions.
- Approved reducing-balance products and permitted management/third-party/insurance fees. Existing separate application/processing configuration has not been automatically converted or certified.
- Verified identity, business/employment evidence, field visits, income/expense assessment, guarantor strength, and collateral records.
- Automatic inclusion of known debt servicing and overdue/default history. `activeExposure` remains informational; these checks still rely on declared debt repayments and existing workflow restrictions. Do not describe this as complete automated underwriting.
- Complete agreements and effective annual rate disclosures, repayment/fee obligations, early settlement, ledger cutover, mapped accounting, reconciliation, and regulator-facing reports.
- Operator/compliance review of applicable Mainland Tier 2 and digital-lending obligations. Zanzibar is outside this implementation's jurisdiction assumptions.

## Regulatory Context

The [BoT 2026 submission guidance](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2026092118400398.pdf), Form 1 lending-policy checklist, specifies reducing-balance interest and assessment of repayment ability. It informs the target policy; it does not prescribe this application's exact 52/12 calculation convention.

The [2019 Tier 2 regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf), regulations 38-39, require borrower financial information and contractual pricing/schedule disclosures. A calculated estimate is not a substitute for the accepted agreement or the verification of those declarations.

The [2024 fees guidelines](https://bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Guidelines/en/2024071716375097.pdf) require review of the management fee, prohibited charges and actual third-party/insurance costs. This increment does not approve existing fees or add charges.

## Verification

Targeted unit tests cover decimal annuity results, exact principal reconciliation, zero-interest final cents, large values, weekly normalization/counts, tenure-proportional flat interest, invalid cash flow, missing repayment, protected declarations, snapshot tampering, date anchoring, and disbursement term mismatches. Full build and rendered verification results are recorded in the conversion gap checklist after completion.
