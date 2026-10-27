# Architecture Contract

## Purpose
Use this reference when creating or refactoring a component. This is a pragmatic layered, feature-first architecture. It borrows dependency-direction principles from Clean Architecture, but it does not pretend that naming folders `model`, `service`, `controller`, `dto`, and `repository` alone creates a clean system.

## Layer responsibility matrix

| Layer | Accepts | Produces | Owns | Must not own |
|---|---|---|---|---|
| Controller | HTTP request, validated DTO, principal | view, redirect, response DTO/status | transport mapping | business rules, SQL, transactions |
| DTO | external/view data | immutable validated values | boundary shape | persistence behavior, service calls |
| Service | DTO/command/query + actor context | result DTO/domain outcome | use case, authorization, transaction | HTTP rendering, SQL strings |
| Repository | typed persistence criteria from the owning service only | entity/projection/optional/page | database access | workflow, authorization decision, non-service callers |
| Model | valid domain/persistent state | domain behavior/state | invariants and mappings | HTTP, JSP, repositories |
| Mapper | model/DTO | model/DTO | explicit transformation | database calls, authorization |

## Repository access rule
Repository access is strictly service-only. A repository, `EntityManager`, `JdbcTemplate`, `NamedParameterJdbcTemplate`, or raw SQL/JPQL is injected and called only by an application or domain service inside the component that owns it.

Everything else — controllers, filters, interceptors, argument resolvers, scheduled jobs, event and message listeners, mappers, validators, DTOs, entity callbacks, exception handlers, JSPs, and other components — reaches persistence only through a service method that owns the transaction, authorization, and tenant/SACCO/branch/record scope.

This exists so transaction boundaries, authorization scope, query budgets, and audit remain in one enforceable place. It is not a naming convention: do not add a pass-through service method purely to satisfy it. If a caller needs data, model the use case on the service. Enforce the rule with an architecture test rather than review alone.

## Component dependency rule
A component may expose a small public application API to other components. Other components must not import its internal controller, repository, JPA entity, mapper, or implementation package.

Suggested module layout:

```text
com.example.sacco.loan/
├── api/                     # public interfaces/events used by other modules
├── controller/
├── dto/
├── service/
├── repository/
├── model/
├── mapper/
└── internal/                # optional implementation details
```

## Vertical slice checklist
For a new use case such as “approve loan”:

1. `ApproveLoanRequest` contains only user-controlled fields.
2. Controller accepts request and authenticated actor.
3. Service checks the required claim and SACCO/branch/record scope.
4. Service starts a short transaction.
5. Repository loads the record within scope and with the required lock/version semantics.
6. Domain/service validates current state transition.
7. Audit/idempotency records are written atomically where required.
8. Service returns `LoanApprovalResponse` or a domain outcome.
9. Controller redirects or returns a response.
10. Tests cover success, validation, missing claim, role without claim, wrong SACCO, stale version, duplicate command, and concurrent approval.

## Naming
Use business-language names:

- `LoanApprovalService`, not `CommonService`.
- `MemberStatementRepository`, not `DataRepository`.
- `CreateRepaymentRequest`, not `GenericDto`.
- `LoanSummaryViewModel`, not `ObjectResponse`.

## Service interfaces
Do not generate `FooService` + `FooServiceImpl` automatically for every class. Use an interface when:

- it is a module/public port;
- more than one implementation is realistic now;
- an adapter boundary exists;
- it materially improves testing or replacement.

Otherwise a final cohesive service class is acceptable.

## Mapper rules
Prefer explicit mapping for security-sensitive data. Mapping libraries are acceptable when configuration is testable and does not silently expose new entity fields. Never use reflection-based “copy all properties” for request-to-entity updates.
