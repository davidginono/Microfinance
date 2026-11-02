# Session timeout settings

Platform Settings → Session timeout → Edit requires the existing platform administrator identity and `PLATFORM_SETTINGS_UPDATE` claim. The overview keeps its existing view permission.

- Session timeout remains configurable from 2 to 480 minutes, with a 30-minute default.
- Show timeout warning defaults to off, including for existing installations through V140.
- When enabled, the shared password-confirmation dialog appears during the final 60 seconds.
- When disabled, browser input (typing, pointer use, scrolling and touch) renews the live session through a CSRF-protected POST to `/session/activity`. Requests are batched to at most one per 30–60 seconds, depending on the configured timeout. Script-dispatched input, focus, background visibility changes and idle timers do not renew it. The endpoint rejects renewal if warnings have been enabled or the authenticated session has expired.
- The browser's inactivity deadline advances only after a confirmed renewal, measured from the latest input included in that request. Stopping interaction therefore logs out a clean page after the configured idle period. Changed POST forms, selected files and pending workspace document uploads still receive the confirmation dialog during the final minute of inactivity.
- Confirmed renewals synchronize across open tabs through BroadcastChannel where supported. Messages contain only timing and warning settings; credentials and form contents are never shared. Receiving a message does not send another renewal request.
- Continue in the confirmation dialog requires the current password and a CSRF-protected POST. Forms and selected files stay in the current document. No form is submitted automatically.
- The dialog does not extend expiry by itself. A renewal submitted before expiry waits at most 15 seconds for the server's decision, including when its original countdown reaches zero. A successful response uses the server's returned timeout, measured from the renewal request's start. A stalled renewal past expiry, a delayed browser wake after expiry or an already-expired server session leads to logout. Unsaved values are not persisted for recovery after logout.

The existing server inactivity timeout remains authoritative. Browser settings are supplied on page/workspace rendering; already-open pages receive changed settings on their next workspace load. Shared form guards supply their initialized dirty baseline, so a saved form normalized by deferred controls is not mistaken for unfinished work.

Initial countdowns start from the HTML request, using browser navigation timing for full-page loads and the recorded fetch start for workspace transitions. Downloading HTML, preparing styles and initializing components do not grant a fresh timeout after the server has already started counting.

Verification: service/default/audit tests, settings authorization and CSRF tests, keepalive/logout security tests, targeted JSP compilation, and JavaScript session/form/navigation tests. Live visual checks and applying V140 to a running PostgreSQL installation remain deployment checks; no throughput capacity is claimed.
