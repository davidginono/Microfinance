# AWS deployment

This folder contains two deployment paths for Microfinance LMS.

## Recommended budget path: Lightsail

Use this path for the practical lowest-cost public web deployment:

- one Amazon Lightsail Linux 2 GB instance with public IPv4
- app and PostgreSQL colocated with Docker Compose
- Caddy HTTPS for `microfinance.example.com`
- local persistent data under `/mnt/saccos-data`

The AWS CLI must be authenticated as IAM user `david`:

```powershell
aws sts get-caller-identity
```

Run from the repository root:

```powershell
.\deploy\aws\deploy-lightsail.ps1
```

Keep `APP_TIME_ZONE=Africa/Nairobi` in the deployment environment. The application
sets the JVM default timezone from this setting before creating application beans,
and converts timestamp displays to that zone. Rebuild and recreate the app
container to apply the code change. Existing timestamps with an offset are
converted for display; do not add three hours to stored database values.

The script creates or reuses:

- Lightsail instance `saccos-lms-prod`
- Lightsail static IP `saccos-lms-prod-ip`
- Lightsail key pair `saccos-lms-prod-key`

It packages the current workspace, uploads it over SSH, creates a production
`.env` on the host, then runs:

```bash
docker compose --env-file .env -f deploy/aws/docker-compose.lightsail.yml up --build -d --remove-orphans
```

Set production secrets as environment variables before running when available:

```powershell
$env:POSTGRES_PASSWORD = "..."
$env:APP_SECRETS_ENCRYPTION_KEY = "..."
$env:SPRING_MAIL_HOST = "..."
$env:SPRING_MAIL_USERNAME = "..."
$env:SPRING_MAIL_PASSWORD = "..."
$env:APP_SMS_BENTER_API_KEY = "..."
```

If `POSTGRES_PASSWORD` or `APP_SECRETS_ENCRYPTION_KEY` is omitted, the script
generates a strong value for this deployment. Keep the generated server `.env`
safe because it is needed for future recovery.

DNS for `microfinance.example.com` is currently outside Route53. After the script
prints the static IP, update the Cloudflare A record for `microfinance.example.com`
to that IP and keep the record proxied. Caddy uses an internal origin
certificate so Cloudflare can complete the origin TLS handshake without public
ACME challenges being redirected by the proxy.

## Higher-cost EC2/RDS path

`deploy.ps1` is the older stronger isolation path. It is not the $12/month
deployment because it uses:

- one EC2 Docker host
- one private RDS PostgreSQL database
- Caddy HTTPS using `microfinance.example.com`
- configuration in AWS Systems Manager Parameter Store under
  `/saccos-lms/prod/`

Run from the repository root:

```powershell
.\deploy\aws\deploy.ps1
```

Redeploys default to `microfinance.example.com` so Caddy and `APP_BASE_URL`
stay aligned with the production hostname:

```powershell
.\deploy\aws\deploy.ps1
```

The script keeps existing SSM parameters by default, then force-updates only the
cloud-derived values such as the RDS URL, RDS credentials, app base URL, and
production safety overrides. Use `-OverwriteExistingConfig` only when you want
to replace all previously stored app configuration values with the current
defaults from `src/main/resources/application.yml`.

The default RDS automated backup retention is 1 day because this AWS account's
free-tier plan rejected 7 days with `FreeTierRestrictionError`. The script also
creates an initial manual DB snapshot after the first successful deployment.
