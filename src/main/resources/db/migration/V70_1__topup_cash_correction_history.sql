-- The replacement's cash reversal is visible in its own history and disbursement totals.
ALTER VIEW accountant_loan_movement RENAME TO accountant_loan_movement_base;
CREATE VIEW accountant_loan_movement AS
 SELECT p.* FROM accountant_loan_movement_base p
 UNION ALL
 SELECT movement.* FROM accountant_loan_topup e JOIN accountant_loan_post p ON p.id=e.posting_id
 JOIN loan_applications n ON n.id=e.loan_id
 CROSS JOIN LATERAL jsonb_populate_record(NULL::accountant_loan_post,jsonb_build_object(
  'id',e.id,'loan_id',e.loan_id,'sacco_id',e.sacco_id,'station_id',e.station_id,'kind','FUNDING_UNDO','state','POSTED',
  'request_key',e.request_key,'payload_hash',e.payload_hash,'effective_date',e.effective_date,'reference','TU-'||e.id::text,
  'amount',e.cash,'principal_amount',n.amount,'interest_amount',0,'principal_balance',0,
  'money_account_id',p.money_account_id,'principal_account_id',p.principal_account_id,'interest_account_id',NULL,
  'money_account_name',p.money_account_name,'principal_account_name',p.principal_account_name,'interest_account_name','',
  'evidence',e.evidence,'notes',e.reason,'actor_id',e.actor_id,'recorded_at',e.recorded_at,'journal_id',e.journal_id,
  'transaction_id',NULL,'reverses_id',p.id)) movement
 WHERE e.reverses_id IS NOT NULL;
