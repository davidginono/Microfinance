alter table sacco_settings
add column if not exists notification_delivery_prefs jsonb not null default
'{
  "LOAN_STATUS": {"email": true, "sms": false},
  "GUARANTEE_REQUEST": {"email": true, "sms": false},
  "REPAYMENT_REMINDER": {"email": true, "sms": false}
}'::jsonb;
