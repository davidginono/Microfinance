# SACCO LMS Benchmark And Stress Tests

These scripts measure route latency, errors, throughput, and memory behavior for the SACCO LMS without changing loan workflow data. Authenticated scripts log in once per virtual user, keep the session cookie, and fail fast if the chosen account requires MFA.

Beginner path without Docker or PowerShell: see `BEGINNER-MEMORY.md`.

## Tools

- k6 for HTTP load.
- Spring Boot Actuator for local health and metrics.
- Docker stats, GC logs, heap dumps, and JFR for memory review.

## Start The App

Docker benchmark run:

```powershell
docker compose -f docker-compose.yml -f docker-compose.benchmark.yml up --build
```

Local JVM benchmark run:

```powershell
mvn -DskipTests package
New-Item -ItemType Directory -Force load-tests/results | Out-Null
$env:SPRING_PROFILES_ACTIVE = "benchmark"
$env:JAVA_TOOL_OPTIONS = "-Xms512m -Xmx512m -XX:NativeMemoryTracking=summary -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./load-tests/results -XX:StartFlightRecording=filename=./load-tests/results/saccos-benchmark.jfr,settings=profile,dumponexit=true -Xlog:gc*:file=./load-tests/results/gc.log:time,uptime,level,tags"
java -jar target/ROOT.war
```

## Configure Credentials

Use an account that can reach the routes you are testing. The default account type is staff.

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:SACCOS_ACCOUNT_TYPE = "staff"
$env:SACCOS_USERNAME = "ADM001"
$env:SACCOS_PASSWORD = "<password>"
```

For member pages:

```powershell
$env:SACCOS_ACCOUNT_TYPE = "member"
$env:SACCOS_USERNAME = "<member-number>"
$env:SACCOS_PASSWORD = "<password>"
$env:SACCOS_ROUTES = "/app/dashboard,/app/loan-products,/app/loan-applications,/app/guarantee-requests"
```

For staff pages:

```powershell
$env:SACCOS_ROUTES = "/admin/dashboard,/admin/users,/admin/settings-controls"
```

## Run k6

Install k6 locally, or use Docker:

```powershell
winget install k6.k6
```

Health smoke test, no credentials needed:

```powershell
k6 run load-tests/k6/health-smoke.js
```

Authenticated smoke test:

```powershell
k6 run load-tests/k6/saccos-smoke.js
```

Stress test:

```powershell
$env:SACCOS_TARGET_RPS = "100"
$env:SACCOS_MAX_VUS = "400"
k6 run load-tests/k6/saccos-stress.js
```

Soak test for memory stability:

```powershell
$env:SACCOS_TARGET_RPS = "50"
$env:SACCOS_SOAK_DURATION = "60m"
$env:SACCOS_MAX_VUS = "300"
k6 run load-tests/k6/saccos-soak.js
```

Docker k6 alternative:

```powershell
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 -e SACCOS_ACCOUNT_TYPE=$env:SACCOS_ACCOUNT_TYPE -e SACCOS_USERNAME=$env:SACCOS_USERNAME -e SACCOS_PASSWORD=$env:SACCOS_PASSWORD -e SACCOS_ROUTES=$env:SACCOS_ROUTES -v ${PWD}/load-tests:/scripts grafana/k6 run /scripts/k6/saccos-smoke.js
```

## Watch Memory

Docker:

```powershell
docker stats
Get-Content -Wait load-tests/results/gc.log
```

Local JVM:

```powershell
jps -l
jcmd <pid> GC.heap_info
jcmd <pid> VM.native_memory summary
jcmd <pid> JFR.dump filename=load-tests/results/manual-dump.jfr
```

Actuator metrics are exposed by the benchmark profile. In a logged-in browser session, open:

```text
http://localhost:8080/actuator/metrics/jvm.memory.used
http://localhost:8080/actuator/metrics/jvm.gc.pause
http://localhost:8080/actuator/metrics/jvm.threads.live
http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

## Pass Signals

- k6 check rate stays above 95%.
- HTTP failures stay below 5%.
- p95 and p99 stay within the selected threshold.
- Heap used after GC rises early, then stabilizes during the soak.
- GC pauses do not climb steadily.
- Thread count and Hikari pending connections stay bounded.
- Docker container memory does not keep rising until the memory limit.
