package com.sacco.mvp.accounting.charges.repository;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanChargeRepository {
    public void clearUnpostedForEdit(UUID loan){jdbc.update("delete from loan_charge_term where loan_id=?",loan);jdbc.update("delete from loan_charge_contract where loan_id=?",loan);}
    private final JdbcTemplate jdbc;
    public record Loan(UUID id,UUID client,String status,LocalDate applicationDate,UUID source,String number,String institution,String branch) {}
    public Loan loan(AppUserPrincipal a,UUID id,boolean lock){return jdbc.query("select l.id,l.applicant_member_id,l.status,r.application_date,l.top_up_source_loan_id,coalesce(l.loan_id,l.application_number::text),i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) from accountant_loan_record r join loan_applications l on l.id=r.loan_id join registered_saccos i on i.sacco_id=r.sacco_id join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id where r.loan_id=? and r.sacco_id=? and r.station_id=?"+(lock?" for update of r":""),(r,n)->new Loan(id,r.getObject(2,UUID.class),r.getString(3),r.getObject(4,LocalDate.class),r.getObject(5,UUID.class),r.getString(6),r.getString(7),r.getString(8)),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Loan unavailable"));}
    public void policyLock(String institution,UUID product){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_CHARGES/"+institution+"/"+product);}
    public Optional<Policy> policy(String institution,UUID product,LocalDate date){return jdbc.query("select * from loan_charge_policy where sacco_id=? and product_id=? and effective_from<=? order by effective_from desc,revision_no desc limit 1",this::policy,institution,product,date).stream().findFirst();}
    private Policy policy(java.sql.ResultSet r,int index) throws java.sql.SQLException {
        return new Policy(r.getObject("id",UUID.class),r.getObject("product_id",UUID.class),r.getObject("effective_from",LocalDate.class),r.getBoolean("enabled"),Timing.valueOf(r.getString("management_timing")),Timing.valueOf(r.getString("insurance_timing")),r.getObject("receivable_account_id",UUID.class),r.getObject("management_account_id",UUID.class),r.getObject("insurance_account_id",UUID.class),r.getString("policy_reference"),r.getString("agreement_reference"),r.getString("insurer"),r.getString("insurer_verification"),r.getBoolean("allow_custom"),r.getBigDecimal("custom_management_limit"),r.getInt("accounting_version"),r.getObject("application_account_id",UUID.class),r.getObject("processing_account_id",UUID.class),r.getObject("fee_recovery_account_id",UUID.class));
    }
    public Choice productChoice(String institution,UUID product){return jdbc.query("select id,coalesce(product_name,loan_type) label from loan_product_settings where sacco_id=? and id=?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),""),institution,product).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Product unavailable"));}
    public Choice selectedAccount(String institution,UUID id,String kind){return jdbc.query("select id,code||' - '||name label from gl_account where sacco_id=? and id=? and active and "+accountWhere(kind),(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),kind),institution,id).stream().findFirst().orElse(null);}
    public boolean product(String institution,UUID product){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_product_settings where id=? and sacco_id=?)",Boolean.class,product,institution));}
    public boolean requiresInsurance(String institution,UUID product){return Boolean.TRUE.equals(jdbc.queryForObject("select coalesce(insurance_rate,0)>0 from loan_product_settings where sacco_id=? and id=? for share",Boolean.class,institution,product));}
    public Optional<String> accountName(String institution,UUID id,String kind){return jdbc.query("select name from gl_account where sacco_id=? and id=? and active and "+accountWhere(kind),(r,n)->r.getString(1),institution,id).stream().findFirst();}
    public Optional<UUID> policyRetry(AppUserPrincipal a,UUID key,String hash){var rows=jdbc.queryForList("select id,payload_hash,actor_id from loan_charge_policy where sacco_id=? and request_key=?",a.getSaccoId(),key);if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("actor_id")))throw new IllegalArgumentException("charges.error.retry");return Optional.of((UUID)row.get("id"));}
    public UUID insertPolicy(AppUserPrincipal a,PolicyForm f,String hash,OffsetDateTime now){var id=UUID.randomUUID();jdbc.update("insert into loan_charge_policy(id,sacco_id,product_id,effective_from,enabled,management_timing,insurance_timing,receivable_account_id,management_account_id,insurance_account_id,policy_reference,agreement_reference,insurer,insurer_verification,allow_custom,custom_management_limit,request_key,payload_hash,actor_id,recorded_at,accounting_version,application_account_id,processing_account_id,fee_recovery_account_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,2,?,?,?)",id,a.getSaccoId(),f.getProductId(),f.getEffectiveFrom(),f.isEnabled(),f.getManagementTiming().name(),f.getInsuranceTiming().name(),f.getReceivableAccountId(),f.getManagementAccountId(),f.getInsuranceAccountId(),f.getPolicyReference().strip(),f.getAgreementReference().strip(),f.getInsurer().strip(),f.getInsurerVerification().strip(),f.isAllowCustom(),f.getCustomManagementLimit(),f.getRequestKey(),hash,a.getMemberId(),now,f.getApplicationAccountId(),f.getProcessingAccountId(),f.getFeeRecoveryAccountId());return id;}
    private String accountWhere(String kind){return switch(kind){case "RECEIVABLE"->"type='ASSET' and kind in ('POSTING','CONTROL') and purpose='FEE_RECEIVABLE'";case "APPLICATION","PROCESSING"->"type='INCOME' and kind='POSTING' and purpose='INCOME'";case "MANAGEMENT"->"type='LIABILITY' and kind in ('POSTING','CONTROL') and purpose='CLEARING'";case "INSURANCE"->"type='LIABILITY' and kind in ('POSTING','CONTROL') and purpose='PAYABLE'";default->throw new IllegalArgumentException("charges.error.account");};}
    public void account(String institution,UUID id,String kind){account(institution,id,kind,true);}
    public void account(String institution,UUID id,String kind,boolean lock){if(id==null || jdbc.queryForList("select id from gl_account where sacco_id=? and id=? and active and "+accountWhere(kind)+(lock?" for share":""),institution,id).isEmpty())throw new IllegalArgumentException("charges.error.account");}
    public List<Choice> accounts(String institution,String kind,String search,int page){return jdbc.query("select id,code||' - '||name label from gl_account where sacco_id=? and active and "+accountWhere(kind)+" and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),kind),institution,search.toLowerCase(Locale.ROOT),page*25);}
    public List<RecoveryBalance> recoveryBalances(UUID loan){
        return jdbc.query("""
            select t.id,t.kind,t.liability_account_id,o.holding_account_id,j.effective_date,
                   sum(case when e.kind in ('RECOVER','UNDO_FUND') then -e.amount else e.amount end) outstanding
            from loan_charge_term t
            join loan_fee_recovery_event o on o.term_id=t.id and o.kind in ('FUND','CUTOVER')
            join gl_journal j on j.id=o.journal_id
            join loan_fee_recovery_event e on e.term_id=t.id
            where t.loan_id=? and not exists(select 1 from loan_fee_recovery_event r where r.reverses_id=o.id)
            group by t.id,o.holding_account_id,j.effective_date
            order by case t.kind when 'APPLICATION' then 1 else 2 end,t.id
            """,(r,n)->new RecoveryBalance(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),
                r.getObject(4,UUID.class),r.getBigDecimal(6).setScale(2),r.getObject(5,LocalDate.class)),loan);
    }
    public Map<UUID,BigDecimal> recoveredAmounts(AppUserPrincipal a,List<UUID> postingIds){
        if(postingIds.isEmpty())return Map.of();
        var args=new ArrayList<Object>();args.add(a.getSaccoId());args.add(a.getStationId());args.addAll(postingIds);
        var result=new HashMap<UUID,BigDecimal>();
        jdbc.query("""
            select p.id,sum(e.amount) from accountant_loan_post p join loan_fee_recovery_event e on e.journal_id=p.journal_id
            where p.sacco_id=? and p.station_id=? and p.id in (%s) and e.kind in ('RECOVER','UNDO_RECOVERY') group by p.id
            """.formatted(String.join(",",Collections.nCopies(postingIds.size(),"?"))),
            r->{result.put(r.getObject(1,UUID.class),r.getBigDecimal(2));},args.toArray());
        return result;
    }
    public boolean includedInRecoveryReview(UUID loan,UUID posting){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from loan_fee_recovery_review r join loan_fee_recovery_decision d on d.review_id=r.id
            join accountant_loan_post p on p.loan_id=r.loan_id and p.event_sequence<=r.posting_snapshot
            where r.loan_id=? and p.id=?)
            """,Boolean.class,loan,posting));
    }
    public List<RecoveryEvent> recoveryEvents(UUID journal){
        return jdbc.query("""
            select e.id,e.term_id,t.liability_account_id,e.holding_account_id,e.kind,e.amount
            from loan_fee_recovery_event e join loan_charge_term t on t.id=e.term_id where e.journal_id=? order by t.kind
            """,(r,n)->new RecoveryEvent(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),
                r.getObject(4,UUID.class),r.getString(5),r.getBigDecimal(6)),journal);
    }
    public void recoveryEvent(AppUserPrincipal actor,UUID term,UUID journal,String kind,BigDecimal amount,UUID holding,UUID destination,UUID reverses){
        UUID debit=UUID.randomUUID(),credit=UUID.randomUUID();
        boolean toHolding=Set.of("FUND","CUTOVER","UNDO_RECOVERY").contains(kind);
        jdbc.batchUpdate("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",List.of(
            new Object[]{debit,journal,actor.getSaccoId(),actor.getStationId(),toHolding?destination:holding,amount,BigDecimal.ZERO},
            new Object[]{credit,journal,actor.getSaccoId(),actor.getStationId(),toHolding?holding:destination,BigDecimal.ZERO,amount}));
        jdbc.update("insert into loan_fee_recovery_event(id,term_id,journal_id,kind,amount,holding_account_id,debit_line_id,credit_line_id,reverses_id) values(?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),term,journal,kind,amount,holding,debit,credit,reverses);
    }
    public RecoverySnapshot recoverySnapshot(UUID loan){
        return jdbc.query("""
            select l.principal-l.principal_paid,coalesce(max(p.event_sequence),0),max(p.effective_date)
            from loan_ledgers l left join accountant_loan_post p on p.loan_id=l.loan_application_id and p.state='POSTED'
            where l.loan_application_id=? group by l.loan_application_id,l.principal,l.principal_paid
            """,(r,n)->new RecoverySnapshot(r.getBigDecimal(1),r.getLong(2),r.getObject(3,LocalDate.class)),loan)
            .stream().findFirst().orElseThrow(()->new IllegalArgumentException("charges.error.recovery"));
    }
    public Map<UUID,BigDecimal> withheldAmounts(UUID loan){
        var amounts=new HashMap<UUID,BigDecimal>();
        jdbc.query("select term_id,sum(case kind when 'DEDUCT' then amount else -amount end) from loan_charge_event where loan_id=? and kind in ('DEDUCT','DEDUCT_UNDO') group by term_id",
            r->{amounts.put(r.getObject(1,UUID.class),r.getBigDecimal(2));},loan);
        return amounts;
    }
    public Optional<RecoveryReview> recoveryReview(AppUserPrincipal a,UUID loan,UUID id,boolean byRequest){
        return jdbc.query("""
            select r.*,d.journal_id,(r.amounts->>'APPLICATION')::numeric application,(r.amounts->>'PROCESSING')::numeric processing
            from loan_fee_recovery_review r left join loan_fee_recovery_decision d on d.review_id=r.id
            where r.loan_id=? and r.sacco_id=? and r.station_id=?
            """+(id==null?"":" and r."+(byRequest?"request_key":"id")+"=?")+" order by posting_snapshot desc,recorded_at desc,r.id limit 1",
            (r,n)->new RecoveryReview(r.getObject("id",UUID.class),loan,r.getObject("maker_id",UUID.class),r.getObject("holding_account_id",UUID.class),
                r.getBigDecimal("principal_snapshot"),r.getLong("posting_snapshot"),
                readReviewAmounts(r.getBigDecimal("application"),r.getBigDecimal("processing")),
                r.getString("evidence"),r.getObject("journal_id",UUID.class)),
            id==null?new Object[]{loan,a.getSaccoId(),a.getStationId()}:new Object[]{loan,a.getSaccoId(),a.getStationId(),id}).stream().findFirst();
    }
    private static Map<String,BigDecimal> readReviewAmounts(BigDecimal application,BigDecimal processing){
        var result=new HashMap<String,BigDecimal>();
        if(application!=null)result.put("APPLICATION",application.setScale(2));
        if(processing!=null)result.put("PROCESSING",processing.setScale(2));
        return Map.copyOf(result);
    }
    public void linkFundingFeeReceipt(UUID funding,UUID receipt){
        jdbc.update("insert into loan_fee_funding_receipt(funding_id,receipt_id) values(?,?)",funding,receipt);
    }
    public void submitRecoveryReview(AppUserPrincipal a,UUID id,UUID loan,RecoveryReviewForm form,RecoverySnapshot snapshot,String amounts,OffsetDateTime now){
        jdbc.update("""
            insert into loan_fee_recovery_review(id,loan_id,sacco_id,station_id,maker_id,request_key,evidence,holding_account_id,principal_snapshot,posting_snapshot,amounts,recorded_at)
            values(?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
            """,id,loan,a.getSaccoId(),a.getStationId(),a.getMemberId(),form.getRequestKey(),form.getEvidence().strip(),form.getHoldingAccountId(),snapshot.principal(),snapshot.sequence(),amounts,now);
    }
    public void approveRecoveryReview(AppUserPrincipal a,RecoveryReview review,UUID journal,UUID period,LocalDate date,String reason,String hash,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,direct_post)
            values(?,?,?,1,?,'LOAN_FEE_CUTOVER',?,?,?,'TZS','DRAFT',?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,review.id().toString(),review.id(),hash,review.evidence(),reason,date,a.getMemberId(),now);
        jdbc.update("insert into loan_fee_recovery_decision(review_id,reviewer_id,journal_id,reason,decided_at) values(?,?,?,?,?)",
            review.id(),a.getMemberId(),journal,reason,now);
    }
    public Optional<Policy> contractPolicy(UUID loan){
        return jdbc.query("select p.* from loan_charge_contract c join loan_charge_policy p on p.id=c.policy_id where c.loan_id=?",this::policy,loan).stream().findFirst();
    }
    public Optional<UUID> pendingRecoveryAccount(AppUserPrincipal actor,UUID loan,LocalDate date){
        return jdbc.query("""
            select p.fee_recovery_account_id
              from accountant_loan_record r join loan_applications l on l.id=r.loan_id
              join lateral (select q.enabled,q.fee_recovery_account_id from loan_charge_policy q
                 where q.sacco_id=r.sacco_id and q.product_id=l.loan_product_setting_id and q.effective_from<=?
                 order by q.effective_from desc,q.revision_no desc limit 1) p on p.enabled
             where r.loan_id=? and r.sacco_id=? and r.station_id=?
               and l.financial_snapshot->'recordedCharges'->>'supported'='false'
               and p.fee_recovery_account_id is not null
            """,(row,index)->row.getObject(1,UUID.class),date,loan,actor.getSaccoId(),actor.getStationId()).stream().findFirst();
    }
    public Map<UUID,String> accountLabels(String institution,List<UUID> ids){
        if(ids.isEmpty())return Map.of();var args=new ArrayList<Object>();args.add(institution);args.addAll(ids);
        var labels=new LinkedHashMap<UUID,String>();
        jdbc.query("select id,code||' - '||name label from gl_account where sacco_id=? and id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",r->{labels.put(r.getObject(1,UUID.class),r.getString(2));},args.toArray());
        return labels;
    }
    public List<GeneratedAccount> generatedAccounts(String institution,UUID product){
        return jdbc.query("""
            select g.component,g.account_id,(v.snapshot->>'code')||' - '||(v.snapshot->>'name') label,v.state
            from loan_product_fee_account_provision g join lateral
              (select snapshot,state from gl_account_version where sacco_id=g.sacco_id and account_id=g.account_id order by number desc limit 1) v on true
            where g.sacco_id=? and g.product_id=? order by g.component limit 2
            """,(r,n)->new GeneratedAccount(r.getString(1),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),institution,product);
    }
    public boolean insuranceUsed(String institution,String provider,String evidence){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_charge_term where sacco_id=? and kind='INSURANCE' and md5(lower(btrim(provider)))=md5(lower(btrim(?))) and md5(lower(btrim(evidence)))=md5(lower(btrim(?))))",Boolean.class,institution,provider,evidence));}
    public void freeze(AppUserPrincipal a,UUID loan,Plan p,OffsetDateTime now){jdbc.update("insert into loan_charge_contract(loan_id,sacco_id,station_id,policy_id,policy_reference,agreement_reference,recorded_at) values(?,?,?,?,?,?,?)",loan,a.getSaccoId(),a.getStationId(),p.policyId(),p.policyReference(),p.agreementReference(),now);for(var t:p.terms())jdbc.update("insert into loan_charge_term(id,loan_id,sacco_id,station_id,kind,basis,basis_value,amount,timing,receivable_account_id,liability_account_id,evidence,provider,verification,receivable_account_name,liability_account_name) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,(select code||' - '||name from gl_account where id=?),(select code||' - '||name from gl_account where id=?))",t.id(),loan,a.getSaccoId(),a.getStationId(),t.kind(),t.basis(),t.basisValue(),t.amount(),t.timing().name(),t.receivableAccountId(),t.liabilityAccountId(),t.evidence(),t.provider(),t.verification(),t.receivableAccountId(),t.liabilityAccountId());}
    public Optional<String> pendingPlan(AppUserPrincipal actor,UUID loan){
        return jdbc.query("""
            select (l.financial_snapshot->'recordedCharges')::text
            from accountant_loan_record r join loan_applications l on l.id=r.loan_id
            where r.loan_id=? and r.sacco_id=? and r.station_id=?
              and l.financial_snapshot->'recordedCharges'->>'supported'='false'
            """,(row,index)->row.getString(1),loan,actor.getSaccoId(),actor.getStationId()).stream().findFirst();
    }
    public Plan frozen(UUID loan){var header=jdbc.queryForList("select * from loan_charge_contract where loan_id=?",loan);if(header.isEmpty())return new Plan(null,"","",List.of(),true,"");var h=header.getFirst();var terms=jdbc.query("select * from loan_charge_term where loan_id=? order by case kind when 'APPLICATION' then 1 when 'PROCESSING' then 2 else 3 end",(r,n)->new Term(r.getObject("id",UUID.class),r.getString("kind"),r.getString("basis"),r.getBigDecimal("basis_value"),r.getBigDecimal("amount"),Timing.valueOf(r.getString("timing")),r.getObject("receivable_account_id",UUID.class),r.getObject("liability_account_id",UUID.class),r.getString("evidence"),r.getString("provider"),r.getString("verification")),loan);return new Plan((UUID)h.get("policy_id"),h.get("policy_reference").toString(),h.get("agreement_reference").toString(),terms,true,"");}
    public Balance balance(UUID loan,LocalDate date){return jdbc.query("select coalesce(max(sequence),0) seq,coalesce(sum(case when kind='ASSESS' then amount when kind='ASSESS_UNDO' then -amount else 0 end),0) assessed,coalesce(sum(case when kind in ('COLLECT','DEDUCT','SETTLE') then amount when kind in ('COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') then -amount else 0 end),0) collected,max(effective_date) last_date from loan_charge_event where loan_id=? and effective_date<=?",(r,n)->new Balance(loan,r.getLong("seq"),r.getBigDecimal("assessed"),r.getBigDecimal("collected"),r.getBigDecimal("assessed").subtract(r.getBigDecimal("collected")),r.getObject("last_date",LocalDate.class)),loan,date).getFirst();}
    public List<FeeBalance> feeBalances(UUID loan){
        return jdbc.query("""
            select t.id,t.receivable_account_id,coalesce(sum(case
                when e.kind in ('ASSESS','COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') then e.amount
                else -e.amount end),0) outstanding
            from loan_charge_term t left join loan_charge_event e on e.term_id=t.id
            where t.loan_id=? group by t.id
            order by case t.kind when 'APPLICATION' then 1 when 'PROCESSING' then 2 else 3 end,t.id
            """,(r,n)->new FeeBalance(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getBigDecimal(3).setScale(2)),loan);
    }
    public BigDecimal outstanding(UUID term){return jdbc.queryForObject("select coalesce(sum(case when kind in('ASSESS','COLLECT_UNDO','DEDUCT_UNDO','SETTLE_UNDO') then amount else -amount end),0) from loan_charge_event where term_id=?",BigDecimal.class,term);}
    public long sequence(UUID loan){return jdbc.queryForObject("select coalesce(max(sequence),0) from loan_charge_event where loan_id=?",Long.class,loan)+1;}
    public boolean assessed(UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_charge_event a where loan_id=? and a.kind='ASSESS' and not exists(select 1 from loan_charge_event r where r.reverses_id=a.id))",Boolean.class,loan));}
    public Optional<UUID> commandRetry(AppUserPrincipal a,UUID key,String hash){var rows=jdbc.queryForList("select id,payload_hash,actor_id from loan_charge_command where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("actor_id")))throw new IllegalArgumentException("charges.error.retry");return Optional.of((UUID)row.get("id"));}
    public UUID command(AppUserPrincipal a,UUID loan,UUID key,String hash,LocalDate date,String kind,String evidence,String reason,UUID reverses,UUID journal,OffsetDateTime now){var id=UUID.randomUUID();jdbc.update("insert into loan_charge_command(id,loan_id,sacco_id,station_id,request_key,payload_hash,effective_date,kind,journal_id,reverses_id,actor_id,evidence,reason,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,loan,a.getSaccoId(),a.getStationId(),key,hash,date,kind,journal,reverses,a.getMemberId(),evidence,reason,now);return id;}
    public UUID event(AppUserPrincipal a,UUID loan,Term term,String kind,BigDecimal amount,LocalDate date,UUID journal,UUID posting,UUID command,UUID reverses,String evidence,String reason,OffsetDateTime now){var id=UUID.randomUUID();jdbc.update("insert into loan_charge_event(id,loan_id,term_id,sacco_id,station_id,sequence,kind,amount,effective_date,journal_id,posting_id,command_id,reverses_id,actor_id,evidence,reason,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,loan,term.id(),a.getSaccoId(),a.getStationId(),sequence(loan),kind,amount,date,journal,posting,command,reverses,a.getMemberId(),evidence,reason,now);return id;}

    public List<Movement> journalEvents(UUID journal){return movements("journal_id=? order by sequence limit 150",journal);}
    public List<Movement> fundingAssessments(UUID loan){return movements("e.loan_id=? and e.kind='ASSESS' and not exists(select 1 from loan_charge_event r where r.reverses_id=e.id) order by sequence limit 3",loan);}
    private List<Movement> movements(String condition,Object...args){return jdbc.query("select e.id,e.loan_id,e.term_id,e.kind,e.amount,e.journal_id,e.actor_id from loan_charge_event e where "+condition,(r,n)->new Movement(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("term_id",UUID.class),r.getString("kind"),r.getBigDecimal("amount"),r.getObject("journal_id",UUID.class),r.getObject("actor_id",UUID.class)),args);}
    public boolean laterPayment(UUID loan,UUID original,LocalDate date){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_post p where loan_id=? and kind='PAYMENT' and id<>? and state='POSTED' and event_sequence>(select event_sequence from accountant_loan_post where id=?) and not exists(select 1 from accountant_loan_post r where r.reverses_id=p.id)) or exists(select 1 from loan_charge_event where loan_id=? and effective_date>?)",Boolean.class,loan,original,original,loan,date));}
    public Map<String,Object> assessmentCommand(UUID journal){return jdbc.queryForMap("select * from loan_charge_command where journal_id=? and kind='ASSESS'",journal);}
    public UUID fundingPosting(UUID loan){return jdbc.queryForObject("select id from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",UUID.class,loan);}
    public boolean fundingDependencies(UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_post p where p.loan_id=? and p.kind='PAYMENT' and not exists(select 1 from accountant_loan_post r where r.reverses_id=p.id)) or exists(select 1 from loan_applications where top_up_source_loan_id=? and status not in ('WITHDRAWN','REJECTED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','PAID','DISBURSEMENT_REVERSED'))",Boolean.class,loan,loan));}
    public void journal(AppUserPrincipal a,UUID id,UUID source,UUID key,String hash,LocalDate date,UUID period,UUID reverses,String evidence,String reason,OffsetDateTime now){jdbc.update("insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post) values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)",id,a.getSaccoId(),a.getStationId(),period,reverses==null?"LOAN_CHARGE_ASSESS":"LOAN_CHARGE_UNDO",source.toString(),key,hash,evidence,reason,date,a.getMemberId(),now,reverses);}
    public void line(AppUserPrincipal a,UUID journal,UUID account,BigDecimal debit,BigDecimal credit){jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),account,debit,credit);}
    public void post(AppUserPrincipal a,UUID journal,OffsetDateTime now){jdbc.update("insert into accounting_outbox(id,sacco_id,station_id,journal_id,event_type,created_at) values(?,?,?,?,'LOAN_CHARGE_POSTED',?)",UUID.randomUUID(),a.getSaccoId(),a.getStationId(),journal,now);jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",now,journal);}
    public List<Object[]> reversalLines(UUID journal){return jdbc.query("select account_id,credit,debit from loan_base_journal_line where journal_id=? order by id",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)},journal);}
}
