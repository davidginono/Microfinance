/* Both sides are aggregated independently before the full join. All history through the
   effective date is needed for opening/closing; only exception rows leave PostgreSQL. */
WITH loans AS MATERIALIZED (
  SELECT a.id,a.loan_id,a.sacco_id,a.station_id,a.applicant_member_id,a.disbursement_date,a.status,m.member_no,
    l.created_at ledger_created,r.loan_id recorded_loan
  FROM loan_applications a JOIN members m ON m.id=a.applicant_member_id AND m.sacco_id=a.sacco_id
  LEFT JOIN loan_ledgers l ON l.loan_application_id=a.id AND l.sacco_id=a.sacco_id AND l.station_id=a.station_id
  LEFT JOIN accountant_loan_record r ON r.loan_id=a.id AND r.sacco_id=a.sacco_id AND r.station_id=a.station_id AND r.recorded_at<=:cutoff
  WHERE a.sacco_id=:institution AND a.station_id=:branch AND a.created_at<=:cutoff
), posts AS MATERIALIZED (
  SELECT p.* FROM accountant_loan_post p JOIN loans a ON a.id=p.loan_id
  WHERE p.sacco_id=:institution AND p.station_id=:branch AND p.state='POSTED' AND p.recorded_at<=:cutoff
), bridges AS (
  SELECT loan_id,transaction_id,min(journal_id::text)::uuid journal_id,
    min(principal_account_id::text)::uuid principal_account,min(interest_account_id::text)::uuid interest_account,
    min(reference) reference,min(kind) activity,count(*) links,min(id::text) source_id
  FROM posts GROUP BY loan_id,transaction_id
), decisions AS MATERIALIZED (
  SELECT q.*,d.journal_id,d.recorded_at decision_time FROM loan_adjustment_request q
  JOIN loan_adjustment_decision d ON d.request_id=q.id AND d.decision='APPROVED'
    AND d.sacco_id=q.sacco_id AND d.station_id=q.station_id AND d.recorded_at<=:cutoff
  JOIN loans a ON a.id=q.loan_id
  WHERE q.sacco_id=:institution AND q.station_id=:branch AND q.recorded_at<=:cutoff
), raw_sl AS (
  SELECT e.loan_application_id loan_id,e.voucher_id,e.transaction_id,e.effective_date date,e.account_code,
    sum(e.debit) debit,sum(e.credit) credit,count(*) lines
  FROM loan_journal_entries e JOIN loans a ON a.id=e.loan_application_id
  WHERE e.posted_at<=:cutoff AND e.effective_date<=:through AND e.account_code IN ('LOAN_PRINCIPAL','INTEREST_COLLECTIONS_CLEARING')
  GROUP BY e.loan_application_id,e.voucher_id,e.transaction_id,e.effective_date,e.account_code
), sl_events AS (
  SELECT e.loan_id,b.journal_id,CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN b.principal_account ELSE b.interest_account END account_id,
    CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN 'PRINCIPAL' ELSE 'INTEREST_COLLECTIONS' END component,
    e.date,e.debit,e.credit,coalesce(b.reference,e.voucher_id::text) reference,
    coalesce(b.activity,CASE WHEN e.transaction_id IS NULL THEN 'DISBURSEMENT' ELSE 'PAYMENT' END) activity,
    e.voucher_id::text identity,
    CASE WHEN e.lines>1 OR b.links>1 THEN 'DUPLICATE_LINK'
      WHEN b.journal_id IS NULL OR (e.transaction_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM loan_repayment_transactions t WHERE t.id=e.transaction_id AND t.loan_application_id=e.loan_id
          AND t.sacco_id=:institution AND t.station_id=:branch AND t.posted_at<=:cutoff AND t.payment_date=e.date)) THEN 'SOURCE_LINK'
      WHEN e.transaction_id IS NULL AND e.account_code='LOAN_PRINCIPAL' AND
        (e.credit<>0 OR e.debit IS DISTINCT FROM (SELECT l.principal FROM loan_ledgers l WHERE l.loan_application_id=e.loan_id)) THEN 'SOURCE_AMOUNT'
      WHEN e.transaction_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM loan_repayment_transactions t WHERE t.id=e.transaction_id AND
          (e.debit<>CASE WHEN t.kind='REVERSAL' THEN CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN t.principal_amount ELSE t.interest_amount END ELSE 0 END
          OR e.credit<>CASE WHEN t.kind='PAYMENT' THEN CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN t.principal_amount ELSE t.interest_amount END ELSE 0 END
          OR CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN t.principal_amount ELSE t.interest_amount END IS DISTINCT FROM (
            SELECT sum(CASE e.account_code WHEN 'LOAN_PRINCIPAL' THEN x.principal_amount ELSE x.interest_amount END)
              FROM loan_repayment_allocations x WHERE x.transaction_id=t.id))) THEN 'SOURCE_AMOUNT'
      ELSE '' END issue
  FROM raw_sl e LEFT JOIN bridges b ON b.loan_id=e.loan_id AND b.transaction_id IS NOT DISTINCT FROM e.transaction_id
  UNION ALL
  SELECT q.loan_id,q.journal_id,v.account_id,v.component,q.effective_date,v.debit,v.credit,q.id::text,q.kind,q.id::text,''
  FROM decisions q CROSS JOIN LATERAL (
    SELECT q.debit_account_id account_id,CASE WHEN a.purpose='LOAN_PRINCIPAL' THEN 'PRINCIPAL' ELSE 'ALLOWANCE' END component,q.amount debit,0::numeric credit
      FROM gl_account a WHERE a.id=q.debit_account_id AND a.sacco_id=:institution AND a.purpose IN ('LOAN_PRINCIPAL','ALLOWANCE')
    UNION ALL
    SELECT q.credit_account_id,CASE WHEN a.purpose='LOAN_PRINCIPAL' THEN 'PRINCIPAL' ELSE 'ALLOWANCE' END,0::numeric,q.amount
      FROM gl_account a WHERE a.id=q.credit_account_id AND a.sacco_id=:institution AND a.purpose IN ('LOAN_PRINCIPAL','ALLOWANCE')
  ) v WHERE q.effective_date<=:through
), sl AS (
  SELECT journal_id,account_id,component,date,min(loan_id::text)::uuid loan_id,min(reference) reference,
    min(activity) activity,min(identity) identity,sum(debit) debit,sum(credit) credit,
    CASE WHEN count(DISTINCT identity)>1 THEN 'DUPLICATE_LINK' ELSE max(issue) END issue
  FROM sl_events GROUP BY journal_id,account_id,component,date,
    CASE WHEN journal_id IS NULL THEN identity ELSE '' END
), gl_links AS (
  SELECT journal_id,min(loan_id::text)::uuid loan_id,min(reference) reference,count(*) links FROM (
    SELECT journal_id,loan_id,reference FROM posts UNION ALL SELECT journal_id,loan_id,id::text FROM decisions
  ) x GROUP BY journal_id
), journal_headers AS MATERIALIZED (
  SELECT j.*,count(*) OVER(PARTITION BY j.source_type,j.source_reference) source_links
  FROM gl_journal j WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.state='POSTED'
    AND j.posted_at<=:cutoff AND j.effective_date<=:through
), gl AS (
  SELECT j.id journal_id,z.account_id,
    CASE a.purpose WHEN 'LOAN_PRINCIPAL' THEN 'PRINCIPAL' WHEN 'ALLOWANCE' THEN 'ALLOWANCE'
      WHEN 'INTEREST_RECEIVABLE' THEN 'INTEREST_RECEIVABLE' ELSE 'INTEREST_COLLECTIONS' END component,
    j.effective_date date,k.loan_id,coalesce(k.reference,j.source_reference) reference,
    CASE j.source_type WHEN 'LOAN_RECORD_DISBURSEMENT' THEN 'DISBURSEMENT' WHEN 'LOAN_RECORD_PAYMENT' THEN 'PAYMENT'
      WHEN 'LOAN_RECORD_REVERSAL' THEN 'REVERSAL' ELSE 'OTHER' END activity,j.id::text identity,
    sum(z.debit) debit,sum(z.credit) credit,
    CASE WHEN k.links>1 OR j.source_links>1 THEN 'DUPLICATE_LINK'
      WHEN a.purpose='INTEREST_RECEIVABLE' THEN 'UNSUPPORTED_INTEREST'
      WHEN k.journal_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM posts p WHERE p.journal_id=j.id AND j.source_reference=p.id::text AND j.source_type='LOAN_RECORD_'||p.kind
        UNION ALL SELECT 1 FROM decisions q WHERE q.journal_id=j.id AND j.source_reference=q.id::text AND j.source_type='LOAN_ADJUSTMENT'
      ) THEN 'SOURCE_LINK' ELSE '' END issue
  FROM journal_headers j JOIN gl_journal_line z ON z.journal_id=j.id AND z.sacco_id=j.sacco_id AND z.station_id=j.station_id
  JOIN gl_account a ON a.id=z.account_id AND a.sacco_id=j.sacco_id
  LEFT JOIN gl_links k ON k.journal_id=j.id
  WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.state='POSTED' AND j.posted_at<=:cutoff AND j.effective_date<=:through
    AND (a.purpose IN ('LOAN_PRINCIPAL','ALLOWANCE','INTEREST_RECEIVABLE') OR EXISTS (
      SELECT 1 FROM posts p WHERE p.interest_account_id=a.id
        AND (p.journal_id=j.id OR j.source_type IN ('LOAN_RECORD_PAYMENT','LOAN_RECORD_REVERSAL'))))
  GROUP BY j.id,j.source_reference,j.source_type,j.source_links,j.effective_date,z.account_id,a.purpose,k.loan_id,k.reference,k.links,k.journal_id
), compared AS MATERIALIZED (
  SELECT coalesce(s.component,g.component) component,coalesce(s.date,g.date) date,coalesce(s.account_id,g.account_id) account_id,
    coalesce(s.loan_id,g.loan_id) loan_id,coalesce(s.reference,g.reference) reference,coalesce(s.activity,g.activity) activity,
    coalesce(s.identity,g.identity) identity,
    CASE WHEN g.component='INTEREST_RECEIVABLE' THEN null ELSE coalesce(s.debit,0) END sl_debit,
    CASE WHEN g.component='INTEREST_RECEIVABLE' THEN null ELSE coalesce(s.credit,0) END sl_credit,
    coalesce(g.debit,0) gl_debit,coalesce(g.credit,0) gl_credit,
    CASE WHEN coalesce(s.issue,'')<>'' THEN s.issue WHEN coalesce(g.issue,'')<>'' THEN g.issue
      WHEN s.identity IS NULL THEN 'GL_ONLY' WHEN g.identity IS NULL THEN 'SL_ONLY'
      WHEN s.debit<>g.debit OR s.credit<>g.credit THEN 'AMOUNT' ELSE '' END issue
  FROM sl s FULL OUTER JOIN gl g ON g.journal_id=s.journal_id AND g.account_id=s.account_id AND g.date=s.date AND g.component=s.component
), balances AS (
  SELECT component,account_id,'OPENING' section,:from::date date,
    coalesce(sum(sl_debit-sl_credit) FILTER(WHERE date<:from),0) sl,
    coalesce(sum(gl_debit-gl_credit) FILTER(WHERE date<:from),0) gl
  FROM compared GROUP BY component,account_id
  UNION ALL
  SELECT component,account_id,'CLOSING',:through::date,sum(sl_debit-sl_credit),sum(gl_debit-gl_credit)
  FROM compared GROUP BY component,account_id
), all_exceptions AS (
  SELECT c.component,'MOVEMENT' section,coalesce(a.member_no,'') client,coalesce(a.loan_id,'') loan,
    coalesce(ac.code,'') account,c.reference,c.date,c.activity,c.issue,c.sl_debit,c.sl_credit,c.gl_debit,c.gl_credit,
    c.sl_debit-c.gl_debit debit_variance,c.sl_credit-c.gl_credit credit_variance,c.identity
  FROM compared c LEFT JOIN loans a ON a.id=c.loan_id LEFT JOIN gl_account ac ON ac.id=c.account_id AND ac.sacco_id=:institution
  WHERE c.date>=:from AND (c.issue<>'' OR c.sl_debit<>c.gl_debit OR c.sl_credit<>c.gl_credit)
  UNION ALL
  SELECT b.component,b.section,'','',coalesce(ac.code,''),'',b.date,'BALANCE','BALANCE',
    greatest(b.sl,0),greatest(-b.sl,0),greatest(b.gl,0),greatest(-b.gl,0),
    greatest(b.sl,0)-greatest(b.gl,0),greatest(-b.sl,0)-greatest(-b.gl,0),coalesce(b.account_id::text,'unmapped')
  FROM balances b LEFT JOIN gl_account ac ON ac.id=b.account_id AND ac.sacco_id=:institution
  WHERE b.sl<>b.gl AND b.component<>'INTEREST_RECEIVABLE'
  UNION ALL
  SELECT b.component,'COVERAGE','','',ac.code,'',b.date,'COVERAGE','UNSUPPORTED_INTEREST',
    null::numeric,null::numeric,greatest(b.gl,0),greatest(-b.gl,0),null::numeric,null::numeric,b.account_id::text
  FROM balances b JOIN gl_account ac ON ac.id=b.account_id AND ac.sacco_id=:institution
  WHERE b.component='INTEREST_RECEIVABLE' AND b.section='CLOSING'
  UNION ALL
  SELECT 'PRINCIPAL','COVERAGE',a.member_no,coalesce(a.loan_id,''),'','',a.disbursement_date,'COVERAGE','OPENING_UNAVAILABLE',
    null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,a.id::text
  FROM loans a WHERE (a.disbursement_date<=:through OR (a.disbursement_date IS NULL AND a.status IN ('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF')))
    AND (a.recorded_loan IS NULL OR a.ledger_created IS NULL OR a.ledger_created>:cutoff
    OR NOT EXISTS(SELECT 1 FROM raw_sl s WHERE s.loan_id=a.id AND s.transaction_id IS NULL AND s.account_code='LOAN_PRINCIPAL'))
  UNION ALL
  SELECT v.component,'COVERAGE',a.member_no,coalesce(a.loan_id,''),'',t.channel_reference,t.payment_date,'COVERAGE','SOURCE_MISSING',
    null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,t.id::text
  FROM loan_repayment_transactions t JOIN loans a ON a.id=t.loan_application_id
  CROSS JOIN LATERAL (VALUES ('PRINCIPAL','LOAN_PRINCIPAL',t.principal_amount),
    ('INTEREST_COLLECTIONS','INTEREST_COLLECTIONS_CLEARING',t.interest_amount)) v(component,code,amount)
  WHERE t.sacco_id=:institution AND t.station_id=:branch AND t.posted_at<=:cutoff AND t.payment_date<=:through AND v.amount>0
    AND NOT EXISTS(SELECT 1 FROM raw_sl s WHERE s.loan_id=t.loan_application_id AND s.transaction_id=t.id AND s.account_code=v.code)
), exceptions AS (
  SELECT * FROM all_exceptions WHERE :component='ALL' OR component=:component
)
