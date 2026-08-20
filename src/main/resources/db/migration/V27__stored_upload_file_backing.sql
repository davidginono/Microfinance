ALTER TABLE public.stored_uploads
    ADD COLUMN IF NOT EXISTS storage_backend varchar(40) NOT NULL DEFAULT 'DATABASE',
    ADD COLUMN IF NOT EXISTS storage_key varchar(1024);

ALTER TABLE public.stored_uploads
    ALTER COLUMN content DROP NOT NULL;

UPDATE public.stored_uploads
SET storage_backend = 'DATABASE'
WHERE storage_backend IS NULL OR storage_backend = '';

CREATE INDEX IF NOT EXISTS idx_stored_uploads_storage_backend
    ON public.stored_uploads (storage_backend)
    WHERE content IS NOT NULL;

ALTER TABLE public.stored_uploads
    ADD CONSTRAINT ck_stored_uploads_content_location
    CHECK (
        (storage_backend = 'DATABASE' AND content IS NOT NULL)
        OR
        (storage_backend <> 'DATABASE' AND storage_key IS NOT NULL AND storage_key <> '')
    );
