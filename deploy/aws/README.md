# AWS deployment

This folder contains the one-command deployment helper for the low-cost AWS
shape used by SACCOS LMS:

- one EC2 Docker host
- one private RDS PostgreSQL database
- Caddy HTTPS using `lms.foresight.co.tz`
- configuration in AWS Systems Manager Parameter Store under
  `/saccos-lms/prod/`

Run from the repository root:

```powershell
.\deploy\aws\deploy.ps1
```

Redeploys default to `lms.foresight.co.tz` so Caddy and `APP_BASE_URL`
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
