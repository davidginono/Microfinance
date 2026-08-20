# Docker: 20 concurrent users, lowest AWS cost

This app needs a JVM, JSP, Hibernate, PostgreSQL, and occasional PDF/Excel. Twenty people using it at once is a **2 GB ARM box** with the database on that same host. Smaller than 2 GB OOMs on export. RDS, load balancers, and a 4 GB instance are extra cost, not extra capability at this size.

## Cheapest complete host

**Amazon Lightsail Linux 2 GB (2 vCPU, 60 GB disk, public IPv4)** in us-east-1: **$12/month**, about **TSh 380,000/year**.

That is the cost floor for this app. Do not go below 2 GB or TSh 380,000/year. Stay inside **TSh 500,000/year** (~$189): leftover covers a domain and snapshots, not a second instance or RDS.

Do not use:

- t4g.nano / Lightsail 512 MB — JVM will not start usefully
- t4g.micro / Lightsail 1 GB — pages may work until one Excel export
- RDS + a tiny app host — RDS alone is ~$12/month, so you pay twice and get less RAM for the app
- T4g CPU credits **unlimited** — surplus credits can exceed the instance price

Put **Cloudflare (free)** in front for HTTPS and static `/css` `/js` `/images` caching. Keep EC2/Lightsail CPU credit mode on **standard**.

## Production compose

Colocates the app (~1150 MB) and Postgres (~512 MB):

```bash
export POSTGRES_PASSWORD='...'
export APP_BASE_URL='https://your-domain.example'
docker compose -f docker-compose.prod.yml up --build -d
```

Build ARM when the host is Graviton or Lightsail ARM:

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
