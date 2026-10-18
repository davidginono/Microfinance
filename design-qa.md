# Design QA - Receipt and payment PDFs (2026-10-12)

## Comparison target and normalization

- Source visual truth: `C:/Users/USER/AppData/Local/Temp/codex-clipboard-1b28164f-5ccb-4add-bd52-0bfbabc4a94f.png`, screenshot 3, 1536 x 1024 pixels. Each stationery panel is 768 x 1024 pixels.
- Implementation: `C:/Users/USER/Desktop/Microfinance/target/receipt-format.png` (992 x 1403) and `target/payment-format.png` (992 x 1403), rendered from the actual embedded-font PDF at 120 DPI.
- Viewport: portrait A4, 595.28 x 841.89 points. This task changes printable documents, not a website prototype; no CSS viewport or browser device scale applies.
- Density normalization: crop each reference panel, reduce the PDF raster to 768 pixels wide while preserving aspect ratio (768 x 1086), and pad the reference's bottom with white to equalize canvas height. Neither document is stretched.
- State: blank reference stationery compared with posted synthetic transactions. Actual institution, accounts, amounts, TZS currency and posting provenance intentionally replace the reference's sample data.

## Full-view and focused evidence

- Source and final implementation were opened together in `target/receipt-comparison.png` and `target/payment-comparison.png`, each 1536 x 1086 pixels, source on the left.
- Focused header/details evidence: `target/receipt-header-comparison.png` and `target/payment-header-comparison.png`. Table, totals and footer were also inspected in the full comparisons.
- Additional PDF rasters inspected: `target/loan-payment-format.png`, `target/loan-disbursement-format.png`, `target/loan-reversal-format.png`, `target/ledger-receipt-format.png`, and the first/last pages `target/voucher-preview.png` / `target/voucher-preview-last.png`.
- Current long-value evidence: `target/voucher-long-0.png` and `target/voucher-long-1.png`, the two pages from the final run. Higher numbered files from earlier iterations are stale and were excluded.

## Comparison history

1. [P1] Posting fields flowed to separate nearly empty pages in a short payment document. Fixed by measuring and keeping the posting group together, reserving its space, and correcting field spacing. Final receipt/payment and loan posting samples are one page; their values stay together.
2. [P2] Details and the transaction table sat too low relative to the reference. Shortened the account label, placed supporting evidence with narration, matched the reference's address-to-phone gap, and reduced the fixed details region. Final paired comparisons show the intended two-column details and pale grey open table.
3. [P1] Extreme branch/institution values consumed the repeated header and pushed fields into the footer. Bounded the repeated header with an explicit continuation ellipsis; retained the full values in narration. Final two-page long-value rasters show the last transaction, exact large amount and complete provenance without clipping.
4. [P2] A short disbursement's ten writing slots pushed its posting record onto a second page. Writing space now reserves room for the actual narration/totals/posting group. All loan posting types and both languages pass the one-page sample assertion.

## Findings and intentional adaptations

No unresolved P0/P1/P2 document-rendering findings remain.

- Three-column header, two-column party/payment details, method boxes, grey table headings, open rows, words/totals, narration and aligned provenance reproduce the selected layout.
- The reference logo is a placeholder. Production uses an available institution-owned image in that slot, otherwise leaves the space empty. Sample US company contacts are not invented.
- Approval/checker signatures are replaced with actual posting evidence under the existing direct-posting mandate. There are no new approval stages or fabricated signatures.
- Unknown channels/contact fields remain unselected/blank. Receipts do not invent other charges. Payment totals represent actual posted amounts, not the zero difference between equal debit and credit totals.
- Long descriptions and account snapshots can expand rows and reduce the number of blank writing slots. Required information continues onto additional pages when necessary.

## Verification and remaining gaps

- Final Maven compilation and focused run: 47 tests, zero failures/errors/skips across VoucherPdfTest, VoucherMvcTest, LoanRecordingPdfTest, LoanPostingDocumentTest, ClientLoanStatementDocumentTest, LoanRepaymentReceiptPdfServiceTest, LoanRepaymentControllerTest and LoanRepaymentLedgerServiceTest.
- Checked exact amounts/words, large values, 50 transactions, English/Kiswahili, print/download PDF responses, resource authorization, denied institution access and existing repayment behavior.
- Live browser verification of the receipt-page links remains unavailable: the in-app browser returned `ERR_BLOCKED_BY_CLIENT`, and the Chrome fallback timed out. This is a PDF-rendering pass, not a live JSP/navigation, database-integration, full-suite or throughput claim.

final result: passed

---

## Earlier QA evidence retained

# Design QA — SIMS Shell Restoration

## Comparison Target

- Source visual truth: `C:\Users\USER\AppData\Local\Temp\codex-clipboard-ba7e990d-685e-4656-a2f1-d6f545881872.png`
- Source implementation constants: `C:\Users\USER\Desktop\SACCO\frontend\src\components\WorkspaceTopbar.jsx`, `WorkspaceSidebar.jsx`, and `src\layouts\AppLayout.jsx`
- Browser-rendered implementation: `C:\Users\USER\AppData\Local\Temp\saccos-shell-implementation-1280x720-v10.png`
- Earlier mobile evidence retained for the unchanged responsive geometry: `C:\Users\USER\AppData\Local\Temp\saccos-shell-mobile-390x844.png`

## Viewport And Normalization

- Source comparison image: 2048 × 466 pixels. Its left half is the SIMS reference normalized from the supplied 150% capture; its right half documents the intended SACCOS CSS viewport treatment.
- Implementation CSS viewport: 1280 × 720 at browser density 1.
- State: expanded desktop navigation with the representative admin dashboard shell.
- Comparison scope: topbar, sidebar, context row, content offset, typography, colors, and control alignment. Route-specific dashboard content is intentionally different.
- Browser measurements: topbar 38px, expanded sidebar 220px, context row 36px, main content x=220, navigation 12px, submenu 12px, and document scroll width equal to the 1280px viewport.

## Full-View Comparison Evidence

The source visual and latest browser capture were opened together in one comparison input. The implementation now uses the SIMS major-region proportions and compact density: the same 38px utility bar, 220px expanded navigation rail, 36px context strip, and 12px navigation typography. No content is hidden by the shell and no document-level horizontal overflow is present.

## Focused Shell Evidence

A separate crop was not required because the topbar and full sidebar remain clearly readable in the paired 2048px source view and 1280px implementation capture. Computed browser evidence confirms that the station, notification, circular profile, and logout controls are all 32px high and share top `2.6667px` and bottom `34.6667px`. The profile control is 32 × 32 with a computed 50% radius.

## Required Fidelity Surfaces

- Fonts and typography: Open Sans remains the application font. Primary navigation and submenu labels are 12px/16px; group labels are 11px/16px; breadcrumb/context copy is 12px/18px. Essential labels are not truncated in expanded mode.
- Spacing and layout rhythm: expanded rail 220px, collapsed rail 56px, topbar 38px, context row 36px, 8px topbar gutters, 36px primary rows, 32px submenu rows, 32px utility controls, and 284px maximum mobile drawer all match the SIMS source components.
- Colors and visual tokens: topbar `#16191f`, sidebar `#1e272e`, border `#3b4851`, control/active fill `#25313a`, hover fill `#232e36`, muted navigation `#aab7b8`, and orange active rail now map directly to the SIMS component source.
- Image quality and asset fidelity: existing SACCOS logo and profile assets remain real application assets. No replacement imagery, generated placeholder, CSS art, or new icon system was introduced.
- Copy and content: SACCOS labels and route content remain unchanged; only the shared shell treatment was restored.
- Icons and controls: the existing application icons remain aligned inside the SIMS-sized slots. Profile, notification, station, and logout controls share one vertical center line.
- Responsiveness and accessibility: the prior 390px browser evidence remains valid because responsive geometry did not change. Mobile remains an off-canvas drawer capped at 284px. Search accepted keyboard input, utility controls were enabled, focus styling remains available, and browser console errors were empty.

## Comparison History

### Iteration 1

- Earlier finding: **P1 — shell consumed too much of the viewport.**
- Earlier evidence: the superseded shell used a 330px sidebar and 52px topbar, compressing the main workspace compared with SIMS.
- Fix: restored the SIMS constants: 220px expanded sidebar, 56px collapsed sidebar, 38px topbar, 36px context row, 12px navigation labels, and compact brand/search sizing.
- Post-fix evidence: `C:\Users\USER\AppData\Local\Temp\saccos-shell-implementation-1272x549.png`.

### Iteration 2

- Earlier finding: **P2 — later sidebar styling had drifted from SIMS.**
- Evidence: submenu rows had grown to 34px with 13px labels and enriched teal/gold states, while the source component specifies 32px rows with 12px labels and restrained dark active/hover fills.
- Fix: restored source-derived sidebar colors, 32px submenu rows, 12px submenu labels, 36px flyout rows, 240px flyout width, and matching muted/hover/active states. The circular profile fix was retained.
- Post-fix evidence: `C:\Users\USER\AppData\Local\Temp\saccos-shell-implementation-1280x720-v10.png` plus the computed browser measurements above.
- Result: no actionable P0, P1, or P2 shell findings remain.

## Verification

- Browser-rendered shared shell loaded from the repository's production styles.
- Search input, notification button, profile button, collapse button, and logout control were checked in the rendered state.
- Browser console errors: none.
- Focused shell contract test: passed.
- Full Maven suite: 347 tests passed, 0 failures, 0 errors.
- Final package: succeeded.
- Final application startup: `http://localhost:8080/login` returned HTTP 200 from process 25040.

## Follow-up Polish

- None required for the requested SIMS sidebar and topbar restoration.

final result: passed

---

# Landing Page Design QA

**Comparison target**

- Source: user-provided landing-page reference, 1672 x 941 pixels.
- Implementation: `http://localhost:8085/`, captured at a 1672 x 941 CSS-pixel viewport with device scale factor 1.
- State: anonymous visitor, default desktop state.

**Findings**

- No P0, P1, or P2 differences remain.
- [P3] The lead mark uses the closest Tabler file/invoice icon rather than the reference's bespoke document-and-coins mark. This keeps the asset crisp and licensed while preserving the intended finance-document meaning.

**Required fidelity surfaces**

- Fonts and typography: passed. Local Open Sans 400/700 matches the product system; title, supporting copy, button labels, and wrapping preserve the reference hierarchy.
- Spacing and layout rhythm: passed. The 650px two-column action row begins at approximately the same vertical position as the source; mobile changes to a single column with no page overflow.
- Colors and visual tokens: passed. Navy overlay, white foregrounds, orange launch action, neutral bordered registration action, and blue focus ring have appropriate contrast and semantic roles.
- Image quality and asset fidelity: passed. The generated 1672 x 941 hero is sharp, full-bleed, and follows the reference composition without embedded interface text. UI icons are local Tabler SVG assets.
- Copy and content: passed. Heading, introductory sentence, actions, and helper labels match the selected reference.

**Interaction and responsive checks**

- Login navigates to `/login`.
- Register navigates to `/register/member`.
- Visible keyboard focus is present on both actions.
- No browser console warnings or errors.
- No document overflow at 360, 390, 768, 1024, 1440, or 1920px widths.

**Implementation checklist**

- [x] Public root route
- [x] Authenticated root redirect preserved
- [x] Responsive desktop and mobile layouts
- [x] Accessible HTML links and focus states
- [x] Local image and icon assets
- [x] Existing login and registration flows reused

final result: passed


---

# Financial statement reference designs — 2026-10-14

## Comparison target and evidence

The seven user-supplied report images are the visual references: trial balance, balance sheet, cash flow, changes in equity, client loan statement and two account statement examples. They specify presentation, not account mappings, transactions, cooperative products or accounting policy. The shared application shell remains unchanged.

Synthetic PDF samples were rendered with Poppler and inspected at up to 1250 pixels: `target/trial-balance-format.pdf`, `target/balance_sheet-reference-format.pdf`, `target/profit_and_loss-reference-format.pdf`, `target/cash-flow-reference.pdf`, `target/equity-reference-format.pdf`, `target/account-statement-reference.pdf` and `target/loan-statement-reference.pdf`. These local ignored artifacts contain synthetic test data and are not institution reports.

## Findings and changes

- Document surfaces use serif trial/loan headings, sans-serif financial/account statements, a red equity heading with monospaced matrix, compact borderless rows, gray transaction headers, underlined totals and parenthesized financial amounts. The PDF renderer uses Times, Helvetica and Courier with the existing embedded fallback where required. Browser CSS selects corresponding local font families. Exact raster font identity cannot be established from the screenshots alone.
- The first loan PDF review found transaction text too small. Increased the Courier body and adjusted column widths; the regenerated PDF remains readable with complete transaction content. Long unavailable values can wrap in narrow metadata/document columns.
- Moved detailed source evidence to appendix pages so the financial layout stays compact. Institution, branch, period, currency and draft/final status remain visible. Real mappings, available ledger data and localized domain wording determine report content; screenshot balances and cooperative reserve percentages are not copied.
- On-screen Details links resolve a statement line's saved accounts into a paginated trial balance, then a scoped account statement. PDF/Print omits Details. Client loan statements link from the client ledger and share their data model with PDF output.
- Responsive CSS contains wide reports in horizontal scroll areas, wraps toolbar controls and stacks metadata on small screens. This is implementation evidence, not a rendered mobile acceptance result.

## Verification

- Focused acceptance: 86 tests passed, zero failures/errors/skips, including PostgreSQL mapping/snapshot scope, trial drilldowns, running balances across pages, loan ownership/opening values, output authorization, PDF-only routes and Jasper compilation of all six affected JSP pages.
- After enlarging loan transaction typography: 6 loan document/PDF tests passed.
- Final PDF checks: 21 tests passed, including layout-version compatibility, embedded fallback, the equity Courier/bold fonts and red title, PDF amount rendering and export limits.
- Rendered and inspected the synthetic PDF designs, including cash flow. Retained source artifacts are unchanged; new financial statement outputs use layout version 4.
- The browser tool timed out on both tab creation and surface inventory. Live page interactions, browser console state, and desktop/mobile screenshot comparison could not be verified. No full-suite, load-test or institution-policy approval claim is made.

final result: blocked — live browser and mobile visual acceptance remains unverified; implementation, focused checks and PDF visual inspection completed.

---

# New financial statement workspace — 2026-10-15

## References and implementation evidence

Compared the six images attached to the current implementation request: financial position (`4d16de51`), trial balance (`1ee8d5b6`), cash flow (`d0f47bab`), equity (`74226c79`), profit or loss (`409c6818`) and notes (`cf598442`). Each source image and the corresponding final PDF rendering were inspected together. Synthetic rendered PDFs, XLSX files and page PNGs are in the ignored `target/statement-workspace/{POSITION,TRIAL,CASH_FLOW,EQUITY,INCOME,NOTES}.*` files.

The new implementation uses the surviving posted ledger after the reset, at `/reports/financial/statements`. Browser acceptance used an isolated PostgreSQL database and a separate local application on port 8097. The existing application on port 8081 and its data were not restarted or modified. Normal password and OTP authentication was used; the test email sink was bound to loopback and sent no external messages.

## Visual and interaction review

- Reference families: Arial/Helvetica ordinary statements, Times trial/ledger, Courier equity matrix with a red sans-serif title. Exact raster font identity is not established from screenshots. Borderless rows, section spacing, numbered notes, comparison columns, parentheses and underlined totals preserve the reference hierarchy. Amounts align right on report paper; workspace registers retain the shared ERP alignment.
- The enclosing workspace retains the existing JSP shell, sidebar and compact controls. Reviewed rendered desktop and tablet screenshots and mobile overflow. Document widths are intentionally contained in a keyboard-focusable horizontal scroll region. No page overflow at 360, 768, 1366 or 1920px viewport widths; toolbars and tabs wrap. The shared responsive sidebar settles offscreen on smaller widths.
- Verified all six tabs, saved snapshot state, mapping search/selection/edit/save, history sorting with cleared selection, and font/density/comparison preferences. Preferences affect display only.
- Verified Investments Details opens only its captured account in the trial balance, then its ledger with the originating journal-voucher link. Current permissions and branch boundaries are checked on each read/export.
- Downloaded individual PDF/XLSX and complete six-statement PDF/XLSX packages through the real UI. The shared download handler clears the loader; no Details control or source-link column is exported. Print uses the PDF inline through the existing print handler. No physical printer job was submitted.
- Corrected findings during review: assets-total placement, report date/timezone labels, UTF-8 JSP punctuation, long trial headings and download-form integration. Browser warning/error logs were empty after the final flow.

Differences in balances, account labels, number of rows and fund columns are intentional: the screenshots are presentation references, not ledger data or institutional policy. Cash flow uses a documented direct method with operating/investing/financing sections. Cooperative savings/shares and reserve-allocation percentages were not imported. Coverage is explicitly provisional because source opening balances and reconciliation are not independently verified.

## Verification and disposition

Standard Maven compilation passed. All 22 focused calculation, export, PostgreSQL, route-reset and ledger-access checks passed, including complete six-document packages. The full suite run reported 1,028 tests, 11 failures, one error and 87 skips. Subsequently corrected the new export GET-form contract classification: download forms are not search filters. Repeating both adjusted generic table-contract classes reports eight remaining failures on existing views/assets; the new statement preview is explicitly treated as financial stationery, and its registers retain the resource-table contract. Other full-run failures concern existing dashboard/guarantor views and the loan-product controller fixture. Full-suite success and production throughput are not claimed.

No P0, P1 or P2 visual/interaction findings remain within this statement-workspace scope. The earlier blocked review above concerns the previous implementation and is preserved as history.

final result: passed — current reference-layout and local browser acceptance; accounting completeness remains explicitly provisional.

## Follow-up: settings wrapping, print clipping and tab navigation — 2026-10-15

The user's two follow-up screenshots showed split Font/Density labels and a cropped rightmost ledger column in the browser print view. The downloaded ledger PDF was inspected separately: all glyphs, including Running balance and amounts, were already inside its 841.89 × 595.28 point page. The print problem came from fitting a wide PDF iframe into a portrait browser sheet.

- Settings now keep each label and enhanced selector together. Checked rendered desktop/mobile screenshots; labels remain whole, controls stay inside the container, and the 360px document has equal client/scroll widths of 345px.
- Print now uses the same frozen typed documents through a standalone print view with named A4 page rules, repeated headings, wrapped cells and no Details/source-link column. Browser print-to-PDF produced a landscape ledger (841.92 × 594.96 points) and a complete six-page package with portrait/landscape orientations matching each statement. No physical printer job was sent. PDF and Excel download behavior remains unchanged.
- Statement tabs update the workspace through cancellable fetches. Browser document-loader identity remained unchanged across Financial position/Profit or loss switching and Back/Forward. Rapid Cash flow → Notes selection ended on Notes with no pending busy state. Export/print URLs followed the active report; display preferences were rebound after each replacement.
- Standard compile and all 24 focused checks passed, including full-ledger print selection/export authorization and PDF glyph-boundary checks. Both generic table-contract classes retain the same eight failures on existing views/assets; this follow-up introduces no additional failures there.

The test instance was restarted and normal OTP sign-in re-established. The browser tool briefly stalled during print-tab creation, then recovered; final print verification completed successfully.

final result: passed — follow-up layout, navigation and print acceptance.

## Follow-up: concise report content and dated headings — 2026-10-15

The three supplied screenshots identify verbose checks/reference IDs, duplicate breadcrumbs/headings/actions after tab navigation, and an undated Trial balance as at title.

- Moved the preview breadcrumb/header outside the replaceable tab workspace. Tab responses refresh only its record actions, keeping the shell's promoted header stable.
- Replaced technical screen wording with Draft, Save report and a collapsed Items to review list. Removed repeated source-coverage prose and internal snapshot IDs from the display. PDF, Excel and print contain one short draft notice after the figures; retained snapshots/checks and audit evidence are unchanged.
- Added the selected end date to trial-balance and financial-position titles in the shared presentation model, including English and Kiswahili. Preview, PDF, Excel and print use that same title.
- Updated artifact keys to retain previous immutable outputs while serving the simplified export layout for existing saved reports. PostgreSQL coverage verifies both versions remain retained.
- Standard compilation and all 26 focused tests passed. The rendered synthetic trial-balance PDF shows TRIAL BALANCE AS AT 31 JAN 2026, complete columns and one short footer with no technical check dump or reference UUID.

Live verification uses a clean copy of the last completed application plus the current statement changes, pinned to its existing version-65 test schema. A concurrent, unrelated receipt-batch migration currently fails on a missing gl_library_immutable function; its source and other working-tree changes are left untouched.

Live acceptance completed after normal OTP sign-in: generated an unsaved report through 15 October 2026, switched between financial position, profit or loss and trial balance, and verified exactly one breadcrumb, one page header, one Save report button and no header nested inside the replaced workspace. The trial-balance title displays 15 Oct 2026. Inspected the rendered browser screenshot and downloaded the revised PDF through the real toolbar. The saved-report action remains in the stable header, with its selected statement context refreshed on each switch.

final result: passed — concise content, stable header and dated-title acceptance. The unrelated receipt-batch migration remains outside this change.

## 2026-10-18 Approval design verification

Visual verification in progress against the four supplied reference screenshots.

## 2026-10-19 Resource ID link verification

- Added native blue ID/reference/code links in 33 admin, staff review, queue, archive and accounting resource views. Destinations reuse existing record routes and permission gates. Unavailable identifiers and users without an authorized record destination retain plain text. Updated all three project UI skills and AGENTS.md to make direct ID navigation compatible with toolbar selection.
- Compiled all 34 changed JSPs, including the previously uncommitted loan-adjustment form layout. The focused JSP/security/MVC run passed all 28 checks; shared table JavaScript passed all 35 tests. No backend or database changes were made for these links.
- In a local, isolated Tomcat fixture rendering the actual JSPs/assets with synthetic records, verified blue IDs, native click and keyboard navigation, visible focus, read-only plain-text fallback, checkbox toolbar actions, selection reset on Back, and retained links after server pagination. Checked Users and roles, manager queue and the shared board review queue, including Kiswahili. At 360px the document remained 360px wide with table overflow contained; desktop layout was checked at 1366px.
- The existing signed-in application correctly denied admin access for its current account. Synthetic fixture checks do not establish production role/branch acceptance or financial workflow completeness; existing server authorization was preserved.
