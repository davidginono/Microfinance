# SACCO Loan Workflow MVP

Server-rendered SACCO loan management system built with Spring Boot, JSP, Spring Security, and PostgreSQL.

This repository supports a multi-role SACCO workflow covering:

- member registration and login
- loan product browsing and loan application
- guarantor selection, commitment checks, approval, and reversal windows
- configurable manager, loan officer, board, accountant, and disbursement review paths
- member, staff, and admin analytics
- support conversations, notifications, incidents, audit events, and reporting
- platform SACCO registry, station access controls, user/role management, and workflow settings

## Stack

- Java 25
- Spring Boot 4.1.0
- Spring MVC with JSP/JSTL
- Spring Security with session-based authentication
- Spring Data JPA + Hibernate
- PostgreSQL
- Tailwind CSS via CDN for JSP views
- Apache PDFBox for printable document output

## Main Roles

- `MEMBER`
- `LOAN_OFFICER`
- `MANAGER`
- `BOARD`
- `CHAIRPERSON`
- `ACCOUNTANT`
- `DISBURSEMENT_OFFICER`
- `MINOR_ADMIN`
- `ADMIN`

Spring Security uses form login, server sessions, and CSRF protection. This is not a JWT-based application.

## Key Functional Areas

### Member workspace

- dashboard
- loan products
- loan applications
- guarantee requests
- guaranteed loans
- archives
- reports
- notifications
- support conversations and support archive

### Manager workspace

- dashboard
- loan review queue
- manager decision and finalization
- repayment state updates
- manager reports and settings

### Loan officer workspace

- loan review queue
- archive
- notifications
- reports

### Board workspace

- review queue
- assigned applications
- archive
- OTP-backed approval flow

### Accountant workspace

- loan payment review queue
- archive
- notifications
- reports

### Disbursement workspace

- disbursement queue
- archive
- notifications
- reports

### Staff workspace

- shared staff analytics
- staff language/settings page

### Admin workspace

- SACCO scope selection before entering the dashboard
- dashboard and database utilization
- incidents, support conversations, and notifications
- users and roles
- loan products and settings controls
- SACCO registry
- station access suspension and restoration
- outbox monitor
- event log

## Workflow Summary

The loan workflow is business-sensitive. In broad terms, the application supports:

1. member creates or edits a draft
2. member selects guarantors
3. loan is sent to guarantors
4. all required guarantors approve
5. configured staff review stages run in priority order
6. board, accountant, and disbursement steps run when required by product settings
7. approval, rejection, forfeiture, disbursement, repayment, and archival paths continue from there

Important notes:

- a member cannot keep opening parallel in-process loans while another loan is still active in workflow
- saving a draft requires the required form data and guarantor selection
- applicant and guarantor qualification policies can be configured globally and per station
- guarantor commitments can be checked against savings, active loans, guaranteed exposure, and default history
- approved guarantee requests stay visible in the live table until the reversal window closes, then move to archive
- board handoff uses the configured required board reviewer count, not a hardcoded full board size
- disbursement officer involvement is configurable per loan product when claim-based disbursement access is available

## Local Development

### Prerequisites

- Java 25
- Maven 3.9+
- PostgreSQL
- Mailpit for local email capture

### Run locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Application URL:

- [http://localhost:8080](http://localhost:8080)

Local OTP and notification emails are captured by Mailpit when it is running:

- SMTP: `localhost:1025`
- Inbox UI: [http://localhost:8025](http://localhost:8025)
- Local user: `no-reply@sacco.local`
- Local password: `sacco_dev_password`
- Local recipient override: `ginonodavid625@gmail.com`

### Compile

```bash
mvn -DskipTests compile
```

### Run tests

```bash
mvn test
```

### Build a package

```bash
mvn -DskipTests package
```

## Configuration

The app loads base configuration from:

- `src/main/resources/application.yml`

It also optionally imports a local override file from the repository root:

- `application-local.yml`

That file is ignored by Git and is the recommended place for local-only secrets such as:

- `SPRING_DATASOURCE_PASSWORD`
- `APP_UPLOAD_MIGRATION_ENABLED`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`

Example structure:

```yaml
spring:
  datasource:
    password: your-local-db-password
  mail:
    host: smtp.example.com
    port: 587
    username: your-from-address@example.com
    password: your-mail-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

Legacy filesystem uploads can be imported once after applying `V40__store_uploads_in_postgresql.sql` by starting the
application with `APP_UPLOAD_MIGRATION_ENABLED=true`. The runner verifies database size and SHA-256 before deleting each
source file, retains failures for retry, and becomes a no-op after a fully successful run.
```

Base defaults in `application.yml` include:

- datasource URL: `jdbc:postgresql://localhost:5432/sacco`
- datasource username: `postgres`
- server port: `8080`
- schema validation through `spring.jpa.hibernate.ddl-auto=validate`
- static resource caching and JSP production mode

Development-only behavior lives in `src/main/resources/application-dev.yml`. Use the `dev` profile locally when you want Hibernate schema updates, JSP development mode, devtools restart, and disabled static caching.

### Demo data seeding

Demo-data seeding is disabled by default so local database changes are not overwritten on restart.

Enable it only when you explicitly want seeded data:

```bash
APP_SEED_DEMO_DATA_ENABLED=true
```

## Docker

This repository includes:

- `Dockerfile` (default **linux/amd64**, Lightsail x86 2 GB with colocated Postgres)
- `docker-compose.yml` (local)
- `docker-compose.prod.yml` (20 concurrent users, app + Postgres on one Lightsail 2 GB IPv4 host)
- `README.Docker.md` for the lowest-cost AWS layout

Start the application and PostgreSQL together locally with:

```bash
docker compose up --build
```

Default local compose services:

- app on port `8080`
- PostgreSQL on port `5432`
- Mailpit SMTP on port `1025` and inbox UI on port `8025`

Production is **Amazon Lightsail Linux 2 GB with public IPv4** (x86 Intel, not Graviton; default image **linux/amd64**), Postgres colocated, extra **20 GB** SSD at `/mnt/saccos-data`, about **$192 / TSh 507,000** per year. Bind `/mnt/saccos-data/postgres`, `/mnt/saccos-data/uploads`, and `/mnt/saccos-data/saccos` as in `docker-compose.prod.yml`. After the disk is mounted:

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

See `README.Docker.md`. Do not use a 1 GB instance, IPv6-only, RDS, or an ALB at this size.

## Important Routes

### Auth

- `/login`
- `/register/member`

### Member

- `/app/dashboard`
- `/app/loan-products`
- `/app/loan-applications`
- `/app/guarantee-requests`
- `/app/guaranteed-loans`
- `/app/archives`
- `/app/reports`
- `/app/notifications`
- `/app/support`
- `/app/support/replies`
- `/app/support/archive`

### Loan Officer

- `/loan-officer/dashboard`
- `/loan-officer/queue`
- `/loan-officer/assigned`
- `/loan-officer/loan-applications/{id}`
- `/loan-officer/archive`
- `/loan-officer/notifications`

### Manager

- `/manager/dashboard`
- `/manager/loan-applications`
- `/manager/archive`
- `/manager/reports`
- `/manager/settings`

### Board

- `/board/queue`
- `/board/assigned`
- `/board/archive`

### Accountant

- `/accountant/dashboard`
- `/accountant/loan-applications`
- `/accountant/archive`
- `/accountant/reports`
- `/accountant/notifications`

### Disbursement

- `/disbursement/dashboard`
- `/disbursement/loan-applications`
- `/disbursement/archive`
- `/disbursement/reports`
- `/disbursement/notifications`

### Staff

- `/staff/analytics`
- `/staff/settings`

### Chairperson

- `/chairperson/manager-decisions`

### Admin

- `/admin/scope/select`
- `/admin/dashboard`
- `/admin/messages`
- `/admin/incidents`
- `/admin/support`
- `/admin/support/replies`
- `/admin/support/archive`
- `/admin/users`
- `/admin/settings-controls`
- `/admin/outbox`
- `/admin/events`
- `/admin/saccos`

## Access Rules

Current route protection is role-based:

- `/app/**` -> `ROLE_MEMBER`
- `/loan-officer/**` -> `ROLE_LOAN_OFFICER`
- `/manager/**` -> `ROLE_MANAGER`
- `/board/**` -> `ROLE_BOARD`
- `/chairperson/**` -> `ROLE_CHAIRPERSON`
- `/accountant/**` -> `ROLE_ACCOUNTANT`
- `/disbursement/**` -> authenticated non-admin users with disbursement queue access claims
- `/staff/**` -> authenticated non-admin staff in loan officer, manager, accountant, disbursement, or board roles
- `/admin/**` -> `ROLE_ADMIN`

Additional ownership and workflow checks are enforced in the service layer and authorization helpers.

Station access can also be suspended by platform admins. Suspended station users are blocked during login and existing sessions are redirected back to login with the suspension reason. Platform admins remain able to sign in and restore access.

## Admin SACCO Scope

Admins now select the SACCO they want to work with before entering the admin dashboard. That scope affects SACCO-specific admin tools such as:

- users and roles
- incidents and notifications
- settings controls
- loan products
- SACCO-specific workflow configuration
- station qualification policies
- station access controls

The selection happens through `/admin/scope/select`, and the chosen scope is stored in session for the current admin workflow.

## Printing And Documents

- printed loan applications are generated with respect to the SACCO attached to the loan itself
- printable output is not supposed to depend on whichever SACCO the current viewer happens to have selected
- uploaded loan attachments are stored under `loan-uploads/`, which is ignored by Git
- attachment viewing is served through the application so route ownership checks can be enforced

## Project Structure

### Backend

- `src/main/java/com/sacco/mvp/config` - Spring configuration, security, interceptors
- `src/main/java/com/sacco/mvp/domain` - JPA entities and enums
- `src/main/java/com/sacco/mvp/repository` - repositories
- `src/main/java/com/sacco/mvp/security` - principals, user details, auth helpers
- `src/main/java/com/sacco/mvp/service` - workflow and application services
- `src/main/java/com/sacco/mvp/web` - MVC controllers

### Views

- `src/main/webapp/WEB-INF/jsp/app` - member pages
- `src/main/webapp/WEB-INF/jsp/admin` - admin pages
- `src/main/webapp/WEB-INF/jsp/manager` - manager pages
- `src/main/webapp/WEB-INF/jsp/board` - board pages
- `src/main/webapp/WEB-INF/jsp/accountant` - accountant pages
- `src/main/webapp/WEB-INF/jsp/disbursement` - disbursement pages
- `src/main/webapp/WEB-INF/jsp/staff` - shared staff pages
- `src/main/webapp/WEB-INF/jsp/chairperson` - chairperson pages
- `src/main/webapp/WEB-INF/jsp/documents` - document and attachment views
- `src/main/webapp/WEB-INF/jsp/fragments` - shared shell fragments

Loan officer pages currently reuse the board and manager JSP templates with loan-officer-specific model labels and routes.

## Development Notes

- shared shell styling lives mostly in `header.jspf` and `sidebar.jspf`
- workflow rules are intentionally enforced in the service layer; avoid changing statuses casually
- SACCO and role boundaries matter throughout the app
- station scope is the default tenant boundary for workspace roles
- `LoanApplication.stationId` is the first-class station reference for loan workflow, reporting, archive, queue, and loan-document visibility paths
- responsiveness matters for all page changes
- modals and dropdown overlays should behave correctly above the shared shell

## Recommended Validation After Changes

When you touch workflow or UI, these areas are the most useful smoke-checks:

- login and logout
- member dashboard and loan application flow
- guarantee requests and archives
- manager queue
- board assigned/queue pages
- loan officer queue, accountant applications, and disbursement applications
- staff analytics and settings
- admin dashboard, users, settings, support, outbox, and events
- sidebar and top-nav behavior on smaller screens

## License

No license file is currently included in this repository.
