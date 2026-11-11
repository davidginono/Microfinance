-- Physical signatures are recorded by staff, never represented as client logins or OTPs.
CREATE TABLE lending_group_signed_paper (
 id uuid PRIMARY KEY,cycle_id uuid NOT NULL,sacco_id varchar(255) NOT NULL,station_id varchar(255) NOT NULL,
 kind text NOT NULL CHECK(kind IN('TERMS','SHARED_PAYOUT','ORG_AGREEMENT','ORG_PAYOUT','PERSONAL')),
 client_id uuid NOT NULL REFERENCES members(id),offer_id uuid REFERENCES lending_group_personal_guarantee(id),
 contract jsonb NOT NULL,expected_row jsonb NOT NULL,reference varchar(500) NOT NULL CHECK(length(trim(reference))>0),
 signed_at timestamptz NOT NULL,recorded_by uuid NOT NULL REFERENCES members(id),recorded_at timestamptz NOT NULL,
 filename varchar(200) NOT NULL,sha256 varchar(64) NOT NULL,body bytea NOT NULL CHECK(octet_length(body) BETWEEN 5 AND 10485760),
 UNIQUE(cycle_id,kind,client_id),FOREIGN KEY(cycle_id,sacco_id,station_id) REFERENCES lending_group_cycle(id,sacco_id,station_id)
);
CREATE INDEX ix_group_paper_scope ON lending_group_signed_paper(sacco_id,station_id,cycle_id);
CREATE TRIGGER group_paper_immutable BEFORE UPDATE OR DELETE ON lending_group_signed_paper FOR EACH ROW EXECUTE FUNCTION gl_protect_evidence();
CREATE TRIGGER group_paper_no_truncate BEFORE TRUNCATE ON lending_group_signed_paper FOR EACH STATEMENT EXECUTE FUNCTION gl_protect_evidence();
ALTER TABLE lending_group_consent_evidence ADD COLUMN paper_id uuid UNIQUE REFERENCES lending_group_signed_paper(id);
ALTER TABLE lending_group_shared_payout ADD COLUMN paper_id uuid UNIQUE REFERENCES lending_group_signed_paper(id);
ALTER TABLE lending_group_org_signature ADD COLUMN paper_id uuid UNIQUE REFERENCES lending_group_signed_paper(id);
ALTER TABLE lending_group_org_payout ADD COLUMN paper_id uuid UNIQUE REFERENCES lending_group_signed_paper(id);
ALTER TABLE lending_group_personal_decision ADD COLUMN paper_id uuid UNIQUE REFERENCES lending_group_signed_paper(id);

-- The selected person is explicit; the acting staff member is retained separately.
CREATE FUNCTION lending_group_paper_contract(cycle uuid,kind text,client uuid,offer uuid,destination jsonb)
RETURNS jsonb LANGUAGE plpgsql STABLE AS $$
DECLARE result jsonb;
BEGIN
 IF kind='TERMS' THEN
  SELECT jsonb_build_object('cycle',c.id,'policy',to_jsonb(p),'person',client,
    'participants',(SELECT jsonb_agg(to_jsonb(x) ORDER BY x.id) FROM lending_group_participant x WHERE x.cycle_id=c.id),
    'guarantees',(SELECT jsonb_agg(to_jsonb(x) ORDER BY x.loan_id,x.guarantor_id) FROM lending_group_guarantee x WHERE x.cycle_id=c.id),
    'loans',(SELECT jsonb_agg(jsonb_build_array(l.id,l.amount,l.tenor_months,l.form_data,l.financial_snapshot,l.policy_snapshot,l.repayment_frequency) ORDER BY l.id)
      FROM loan_applications l WHERE l.id IN(SELECT loan_id FROM lending_group_participant WHERE cycle_id=c.id UNION SELECT loan_id FROM lending_group_shared_facility WHERE cycle_id=c.id)))
  INTO result FROM lending_group_cycle c JOIN lending_group_policy p ON p.id=c.policy_id
  WHERE c.id=cycle AND EXISTS(SELECT 1 FROM lending_group_participant x WHERE x.cycle_id=c.id AND x.client_id=client);
 ELSIF kind IN('ORG_AGREEMENT','ORG_PAYOUT') THEN
  SELECT snapshot INTO result FROM lending_group_org_agreement WHERE cycle_id=cycle;
 ELSIF kind='SHARED_PAYOUT' THEN
  SELECT jsonb_build_object('packet',lending_group_shared_packet(cycle,client,destination),'destination',destination,'net',net)
  INTO result FROM lending_group_shared_allocation WHERE cycle_id=cycle AND client_id=client;
 ELSIF kind='PERSONAL' THEN
  SELECT jsonb_build_object('offer',id,'packet',packet_hash,'cap',amount_cap,'client',client_id,'loan',loan_id)
  INTO result FROM lending_group_personal_guarantee WHERE id=offer AND cycle_id=cycle AND client_id=client;
 END IF;
 IF result IS NULL THEN RAISE EXCEPTION 'group.error.changed'; END IF;
 RETURN result;
END $$;

CREATE FUNCTION lending_group_paper_expected(document lending_group_signed_paper) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; m members; p lending_group_org_agreement; g lending_group_personal_guarantee;
 s lending_group_shared_allocation; d lending_group_shared_document; contract jsonb; row_data jsonb;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=document.cycle_id;
 SELECT * INTO m FROM members WHERE id=document.client_id;
 contract=document.contract;
 row_data=jsonb_build_object('cycle_id',c.id,'sacco_id',c.sacco_id,'station_id',c.station_id,'client_id',m.id,
  'signature_text','Signed paper / '||m.full_name,'otp_required',false,'challenge_id',null);
 IF document.kind='TERMS' THEN
  row_data=row_data||jsonb_build_object('policy_id',c.policy_id,'signed_at',document.signed_at,'preferred_language',document.expected_row->>'preferred_language');
 ELSIF document.kind='SHARED_PAYOUT' THEN
  SELECT * INTO s FROM lending_group_shared_allocation WHERE cycle_id=c.id AND client_id=m.id;
  SELECT * INTO d FROM lending_group_shared_document WHERE cycle_id=c.id;
  IF s.client_id IS NULL OR d.id IS NULL OR document.signed_at<d.recorded_at
   OR NOT EXISTS(SELECT 1 FROM lending_group_consent_evidence WHERE cycle_id=c.id AND client_id=m.id)
   OR coalesce((contract->'destination'->>'available')::boolean,false) IS NOT TRUE
   OR contract->'destination'->>'destinationType' NOT IN('BANK_ACCOUNT','MOBILE_MONEY','OTHER')
   OR EXISTS(SELECT 1 FROM unnest(ARRAY['provider','accountHolderName','accountIdentifier']) k WHERE length(trim(coalesce(contract->'destination'->>k,'')))=0)
  THEN RAISE EXCEPTION 'group.error.sharedDestination'; END IF;
  row_data=row_data||jsonb_build_object('document_id',d.id,'packet_hash',contract->>'packet','destination',contract->'destination',
   'net_amount',s.net,'accepted_at',document.signed_at,'preferred_language',document.expected_row->>'preferred_language');
 ELSIF document.kind IN('ORG_AGREEMENT','ORG_PAYOUT') THEN
  SELECT * INTO p FROM lending_group_org_agreement WHERE cycle_id=c.id;
  IF document.kind='ORG_AGREEMENT' THEN
   PERFORM lending_group_org_signing_scope(c.id,m.id,c.sacco_id,c.station_id);
   IF document.signed_at<p.prepared_at OR p.snapshot->'document' IS NULL OR p.snapshot->'document'='null'::jsonb THEN RAISE EXCEPTION 'group.error.documentRequired'; END IF;
   row_data=row_data||jsonb_build_object('packet_hash',p.packet_hash,'signed_at',document.signed_at,'preferred_language',document.expected_row->>'preferred_language');
  ELSE
   PERFORM lending_group_org_payout_scope(c.id,m.id,c.sacco_id,c.station_id,document.signed_at);
   row_data=row_data||jsonb_build_object('packet_hash',p.packet_hash,'principal',(p.snapshot->'loan'->>'principal')::numeric,
    'deductions',(p.snapshot->'financial'->>'recordedChargeDeductions')::numeric,'net_amount',(p.snapshot->'financial'->>'recordedNetCashEstimate')::numeric,
    'destination',p.snapshot->'payout','client_name',m.full_name,'client_number',m.member_no,'authorized_at',document.signed_at);
  END IF;
 ELSE
  SELECT * INTO g FROM lending_group_personal_guarantee WHERE id=document.offer_id;

  IF g.client_id IS DISTINCT FROM m.id OR g.cycle_id IS DISTINCT FROM c.id OR document.signed_at<g.recorded_at
   OR (document.signed_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date>g.valid_through
   OR NOT EXISTS(SELECT 1 FROM loan_applications WHERE id=g.loan_id AND status='AWAITING_GUARANTORS')
  THEN RAISE EXCEPTION 'group.error.personalGuaranteesClosed'; END IF;

  row_data=(row_data-'cycle_id'-'sacco_id'-'station_id')||jsonb_build_object('offer_id',g.id,'decision','ACCEPTED','reason',document.reference,
   'actor_id',document.recorded_by,'packet_hash',g.packet_hash,'decided_at',document.signed_at);
 END IF;
 RETURN row_data;
END $$;

CREATE FUNCTION lending_group_paper_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c lending_group_cycle; m members; p lending_group_org_agreement; g lending_group_personal_guarantee;
 s lending_group_shared_allocation; d lending_group_shared_document; contract jsonb; row_data jsonb;
BEGIN
 SELECT * INTO c FROM lending_group_cycle WHERE id=NEW.cycle_id FOR UPDATE;
 SELECT * INTO m FROM members WHERE id=NEW.client_id AND sacco_id=NEW.sacco_id AND station_id=NEW.station_id AND status='ACTIVE' AND is_member;
 IF c.state IS DISTINCT FROM 'SUBMITTED' OR m.id IS NULL OR c.sacco_id<>NEW.sacco_id OR c.station_id<>NEW.station_id
  OR NEW.signed_at>NEW.recorded_at OR NEW.signed_at<c.submitted_at
  OR NEW.sha256 IS DISTINCT FROM encode(sha256(NEW.body),'hex') OR substring(NEW.body FROM 1 FOR 5)<>convert_to('%PDF-','UTF8')
  OR NOT EXISTS(SELECT 1 FROM members a WHERE a.id=NEW.recorded_by AND a.id<>NEW.client_id
   AND a.sacco_id=NEW.sacco_id AND a.station_id=NEW.station_id AND a.status='ACTIVE'
   AND (a.staff_access_status='ACTIVE' OR (a.staff_access_status='NONE' AND a.position<>'MEMBER')))
 THEN RAISE EXCEPTION 'group.error.scope'; END IF;
 contract=lending_group_paper_contract(c.id,NEW.kind,m.id,NEW.offer_id,NEW.contract->'destination');
 IF NEW.contract IS DISTINCT FROM contract THEN RAISE EXCEPTION 'group.error.changed'; END IF;
 row_data=lending_group_paper_expected(NEW);
 IF NEW.kind='PERSONAL' THEN
  SELECT * INTO g FROM lending_group_personal_guarantee WHERE id=NEW.offer_id;
  PERFORM id FROM loan_applications WHERE id=g.loan_id FOR UPDATE;
  PERFORM lending_personal_capacity(m.id,c.sacco_id,c.station_id,g.loan_id,g.amount_cap);
 END IF;
 IF NOT NEW.expected_row @> row_data OR NOT row_data @> NEW.expected_row THEN RAISE EXCEPTION 'group.error.changed'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_paper_scope BEFORE INSERT ON lending_group_signed_paper FOR EACH ROW EXECUTE FUNCTION lending_group_paper_insert();

CREATE FUNCTION lending_group_paper_row(table_name text,row_data jsonb) RETURNS void LANGUAGE plpgsql AS $$
DECLARE p lending_group_signed_paper; kind text;
BEGIN
 kind=CASE table_name WHEN 'lending_group_consent_evidence' THEN 'TERMS' WHEN 'lending_group_shared_payout' THEN 'SHARED_PAYOUT'
  WHEN 'lending_group_org_signature' THEN 'ORG_AGREEMENT' WHEN 'lending_group_org_payout' THEN 'ORG_PAYOUT' WHEN 'lending_group_personal_decision' THEN 'PERSONAL' END;
 SELECT * INTO p FROM lending_group_signed_paper WHERE id=(row_data->>'paper_id')::uuid;
 IF p.id IS NULL OR p.kind IS DISTINCT FROM kind OR NOT row_data @> p.expected_row THEN RAISE EXCEPTION 'group.error.changed'; END IF;
END $$;
-- Retain the portal proof checks for old evidence, and validate physical proof explicitly.
DO $$ DECLARE name text; definition text; marker text='BEGIN'; BEGIN
 FOREACH name IN ARRAY ARRAY['lending_group_signed_consent_guard','lending_group_shared_payout_guard',
  'lending_group_org_signature_guard','lending_group_org_full_document_signature_guard','lending_group_org_payout_guard','lending_personal_evidence_guard'] LOOP
  SELECT pg_get_functiondef((name||'()')::regprocedure) INTO definition;
  EXECUTE overlay(definition PLACING $guard$BEGIN
   IF TG_OP='INSERT' AND to_jsonb(NEW)->>'paper_id' IS NOT NULL THEN
    PERFORM lending_group_paper_row(TG_TABLE_NAME,to_jsonb(NEW)); RETURN NEW;
   END IF;$guard$ FROM strpos(definition,marker) FOR length(marker));
 END LOOP;
END $$;
-- Physical evidence does not claim that a client opened an online document.
DO $$ DECLARE constraint_name text; BEGIN
 FOR constraint_name IN SELECT conname FROM pg_constraint WHERE conrelid='lending_group_shared_payout'::regclass AND confrelid='lending_group_shared_opened'::regclass LOOP
  EXECUTE format('ALTER TABLE lending_group_shared_payout DROP CONSTRAINT %I',constraint_name);
 END LOOP;
END $$;
CREATE FUNCTION lending_group_shared_opened_required() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.paper_id IS NULL AND NOT EXISTS(SELECT 1 FROM lending_group_shared_opened WHERE cycle_id=NEW.cycle_id AND client_id=NEW.client_id AND packet_hash=NEW.packet_hash)
 THEN RAISE EXCEPTION 'Opened portal document or recorded signed paper required'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER shared_opened_required BEFORE INSERT ON lending_group_shared_payout FOR EACH ROW EXECUTE FUNCTION lending_group_shared_opened_required();
