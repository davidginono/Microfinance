do $$
declare
    constraint_record record;
begin
    for constraint_record in
        select c.conname
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        join pg_namespace n on n.oid = t.relnamespace
        where n.nspname = 'public'
          and t.relname = 'loan_product_board_reviewers'
          and c.contype = 'u'
          and (
              select array_agg(a.attname::text order by key_columns.ordinality)
              from unnest(c.conkey) with ordinality as key_columns(attnum, ordinality)
              join pg_attribute a on a.attrelid = c.conrelid
                  and a.attnum = key_columns.attnum
          ) = array['loan_product_setting_id', 'board_member_id']
    loop
        execute format('alter table loan_product_board_reviewers drop constraint if exists %I', constraint_record.conname);
    end loop;

    for constraint_record in
        select c.conname
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        join pg_namespace n on n.oid = t.relnamespace
        where n.nspname = 'public'
          and t.relname = 'loan_product_board_reviewers'
          and c.contype = 'u'
          and c.conname <> 'uk_loan_product_stage_reviewer'
          and (
              select array_agg(a.attname::text order by key_columns.ordinality)
              from unnest(c.conkey) with ordinality as key_columns(attnum, ordinality)
              join pg_attribute a on a.attrelid = c.conrelid
                  and a.attnum = key_columns.attnum
          ) = array['loan_product_setting_id', 'review_stage', 'board_member_id']
    loop
        execute format('alter table loan_product_board_reviewers drop constraint if exists %I', constraint_record.conname);
    end loop;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        join pg_namespace n on n.oid = t.relnamespace
        where n.nspname = 'public'
          and t.relname = 'loan_product_board_reviewers'
          and c.conname = 'uk_loan_product_stage_reviewer'
    ) then
        alter table loan_product_board_reviewers
            add constraint uk_loan_product_stage_reviewer unique (loan_product_setting_id, review_stage, board_member_id);
    end if;
end $$;

create index if not exists idx_loan_product_board_reviewers_product_stage
    on loan_product_board_reviewers (loan_product_setting_id, review_stage);
