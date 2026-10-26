
, group_facilities AS MATERIALIZED (
 SELECT f.*,a.loan_id loan_number,a.amount principal,a.application_date,c.group_id,g.code group_code,
   jsonb_array_length(f.snapshot->'shares') members
 FROM lending_group_shared_facility f JOIN loans a ON a.id=f.loan_id
 JOIN lending_group_cycle c ON c.id=f.cycle_id AND c.sacco_id=f.sacco_id AND c.station_id=f.station_id
 JOIN lending_group g ON g.id=c.group_id AND g.sacco_id=f.sacco_id AND g.station_id=f.station_id
 WHERE :includeGroups AND f.sacco_id=:institution AND f.station_id=:branch AND f.created_at<=:cutoff
), group_checks AS (
 SELECT f.loan_id,f.group_code,f.cycle_id::text identity,f.application_date date,'ALLOCATIONS' activity,
   f.cycle_id::text reference,v.component,v.source_amount,v.attributed_amount,f.members expected_rows,
   x.rows actual_rows,x.people distinct_rows,null::uuid activity_id
 FROM group_facilities f LEFT JOIN LATERAL (
   SELECT count(*) rows,count(DISTINCT client_id) people,sum(principal) principal,sum(interest) interest,
     sum(charges) charges,sum(deductions) deductions,sum(net) net
   FROM lending_group_shared_allocation WHERE cycle_id=f.cycle_id
 ) x ON true CROSS JOIN LATERAL(VALUES
   ('GROUP_PRINCIPAL',f.principal,x.principal),('GROUP_INTEREST',(f.snapshot->>'interest')::numeric,x.interest),
   ('GROUP_CHARGES',(f.snapshot->>'charges')::numeric,x.charges),('GROUP_DEDUCTIONS',(f.snapshot->>'deductions')::numeric,x.deductions),
   ('GROUP_NET',(f.snapshot->>'net')::numeric,x.net)) v(component,source_amount,attributed_amount)
 UNION ALL
 SELECT f.loan_id,f.group_code,p.id::text,p.effective_date,'FUNDING_ALLOCATIONS',p.reference,'GROUP_NET',
   (f.snapshot->>'net')::numeric,x.amount,f.members,x.rows,x.people,null::uuid
 FROM group_facilities f JOIN posts p ON p.loan_id=f.loan_id AND p.kind='DISBURSEMENT'
 LEFT JOIN lending_group_shared_funding e ON e.cycle_id=f.cycle_id AND e.posting_id=p.id AND e.sacco_id=:institution AND e.station_id=:branch AND e.signed_at<=:cutoff
 LEFT JOIN LATERAL(SELECT count(*) rows,count(DISTINCT t.client_id) people,sum(t.amount) amount
   FROM lending_group_shared_transfer t WHERE e.cycle_id IS NOT NULL AND t.cycle_id=f.cycle_id AND t.sacco_id=:institution AND t.station_id=:branch) x ON true
 UNION ALL
 SELECT f.loan_id,f.group_code,p.id::text,p.effective_date,p.kind,p.reference,v.component,v.source_amount,v.attributed_amount,
   f.members,x.rows,x.people,(SELECT id FROM repayment_activity)
 FROM group_facilities f JOIN posts p ON p.loan_id=f.loan_id AND p.kind IN ('PAYMENT','REVERSAL')
 LEFT JOIN loan_repayment_transactions t ON t.id=p.transaction_id AND t.loan_application_id=p.loan_id
   AND t.sacco_id=:institution AND t.station_id=:branch AND t.posted_at<=:cutoff
 LEFT JOIN LATERAL(SELECT count(*) rows,count(DISTINCT m.client_id) people,sum(m.principal) principal,sum(m.interest) interest,
   sum(m.charges) charges,sum(m.excluded_interest) excluded FROM lending_group_shared_movement m
   WHERE m.posting_id=p.id AND m.cycle_id=f.cycle_id AND m.sacco_id=:institution AND m.station_id=:branch) x ON true
 CROSS JOIN LATERAL(VALUES('GROUP_PRINCIPAL',p.principal_amount,x.principal),('GROUP_INTEREST',p.interest_amount,x.interest),
   ('GROUP_CHARGES',p.charge_amount,x.charges),('GROUP_EXCLUDED_INTEREST',t.future_interest_excluded,x.excluded)) v(component,source_amount,attributed_amount)
 UNION ALL
 SELECT f.loan_id,f.group_code,w.id::text,w.effective_date,CASE WHEN w.kind='REVERSAL' THEN 'WAIVER_REVERSAL' ELSE 'INTEREST_WAIVER' END,
   'W-'||w.sequence,'GROUP_WAIVED_INTEREST',w.amount,x.amount,f.members,x.rows,x.people,null::uuid
 FROM group_facilities f JOIN loan_interest_waiver w ON w.loan_id=f.loan_id AND w.sacco_id=:institution AND w.station_id=:branch AND w.created_at<=:cutoff
 LEFT JOIN LATERAL(SELECT count(*) rows,count(DISTINCT m.client_id) people,sum(m.amount) amount FROM lending_group_shared_waiver m
   WHERE m.waiver_id=w.id AND m.cycle_id=f.cycle_id AND m.sacco_id=:institution AND m.station_id=:branch) x ON true
 UNION ALL
 SELECT f.loan_id,f.group_code,q.id::text,q.effective_date,q.kind,q.id::text,'GROUP_WRITE_OFF',q.amount,x.amount,
   f.members,x.rows,x.people,null::uuid
 FROM group_facilities f JOIN decisions q ON q.loan_id=f.loan_id AND q.kind IN ('WRITE_OFF','WRITE_BACK')
 LEFT JOIN LATERAL(SELECT count(*) rows,count(DISTINCT m.client_id) people,sum(m.amount) amount FROM lending_group_shared_adjustment m
   WHERE m.request_id=q.id AND m.cycle_id=f.cycle_id AND m.sacco_id=:institution AND m.station_id=:branch) x ON true
 UNION ALL
 SELECT p.loan_id,r.group_code,r.id::text||'/'||p.id::text,r.effective_date,p.kind,r.number,v.component,v.source_amount,v.attributed_amount,
   1,CASE WHEN x.rows>0 THEN 1 ELSE 0 END,CASE WHEN x.rows=x.identities THEN 1 ELSE 0 END,(SELECT id FROM repayment_activity)
 FROM lending_group_receipt r JOIN lending_group_receipt_loan link ON link.receipt_id=r.id AND link.sacco_id=r.sacco_id AND link.station_id=r.station_id
 JOIN posts p ON p.id=link.posting_id
 LEFT JOIN LATERAL(SELECT count(*) rows,count(DISTINCT c.id) identities,sum(c.amount) amount,sum(c.principal) principal,
   sum(c.interest) interest,sum(c.charges) charges FROM lending_group_contribution c
   WHERE c.receipt_id=r.id AND c.loan_id=p.loan_id AND c.sacco_id=:institution AND c.station_id=:branch) x ON true
 CROSS JOIN LATERAL(VALUES('GROUP_CONTRIBUTIONS',p.amount,x.amount),('GROUP_PRINCIPAL',p.principal_amount,x.principal),
   ('GROUP_INTEREST',p.interest_amount,x.interest),('GROUP_CHARGES',p.charge_amount,x.charges)) v(component,source_amount,attributed_amount)
 WHERE :includeGroups AND r.sacco_id=:institution AND r.station_id=:branch AND r.posted_at<=:cutoff
 UNION ALL
 SELECT null::uuid,r.group_code,r.id::text,r.effective_date,'RECEIPT',r.number,'GROUP_CONTRIBUTIONS',r.total,x.amount,
   r.transaction_count,x.rows,x.identities,(SELECT id FROM repayment_activity)
 FROM lending_group_receipt r LEFT JOIN LATERAL(SELECT count(p.id) rows,count(DISTINCT p.id) identities,sum(p.amount) amount
   FROM lending_group_receipt_loan link LEFT JOIN posts p ON p.id=link.posting_id
   WHERE link.receipt_id=r.id AND link.sacco_id=:institution AND link.station_id=:branch) x ON true
 WHERE :includeGroups AND r.sacco_id=:institution AND r.station_id=:branch AND r.posted_at<=:cutoff
), group_exceptions AS (
 SELECT c.component,'ATTRIBUTION' section,c.group_code client,coalesce(a.loan_id,'') loan,'' account,c.reference,c.date,c.activity,
   CASE WHEN c.source_amount IS NULL OR c.expected_rows IS NULL THEN 'ATTRIBUTION_UNAVAILABLE'
     WHEN c.actual_rows>c.distinct_rows OR c.actual_rows>c.expected_rows THEN 'ATTRIBUTION_DUPLICATE'
     WHEN c.actual_rows<c.expected_rows THEN 'ATTRIBUTION_MISSING' ELSE 'ATTRIBUTION_AMOUNT' END issue,
   null::numeric sl_debit,null::numeric sl_credit,null::numeric gl_debit,null::numeric gl_credit,
   null::numeric debit_variance,null::numeric credit_variance,c.identity,
   c.source_amount,c.attributed_amount,c.attributed_amount-c.source_amount attribution_variance
 FROM group_checks c LEFT JOIN loans a ON a.id=c.loan_id
 WHERE c.date>=:from AND c.date<=:through AND (:allActivities OR c.activity_id=:activityId::uuid)
   AND (c.source_amount IS NULL OR c.expected_rows IS NULL OR c.actual_rows<>c.expected_rows OR c.actual_rows<>c.distinct_rows
     OR c.source_amount IS DISTINCT FROM c.attributed_amount)

 UNION ALL
 SELECT 'GROUP_SOURCE_LINKS','ATTRIBUTION',a.member_no,coalesce(a.loan_id,''),'',a.id::text,a.application_date,'ALLOCATIONS','ATTRIBUTION_MISSING',
   null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,null::numeric,a.id::text,null::numeric,null::numeric,null::numeric
 FROM loans a WHERE :includeGroups AND :allActivities AND a.recorded_loan IS NOT NULL AND a.application_date BETWEEN :from AND :through
   AND (a.borrower_kind='JOINT_GROUP' AND NOT EXISTS(SELECT 1 FROM lending_group_shared_facility f WHERE f.loan_id=a.id AND f.sacco_id=:institution AND f.station_id=:branch AND f.created_at<=:cutoff)
     OR a.borrower_kind='ORGANIZATION' AND NOT EXISTS(SELECT 1 FROM lending_group_facility f WHERE f.loan_id=a.id AND f.sacco_id=:institution AND f.station_id=:branch AND f.created_at<=:cutoff))
), diagnostic_exceptions AS (
 SELECT e.*,null::numeric source_amount,null::numeric attributed_amount,null::numeric attribution_variance FROM exceptions e
 UNION ALL SELECT * FROM group_exceptions WHERE :component IN ('ALL','GROUP_ATTRIBUTION')
)
