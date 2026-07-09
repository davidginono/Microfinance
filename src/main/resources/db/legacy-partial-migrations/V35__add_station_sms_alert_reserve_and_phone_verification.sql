alter table station_sms_accounts
    add column if not exists alert_reserved_units bigint not null default 0;

alter table station_sms_accounts
    drop constraint if exists ck_station_sms_alert_reserved_units;

alter table station_sms_accounts
    add constraint ck_station_sms_alert_reserved_units
    check (alert_reserved_units between 0 and 3);

update station_sms_accounts
set alert_reserved_units = least(3, available_units),
    available_units = greatest(available_units - 3, 0),
    warning_baseline = greatest(warning_baseline - least(3, available_units), 0),
    status = case when available_units <= 3 then 'DEPLETED' else status end,
    updated_at = current_timestamp
where alert_reserved_units = 0
  and available_units > 0;

alter table members
    add column if not exists phone_verified_at timestamptz;

update members m
set phone_verified_at = current_timestamp
where m.phone_verified_at is null
  and m.status = 'ACTIVE'
  and m.phone ~ '^255[0-9]{9}$'
  and (
      m.position = 'MINOR_ADMIN'
      or exists (
          select 1
          from member_staff_roles r
          where r.member_id = m.id
            and r.role_name = 'MINOR_ADMIN'
      )
  );

alter table email_otp_tokens
    drop constraint if exists email_otp_tokens_purpose_check;

alter table email_otp_tokens
    add constraint email_otp_tokens_purpose_check
    check (purpose in (
        'LOGIN',
        'STAFF_LOGIN',
        'STAFF_LOGIN_MFA',
        'REGISTRATION',
        'PASSWORD_RESET',
        'APPLICANT_SIGNATURE',
        'LOAN_APPLICATION_FORFEIT',
        'GUARANTOR_SIGNATURE',
        'BOARD_SIGNATURE',
        'CLAIM_ACCOUNT',
        'CLAIM_PHONE'
    ));
