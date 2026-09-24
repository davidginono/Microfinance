alter table public.members
    add column if not exists staff_no character varying(255);

alter table public.members
    add column if not exists staff_access_status character varying(64) not null default 'NONE';

alter table public.members
    add column if not exists staff_access_assigned_at timestamp(6) with time zone;

alter table public.members
    add column if not exists staff_access_activated_at timestamp(6) with time zone;

update public.members m
set staff_no = m.member_no
where m.staff_no is null
  and coalesce(m.is_member, m."position" = 'MEMBER') = false
  and (
      m."position" <> 'MEMBER'
      or exists (
          select 1
          from public.member_staff_roles msr
          where msr.member_id = m.id
      )
  );

update public.members m
set staff_no = m.member_no
where m.staff_no is null
  and coalesce(m.is_member, m."position" = 'MEMBER') = true
  and (
      m."position" <> 'MEMBER'
      or exists (
          select 1
          from public.member_staff_roles msr
          where msr.member_id = m.id
      )
  )
  and not exists (
      select 1
      from public.members other
      where other.id <> m.id
        and lower(other.staff_no) = lower(m.member_no)
  );

with staff_member_accounts as (
    select m.id, row_number() over (order by m.created_at, m.id) as rn
    from public.members m
    where m.staff_no is null
      and coalesce(m.is_member, m."position" = 'MEMBER') = true
      and (
          m."position" <> 'MEMBER'
          or exists (
              select 1
              from public.member_staff_roles msr
              where msr.member_id = m.id
          )
      )
),
available_numbers as (
    select value, row_number() over (order by value) as rn
    from generate_series(10000, 99999) value
    where not exists (
        select 1
        from public.members m
        where m.staff_no = value::text
           or m.member_no = value::text
           or m.member_no = concat('STAFF-', value::text)
    )
)
update public.members m
set staff_no = available_numbers.value::text
from staff_member_accounts
join available_numbers on available_numbers.rn = staff_member_accounts.rn
where m.id = staff_member_accounts.id;

update public.members m
set staff_access_status = 'ACTIVE',
    staff_access_assigned_at = coalesce(m.staff_access_assigned_at, m.created_at, now()),
    staff_access_activated_at = coalesce(m.staff_access_activated_at, now())
where m.staff_no is not null
  and (
      m."position" <> 'MEMBER'
      or exists (
          select 1
          from public.member_staff_roles msr
          where msr.member_id = m.id
      )
  );

update public.members m
set staff_access_status = 'NONE',
    staff_access_assigned_at = null,
    staff_access_activated_at = null
where m.staff_no is null;

update public.members m
set member_no = case
    when exists (
        select 1
        from public.members other
        where other.id <> m.id
          and lower(other.member_no) = lower(concat('STAFF-', m.staff_no))
    )
        then concat('STAFF-', replace(m.id::text, '-', ''))
    else concat('STAFF-', m.staff_no)
end
where m.staff_no is not null
  and coalesce(m.is_member, m."position" = 'MEMBER') = false
  and m.member_no = m.staff_no;

create unique index if not exists uk_members_staff_no
    on public.members (lower(staff_no))
    where staff_no is not null;
