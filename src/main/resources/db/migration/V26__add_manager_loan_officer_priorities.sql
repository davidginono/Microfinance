alter table loan_product_settings
    add column if not exists manager_priority integer,
    add column if not exists loan_officer_priority integer;

update loan_product_settings
set manager_priority = case
        when workflow_start_stage = 'LOAN_OFFICER' then 2
        else 1
    end,
    loan_officer_priority = case
        when workflow_start_stage = 'LOAN_OFFICER' then 1
        else 2
    end
where manager_priority is null
   or loan_officer_priority is null;
