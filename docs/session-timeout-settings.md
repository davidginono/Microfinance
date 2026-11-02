# Session timeout settings

Platform Settings → Session timeout → Edit requires the existing platform administrator identity and `PLATFORM_SETTINGS_UPDATE` claim. The overview keeps its existing view permission.

- Session timeout remains configurable from 2 to 480 minutes, with a 30-minute default.
- Show timeout warning defaults to off, including for existing installations through V140.
- When enabled, the shared password-confirmation dialog appears during the final 60 seconds.
- When disabled, clean pages log out automatically. Changed POST forms, selected files and pending workspace document uploads still receive the confirmation dialog during the final minute. An edit made during that minute opens it with the time remaining.
- Continue requires the current password and a CSRF-protected POST. Forms and selected files stay in the current document. No automatic keepalive or form submission is introduced.
- The dialog does not extend expiry by itself. No response, a delayed browser wake after expiry or an already-expired server session leads to logout. Unsaved values are not persisted for recovery after logout.

The existing server inactivity timeout remains authoritative. Browser settings are supplied on page/workspace rendering; already-open pages receive changed settings on their next workspace load. Shared form guards supply their initialized dirty baseline, so a saved form normalized by deferred controls is not mistaken for unfinished work.

Verification: service/default/audit tests, settings authorization and CSRF tests, keepalive/logout security tests, targeted JSP compilation, and JavaScript session/form/navigation tests. Live visual checks and applying V140 to a running PostgreSQL installation remain deployment checks; no throughput capacity is claimed.
