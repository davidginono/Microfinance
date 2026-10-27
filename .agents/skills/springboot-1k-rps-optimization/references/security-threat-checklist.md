# Security Threat Checklist

## Route-level questions

- Who may call this route?
- Which atomic claim/authority is required for the action?
- Which tenant/SACCO/branch/record may they access?
- Which properties may they submit?
- Which properties may they see?
- Can the operation be repeated safely?
- Can the route be automated or abused at scale?
- Does it fetch URLs, parse XML, process files, or call external APIs?
- What sensitive data could logs/errors expose?
- What happens if a security dependency fails?

## Current web risk coverage
Review against OWASP Top 10:2025:

1. Broken Access Control
2. Security Misconfiguration
3. Software Supply Chain Failures
4. Cryptographic Failures
5. Injection
6. Insecure Design
7. Authentication Failures
8. Software or Data Integrity Failures
9. Security Logging and Alerting Failures
10. Mishandling of Exceptional Conditions

Also review API-specific risks including object-level authorization, property-level authorization, unrestricted resource consumption, function-level authorization, sensitive business-flow abuse, SSRF, inventory gaps, and unsafe consumption of APIs.

## Claims-only RBAC review

- `@PreAuthorize`, route guards, service guards, and frontend permission checks use claims/authorities only, not role names.
- Roles are named aggregates of claims for assignment and reporting; access is granted by effective claims, not by role labels.
- Custom roles are per-user claim bundles or per-user role compositions inside the claims-only model.
- The access matrix maps capability, action, scope, required claim, default role bundles, and risky-operation controls.
- Tenant/SACCO/branch/owner/record scope is enforced separately from the claim check.
- JWT/session authorities contain effective claims or authorities derived from claims, not role names used as business gates.
- Permission changes refresh/invalidate sessions or tokens and create audit events.
- Tests prove required-claim success, missing-claim denial, role-without-claim denial, per-user custom-claim behavior, and cross-scope denial.

## JSP review

- Use escaped output for user-controlled values.
- Avoid raw HTML from database/user input.
- Keep CSRF token integration for forms.
- Avoid state changes through links/GET.
- Do not render secrets into hidden fields or JavaScript.
- Use secure headers and a CSP compatible with the actual frontend.
- Do not place authorization decisions only in JSP tags.

## Authentication/session review

- Adaptive one-way password encoding through Spring Security.
- Session fixation protection and secure cookie attributes.
- Concurrent-session requirements are explicit.
- Logout invalidates server state.
- Login/reset/OTP endpoints are rate-limited and monitored.
- JWT validation covers issuer, audience, time, signature, algorithm, and rotation.

## Data and file review

- Request DTO allowlists fields.
- Server calculates status, balances, effective claims, role-derived claim bundles, ownership, and totals.
- Upload size/type/content are checked.
- Storage name is generated.
- File is outside executable/static paths.
- Download authorization is checked on every request.
- XML parsers disable external entities/DTDs unless safely required.

## Failure behavior

- Authorization uncertainty fails closed.
- Timeouts are bounded.
- Retries are bounded and safe.
- Exceptions do not disclose SQL, stack traces, internal paths, or secrets.
- Security events are logged with a correlation ID, not credentials.
