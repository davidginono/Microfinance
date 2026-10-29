# Local and host-independent containers

No cloud deployment is configured. The application can run directly with Java 25/PostgreSQL or through Docker.

## Local development

Set `APP_BOOTSTRAP_ADMIN_PASSWORD` privately, then run `docker compose up --build`. The app listens on 8080; local development email appears in Mailpit at 8025. Use `/system-admin/login` with `ADM001` and the temporary password, then finish the mandatory setup and sign in again with the chosen password. See [fresh-deployment onboarding](docs/fresh-deployment-onboarding.md).

## Optional production containers

`docker-compose.prod.yml` runs the application and PostgreSQL on one host, using named volumes `postgres-data`, `uploads` and `institution-files`. The database port is private. Set `POSTGRES_PASSWORD`, `APP_BASE_URL`, the first-run `APP_BOOTSTRAP_ADMIN_PASSWORD`, the encryption key and real SMTP configuration in a private deployment environment. Run `docker compose -f docker-compose.prod.yml up --build -d`. Production secure cookies require HTTPS termination.

The default image is linux/amd64; choose linux/arm64 when building for an ARM host. Runtime defaults bound the JVM heap, Tomcat threads, database connections and concurrent exports. These settings are not measured capacity evidence. Size and benchmark the actual workload before claiming throughput.

The image build installs locked frontend dependencies, rebuilds Tailwind and packages the WAR. Redeploy source changes by rebuilding the image. Keep database and upload backups in protected storage, and test restoration. Never run `docker compose down -v` unless an intentional whole-deployment reset is authorized.
