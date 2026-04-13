<<<<<<< HEAD
# SACCO Loan Workflow MVP (Spring Boot + JSP)

Server-rendered SACCO loan workflow MVP using:
- Spring Boot 3.x, Java 21
- Spring MVC + JSP/JSTL
- Tailwind CSS (CDN) for all UI pages
- Spring Security (form login, session, CSRF)
- Spring Data JPA + Hibernate
- PostgreSQL
- JPA schema auto-update + startup data seeding
- i18n English/Kiswahili

## Run with Docker

```bash
docker compose up --build
```

Open: http://localhost:8080

## Seeded Login Accounts
Password for all users: `password`

- Manager: `MGR001`
- Board: `BRD001`, `BRD002`, `BRD003`
- Members: `MEM001` ... `MEM008`

## Main URLs

- `/login`
- `/app/dashboard`
- `/app/loan-products`
- `/app/loan-applications`
- `/app/guarantee-requests`
- `/manager/loan-applications`
- `/board/assigned`

## Security/RBAC

- `/app/**`: any authenticated member (`ROLE_MEMBER`)
- `/manager/**`: `ROLE_MANAGER` or `ROLE_ADMIN`
- `/board/**`: `ROLE_BOARD` or `ROLE_ADMIN`
- Ownership and assignment checks enforced with `@PreAuthorize` + `AuthzService`.

## Tests

```bash
mvn test
```

Includes tests for:
- Eligibility ratio (1/3 rule)
- Loan advance guarantor skip logic
- Board quorum (2 of 3)
- Ownership/assignment authorization checks
=======
# SACCOS_LMS
Loan Management System for SACCOS
>>>>>>> 0f2163f16b4afdc56931cf281bfe3bcb3e198c76
