-- One parent posting; beneficiary transfers and member movements are evidence, never extra journals.
CREATE FUNCTION lending_group_shared_funding_packet(cycle uuid) RETURNS text LANGUAGE sql STABLE AS $$
 SELECT encode(sha256(convert_to(jsonb_build_object('contract',d.contract_hash,'payouts',
   (SELECT jsonb_agg(jsonb_build_object('client',s.client_id,'packet',p.packet_hash,'net',s.net) ORDER BY s.ordinal)
    FROM lending_group_shared_allocation s LEFT JOIN lending_group_shared_payout p ON p.cycle_id=s.cycle_id AND p.client_id=s.client_id WHERE s.cycle_id=d.cycle_id))::text,'UTF8')),'hex')
 FROM lending_group_shared_document d WHERE d.cycle_id=cycle
$$;
CREATE TABLE lending_group_shared_funding (
 cycle_id uuid PRIMARY KEY REFERENCES lending_group_shared_document(cycle_id),loan_id uuid NOT NULL UNIQUE,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,posting_id uuid NOT NULL UNIQUE,journal_id uuid NOT NULL UNIQUE REFERENCES gl_journal(id),
 packet_hash varchar(64) NOT NULL,actor_id uuid NOT NULL REFERENCES members(id),actor_name varchar(255) NOT NULL,actor_number varchar(255) NOT NULL,
 signature_text text NOT NULL CHECK(length(trim(signature_text))>0),signed_at timestamptz NOT NULL,
 FOREIGN KEY(loan_id,sacco_id,station_id) REFERENCES loan_applications(id,sacco_id,station_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE TABLE lending_group_shared_transfer (
 cycle_id uuid NOT NULL REFERENCES lending_group_shared_funding(cycle_id),client_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 amount numeric(18,2) NOT NULL CHECK(amount>0),reference varchar(100) NOT NULL CHECK(length(trim(reference))>0),
 PRIMARY KEY(cycle_id,client_id),UNIQUE(sacco_id,station_id,reference),
 FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_shared_payout(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE TRIGGER shared_funding_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_funding FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER shared_transfer_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_transfer FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_funding_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility;c lending_group_cycle;m members;j gl_journal;
BEGIN
 SELECT * INTO f FROM lending_group_shared_facility WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR SHARE;
 PERFORM id FROM lending_group WHERE id=c.group_id FOR SHARE;
 SELECT * INTO m FROM members WHERE id=NEW.actor_id;
 SELECT * INTO j FROM gl_journal WHERE id=NEW.journal_id;
 IF f.cycle_id IS NULL OR c.state<>'SUBMITTED' OR NOT EXISTS(SELECT 1 FROM lending_group WHERE id=c.group_id AND status='ACTIVE')
  OR ROW(NEW.loan_id,NEW.sacco_id,NEW.station_id) IS DISTINCT FROM ROW(f.loan_id,f.sacco_id,f.station_id)
  OR NEW.packet_hash IS DISTINCT FROM lending_group_shared_funding_packet(c.id)
  OR ROW(m.sacco_id,m.station_id,m.status,NEW.actor_name,NEW.actor_number,NEW.signature_text)
    IS DISTINCT FROM ROW(f.sacco_id,f.station_id,'ACTIVE'::varchar,m.full_name,coalesce(m.staff_no,m.member_no),m.signature_text)
  OR (m.position='MEMBER' AND NOT EXISTS(SELECT 1 FROM member_staff_roles WHERE member_id=m.id))
  OR j.id IS NULL OR ROW(j.sacco_id,j.station_id,j.maker_id,j.state) IS DISTINCT FROM ROW(f.sacco_id,f.station_id,m.id,'DRAFT'::varchar)
  OR EXISTS(SELECT 1 FROM lending_group_shared_allocation s LEFT JOIN lending_group_shared_payout p ON p.cycle_id=s.cycle_id AND p.client_id=s.client_id
    JOIN members b ON b.id=s.client_id WHERE s.cycle_id=c.id AND (p.client_id IS NULL OR p.accepted_at>NEW.signed_at
      OR j.effective_date<(p.accepted_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
      OR ROW(b.sacco_id,b.station_id,b.status,b.is_member) IS DISTINCT FROM ROW(f.sacco_id,f.station_id,'ACTIVE'::varchar,true)))
 THEN RAISE EXCEPTION 'Shared funding requires all signed payouts and current scoped lender execution'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER shared_funding_insert BEFORE INSERT ON lending_group_shared_funding FOR EACH ROW EXECUTE FUNCTION lending_group_shared_funding_insert();
CREATE FUNCTION lending_group_shared_funding_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility; x lending_group_shared_funding; n integer;
BEGIN
 SELECT * INTO f FROM lending_group_shared_facility WHERE cycle_id=NEW.cycle_id;
 SELECT * INTO x FROM lending_group_shared_funding WHERE cycle_id=NEW.cycle_id;
 SELECT count(*) INTO n FROM lending_group_shared_allocation WHERE cycle_id=f.cycle_id;
 IF NOT EXISTS(SELECT 1 FROM accountant_loan_post p JOIN gl_journal j ON j.id=p.journal_id
   JOIN loan_ledgers b ON b.loan_application_id=p.loan_id JOIN loan_applications l ON l.id=p.loan_id
   WHERE p.id=x.posting_id AND p.loan_id=x.loan_id AND p.journal_id=x.journal_id AND p.kind='DISBURSEMENT' AND p.state='POSTED' AND j.state='POSTED'
    AND ROW(p.sacco_id,p.station_id,p.actor_id) IS NOT DISTINCT FROM ROW(x.sacco_id,x.station_id,x.actor_id)
    AND j.currency='TZS' AND j.source_reference=p.id::text AND j.effective_date=p.effective_date AND l.disbursement_date=p.effective_date
    AND j.source_type IN ('LOAN_RECORD_DISBURSEMENT','LOAN_CHARGE_FUNDING') AND b.applicant_member_id IS NULL
    AND b.principal=p.amount AND p.amount=(f.snapshot->>'principal')::numeric
    AND l.first_repayment_date=(SELECT (form_data->>'firstPaymentDate')::date FROM lending_group_cycle WHERE id=f.cycle_id)
    AND (SELECT sum(credit-debit) FROM gl_journal_line WHERE journal_id=j.id AND account_id=p.money_account_id)=(f.snapshot->>'net')::numeric
    AND (SELECT sum(debit-credit) FROM gl_journal_line WHERE journal_id=j.id AND account_id=p.principal_account_id)=p.amount)
  OR (SELECT count(*) FROM lending_group_shared_transfer WHERE cycle_id=f.cycle_id)<>n
  OR EXISTS(SELECT 1 FROM lending_group_shared_transfer t JOIN lending_group_shared_allocation s ON s.cycle_id=t.cycle_id AND s.client_id=t.client_id
    WHERE t.cycle_id=f.cycle_id AND t.amount<>s.net)
  OR (SELECT count(*) FROM loan_ledger_installments WHERE loan_application_id=f.loan_id)<>jsonb_array_length(f.snapshot->'schedule')
  OR EXISTS(SELECT 1 FROM jsonb_array_elements(f.snapshot->'schedule') d LEFT JOIN loan_ledger_installments i ON i.loan_application_id=f.loan_id AND i.installment_number=(d->>'number')::integer
    WHERE ROW(i.due_date,i.principal,i.interest) IS DISTINCT FROM ROW((d->>'date')::date,(d->>'principal')::numeric,(d->>'interest')::numeric))
 THEN RAISE EXCEPTION 'Shared parent ledger, all beneficiary transfers and cash must conserve their signed source'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_funding_commit AFTER INSERT ON lending_group_shared_funding DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_funding_commit();
CREATE CONSTRAINT TRIGGER shared_transfer_commit AFTER INSERT ON lending_group_shared_transfer DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_funding_commit();
CREATE OR REPLACE FUNCTION lending_group_organization_funding_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.applicant_member_id IS NULL AND NEW.status IN ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')
   AND NOT EXISTS(SELECT 1 FROM lending_group_org_funding f WHERE f.loan_id=NEW.id AND f.sacco_id=NEW.sacco_id AND f.station_id=NEW.station_id)
   AND NOT EXISTS(SELECT 1 FROM lending_group_shared_funding f WHERE f.loan_id=NEW.id AND f.sacco_id=NEW.sacco_id AND f.station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Typed group borrower requires its lender execution and complete funding'; END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE definition text; BEGIN
 SELECT pg_get_functiondef('loan_ledger_borrower_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'p.kind=''ORGANIZATION''')=0 THEN RAISE EXCEPTION 'Typed ledger marker missing'; END IF;
 definition=replace(definition,'p.kind=''ORGANIZATION''','p.kind IN (''ORGANIZATION'',''JOINT_GROUP'')');
 definition=replace(definition,'FROM lending_group_org_funding f','FROM (SELECT loan_id,sacco_id,station_id FROM lending_group_org_funding UNION ALL SELECT loan_id,sacco_id,station_id FROM lending_group_shared_funding) f');
 EXECUTE definition;
 SELECT pg_get_functiondef('accountant_loan_receipt_borrower_guard()'::regprocedure) INTO definition;
 IF strpos(definition,'p.kind=''ORGANIZATION''')=0 THEN RAISE EXCEPTION 'Typed receipt marker missing'; END IF;
 EXECUTE replace(definition,'p.kind=''ORGANIZATION''','p.kind IN (''ORGANIZATION'',''JOINT_GROUP'')');
END $$;

CREATE TABLE lending_group_shared_movement (
 posting_id uuid NOT NULL REFERENCES accountant_loan_post(id),cycle_id uuid NOT NULL,client_id uuid NOT NULL,
 sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,payer varchar(160) NOT NULL CHECK(length(trim(payer))>0),
 principal numeric(18,2) NOT NULL CHECK(principal>=0),interest numeric(18,2) NOT NULL CHECK(interest>=0),charges numeric(18,2) NOT NULL CHECK(charges>=0),
 excluded_interest numeric(18,2) NOT NULL CHECK(excluded_interest>=0),reverses_posting_id uuid REFERENCES accountant_loan_post(id),
 PRIMARY KEY(posting_id,client_id),FOREIGN KEY(cycle_id,client_id) REFERENCES lending_group_shared_allocation(cycle_id,client_id),
 FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_shared_document(cycle_id,sacco_id,station_id)
);
CREATE INDEX ix_shared_movement_balance ON lending_group_shared_movement(cycle_id,client_id,posting_id);
CREATE TRIGGER shared_movement_immutable BEFORE UPDATE OR DELETE ON lending_group_shared_movement FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE FUNCTION lending_group_shared_movement_commit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE f lending_group_shared_facility;p accountant_loan_post; posting uuid; total record;
BEGIN
 IF TG_TABLE_NAME='accountant_loan_post' THEN posting=NEW.id; ELSE posting=NEW.posting_id; END IF;
 SELECT * INTO p FROM accountant_loan_post WHERE id=posting;
 SELECT * INTO f FROM lending_group_shared_facility WHERE loan_id=p.loan_id;
 IF f.cycle_id IS NULL OR p.kind NOT IN ('PAYMENT','REVERSAL') THEN RETURN NULL; END IF;
 SELECT count(*) n,sum(principal) principal,sum(interest) interest,sum(charges) charges,sum(excluded_interest) excluded INTO total FROM lending_group_shared_movement WHERE posting_id=p.id;
 IF p.state<>'POSTED' OR total.n<>(SELECT count(*) FROM lending_group_shared_allocation WHERE cycle_id=f.cycle_id)
  OR ROW(total.principal,total.interest,total.charges,total.excluded) IS DISTINCT FROM ROW(p.principal_amount,p.interest_amount,p.charge_amount,
    coalesce((SELECT future_interest_excluded FROM loan_repayment_transactions WHERE id=p.transaction_id),0))
  OR EXISTS(SELECT 1 FROM lending_group_shared_movement x WHERE x.posting_id=p.id AND ROW(x.cycle_id,x.sacco_id,x.station_id,x.reverses_posting_id)
    IS DISTINCT FROM ROW(f.cycle_id,p.sacco_id,p.station_id,p.reverses_id))
  OR (p.kind='REVERSAL' AND EXISTS(SELECT 1 FROM lending_group_shared_movement x LEFT JOIN lending_group_shared_movement o ON o.posting_id=p.reverses_id AND o.client_id=x.client_id
    WHERE x.posting_id=p.id AND ROW(x.payer,x.principal,x.interest,x.charges,x.excluded_interest) IS DISTINCT FROM ROW(o.payer,o.principal,o.interest,o.charges,o.excluded_interest)))
 THEN RAISE EXCEPTION 'Shared repayment attribution must conserve its one owning payment and linked correction'; END IF;
 IF EXISTS(SELECT 1 FROM lending_group_shared_allocation s JOIN LATERAL (
   SELECT coalesce(sum(CASE WHEN t.kind='REVERSAL' THEN -x.principal ELSE x.principal END),0) principal,
     coalesce(sum(CASE WHEN t.kind='REVERSAL' THEN -x.interest ELSE x.interest END),0) interest,
     coalesce(sum(CASE WHEN t.kind='REVERSAL' THEN -x.charges ELSE x.charges END),0) charges,
     coalesce(sum(CASE WHEN t.kind='REVERSAL' THEN -x.excluded_interest ELSE x.excluded_interest END),0) excluded
   FROM lending_group_shared_movement x JOIN accountant_loan_post t ON t.id=x.posting_id WHERE x.cycle_id=s.cycle_id AND x.client_id=s.client_id) a ON true
   WHERE s.cycle_id=f.cycle_id AND (a.principal<0 OR a.principal>s.principal OR a.interest<0 OR a.excluded<0 OR a.interest+a.excluded>s.interest OR a.charges<0 OR a.charges>s.charges-s.deductions))
 THEN RAISE EXCEPTION 'Shared attributed components cannot exceed or become negative against their frozen share'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER shared_movement_commit AFTER INSERT ON lending_group_shared_movement DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_movement_commit();
CREATE CONSTRAINT TRIGGER shared_posting_attribution AFTER INSERT OR UPDATE ON accountant_loan_post DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION lending_group_shared_movement_commit();
