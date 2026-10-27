---
name: springboot-1k-rps-optimization
description: Design, implement, review, profile, secure, and load-test Spring Boot MVC/JSP/PostgreSQL application routes for a measured 1,000 requests/second target. Use for controllers, services, DTOs, repositories, models/entities, database queries, indexes, transactions, threads, virtual threads, async work, RBAC and claims-only authorization, security vulnerabilities, caching, observability, or performance bottlenecks.
---
# Spring Boot 1K-RPS Optimization and Clean Layering

## 1. Mission
Apply this skill whenever creating or changing a Spring Boot application component, subcomponent, route, JSP workflow, REST endpoint, service, DTO, repository, entity/model, query, scheduled job, integration, or security configuration.

The implementation must pursue all of these outcomes together:

1. A measurable path toward sustaining **1,000 requests per second** for the declared workload.
2. Bounded, efficient PostgreSQL communication.
3. Secure-by-default behavior against current web and API threat classes.
4. Deliberate thread, executor, transaction, and concurrency management.
5. Consistent feature-first **Controller → Service → Repository → Model** layering with DTOs at boundaries.
6. Observable and testable behavior with evidence instead of unsupported claims.

Do not optimize throughput by weakening correctness, authorization, financial integrity, durability, validation, auditability, or error handling.

## 2. Interpret the 1K-RPS target correctly
“Support 1K RPS” is not a code annotation, thread-count setting, or connection-pool value. It is a system-level acceptance target that is valid only for a documented workload and environment.

Before claiming or optimizing for 1K RPS, record:

- routes and their traffic percentages;
- read/write ratio;
- authenticated vs anonymous traffic;
- request and response sizes;
- database size and hot-data distribution;
- cache hit expectations;
- external API calls per route;
- application replicas, CPU, memory, JVM, network, and PostgreSQL capacity;
- p50, p95, and p99 latency targets;
- maximum acceptable error rate;
- peak duration, soak duration, and expected burst factor;
- consistency, durability, and idempotency requirements.

Use these capacity relationships as estimation tools, never as proof:

```text
in_flight_requests ≈ requests_per_second × average_response_time_seconds
concurrent_db_demand ≈ requests_per_second × db_calls_per_request × average_db_call_seconds
```

Example: 1,000 RPS at 200 ms average latency implies roughly 200 requests in flight. If each request performs two database calls averaging 10 ms, the rough concurrent database demand is 20 connections before safety margin and workload variance. Measure actual queueing and saturation before selecting any pool size.

When the user has not supplied a load profile, create a clearly labeled provisional profile. Do not silently invent one. A useful provisional gate is:

- target: 1,000 RPS for the declared route mix;
- steady test: at least 15 minutes;
- soak test: at least 60 minutes for production-critical flows;
- error rate: below 0.5%, excluding intentional 4xx validation/authentication responses;
- no increasing memory, thread, queue, connection, or lock backlog;
- p95/p99 targets explicitly agreed before declaring success.

## 3. Required workflow for every task

### 3.1 Inspect before editing
1. Read the nearest `AGENTS.md` and relevant skill instructions.
2. Identify the owning business component/module.
3. Trace the full request path: filter/security → controller → service → repository → PostgreSQL → mapper → DTO/view model → JSP/API response.
4. Inspect tests, migrations, configuration, logs, metrics, and existing conventions.
5. Identify security scope: required claim, role-derived claim bundles, tenant, SACCO, branch, owner, record, and operation.
6. Establish a baseline for performance-sensitive work.

### 3.2 Plan the smallest coherent change
State:

- affected routes and layers;
- current bottleneck or risk;
- expected improvement;
- transaction boundary;
- authorization rule;
- query/index impact;
- thread/executor impact;
- tests and measurements to run.

Keep generated code lean:

- Reuse established project components, DTOs, utilities, queries, and conventions before adding new ones.
- Add only the classes, methods, configuration, dependencies, and abstractions required by the current use case.
- Do not introduce pass-through wrappers, duplicate models, speculative extension points, one-use interfaces, generic utility layers, or defensive branches for impossible states.
- Prefer direct, cohesive code over indirection, but never remove necessary authorization, validation, transaction safety, observability, error handling, or tests to reduce line count.
- Remove imports, helpers, scaffolding, and duplication made obsolete by the change; preserve unrelated existing code unless cleanup is explicitly in scope.
- Review the final diff for avoidable surface area. If the same behavior can be expressed clearly with materially less code, simplify it before delivery.

### 3.3 Implement vertically
Implement one complete vertical slice rather than scattering incomplete classes. A slice normally contains the required controller, request/response DTOs, service operation, repository query, model changes, mapper, migration, security rule, tests, and observability.

### 3.4 Verify after editing
Run the relevant compile, unit, MVC, security, PostgreSQL integration, architecture, concurrency, and load checks. Review the final diff for query explosions, missing authorization, unsafe logging, unbounded work, thread leaks, and transaction errors.

## 4. Mandatory component architecture

### 4.1 Feature-first package structure
Every business component or subcomponent must follow this structure where the layers are actually needed:

```text
com.example.application.<component>/
├── controller/
│   ├── <Component>PageController.java
│   └── <Component>ApiController.java
├── dto/
│   ├── request/
│   │   ├── Create<Component>Request.java
│   │   └── Update<Component>Request.java
│   ├── response/
│   │   └── <Component>Response.java
│   └── view/
│       └── <Component>ViewModel.java
├── service/
│   ├── <Component>Service.java
│   └── internal/                 # only if an implementation split is justified
│       └── Default<Component>Service.java
├── repository/
│   ├── <Component>Repository.java
│   └── projection/
│       └── <Component>SummaryProjection.java
├── model/
│   ├── <Component>.java          # JPA entity or aggregate root
│   ├── <Component>Status.java
│   └── value/
│       └── <Component>Id.java
├── mapper/
│   └── <Component>Mapper.java
├── specification/               # only for justified dynamic query composition
├── validation/                  # cross-field/domain-aware validators
└── exception/
    └── <Component>NotFoundException.java
```

Do not create empty ceremonial layers. Create only classes required by the use case, but never bypass the responsibilities below.

### 4.2 Dependency direction
Allowed normal direction:

```text
Controller → Service → Repository → Model/Persistence
Controller ↔ Request/Response/View DTOs
Service ↔ DTOs/Commands/Domain Models
Mapper converts Model ↔ DTO/View Model
```

**Repository access is strictly service-only.** A repository, `EntityManager`, `JdbcTemplate`, `NamedParameterJdbcTemplate`, or raw SQL/JPQL may be injected and called only by an application or domain service inside the component that owns it. Every other caller — controller, filter, interceptor, argument resolver, scheduled job, event/message listener, mapper, validator, DTO, entity callback, exception handler, JSP, or another component — must reach persistence through a service method that owns the transaction, authorization, and tenant/SACCO/branch/record scope. Do not add a pass-through service method purely to satisfy this rule; the service method must express a real use case. Enforce the rule with an architecture test.

Forbidden:

- Controller directly calling a repository.
- Any non-service class injecting a repository, `EntityManager`, or JDBC template.
- Controller containing business calculations or transaction logic.
- JSP calling repositories or services.
- Repository deciding authorization or business workflow.
- DTO calling services, repositories, or databases.
- Entity depending on controllers, JSPs, HTTP, servlet objects, or repositories.
- Service returning mutable JPA entities to JSPs or public APIs.
- Cross-component access to another component’s internal repository or entity.

### 4.3 Controller contract
Controllers must:

- map HTTP requests and status codes/views;
- accept validated request DTOs or explicit scalar parameters;
- obtain the authenticated principal through supported Spring Security mechanisms;
- delegate exactly one business use case to a service where practical;
- convert service outcomes into response DTOs, view models, redirects, or errors;
- remain free of repository calls, transaction annotations, entity mutation, and heavy mapping loops.

Use `@Controller` for JSP pages and `@RestController` for JSON APIs. Keep page and API controllers separate when their response and security needs differ.

Controllers must not:

- use `EntityManager`;
- call `save`, `findById`, or `findAll` on repositories;
- build SQL/JPQL;
- perform remote API calls;
- start threads;
- perform file parsing or report generation synchronously when it is heavy;
- return JPA entities.

### 4.4 DTO contract
Use separate DTOs for separate trust boundaries:

- request DTO: only fields the client is allowed to submit;
- response DTO: only fields the caller is authorized to see;
- view model: data already prepared for a JSP;
- command/query object: optional service boundary for complex use cases.

Prefer immutable Java records when compatible with binding and project conventions. Validate length, format, range, collection size, and required fields. Perform business validation again in the service because DTO validation alone cannot protect invariants under concurrency.

Never bind a JPA entity directly from a request. This prevents mass assignment and unintended property modification.

### 4.5 Service contract
Services own use cases, authorization scope, business rules, transaction boundaries, idempotency, and orchestration.

Rules:

- Use constructor injection.
- Put `@Transactional` on service methods, not controllers.
- Use `@Transactional(readOnly = true)` for bounded read use cases when appropriate.
- Keep transactions short.
- Do not perform slow remote calls, email, SMS, file generation, or unbounded loops inside a database transaction.
- Re-check mutable invariants inside the transaction.
- Enforce actor/tenant/branch/SACCO/record scope before returning or mutating data.
- Return DTOs, domain results, IDs, or explicit outcomes—not live persistence graphs.
- Do not add a service interface solely by habit. Use an interface when it represents a stable port, has multiple implementations, supports module boundaries, or materially improves testability.

### 4.6 Repository contract
Repositories contain persistence access only, and only the owning component's service layer may call them.

Rules:

- Keep repositories package-visible to their component where the framework allows it, and never expose one through a public API, another component, or a non-service bean.
- Every collection query must be bounded by `Pageable`, `Slice`, `Window`, `Limit`, an explicit maximum, or a proven small invariant.
- Prefer DTO/interface projections for list pages, dashboards, exports, and reports.
- Query within authorization scope: for example, `findByIdAndSaccoId(...)`, not global `findById(...)` followed by a late check.
- Use `exists...` when only existence is needed.
- Use count queries only when the total is truly required.
- Declare explicit `countQuery` for complex paged queries when derivation is expensive or incorrect.
- Use entity graphs or targeted fetch joins for known association needs.
- Do not hide multi-step business workflows in repository default methods.
- Native SQL requires a clear reason, parameters, projection mapping, tests, and PostgreSQL plan review.

### 4.7 Model/entity contract
Models must represent valid domain state and persistence mappings.

Rules:

- Use `BigDecimal` for money with explicit precision, scale, and rounding rules.
- Use database constraints for final invariant protection.
- Use `@Version` for ordinary optimistic concurrency where lost updates are possible.
- Keep `equals`/`hashCode` safe for persistence identity and mutable fields.
- Avoid `EAGER` collections.
- Avoid large object graphs in `toString`, logs, JSON, and JSP rendering.
- Do not expose secrets or internal audit fields unintentionally.
- Keep entity callbacks small and deterministic; do not use them for service or remote work.

## 5. Database performance route

### 5.1 Query budget
For every hot route, define and verify a database-call budget. Start with a desired goal such as:

- simple lookup or command: 1–3 SQL statements;
- list page: 1 content query plus an optional count query;
- dashboard: a small fixed number of aggregate/projection queries;
- no query count that grows with returned row count.

The exact budget depends on the use case, but **O(N) database round trips per response are forbidden**.

### 5.2 Heavy query and ORM patterns to avoid
Codex must actively detect and remove these patterns:

1. **N+1 selects** caused by looping over lazily loaded associations.
2. Lazy database access during JSP rendering or JSON serialization.
3. `findAll()` or unbounded `List<T>` queries on growing tables.
4. `SELECT *` or complete entity loading when only a few columns are needed.
5. Multiple repository calls for data that can be fetched in one well-designed projection.
6. Repeated lookup of the same reference data in one request.
7. `saveAndFlush()` or `flush()` inside per-row loops.
8. Per-row inserts/updates instead of measured JDBC/Hibernate batching.
9. Huge persistence contexts in imports, exports, and scheduled jobs without chunking and clearing.
10. Deep offset pagination; large `OFFSET` still requires skipped rows to be computed.
11. `Page` when only “has next” is needed; use `Slice` or keyset/scrolling where appropriate.
12. Expensive derived count queries on joins, groups, or distinct results.
13. Sorting by arbitrary user-provided, non-indexed columns.
14. Leading-wildcard search such as `LIKE '%term%'` without intentional full-text or trigram indexing.
15. Functions or casts on indexed predicate columns that prevent index use.
16. Low-selectivity indexes added without workload evidence.
17. Composite indexes with the wrong leading column order.
18. Too many overlapping indexes, increasing write and storage cost.
19. Large `IN (...)` lists generated from unbounded input.
20. Correlated subqueries repeatedly scanning large tables when a join, aggregate, or precomputation is more suitable.
21. Fetch-joining multiple to-many collections and multiplying result rows.
22. Collection fetch joins combined with pagination unless verified safe and efficient.
23. Chatty read-modify-write sequences that can be expressed atomically.
24. Long-running transactions holding locks while doing network or CPU-heavy work.
25. Database connections held while rendering JSPs because Open EntityManager in View remains enabled.
26. Connection pool size copied from internet examples without database and replica budgeting.
27. Speculative caching that hides a bad query while introducing stale or cross-tenant data.

Default to `spring.jpa.open-in-view=false` for production JSP applications unless the project has a documented reason and tests for keeping it enabled. Services must fully prepare view models before returning to controllers.

### 5.3 Index design rules
For each proposed index, document:

- exact target query;
- predicate columns;
- join columns;
- ordering;
- selectivity/cardinality;
- leading column order;
- whether a partial index is appropriate;
- whether `INCLUDE` can support an index-only scan;
- overlap with existing indexes;
- write/storage cost;
- before/after `EXPLAIN (ANALYZE, BUFFERS)` evidence in a safe environment.

Do not add indexes to every foreign key or filter automatically without workload analysis. Do not use a large collection of partial indexes as a substitute for correct partitioning or schema design.

### 5.4 Transactions, locks, and concurrency

- Use database uniqueness, foreign keys, check constraints, and not-null constraints as final guards.
- Prefer optimistic locking for normal concurrent edits.
- Use `SELECT ... FOR UPDATE` or pessimistic locks only around a proven contention point and only for the minimum rows and duration.
- Acquire locks in a consistent order.
- Treat deadlocks, serialization failures, and optimistic-lock exceptions as expected concurrency outcomes; retry only bounded, idempotent operations.
- Avoid table locks for ordinary workflows.
- For balances, counters, quotas, and inventory-like values, use atomic updates, ledgers, constraints, or compare-and-set semantics rather than an unsafe read-modify-write.
- Use idempotency keys and unique constraints for retried commands.

### 5.5 Connection-pool rules
Spring Boot normally prefers HikariCP when available. Pool tuning must be derived from PostgreSQL capacity and measured demand.

Required checks:

- total maximum pool size across all replicas and background workers;
- PostgreSQL connection reserve for administration, migrations, jobs, and failover;
- connection acquisition wait time;
- active, idle, pending, timeout, and usage duration metrics;
- transaction duration and queries per transaction;
- database CPU, I/O, lock waits, and active session count.

Never solve pool waiting by increasing the pool until PostgreSQL saturates. The correct fix may be query reduction, shorter transactions, caching, admission control, fewer application DB calls, or scaling PostgreSQL.

## 6. Application bottlenecks to avoid

- Synchronous remote calls without connect/read/overall timeouts.
- Retrying external calls on the request thread without a strict budget.
- CPU-heavy PDF, spreadsheet, image, encryption, compression, or report generation in request threads.
- Parsing unbounded uploads in memory.
- Returning unbounded JSON or rendering thousands of JSP rows.
- Logging full request/response bodies at high volume.
- Excessive debug logging in production.
- Repeated object mapping and formatting inside nested loops.
- Global synchronized blocks or coarse locks.
- Shared mutable singleton state.
- Unbounded in-memory caches, maps, queues, or collections.
- Loading entire files or database results when streaming/chunking is possible.
- Automatic retries that amplify an outage.
- Missing backpressure/admission control for expensive operations.
- Performing work after the client disconnects without a business reason.

Move slow, retryable, non-interactive work to a bounded background workflow or durable queue. Return an operation ID/status where appropriate. Preserve transactional handoff using an outbox pattern when database state and messaging must remain consistent.

## 7. Threading and executor policy

### 7.1 Default concurrency model
For JSP + JPA/PostgreSQL applications, use Spring MVC’s servlet model by default. WebFlux is not a universal performance upgrade and must not wrap blocking JPA/JDBC work.

### 7.2 Virtual threads
Virtual threads may improve concurrency for workloads dominated by blocking I/O, but they do not make PostgreSQL, external services, CPU, locks, or connection pools unlimited.

Before enabling them:

- use Java 21 or later and a supported Spring Boot version;
- benchmark the same workload before and after;
- inspect pinned virtual threads and carrier-thread utilization;
- keep database and remote-call concurrency bounded;
- monitor virtual-thread metrics, queueing, memory, and downstream saturation;
- do not use virtual threads to launch unbounded work.

### 7.3 Executor rules

- Never call `new Thread(...)` in application code.
- Never create an executor per request.
- Avoid `Executors.newCachedThreadPool()` and unbounded queues.
- Avoid the common `ForkJoinPool` for request-critical work.
- Avoid `parallelStream()` inside controllers/services unless a benchmark proves benefit and thread-pool interaction is controlled.
- Every `@Async` use must name a configured executor.
- Separate executors by workload class when isolation is needed: CPU work, external I/O, scheduled jobs, and notifications.
- Bound concurrency, queue capacity, task duration, and rejection policy.
- Emit active-thread, queued-task, rejection, and task-duration metrics.
- Apply timeouts and cancellation where safe.
- Preserve or deliberately reconstruct MDC/correlation context.

### 7.4 Spring proxy and context rules

- `@Async`, `@Transactional`, and `@Cacheable` are commonly proxy-based; self-invocation can bypass the behavior. Refactor into a separate bean rather than relying on `this.method()`.
- A transaction does not automatically move to another thread.
- A JPA `EntityManager`/Hibernate session and managed entities must not be shared across threads.
- Spring Security context is usually thread-bound; use supported delegation mechanisms or pass the minimum authenticated actor information explicitly.
- Do not pass mutable entities to asynchronous tasks. Pass immutable IDs and reload authorized state in the task.

## 8. Security and vulnerability policy
Review every route against OWASP Top 10:2025 and OWASP API Security Top 10:2023, with special attention to the controls below.

### 8.1 Broken access control, IDOR, and BOLA

- Deny by default.
- Authentication is not authorization.
- Check required claim/authority and object scope server-side.
- Query by both object ID and authorized tenant/SACCO/branch/owner scope.
- Protect function-level actions such as approve, reverse, export, invite, and delete.
- Never rely on hidden buttons, disabled inputs, URLs, or UUID unpredictability.
- Add allowed and forbidden tests, including cross-tenant access.

### 8.1.1 Claims-only RBAC architecture

When creating or changing RBAC, make every pre-authorization check claim-based:

- Use atomic claims/authorities such as `member.read`, `loan.approve`, or `iam.user.disable` as the only values in `@PreAuthorize`, route-security rules, service guards, and frontend permission checks.
- Do not hard-code role names in pre-authorization expressions. Avoid `hasRole(...)`, `hasAnyRole(...)`, `principal.role == ...`, and equivalent role-name checks for business authorization.
- Treat each role as a named aggregate of claims used for assignment, administration, defaults, and reporting. A role grants access only through the effective claims it contributes.
- Support customized roles as per-user claim bundles or per-user role compositions inside the claims-only architecture. Do not design custom roles as new global code-level roles that require new `@PreAuthorize` role checks.
- Use an access matrix as the source of truth for mapping business capability, action, scope, required claim, default role bundles, and risky-operation controls such as maker-checker or extra approval.
- Keep object scope separate from claims. A claim means the actor may attempt an operation; services and repositories must still enforce tenant, SACCO, branch, owner, and record scope.
- If JWTs, sessions, or `GrantedAuthority` values carry permissions, emit effective claims/authorities derived from the user's assigned claim bundles and roles, not role names used as authorization gates.
- Refresh or invalidate sessions/tokens after claim changes, and define how revocation, cache invalidation, and audit logs work for permission updates.
- Test RBAC by claims: a user with the required claim is allowed regardless of role name, a role without the claim is denied, per-user custom claims affect only the intended user/actions, and cross-scope access remains forbidden.

### 8.2 Mass assignment and property exposure

- Accept request DTOs, never entities.
- Allowlist mutable fields.
- Separate create/update/approve DTOs.
- Prevent clients from setting IDs, owner, tenant, role, status, balance, approval metadata, audit fields, or calculated totals unless explicitly authorized.
- Return response DTOs that omit secrets and unauthorized fields.

### 8.3 Injection

- Use bind parameters for SQL/JPQL.
- Never concatenate untrusted input into SQL, JPQL, shell commands, paths, LDAP, templates, or expressions.
- Allowlist dynamic sort fields and directions.
- Validate filenames and generate storage names.
- Avoid unsafe SpEL evaluation and dynamic class loading.

### 8.4 JSP XSS and content injection

- Escape untrusted output with `<c:out>`, Spring form/message tags, or an equivalent escaping component.
- Plain JSP EL output must not be assumed to be HTML-safe.
- Do not use `escapeXml="false"` or raw HTML rendering without a documented sanitizer and business requirement.
- Add a restrictive Content Security Policy appropriate to the application.
- Avoid inline scripts/styles where possible and use nonces/hashes if required.
- Encode for the correct context: HTML, attribute, URL, JavaScript, or CSS.

### 8.5 CSRF, sessions, and tokens

- Keep Spring Security CSRF protection enabled for browser-authenticated state-changing requests.
- Use POST/PUT/PATCH/DELETE for state changes; never mutate state through GET.
- Configure session cookies as Secure, HttpOnly, and appropriate SameSite.
- Rotate session identifiers on authentication and enforce logout invalidation.
- Use Spring Security password encoders; never store plaintext, reversible passwords, or raw fast hashes.
- For JWTs, validate signature, issuer, audience, algorithm, expiry, not-before, and key rotation. Keep access tokens short-lived.
- Avoid browser local/session storage for credentials in server-rendered applications when secure HttpOnly cookies are available.

### 8.6 Resource consumption and abuse

- Rate-limit login, OTP, password reset, exports, searches, uploads, and expensive reports.
- Bound request body, page size, collection length, upload size, decompression ratio, and execution time.
- Add concurrency limits for expensive per-user or per-tenant operations.
- Prevent repeated or automated abuse of sensitive business flows.
- Use idempotency for retried mutations.

### 8.7 SSRF, XXE, files, and integrations

- Do not fetch arbitrary user-provided URLs. Use scheme/host/port allowlists and block private/link-local/metadata destinations.
- Disable XML external entities and DTD processing unless explicitly required and safely configured.
- Validate file type by content and extension, enforce size limits, scan where required, generate storage names, and store outside executable/static roots.
- Set strict timeouts, TLS validation, response-size limits, and safe retry rules for external services.

### 8.8 Misconfiguration and supply chain

- Never expose stack traces, Actuator internals, H2 consoles, debug security logs, or environment/secrets publicly.
- Use explicit production profiles and externalized secrets.
- Keep dependencies and plugins supported and patched.
- Review transitive dependencies, lock/build reproducibility, artifact provenance, and vulnerability scan results.
- Do not disable Spring Security headers globally without a documented replacement.
- Prefer `permitAll` over bypassing the security filter chain for static resources when possible.

### 8.9 Logging and exceptional conditions

- Log authentication/authorization failures, privilege changes, sensitive mutations, and abuse indicators with correlation IDs.
- Never log passwords, full tokens, session IDs, private keys, OTPs, or unnecessary personal/financial data.
- Map exceptions centrally with `@ControllerAdvice`.
- Return stable, non-sensitive errors.
- Do not swallow exceptions that affect authorization, money, audit, idempotency, or message delivery.
- Fail closed when security-critical validation or dependencies fail.

## 9. Caching policy
Cache only after the underlying query and data model are reasonable.

Every cache must define:

- owner and purpose;
- key including tenant/security scope where necessary;
- maximum size;
- TTL;
- invalidation/refresh strategy;
- consistency tolerance;
- behavior during cache failure;
- metrics for hits, misses, evictions, load time, and size.

Never cache mutable authorization decisions, passwords/tokens, or sensitive cross-tenant results without explicit threat analysis. Prevent cache stampedes for hot keys.

## 10. Observability required for 1K RPS
Use Spring Boot Actuator/Micrometer or the project’s equivalent. Capture at minimum:

- HTTP RPS by route and status;
- p50/p95/p99 route latency;
- active/in-flight requests;
- error and timeout rate;
- JVM CPU, heap, allocation, GC pauses, and thread counts;
- virtual-thread metrics when enabled;
- executor active, queue, completed, rejected, and duration metrics;
- Hikari active, idle, pending, timeout, and usage metrics;
- SQL call count and duration per route where practical;
- PostgreSQL CPU, I/O, active sessions, locks, deadlocks, temp files, and slow queries;
- cache hit/miss/eviction/load metrics;
- external dependency latency, timeout, retry, and circuit state;
- business metrics for critical operations.

Use `pg_stat_statements` to identify expensive/frequent SQL and `auto_explain` selectively where operationally appropriate. Use `EXPLAIN (ANALYZE, BUFFERS)` only in a safe environment with representative data because it executes the statement.

## 11. Testing and proof

### 11.1 Required test layers

- Unit tests for domain/service rules.
- `MockMvc` tests for controller validation, views/status, and CSRF.
- Method and request authorization tests, including claims-only RBAC checks when RBAC is involved.
- PostgreSQL integration tests with Testcontainers for queries, constraints, migrations, and transaction behavior.
- Query-count tests for hot routes where tooling exists.
- Concurrency tests for duplicate submissions, lost updates, locking, idempotency, and balance/counter changes.
- Architecture tests that prevent cross-component internal dependencies and prove that repositories, `EntityManager`, and JDBC templates are reachable only from the owning component's service layer.
- JSP rendering or end-to-end tests for critical flows.

### 11.2 Load-test sequence

1. Warm-up.
2. Baseline at low load.
3. Ramp to target.
4. Steady 1K-RPS test.
5. Spike above target.
6. Soak test.
7. Dependency degradation/failure test.
8. Recovery test.

Record:

- exact commit and configuration;
- test tool/script;
- environment and instance counts;
- dataset size;
- traffic mix;
- throughput and latency percentiles;
- error breakdown;
- CPU, memory, GC, threads, executor queues;
- connection-pool metrics;
- PostgreSQL query/lock/I/O metrics;
- identified bottleneck;
- before/after comparison.

Do not claim sustained 1K RPS from a unit test, a single endpoint on localhost, an average latency alone, or a short burst with growing queues.

## 12. Codex deliverable format
For every task that uses this skill, return:

1. **Scope and assumptions**
2. **Architecture/layer changes**
3. **Security review**
4. **Database/query review**
5. **Threading/concurrency review**
6. **Files changed**
7. **Tests and commands executed**
8. **Measured results**
9. **Remaining bottlenecks and risks**

For performance work, include a compact before/after table. If no benchmark was executed, explicitly say capacity remains unproven.

## 13. Definition of done
A route/component is complete only when:

- its package and dependency direction follow the component architecture;
- controller, DTO, service, repository, and model responsibilities are separated;
- every persistence access goes through the owning component's service layer, with no repository, `EntityManager`, or raw SQL/JPQL call from any other layer;
- entities are not exposed to clients or JSPs;
- authorization uses required claims and includes object/tenant scope;
- input and output are validated/escaped;
- CSRF/session/token handling matches the threat model;
- queries are bounded and have no row-dependent query growth;
- hot queries have representative PostgreSQL plan evidence;
- transaction and lock scope are minimal and correct;
- async/threading is bounded, observable, and context-safe;
- idempotency/concurrency behavior is tested where retries or races are possible;
- metrics expose saturation and failures;
- relevant tests pass;
- 1K-RPS claims are backed by a reproducible load test.

## 14. Invocation examples

```text
$springboot-1k-rps-optimization implement the member search route and keep it safe under 1,000 RPS.
```

```text
$springboot-1k-rps-optimization review this loan dashboard for N+1 queries, authorization flaws, and thread-pool bottlenecks.
```

```text
$springboot-1k-rps-optimization refactor the accounting component into Controller, Service, DTO, Repository, and Model layers without changing behavior.
```

```text
$springboot-1k-rps-optimization benchmark the repayment endpoint, identify the first bottleneck, fix it, and prove the before/after result.
```

## 15. Supporting references in this skill
Read only the references relevant to the current task:

- `references/architecture-contract.md`
- `references/database-query-antipatterns.md`
- `references/security-threat-checklist.md`
- `references/threading-concurrency.md`
- `references/1k-rps-validation.md`
- `references/research-sources.md`

Run `scripts/audit-springboot-route.sh <project-root>` as an initial heuristic scan when useful. Its findings are leads for human/code review, not proof of a defect.
