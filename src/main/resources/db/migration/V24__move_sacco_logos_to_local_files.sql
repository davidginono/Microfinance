-- SACCO logos are now stored in per-SACCO local folders:
-- registered-saccos/<SACCO_ID>/images/logo.{png,jpeg,jpg}
-- Keep stored_uploads for loan attachments and member profile photos only.

DELETE FROM public.stored_uploads
WHERE owner_type = 'SACCO'
  AND category = 'SACCO_LOGO';

ALTER TABLE public.stored_uploads
    ADD CONSTRAINT ck_stored_uploads_no_sacco_logo
    CHECK (NOT (owner_type = 'SACCO' AND category = 'SACCO_LOGO'));
