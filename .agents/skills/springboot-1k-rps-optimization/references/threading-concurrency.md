# Threading and Concurrency Reference

## Choose the model deliberately

### Spring MVC
Best default for JSP and blocking JPA/JDBC. Capacity comes from bounded request concurrency, efficient database use, short transactions, horizontal replicas, and downstream protection.

### Spring MVC async
Use `Callable`, `DeferredResult`, `WebAsyncTask`, SSE, or reactive clients only when the use case benefits. Configure a dedicated executor and timeouts. Understand security/context propagation.

### WebFlux
Use for end-to-end non-blocking I/O or streaming. Do not call blocking JPA/JDBC on event-loop threads. A separate reactive adapter/service can be justified, but mixing models casually increases operational complexity.

### Virtual threads
Useful for high-concurrency blocking I/O when supported. Still bound calls to PostgreSQL and external services. Diagnose pinning and benchmark.

## Executor design worksheet

```text
Executor name:
Workload type: CPU / blocking I/O / scheduled / notification
Expected tasks/sec:
Average and p99 task duration:
Concurrency limit:
Queue capacity:
Timeout/cancellation:
Rejection policy:
Security/MDC context strategy:
Metrics/alerts:
Shutdown behavior:
```

## Avoid

- new thread per request;
- unbounded cached pools;
- unbounded executor queues;
- common-pool `CompletableFuture` for critical work;
- `parallelStream` in request paths;
- passing managed JPA entities between threads;
- assuming `@Transactional` or SecurityContext propagates automatically;
- executor sizes copied from CPU count when tasks mostly wait on a smaller DB pool;
- silently dropping rejected tasks;
- scheduled work without cluster-wide duplicate protection.

## Concurrency correctness patterns

- Optimistic version for editing records.
- Atomic SQL for counters/quotas.
- Unique idempotency key for retryable commands.
- Consistent lock ordering.
- Short pessimistic lock only for a proven critical section.
- Durable queue/outbox for asynchronous side effects.
- Leader lock or database-backed scheduler for one-per-cluster jobs.
- Bounded retry with jitter only when semantics permit.
