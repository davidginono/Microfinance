# Accounting branch consolidation — 2026-10-05

The user requested consolidation into local `main` and deletion of the remaining local branches. The current delivery branch was fast-forwarded into `main`. The retained export/release implementation at `8e60013` was selected as the active application when resolving the overlapping accounting foundations.

The October 5 policy/operational-builder foundation and the retained implementation use incompatible policy schemas, migration versions, component names and page routes. Running both would create competing accounting implementations. The active `src` tree therefore uses the complete retained export/release lineage. The alternative foundation commits remain in merged Git history; their completed worktrees and the unfinished business/reporting drafts remain available. No unfinished worktree files are discarded by branch deletion.

The older retained branch at `0469788` contains earlier versions of files already present in the export/release lineage. Its ancestry is consolidated while retaining the later implementation. The alternative ledger checkpoint `878532b` is also retained as development history; it is not installed as a second general ledger.

This consolidation does not establish completion of the accounting checklist. Existing phase evidence identifies unfinished advanced accounting, integration and release work. Institution-approved policies, mappings, verified opening balances and accountant/compliance/staff acceptance remain unavailable. Accounting activation and final release remain gated.

Verification results for this consolidation are recorded below. Earlier evidence retains its original scope and is not relabeled as a new acceptance run.

The saved October 5 top-up safety change was ported into the active manager workflow with its regression test. An unverified legacy snapshot cannot mark a source loan paid or disburse the top-up. The separately approved financial settlement workflow remains a required implementation gate.

The initial `mvn clean test` completed with 1,174 cases, one failure, no errors and 223 opt-in skips. The failure identified missing shared table-region registration on the three newly retained source-opening JSPs. Those pages now register their table regions and retain existing server-side pagination. This failed run is recorded as diagnosis, not acceptance.

After the repairs and top-up guard were applied, `mvn '-Dtest=AwsConsoleViewContractTest,ManagerServiceTest' test` passed all 33 focused cases with no failures, errors or skips on October 5 at 12:08:47 EAT. This run recompiled the application and test sources. The full suite was not rerun after those scoped fixes, and the skipped database checks do not establish release acceptance.

`mvn -DskipTests package` succeeded on October 5 at 12:15:04 EAT and produced `target/ROOT.war`. Packaging skipped tests; the test results above are separate evidence. No deployment, remote push or remote branch deletion is part of this local consolidation.
