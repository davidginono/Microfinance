-- Independent source evidence only. No money, loan history, or favourable opening is seeded.
CREATE TABLE accounting_business_opening (
 id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 request_key uuid NOT NULL,account_id uuid NOT NULL,general_ledger_opening_id uuid,
 through_date date NOT NULL,purpose varchar(40) NOT NULL CHECK(purpose IN('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','ALLOWANCE',
 'SUPPLIER_PAYABLE','FUNDING_PRINCIPAL','UNAPPLIED_FUNDS','INTERNAL_DUE_FROM','INTERNAL_DUE_TO')),
 complete_coverage boolean NOT NULL,signed_balance numeric NOT NULL CHECK(scale(signed_balance)<=2 AND abs(signed_balance)<10000000000000000),
 maker_id uuid NOT NULL REFERENCES members(id),evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),
 filename varchar(160) NOT NULL,file_bytes bytea NOT NULL CHECK(octet_length(file_bytes) BETWEEN 1 AND 1000000),
 file_checksum varchar(64) NOT NULL CHECK(file_checksum=encode(sha256(file_bytes),'hex')),
 preview_json text NOT NULL CHECK(octet_length(preview_json)<=1000000),
 payload_checksum varchar(64) NOT NULL CHECK(payload_checksum=encode(sha256(convert_to(preview_json,'UTF8')),'hex')),
 imported_at timestamptz NOT NULL,
 FOREIGN KEY(account_id,sacco_id) REFERENCES gl_account(id,sacco_id),
 FOREIGN KEY(general_ledger_opening_id,sacco_id,station_id) REFERENCES gl_journal(id,sacco_id,station_id),
 UNIQUE(id,sacco_id,station_id,account_id),UNIQUE(sacco_id,station_id,request_key),
 UNIQUE(sacco_id,station_id,account_id,through_date,file_checksum)
);
CREATE INDEX ix_business_opening_scope ON accounting_business_opening(sacco_id,station_id,imported_at DESC,id);
CREATE TABLE accounting_business_opening_review (
 opening_id uuid PRIMARY KEY,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,account_id uuid NOT NULL,
 reviewer_id uuid NOT NULL REFERENCES members(id),decision varchar(10) NOT NULL CHECK(decision IN('APPROVED','REJECTED')),
 evidence varchar(500) NOT NULL CHECK(length(trim(evidence))>0),reviewed_at timestamptz NOT NULL,
 FOREIGN KEY(opening_id,sacco_id,station_id,account_id) REFERENCES accounting_business_opening(id,sacco_id,station_id,account_id)
);
CREATE UNIQUE INDEX ux_business_opening_approved ON accounting_business_opening_review(sacco_id,station_id,account_id) WHERE decision='APPROVED';
CREATE TRIGGER business_opening_immutable BEFORE UPDATE OR DELETE ON accounting_business_opening FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER business_opening_review_immutable BEFORE UPDATE OR DELETE ON accounting_business_opening_review FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();

CREATE FUNCTION business_opening_validate_import() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v jsonb; c jsonb; row_count integer; total numeric; lines text[]; cells text[]; i integer;
BEGIN
 v:=NEW.preview_json::jsonb;c:=v->'command';
 IF NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.maker_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=NEW.account_id AND sacco_id=NEW.sacco_id AND active AND kind<>'HEADING')
 OR ROW(c->>'requestKey',c->>'account',c->>'generalLedgerOpening',c->>'through',c->>'purpose',c->>'evidence',c->>'completeCoverage',v->>'filename',v->>'fileChecksum')
 IS DISTINCT FROM ROW(NEW.request_key::text,NEW.account_id::text,NEW.general_ledger_opening_id::text,NEW.through_date::text,NEW.purpose,NEW.evidence,NEW.complete_coverage::text,NEW.filename,NEW.file_checksum)
 OR jsonb_typeof(v->'rows') IS DISTINCT FROM 'array'
 THEN RAISE EXCEPTION 'Scoped source register metadata required'; END IF;
 row_count:=jsonb_array_length(v->'rows');
 IF row_count>1000 OR EXISTS(SELECT 1 FROM jsonb_to_recordset(v->'rows') AS r(reference text,"signedBalance" numeric,evidence text)
 WHERE r.reference IS NULL OR length(trim(r.reference)) NOT BETWEEN 1 AND 160 OR r.evidence IS NULL OR length(trim(r.evidence)) NOT BETWEEN 1 AND 500
 OR r."signedBalance" IS NULL OR r."signedBalance"=0 OR scale(r."signedBalance")>2 OR abs(r."signedBalance")>=10000000000000000)
 OR (SELECT count(DISTINCT r->>'reference') FROM jsonb_array_elements(v->'rows') r)<>row_count
 THEN RAISE EXCEPTION 'Bounded distinct exact source rows required'; END IF;
 SELECT coalesce(sum(r."signedBalance"),0) INTO total FROM jsonb_to_recordset(v->'rows') AS r("signedBalance" numeric);
 IF total<>NEW.signed_balance OR (v->>'signedBalance')::numeric IS DISTINCT FROM total
 THEN RAISE EXCEPTION 'Source register total must equal retained rows'; END IF;
 lines:=string_to_array(convert_from(NEW.file_bytes,'UTF8'),E'\n');
 IF array_length(lines,1)>1 AND lines[array_length(lines,1)]='' THEN lines:=lines[1:array_length(lines,1)-1]; END IF;
 IF array_length(lines,1)<>row_count+1 OR rtrim(lines[1],E'\r')<>'reference,signed_balance,evidence'
 OR NEW.filename~'[[:cntrl:]/\\]' THEN RAISE EXCEPTION 'Retained UTF8 source register format required'; END IF;
 FOR i IN 1..row_count LOOP
 cells:=string_to_array(rtrim(lines[i+1],E'\r'),',');
 IF array_length(cells,1)<>3 OR cells[2]!~'^-?[0-9]{1,16}(\.[0-9]{1,2})?$'
 OR cells[1]~'[[:cntrl:]]' OR cells[3]~'[[:cntrl:]]'
 OR cells[1] IS DISTINCT FROM v->'rows'->(i-1)->>'reference'
 OR cells[3] IS DISTINCT FROM v->'rows'->(i-1)->>'evidence'
 OR cells[2]::numeric IS DISTINCT FROM (v->'rows'->(i-1)->>'signedBalance')::numeric
 THEN RAISE EXCEPTION 'Retained source bytes and parsed rows differ'; END IF;
 END LOOP;
 RETURN NEW;
END; $$;
CREATE TRIGGER business_opening_import_validation BEFORE INSERT ON accounting_business_opening FOR EACH ROW EXECUTE FUNCTION business_opening_validate_import();

-- The common lock order serializes reviewed source coverage with postings and branch/institution closing.
CREATE FUNCTION business_opening_assert_reviewable(source_id uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE s accounting_business_opening; j gl_journal; period_count integer; opening_total numeric;
BEGIN
 SELECT * INTO STRICT s FROM accounting_business_opening WHERE id=source_id;
 PERFORM pg_advisory_xact_lock_shared(hashtextextended('GL_SETUP/'||s.sacco_id,0));
 SELECT count(*) INTO period_count FROM (SELECT id FROM accounting_period WHERE sacco_id=s.sacco_id ORDER BY starts_on,id LIMIT 1001 FOR UPDATE) locked;
 IF period_count=0 OR period_count>1000 OR EXISTS(SELECT 1 FROM accounting_period WHERE sacco_id=s.sacco_id AND state<>'OPEN')
 OR EXISTS(SELECT 1 FROM accounting_close_review r JOIN accounting_close_decision d ON d.review_id=r.id
 WHERE r.sacco_id=s.sacco_id AND r.station_id=s.station_id AND r.action='CLOSE'
 AND d.decided_at>coalesce((SELECT max(rd.decided_at) FROM accounting_close_review rr JOIN accounting_close_decision rd ON rd.review_id=rr.id
 WHERE rr.sacco_id=s.sacco_id AND rr.period_id=r.period_id AND rr.action='REOPEN'),'-infinity'))
 THEN RAISE EXCEPTION 'Source coverage requires open periods and controlled reopening of approved branch closes'; END IF;
 SELECT * INTO j FROM gl_journal WHERE id=s.general_ledger_opening_id AND sacco_id=s.sacco_id AND station_id=s.station_id;
 IF NOT FOUND OR NOT s.complete_coverage OR s.through_date>CURRENT_DATE OR j.state<>'POSTED' OR j.source_type<>'OPENING' OR j.effective_date<>s.through_date
 OR NOT EXISTS(SELECT 1 FROM gl_account WHERE id=s.account_id AND sacco_id=s.sacco_id AND active AND kind<>'HEADING')
 OR NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c WHERE c.opening_journal_id=j.id AND c.complete AND c.reconciled_through=s.through_date)
 OR NOT EXISTS(SELECT 1 FROM accounting_policies p JOIN accounting_policy_approvals a ON a.policy_id=p.id
 WHERE p.id=j.policy_id AND p.sacco_id=s.sacco_id AND p.policy_version=j.policy_version AND a.decision='APPROVED'
 AND p.authoritative_ledger='LOCAL_GL' AND p.account_mappings_json::jsonb->>s.purpose=s.account_id::text)
 THEN RAISE EXCEPTION 'Complete reviewed source and mapped posted GL opening required'; END IF;
 SELECT coalesce(sum(debit-credit),0) INTO opening_total FROM gl_journal_line WHERE journal_id=j.id AND account_id=s.account_id;
 IF opening_total<>s.signed_balance THEN RAISE EXCEPTION 'Source register and GL opening differ'; END IF;
 IF s.purpose IN('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','ALLOWANCE','INTERNAL_DUE_FROM','INTERNAL_DUE_TO')
 AND jsonb_array_length(s.preview_json::jsonb->'rows')<>0
 THEN RAISE EXCEPTION 'Verified legacy loan or paired internal opening adapter required'; END IF;
 IF s.purpose IN('LOAN_PRINCIPAL','INTEREST_RECEIVABLE','ALLOWANCE') AND (
 EXISTS(SELECT 1 FROM loan_ledgers WHERE sacco_id=s.sacco_id AND station_id=s.station_id AND disbursement_date<=s.through_date)
 OR EXISTS(SELECT 1 FROM loan_applications WHERE sacco_id=s.sacco_id AND station_id=s.station_id AND disbursement_date<=s.through_date))
 THEN RAISE EXCEPTION 'Legacy loan source history cannot be certified empty'; END IF;
END; $$;
CREATE FUNCTION business_opening_validate_review() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s accounting_business_opening;
BEGIN
 SELECT * INTO STRICT s FROM accounting_business_opening WHERE id=NEW.opening_id FOR UPDATE;
 IF NEW.reviewer_id=s.maker_id OR NEW.reviewed_at<s.imported_at
 OR NOT EXISTS(SELECT 1 FROM members WHERE id=NEW.reviewer_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id)
 THEN RAISE EXCEPTION 'Independent scoped source-opening review required'; END IF;
 IF NEW.decision='APPROVED' THEN PERFORM business_opening_assert_reviewable(s.id); END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER business_opening_review_validation BEFORE INSERT ON accounting_business_opening_review FOR EACH ROW EXECUTE FUNCTION business_opening_validate_review();
