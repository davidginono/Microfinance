ALTER TABLE board_reviews
    ADD COLUMN IF NOT EXISTS review_stage VARCHAR(64);

UPDATE board_reviews
SET review_stage = COALESCE(review_stage, 'BOARD');

ALTER TABLE board_reviews
    ALTER COLUMN review_stage SET NOT NULL;

DO $$
DECLARE
    old_constraint_name TEXT;
BEGIN
    SELECT tc.constraint_name INTO old_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON tc.constraint_name = ccu.constraint_name
     AND tc.table_schema = ccu.table_schema
    WHERE tc.table_schema = current_schema()
      AND tc.table_name = 'board_reviews'
      AND tc.constraint_type = 'UNIQUE'
    GROUP BY tc.constraint_name
    HAVING array_agg(ccu.column_name::text ORDER BY ccu.column_name::text) = ARRAY['board_member_id','loan_application_id'];

    IF old_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE board_reviews DROP CONSTRAINT %I', old_constraint_name);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'board_reviews'
          AND constraint_name = 'uk_board_reviews_loan_member_stage'
    ) THEN
        ALTER TABLE board_reviews
        ADD CONSTRAINT uk_board_reviews_loan_member_stage
        UNIQUE (loan_application_id, board_member_id, review_stage);
    END IF;
END $$;
