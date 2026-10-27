# PostgreSQL and JPA Query Anti-Patterns

## Fast review table

| Anti-pattern | Why it hurts | Preferred response |
|---|---|---|
| N+1 associations | SQL count grows with rows | projection, entity graph, targeted fetch join, batch fetch |
| `findAll()` | memory/latency grows without bound | page, slice, window, limit |
| Entity list for dashboard | overfetch and dirty-checking | read-only DTO projection/aggregate query |
| Lazy loading in JSP | hidden DB calls after service | disable OSIV; prepare view model in service |
| Deep `OFFSET` | skipped rows are still processed | keyset/scroll pagination |
| `Page` everywhere | automatic count query | `Slice` when total count is unnecessary |
| `%term%` search | normal B-tree often unusable | exact/prefix search, full-text, or trigram index |
| Function on indexed column | may prevent index scan | transform parameter or add justified expression index |
| Per-row `saveAndFlush` | round trips and fsync/flush cost | batch/chunk and flush deliberately |
| Remote call in transaction | holds connection/locks while waiting | call outside transaction or use transactional outbox |
| Huge `IN` list | planning/parameter/memory cost | temp/staging table, join, chunking, bounded input |
| Read then update counter | race/lost update | atomic SQL, version, constraint, ledger |
| Multiple to-many fetch joins | Cartesian multiplication | split query/fetch plan/projection |
| Arbitrary sort property | expensive/no index and injection risk | allowlist supported sort keys |

## Repository review questions

1. Can the caller request an unbounded page size?
2. Does the method name hide an expensive join or count?
3. Does the returned entity graph contain collections not needed by the route?
4. Can the query enforce tenant/owner scope directly?
5. Is the result read-only and could a projection avoid dirty checking?
6. Is ordering stable and supported by an index?
7. Is pagination deep enough to require keyset scrolling?
8. Does a `Page` trigger a count that the UI never displays?
9. Are search predicates sargable?
10. Are writes batched without making transactions too large?

## Index evidence template

```text
Target route/query:
Current plan:
Rows estimated vs actual:
Buffers / I/O:
Current latency p95:
Proposed index:
Column order rationale:
Write/storage cost:
Overlap with existing indexes:
New plan:
New latency p95:
```

## Production diagnostics
Use a controlled combination of:

- `pg_stat_statements` for frequent/expensive normalized statements;
- `pg_stat_activity` and lock views for active/waiting sessions;
- PostgreSQL cumulative I/O/table/index statistics;
- Hikari active/pending/timeout metrics;
- application route/query timers;
- selective `auto_explain` with awareness of overhead.

Do not run destructive or write `EXPLAIN ANALYZE` statements in production merely to inspect them. Use representative staging data or wrap safe read analysis appropriately.
