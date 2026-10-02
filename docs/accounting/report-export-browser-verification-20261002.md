# Report export interaction verification

Disposable loopback PostgreSQL/browser fixtures only, 2026-10-02. No institution approval inferred.

Keyboard-selected report columns were previewed, saved and independently published in English and Kiswahili. Self-publication was rejected. Both published layouts rendered at 360/768/1366/1920 pixels without page overflow or JavaScript errors.

Actual consecutive CSV/XLSX/PDF downloads returned HTTP 200 with correct media types and attachment headers. Every download left zero table loaders and zero busy table regions; PDF bytes began with %PDF. English-layout bytes: 880/4266/11847. Kiswahili-layout bytes: 938/4290/12144. Source recorded cutoffs and layout language were retained. Published run pages inherit the institution shell language, which was Kiswahili during this final export check; these are two layout-language checks, not a new whole-shell language matrix.

The first attempt exposed a persistent loader after XLSX. Shared download recognition and explicit builder download metadata fixed it; the successful checks used the canonical JS copied into the disposable runtime classpath and the same JSP change. Print, populated financial equivalence, all builder controls and full H10 remain unverified.
