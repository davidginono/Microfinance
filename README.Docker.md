# Docker and small EC2

## Local compose

Start the application, PostgreSQL, and Mailpit together:

```bash
docker compose up --build
```

Services:

- app on port `8080`
- PostgreSQL on port `5432`
- Mailpit SMTP on `1025` and inbox UI on `8025`

Local compose keeps the `dev` profile so HTTP session cookies work. The image itself defaults to `prod`.

## Production image

```bash
docker build --platform linux/arm64 -t sacco-lms .
```

Use `linux/arm64` for Graviton (`t4g`). Use `linux/amd64` only if the host is Intel/AMD.

The image:

- builds a custom JRE with `jlink`
- activates the `prod` profile
- listens on `8080`
- uses Serial GC and a 65% RAM heap cap
- caps Tomcat at 32 threads and Hikari at 6 connections
- allows one PDF/Excel export at a time
- exposes `/actuator/health` for the container health check

Point the container at PostgreSQL with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Do not run Postgres in the same process or on the cheapest app instance.

## Smallest honest EC2 size

This is a Spring MVC + JSP + Hibernate app with on-request PDF/Excel. Heap, metaspace, Tomcat native threads, and one export already need about 1.2–1.6 GB RSS.

| Topology | Smallest practical size | Why |
| --- | --- | --- |
| App container only, Postgres on RDS or another host | **t4g.small (2 vCPU, 2 GB)** | Fits Serial GC + 32 Tomcat threads + one export |
| App + Postgres on one box | **t4g.medium (2 vCPU, 4 GB)** | Postgres shared buffers and the JVM cannot share 2 GB safely |
| t4g.micro (1 GB) | Not for production | Can boot with a tiny heap, but one Excel export can OOM |
| t4g.nano (0.5 GB) | Not viable | Below JVM + Alpine + JSP baseline |

Prefer **t4g** over **t3**: same burst family, lower price, and the ARM image above runs natively.

Approximate us-east-1 Linux On-Demand (Aug 2026): t4g.small ~$12/month, t4g.medium ~$24/month. RDS is a separate bill and is still cheaper than upsizing EC2 enough to colocate Postgres.

This instance size is a cost floor, not a 1,000 RPS path. Sustained high throughput needs more heap, more Tomcat threads, and a larger connection pool than a 2 GB box can hold.

## Required production environment

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-host>:5432/sacco
SPRING_DATASOURCE_USERNAME=sacco
SPRING_DATASOURCE_PASSWORD=...
APP_BASE_URL=https://your-domain.example
APP_SECRETS_ENCRYPTION_KEY=...
SERVER_SERVLET_SESSION_COOKIE_SECURE=true
```

Optional overrides if you later move to a larger instance:

```bash
SERVER_TOMCAT_THREADS_MAX=80
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10
APP_REPORTS_MAX_CONCURRENT_EXPORTS=2
JAVA_TOOL_OPTIONS=-XX:+UseG1GC -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError
```
