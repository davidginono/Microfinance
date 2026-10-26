package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupCollectionRepository {
    private final JdbcTemplate jdbc;
    public Cycle cycle(AppUserPrincipal a,UUID id){return jdbc.query("""
        select c.id,c.model,c.state,g.code,g.name from lending_group_cycle c join lending_group g on g.id=c.group_id
        where c.id=? and c.sacco_id=? and c.station_id=?
        """,(r,n)->new Cycle(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("model"),r.getString("state")),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Group cycle unavailable"));}
    private static final String BENEFICIARIES="""
        select l.id,p.client_id,m.member_no,m.full_name,coalesce(l.loan_id,l.application_number::text) loan_number,l.status
        from lending_group_participant p join loan_applications l on l.id=p.loan_id join members m on m.id=p.client_id
        where p.cycle_id=? and p.sacco_id=? and p.station_id=?
        union all
        select l.id,l.borrower_party_id,m.member_no,m.full_name,coalesce(l.loan_id,l.application_number::text),l.status
        from lending_group_shared_facility f join loan_applications l on l.id=f.loan_id
        join loan_borrower_directory m on m.party_id=l.borrower_party_id and m.sacco_id=l.sacco_id
        where f.cycle_id=? and f.sacco_id=? and f.station_id=?
        """;
    public List<Choice> loanChoices(AppUserPrincipal a,UUID cycle,String search,int page){return jdbc.query("select b.id,b.loan_number||' - '||b.member_no||' - '||b.full_name label from ("+BENEFICIARIES+") b where b.status in ('DISBURSED','PAR','DEFAULTED') and strpos(lower(b.loan_number||' '||b.member_no||' '||b.full_name),?)>0 order by b.loan_number,b.id limit 26 offset ?",
        (r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),cycle,a.getSaccoId(),a.getStationId(),cycle,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> payerChoices(AppUserPrincipal a,UUID cycle,String search,int page){return jdbc.query("""
        select m.id,m.member_no||' - '||m.full_name label from lending_group_participant p join members m on m.id=p.client_id
        where p.cycle_id=? and p.sacco_id=? and p.station_id=? and strpos(lower(m.member_no||' '||m.full_name),?)>0
        order by m.member_no,m.id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),cycle,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Map<UUID,Beneficiary> beneficiaries(AppUserPrincipal a,UUID cycle,List<UUID> loans){
        if(loans.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(cycle,a.getSaccoId(),a.getStationId(),cycle,a.getSaccoId(),a.getStationId()));args.addAll(loans);
        return jdbc.query("select * from ("+BENEFICIARIES+") b where b.id in ("+placeholders(loans.size())+")",
            (r,n)->new Beneficiary(r.getObject("id",UUID.class),r.getObject("client_id",UUID.class),r.getString("member_no"),r.getString("full_name"),r.getString("loan_number")),args.toArray()).stream().collect(java.util.stream.Collectors.toMap(Beneficiary::loanId,java.util.function.Function.identity()));
    }
    public Map<UUID,Payer> payers(AppUserPrincipal a,UUID cycle,List<UUID> ids){
        if(ids.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(cycle,a.getSaccoId(),a.getStationId()));args.addAll(ids);
        return jdbc.query("select m.id,m.member_no,m.full_name from lending_group_participant p join members m on m.id=p.client_id where p.cycle_id=? and p.sacco_id=? and p.station_id=? and m.id in ("+placeholders(ids.size())+")",
            (r,n)->Map.entry(r.getObject(1,UUID.class),new Payer(r.getObject(1,UUID.class),r.getString(2),r.getString(3))),args.toArray()).stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }
    public List<Map<String,Object>> versions(AppUserPrincipal a,List<UUID> loans){
        var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(loans);
        return jdbc.queryForList("""
            select l.id,l.status,l.installment_amount,b.next_sequence,b.version,b.principal_paid,b.interest_paid,b.last_payment_date,
            c.sequence charge_sequence,c.outstanding charges,c.last_date,ab.principal_account_id,ab.interest_account_id
            from loan_applications l left join loan_ledgers b on b.loan_application_id=l.id left join loan_charge_balance c on c.loan_id=l.id
            left join loan_product_account_binding ab on ab.loan_id=l.id
            where l.sacco_id=? and l.station_id=? and l.id in (
            """+placeholders(loans.size())+") order by l.id",args.toArray());
    }
    public void requestLock(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","CORE/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash){var rows=jdbc.queryForList("select id,payload_hash,posted_by from lending_group_receipt where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("posted_by")))throw new IllegalArgumentException("group.error.retry");return Optional.of((UUID)row.get("id"));}
    private static final String HEAD="""
        select r.*,i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) branch_name,
        (select x.id from lending_group_receipt x where x.reverses_id=r.id) reversed_by,
        coalesce((select x.number from lending_group_receipt x where x.id=r.reverses_id and x.sacco_id=r.sacco_id and x.station_id=r.station_id),'') reverses_number,
        coalesce((select x.number from lending_group_receipt x where x.reverses_id=r.id and x.sacco_id=r.sacco_id and x.station_id=r.station_id),'') reversed_by_number
        from lending_group_receipt r join registered_saccos i on i.sacco_id=r.sacco_id join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id
        where r.sacco_id=? and r.station_id=?
        """;
    public Optional<LoanReceipt> header(AppUserPrincipal a,UUID id,boolean lock){return jdbc.query(HEAD+" and r.id=?"+(lock?" for update of r":""),(r,n)->new LoanReceipt(new Voucher(r.getObject("id",UUID.class),r.getString("number"),Type.RECEIPT,r.getObject("effective_date",LocalDate.class),r.getString("remitter"),r.getString("reference"),r.getString("description"),r.getString("evidence"),r.getObject("money_account_id",UUID.class),r.getBigDecimal("total"),r.getInt("transaction_count"),null,r.getObject("posted_by",UUID.class),r.getString("posted_by_name"),r.getObject("posted_at",OffsetDateTime.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getString("sacco_name"),r.getString("branch_name"),List.of()),"",List.of(),new GroupReceipt(r.getObject("cycle_id",UUID.class),r.getString("group_code"),r.getString("group_name"),List.of(),r.getString("reverses_number"),r.getString("reversed_by_number"))),a.getSaccoId(),a.getStationId(),id).stream().findFirst();}
    public List<LoanReceiptRow> rows(AppUserPrincipal a,UUID id){return jdbc.query("""
        select x.line_no,p.*,l.loan_id loan_number,coalesce((select t.receivable_account_name from loan_charge_event e join loan_charge_term t on t.id=e.term_id where e.posting_id=p.id limit 1),'') charge_account
        from lending_group_receipt_loan x join accountant_loan_post p on p.id=x.posting_id join loan_applications l on l.id=p.loan_id
        where x.receipt_id=? and x.sacco_id=? and x.station_id=? order by x.line_no limit 50
        """,(r,n)->new LoanReceiptRow(r.getInt("line_no"),r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("notes"),r.getBigDecimal("amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getBigDecimal("principal_balance"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString("reference"),r.getString("charge_account")),id,a.getSaccoId(),a.getStationId());}
    public List<ReceiptContribution> contributions(AppUserPrincipal a,UUID receipt){return jdbc.query("select * from lending_group_contribution where receipt_id=? and sacco_id=? and station_id=? order by line_no limit 100",
        (r,n)->new ReceiptContribution(r.getObject("id",UUID.class),r.getInt("line_no"),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getObject("payer_id",UUID.class),r.getString("payer_number"),r.getString("payer_name"),r.getObject("beneficiary_id",UUID.class),r.getString("beneficiary_number"),r.getString("beneficiary_name"),r.getString("description"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getObject("reverses_id",UUID.class),r.getObject("source_line_no",Integer.class)),receipt,a.getSaccoId(),a.getStationId());}
    public void insert(AppUserPrincipal a,LoanReceipt receipt,UUID key,String hash,String input){var v=receipt.header();var g=receipt.group();jdbc.update("""
        insert into lending_group_receipt(id,sacco_id,station_id,cycle_id,number,request_key,payload_hash,effective_date,money_account_id,reference,remitter,evidence,description,group_code,group_name,input_data,total,transaction_count,posted_by,posted_by_name,posted_at,reverses_id)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)
        """,v.id(),a.getSaccoId(),a.getStationId(),g.cycleId(),v.number(),key,hash,v.effectiveDate(),v.moneyAccountId(),v.reference(),v.party(),v.evidence(),v.description(),g.code(),g.name(),input,v.total(),receipt.rows().size(),a.getMemberId(),a.getFullName(),v.postedAt(),v.reversesId());
        jdbc.batchUpdate("insert into lending_group_receipt_loan(receipt_id,sacco_id,station_id,line_no,posting_id) values(?,?,?,?,?)",receipt.rows().stream().map(r->new Object[]{v.id(),a.getSaccoId(),a.getStationId(),r.lineNo(),r.postingId()}).toList());
        jdbc.batchUpdate("""
            insert into lending_group_contribution(id,receipt_id,sacco_id,station_id,line_no,loan_id,loan_number,payer_id,payer_number,payer_name,beneficiary_id,beneficiary_number,beneficiary_name,description,amount,principal,interest,charges,reverses_id,source_line_no)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,g.contributions().stream().map(r->new Object[]{r.id(),v.id(),a.getSaccoId(),a.getStationId(),r.lineNo(),r.loanId(),r.loanNumber(),r.payerId(),r.payerNumber(),r.payer(),r.beneficiaryId(),r.beneficiaryNumber(),r.beneficiary(),r.description(),r.amount(),r.principal(),r.interest(),r.charges(),r.reversesId(),r.sourceLineNo()}).toList());
    }
    private static String placeholders(int n){if(n<1 || n>50)throw new IllegalArgumentException("group.error.members");return String.join(",",Collections.nCopies(n,"?"));}
}
