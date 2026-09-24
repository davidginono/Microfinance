ALTER TABLE public.outbox_events
    DROP CONSTRAINT IF EXISTS outbox_events_status_check;

ALTER TABLE public.outbox_events
    ADD CONSTRAINT outbox_events_status_check
    CHECK ((status)::text IN ('NEW', 'PROCESSING', 'PUBLISHED', 'FAILED'));
