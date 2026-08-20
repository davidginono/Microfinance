# Docker: 20 concurrent users, lowest AWS cost

This app needs a JVM, JSP, Hibernate, PostgreSQL, and occasional PDF/Excel. Twenty people using it at once is a **2 GB box** with the database on that same host. Smaller than 2 GB OOMs on export. RDS, load balancers, and a 4 GB instance are extra cost, not extra capability at this size.

## Cheapest complete host

**Amazon Lightsail Linux 2 GB with public IPv4** (x86 Intel, 2 vCPU, 60 GB system disk) in **eu-central-1 (Frankfurt)** or **us-east-1**: **$12/month**. Public IPv4 is included in that bundle. Do **not** pick the IPv6-only 2 GB plan ($10) to save $2, and do not switch this app to IPv6-only.

The $12 instance is the compute floor (~TSh 380,000/year). New uploads and logos are **local files** (`APP_UPLOADS_FILES_ROOT=/var/lib/saccos-lms/uploads`, `APP_SACCOS_FILES_ROOT=/var/lib/saccos-lms/saccos`), not S3 and not PostgreSQL BYTEA. The app has no AWS SDK live-storage path. The viable yearly total therefore also includes extra storage:

| AWS resource | Why |
| --- | --- |
| Extra Lightsail SSD **20 GB** ($2/month) | Live Postgres data + uploads + logos; detachable from the 60 GB OS disk |
| Lightsail snapshots (~$1.50/month) | Daily restore of instance + extra disk |
| S3 Standard (~$0.50/month) | Off-box `pg_dump` + tar of uploads (operational dumps only) |

**Complete AWS year: about $192 / TSh 507,000** (at TSh 2,640/USD). That is the minimalist viable total with IPv4 and extra storage. An ALB (~$18/month) or RDS (~$12–15/month) does not fit this budget.

Do not use:

- Lightsail 512 MB — JVM will not start usefully
- Lightsail 1 GB — pages may work until one Excel export
- IPv6-only Lightsail — this app needs a public IPv4
- RDS + a tiny app host — RDS alone is ~$12/month, so you pay twice and get less RAM for the app
- Graviton/EC2 t4g as the default — Lightsail 2 GB is **x86**. If you ever run t4g as an alternative, keep CPU credits **standard**; **unlimited** surplus credits can exceed the instance price

Default compose/image build is **linux/amd64**. Use `--platform linux/arm64` only on Graviton. Cloudflare (free) is optional in front for HTTPS and static `/css` `/js` `/images` caching.

## Extra disk and bind mounts

Attach the extra **20 GB** Lightsail SSD and mount it at `/mnt/saccos-data` so live files are not only on the included OS disk. `docker-compose.prod.yml` bind-mounts:

| Host path | Container path | Used for |
| --- | --- | --- |
| `/mnt/saccos-data/postgres` | `/var/lib/postgresql/data` | Postgres data |
| `/mnt/saccos-data/uploads` | `/var/lib/saccos-lms/uploads` | `APP_UPLOADS_FILES_ROOT` |
| `/mnt/saccos-data/saccos` | `/var/lib/saccos-lms/saccos` | `APP_SACCOS_FILES_ROOT` |

On the Lightsail VM (once, after attaching the disk):

```bash
# Identify the extra disk (not the 60 GB OS volume), then format only if it is new:
lsblk
sudo mkfs.ext4 /dev/nvme1n1   # replace with the extra-disk device; skip if already formatted
sudo mkdir -p /mnt/saccos-data
sudo mount /dev/nvme1n1 /mnt/saccos-data
# Persist by UUID in /etc/fstab, then:
sudo mkdir -p /mnt/saccos-data/postgres /mnt/saccos-data/uploads /mnt/saccos-data/saccos
```

## Production compose

Colocates the app (~1150 MB cgroup) and Postgres (~512 MB). Postgres is not published on the host. No Mailpit in prod.

```bash
export POSTGRES_PASSWORD='...'
export APP_BASE_URL='https://your-domain.example'
docker compose -f docker-compose.prod.yml up --build -d
```

Lightsail is x86; that command builds **linux/amd64**. On Graviton/EC2 t4g only:

```bash
docker build --platform linux/arm64 -t sacco-lms .
```

Runtime caps for 20 concurrent users:

- Tomcat **24** threads, **50** connections
- Hikari **8** (Postgres `max_connections=30`)
- Serial GC, **55%** of the app cgroup as heap
- one PDF/Excel export at a time

Open only `8080` (or 443 if Cloudflare or another TLS terminator is in front) to the internet.

Local development is unchanged: `docker compose up --build` (dev profile, Mailpit). Do not use `docker-compose.prod.yml` for local work.

## Snapshots and S3 dumps (operational)

Keep Lightsail automatic snapshots of the instance and the extra disk. Also copy dumps off-box; this does not put live files in S3.

Example crontab on the VM (adjust bucket, credentials, and the compose service name):

```cron
# Daily Postgres dump (gzip) to S3
15 2 * * * docker compose -f /opt/saccos-lms/docker-compose.prod.yml exec -T db pg_dump -U sacco sacco | gzip | aws s3 cp - s3://YOUR_BUCKET/saccos-lms/pg/sacco-$(date -u +\%Y\%m\%d).sql.gz
# Daily tar of local uploads and SACCO files
30 2 * * * tar -C /mnt/saccos-data -czf - uploads saccos | aws s3 cp - s3://YOUR_BUCKET/saccos-lms/files/files-$(date -u +\%Y\%m\%d).tar.gz
```

Install the AWS CLI on the host for those dumps if you use S3. Do not add an AWS SDK to the application for this.

## What 20 concurrent users means

Twenty logged-in people clicking at the same time, not 1,000 requests/second. Short service transactions (`open-in-view: false`) so eight database connections are enough; extra requests wait a few milliseconds instead of holding RAM.

If you later split Postgres off-box, raise the app cgroup and `MaxRAMPercentage` — do not shrink the host below 2 GB while exports stay on the request thread.
