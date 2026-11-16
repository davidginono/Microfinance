# Loan product document settings

Create and Edit Loan Product share one Supporting documents form. Require application attachments controls individual application papers, including signed guarantor consent when the recorded loan requires guarantors. With the setting off, retained document names are inactive for review validation. Disbursement proof remains a separate funding setting. Unchecked boxes bind to false.

Optional document names and file limits (up to 25 MB, ten names) save in the product transaction. Reordering or changing a size retains a document ID; renaming creates a new ID. Removing a name deactivates its definition and keeps historical references. Failed saves retain submitted values.

Before the first review, submission reads the institution-scoped current document setting under a product read lock, without refreshing financial terms. V162 freezes the checked rules in the existing append-only review request. Later review rounds retain those rules. Historical review requests keep their original guarantor-consent gate. Group signed agreements and separate guarantor evidence gates remain mandatory.

Verification uses the opt-in isolated PostgreSQL recording fixtures, MVC/security tests, Jasper compilation and JavaScript interaction tests. The targeted runs passed 165 Java checks and 30 JavaScript checks. These checks establish correctness, not throughput. Runtime edit-page rendering was checked in English and Kiswahili; browser-control timeouts prevented visual viewport verification. No live loan was submitted and no live product configuration was changed.

Deployment requires the new build and V162. Follow the project's verified-backup migration procedure; do not clean a build directory used by a running application.
