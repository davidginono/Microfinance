create sequence if not exists public.staff_number_seq
    as bigint
    start with 10000
    increment by 1
    minvalue 10000
    maxvalue 99999
    cycle
    cache 1;

alter sequence public.staff_number_seq
    minvalue 10000
    maxvalue 99999
    cycle
    cache 1;

do $$
declare
    highest_existing bigint;
    current_issued bigint;
    sequence_target bigint;
begin
    select coalesce(max(value), 9999)
    into highest_existing
    from (
        select staff_no::bigint as value
        from public.members
        where staff_no ~ '^[0-9]{5}$'
        union all
        select member_no::bigint as value
        from public.members
        where member_no ~ '^[0-9]{5}$'
        union all
        select substring(member_no from '^STAFF-([0-9]{5})$')::bigint as value
        from public.members
        where member_no ~ '^STAFF-[0-9]{5}$'
    ) used_numbers;

    select case when is_called then last_value else last_value - 1 end
    into current_issued
    from public.staff_number_seq;

    sequence_target := greatest(coalesce(current_issued, 9999), highest_existing);
    if sequence_target < 10000 then
        perform setval('public.staff_number_seq', 10000, false);
    elsif sequence_target > 99999 then
        perform setval('public.staff_number_seq', 99999, true);
    else
        perform setval('public.staff_number_seq', sequence_target, true);
    end if;
end $$;
