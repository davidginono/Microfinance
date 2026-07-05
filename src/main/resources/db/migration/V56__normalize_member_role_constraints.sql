alter table members
    drop constraint if exists members_position_check;

alter table members
    add constraint members_position_check
    check (position in (
        'MEMBER',
        'MINOR_ADMIN',
        'MANAGER',
        'ACCOUNTANT',
        'DISBURSEMENT_OFFICER',
        'CHAIRPERSON',
        'BOARD',
        'CREDIT_COMMITTEE',
        'LOAN_OFFICER',
        'ADMIN'
    ));

alter table member_staff_roles
    drop constraint if exists member_staff_roles_role_name_check;

alter table member_staff_roles
    add constraint member_staff_roles_role_name_check
    check (role_name in (
        'MEMBER',
        'MINOR_ADMIN',
        'MANAGER',
        'ACCOUNTANT',
        'DISBURSEMENT_OFFICER',
        'CHAIRPERSON',
        'BOARD',
        'CREDIT_COMMITTEE',
        'LOAN_OFFICER',
        'ADMIN'
    ));
