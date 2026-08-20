# Docker: 20 concurrent users, lowest AWS cost

This app needs a JVM, JSP, Hibernate, PostgreSQL, and occasional PDF/Excel. Twenty people using it at once is a **2 GB box** with the database on that same host. Smaller than 2 GB OOMs on export. RDS, load balancers, and a 4 GB instance are extra cost, not extra capability at this size.

## Cheapest complete host

**Amazon Lightsail Linux 2 GB with public IPv4** (2 vCPU, 60 GB system disk) in **eu-central-1 (Frankfurt)** or **us-east-1**: **$12/month**. Public IPv4 is included in that bundle. Do **not** pick the IPv6-only 2 GB plan ($10) to save $2.

The $12 instance is the compute floor (~TSh 380,000/year). New uploads and logos are **local files** (`APP_UPLOADS_FILES_ROOT`, `APP_SACCOS_FILES_ROOT`), not S3 and not PostgreSQL BYTEA. The viable yearly total therefore also includes extra storage:

| AWS resource | Why |
| --- | --- |
| Extra Lightsail SSD **20 GB** ($2/month) | Live Postgres data + `/var/lib/saccos-lms/uploads` + logos; detachable from the 60 GB OS disk |
| Lightsail snapshots (~$1.50/month) | Daily restore of instance + extra disk |
| S3 Standard (~$0.50/month) | Off-box `pg_dump` + tar of uploads |

**Complete AWS year: about $192 / TSh 507,000** (at TSh 2,640/USD). That is the minimalist viable total with IPv4 and extra storage. An ALB (~$18/month) or RDS (~$12–15/month) does not fit this budget.

Do not use:

- t4g.nano / Lightsail 512 MB — JVM will not start usefully
- t4g.micro / Lightsail 1 GB — pages may work until one Excel export
- IPv6-only Lightsail — this app needs a public IPv4
- RDS + a tiny app host — RDS alone is ~$12/month, so you pay twice and get less RAM for the app
- T4g CPU credits **unlimited** — surplus credits can exceed the instance price (EC2 only; keep **standard** if you ever use t4g)

Lightsail 2 GB is **x86**. Build the default image (`docker compose -f docker-compose.prod.yml up --build`). Use `--platform linux/arm64` only on Graviton (EC2 t4g). Put **Cloudflare (free)** in front for HTTPS and static `/css` `/js` `/images` caching.

Mount the extra disk at `/mnt/saccos-data` and bind it over the compose volumes (`postgres` data, `uploads`, `saccos`) so live files are not only on the included OS disk.

## Production compose

Colocates the app (~1150 MB) and Postgres (~512 MB):

```bash
export POSTGRES_PASSWORD='...'
export APP_BASE_URL='https://your-domain.example'
docker compose -f docker-compose.prod.yml up --build -d
```

Lightsail is x86. On Graviton/EC2 t4g only:

```bash
docker build --platform linux/arm64 -t sacco-lms .
```

Runtime caps for 20 concurrent users:

- Tomcat **24** threads, **50** connections
- Hikari **8** (Postgres `max_connections=30`)
- Serial GC, **55%** of the app cgroup as heap
- one PDF/Excel export at a time

Postgres is not published on the public interface. Open only `8080` (or 443 on Cloudflare) to the internet.

Local development is unchanged: `docker compose up --build` (dev profile, Mailpit).

## What 20 concurrent users means

Twenty logged-in people clicking at the same time, not 1,000 requests/second. Short service transactions (`open-in-view: false`) so eight database connections are enough; extra requests wait a few milliseconds instead of holding RAM.

If you later split Postgres off-box, raise the app cgroup and `MaxRAMPercentage` — do not shrink the host below 2 GB while exports stay on the request thread.
