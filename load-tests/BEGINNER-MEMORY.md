# Beginner Memory Load Test

This is the simplest Windows path for testing login traffic, page requests, and JVM memory behavior with k6. It does not require Docker or PowerShell.

Do not run this against production. Use a local or test database.

## What This Test Does

- Logs in virtual users with one test account.
- Opens common SACCO LMS pages repeatedly.
- Samples Spring Boot Actuator memory metrics while traffic is running.
- Saves a small report and a CSV timeline under `load-tests/results/`.

Using one account still creates many browser sessions because each k6 virtual user has its own cookies. For a stronger test later, create several non-MFA test accounts and rotate them in the script.

## One-Time Setup

1. Double-click `load-tests/windows/01-install-k6.bat`.
2. If Windows opens an installer or asks for permission, accept it.
3. Close the installer window when it finishes.

## Start The App

Easy option:

1. Double-click `load-tests/windows/02-start-app-for-memory-test.bat`.
2. Wait until the window says `Started SaccoLoanApplication`.
3. Leave that window open.

If you already start the app from your IDE, that is also fine. For memory metrics, start it with the `benchmark` profile and use port `8080`.

The benchmark profile exposes `/actuator/metrics` locally so k6 can read memory numbers. Normal production profiles should keep those metrics protected.

## Run The Test

1. Double-click `load-tests/windows/03-run-memory-test.bat`.
2. Answer the questions.
3. For your first run, keep the defaults:
   - account type: `staff`
   - page requests per second: `25`
   - duration: `10m`
   - maximum virtual users: `150`

After the first run works, try a heavier run:

- page requests per second: `100`
- duration: `30m`
- maximum virtual users: `400`

## Read The Result

Open `load-tests/results/beginner-memory-report.txt`.

Good first signs:

- Checks passed is above `95%`.
- HTTP failures is below `5%`.
- Heap used max percent stays below about `85%`.
- Hikari pending connections max is usually `0` or very low.
- The app still responds after the traffic stops.

Possible memory problem:

- Heap keeps rising for the whole run.
- The app becomes slower over time.
- Garbage collection pauses become large.
- The app crashes or creates a heap dump in `load-tests/results/`.

Memory rising during login is not automatically a leak. Many logged-in users mean many sessions, and sessions use memory. A leak is more likely when memory keeps rising during a long soak and does not settle after traffic drops.

## Files Created By The Run

- `load-tests/results/beginner-memory-report.txt`
- `load-tests/results/beginner-memory-summary.json`
- `load-tests/results/beginner-memory-timeseries.csv`
- `load-tests/results/gc.log`
- `load-tests/results/saccos-beginner-memory.jfr`

The `load-tests/results/` folder is ignored by Git.
