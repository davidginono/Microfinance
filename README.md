# SACCO Loan Workflow MVP

Server-rendered SACCO loan management system built with Spring Boot, JSP, Spring Security, and PostgreSQL.

This repository supports a multi-role SACCO workflow covering:

- member registration and login
- loan product browsing and loan application
- guarantor approval and reversal windows
- manager review and board review
- admin operations, SACCO registry, user/role management, notifications, incidents, and reporting

## Stack

- Java 21
- Spring Boot 3.4.3
- Spring MVC with JSP/JSTL
- Spring Security with session-based authentication
- Spring Data JPA + Hibernate
- PostgreSQL
- Tailwind CSS via CDN for JSP views
- Apache PDFBox for printable document output

## Main Roles

- `MEMBER`
- `MANAGER`
- `BOARD`
- `CHAIRPERSON`
- `ADMIN`

Spring Security uses form login, server sessions, and CSRF protection. This is not a JWT-based application.

## Key Functional Areas

### Member workspace

- dashboard
- loan products
- loan applications
- guarantee requests
- archives
- reports
- notifications
- support / incidents raised to admin

### Manager workspace

- dashboard
- loan review queue
- manager decision and finalization
- repayment state updates
- manager reports and settings

### Board workspace

- review queue
- assigned applications
- archive
- OTP-backed approval flow

### Admin workspace

- SACCO scope selection before entering the dashboard
- dashboard and database utilization
- incidents and notifications
- users and roles
- loan products and settings controls
- SACCO registry
- outbox monitor
- event log

## Workflow Summary

The loan workflow is business-sensitive. In broad terms, the application supports:

1. member creates or edits a draft
2. member selects guarantors
3. loan is sent to guarantors
4. all required guarantors approve
5. manager reviews the loan
6. board reviews the loan
7. approval, rejection, disbursement, repayment, and archival paths continue from there

Important notes:

- a member cannot keep opening parallel in-process loans while another loan is still active in workflow
- saving a draft requires the required form data and guarantor selection
- approved guarantee requests stay visible in the live table until the reversal window closes, then move to archive
- board handoff uses the configured required board reviewer count, not a hardcoded full board size

## Local Development

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL

### Run locally

```bash
mvn spring-boot:run
```

Application URL:

- [http://localhost:8080](http://localhost:8080)

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
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`

Example structure:

```yaml
spring:
  datasource:
    password: your-local-db-password
  mail:
    username: your-mail-username
    password: your-mail-password
```

Base defaults in `application.yml` include:

- datasource URL: `jdbc:postgresql://localhost:5432/sacco`
- datasource username: `postgres`
- server port: `8080`

### Demo data seeding

Demo-data seeding is disabled by default so local database changes are not overwritten on restart.

Enable it only when you explicitly want seeded data:

```bash
APP_SEED_DEMO_DATA_ENABLED=true
```

## Docker

This repository includes:

- `Dockerfile`
- `docker-compose.yml`

Start the application and PostgreSQL together with:

```bash
docker compose up --build
```

Default compose services:

- app on port `8080`
- PostgreSQL on port `5432`

## Important Routes

### Auth

- `/login`
- `/register/member`

### Member

- `/app/dashboard`
- `/app/loan-products`
- `/app/loan-applications`
- `/app/guarantee-requests`
- `/app/archives`
- `/app/reports`
- `/app/notifications`
- `/app/support`

### Manager

- `/manager/dashboard`
- `/manager/loan-applications`
- `/manager/reports`

### Board

- `/board/queue`
- `/board/assigned`
- `/board/archive`

### Chairperson

- `/chairperson/manager-decisions`

### Admin

- `/admin/scope/select`
- `/admin/dashboard`
- `/admin/messages`
- `/admin/incidents`
- `/admin/users`
- `/admin/settings-controls`
- `/admin/outbox`
- `/admin/events`
- `/admin/saccos`

## Access Rules

Current route protection is role-based:

- `/app/**` -> `ROLE_MEMBER`
- `/manager/**` -> `ROLE_MANAGER`
- `/board/**` -> `ROLE_BOARD`
- `/chairperson/**` -> `ROLE_CHAIRPERSON`
- `/admin/**` -> `ROLE_ADMIN`

Additional ownership and workflow checks are enforced in the service layer and authorization helpers.

## Admin SACCO Scope

Admins now select the SACCO they want to work with before entering the admin dashboard. That scope affects SACCO-specific admin tools such as:

- users and roles
- incidents and notifications
- settings controls
- loan products
- SACCO-specific workflow configuration

The selection happens through `/admin/scope/select`, and the chosen scope is stored in session for the current admin workflow.

## Printing And Documents

- printed loan applications are generated with respect to the SACCO attached to the loan itself
- printable output is not supposed to depend on whichever SACCO the current viewer happens to have selected

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
- `src/main/webapp/WEB-INF/jsp/chairperson` - chairperson pages
- `src/main/webapp/WEB-INF/jsp/fragments` - shared shell fragments

## Development Notes

- shared shell styling lives mostly in `header.jspf` and `sidebar.jspf`
- workflow rules are intentionally enforced in the service layer; avoid changing statuses casually
- SACCO and role boundaries matter throughout the app
- responsiveness matters for all page changes
- modals and dropdown overlays should behave correctly above the shared shell

## Recommended Validation After Changes

When you touch workflow or UI, these areas are the most useful smoke-checks:

- login and logout
- member dashboard and loan application flow
- guarantee requests and archives
- manager queue
- board assigned/queue pages
- admin dashboard, users, settings, outbox, and events
- sidebar and top-nav behavior on smaller screens

## License

No license file is currently included in this repository.
