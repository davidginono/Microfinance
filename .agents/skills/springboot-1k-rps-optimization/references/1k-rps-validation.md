# 1K-RPS Validation Plan

## Workload declaration

```text
Target: 1,000 RPS
Routes and percentages:
Read/write ratio:
Authentication method:
Payload sizes:
Database size:
Hot-key distribution:
External calls:
Application replicas:
App CPU/memory:
PostgreSQL CPU/memory/storage:
Cache topology:
p95 target:
p99 target:
Error budget:
Test duration:
```

## Test stages

### Baseline
Run low load to verify correctness and establish query counts and resource cost.

### Ramp
Increase load gradually. Identify the first metric that bends: latency, CPU, GC, DB CPU, I/O, locks, pool pending, executor queue, external latency, or errors.

### Steady target
Hold 1,000 RPS long enough to reach steady state. A brief burst is not sustained capacity.

### Spike
Test short peaks above target and verify admission control, bounded queues, and recovery.

### Soak
Look for memory growth, connection leaks, thread growth, cache growth, lock buildup, autovacuum pressure, and gradual latency increase.

### Failure and recovery
Degrade PostgreSQL, cache, or external services in a safe environment. Verify timeouts, circuit behavior, queue bounds, user-facing errors, and recovery without a retry storm.

## Pass/fail signals

Pass only when:

- achieved throughput matches the route mix;
- agreed p95/p99 and error budget pass;
- no hidden queue grows continuously;
- DB pool pending is controlled;
- PostgreSQL is not in sustained overload;
- lock/deadlock/timeout behavior is acceptable;
- memory and thread counts stabilize;
- correctness/security tests still pass;
- results are reproducible.

## Result table

| Metric | Before | After | Target | Pass? |
|---|---:|---:|---:|---|
| Throughput | | | 1,000 RPS | |
| p95 latency | | | agreed | |
| p99 latency | | | agreed | |
| Error rate | | | < agreed | |
| SQL/request | | | bounded | |
| Hikari pending | | | near zero/controlled | |
| DB CPU | | | no sustained saturation | |
| App CPU | | | headroom retained | |
| Heap/GC | | | stable | |
| Executor queue | | | bounded/stable | |
