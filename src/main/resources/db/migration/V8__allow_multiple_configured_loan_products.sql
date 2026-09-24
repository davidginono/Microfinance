ALTER TABLE public.loan_product_settings
    DROP CONSTRAINT IF EXISTS ukm5g7688aw9o3u54davwugs1gk;

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'loan_product_settings'
          AND con.contype = 'u'
          AND (
              SELECT array_agg(att.attname::text ORDER BY cols.ordinality)
              FROM unnest(con.conkey) WITH ORDINALITY AS cols(attnum, ordinality)
              JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = cols.attnum
          ) = ARRAY['sacco_id', 'loan_type']
    LOOP
        EXECUTE format('ALTER TABLE public.loan_product_settings DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE public.loan_applications
    ADD COLUMN IF NOT EXISTS loan_product_setting_id uuid;

UPDATE public.loan_applications app
SET loan_product_setting_id = product.id
FROM public.loan_product_settings product
WHERE app.loan_product_setting_id IS NULL
  AND product.sacco_id = app.sacco_id
  AND product.loan_type = app.loan_type;

CREATE INDEX IF NOT EXISTS ix_loan_applications_product_setting
    ON public.loan_applications (loan_product_setting_id);

ALTER TABLE public.loan_applications
    DROP CONSTRAINT IF EXISTS fk_loan_applications_product_setting;

ALTER TABLE public.loan_applications
    ADD CONSTRAINT fk_loan_applications_product_setting
        FOREIGN KEY (loan_product_setting_id)
        REFERENCES public.loan_product_settings(id);
