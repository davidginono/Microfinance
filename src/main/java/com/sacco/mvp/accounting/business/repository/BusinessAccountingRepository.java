package com.sacco.mvp.accounting.business.repository;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class BusinessAccountingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public boolean hasInstitutionHistory(String institution) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_business_document where sacco_id=?
                union all select 1 from accounting_supplier where sacco_id=?)
            """,Boolean.class,institution,institution));
    }

    public boolean hasMemberHistory(UUID member) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_business_document where maker_id=? or checker_id=?
                union all select 1 from accounting_supplier where maker_id=?)
            """,Boolean.class,member,member,member));
    }

    public void lockRequest(String institution,String branch,UUID key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","BUSINESS/"+institution+"/"+branch+"/"+key);
    }
    public Optional<Document> byRequest(String institution,String branch,UUID key) {
        return jdbc.query("select * from accounting_business_document where sacco_id=? and station_id=? and request_key=?",this::document,institution,branch,key).stream().findFirst();
    }
    public Optional<Document> document(String institution,String branch,UUID id,boolean lock) {
        return jdbc.query("select * from accounting_business_document where sacco_id=? and station_id=? and id=?"+(lock?" for update":""),this::document,institution,branch,id).stream().findFirst();
    }
    public List<Document> list(String institution,String branch,int offset,int limit) {
        return jdbc.query("select * from accounting_business_document where sacco_id=? and station_id=? order by created_at desc,id limit ? offset ?",this::document,institution,branch,limit,offset);
    }
    public List<UUID> originVouchers(String institution,String branch,UUID loan) {
        return jdbc.query("select distinct e.voucher_id from loan_journal_entries e join loan_ledgers l on l.loan_application_id=e.loan_application_id where l.sacco_id=? and l.station_id=? and l.loan_application_id=? and e.transaction_id is null order by e.voucher_id limit 2",(r,n)->r.getObject(1,UUID.class),institution,branch,loan);
    }
    public String payload(Command command) {return mapper.writeValueAsString(command);}
    public void insert(Document d,String hash) {
        Command c=d.command();
        jdbc.update("insert into accounting_business_document(id,sacco_id,station_id,maker_id,request_key,kind,effective_date,amount,loan_id,related_document_id,supplier_id,description,evidence_reference,channel_reference,money_account_key,destination_branch,command_json,payload_hash,state,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?)",
            d.id(),d.institutionId(),d.branchId(),d.makerId(),c.requestKey(),c.kind().name(),c.effectiveDate(),c.amount(),c.loanId(),c.relatedDocumentId(),c.supplierId(),c.description(),c.evidenceReference(),c.channelReference(),c.moneyAccountKey(),c.destinationBranch(),payload(c),hash,d.createdAt());
    }
    public void submitted(UUID id,UUID journal,BigDecimal principal,BigDecimal interest,BigDecimal fees) {
        jdbc.update("update accounting_business_document set state='SUBMITTED',journal_id=?,principal=?,interest=?,fees=? where id=? and state='DRAFT'",journal,principal,interest,fees,id);
    }
    public void posting(UUID id,UUID checker,String evidence) {
        jdbc.update("update accounting_business_document set state='POSTING',checker_id=?,approval_evidence=? where id=? and state='SUBMITTED'",checker,evidence,id);
    }
    public void posted(UUID id,UUID transaction,OffsetDateTime now) {
        jdbc.update("update accounting_business_document set state='POSTED',loan_transaction_id=?,posted_at=? where id=? and state='POSTING'",transaction,now,id);
    }
    public void reject(UUID id,UUID checker,String evidence) {
        jdbc.update("update accounting_business_document set state='REJECTED',checker_id=?,approval_evidence=? where id=? and state in ('DRAFT','SUBMITTED')",checker,evidence,id);
    }
    public void control(UUID document,UUID root,String institution,String branch,BigDecimal amount,OffsetDateTime now) {
        jdbc.update("insert into accounting_business_control_entry(id,document_id,root_document_id,sacco_id,station_id,amount,created_at) values(?,?,?,?,?,?,?)",UUID.randomUUID(),document,root,institution,branch,amount,now);
    }
    public BigDecimal remaining(UUID root) {
        return jdbc.queryForObject("select coalesce(sum(amount),0) from accounting_business_control_entry where root_document_id=?",BigDecimal.class,root);
    }
    public BigDecimal loanControl(String institution,String branch,UUID loan,String kind,LocalDate through) {
        return jdbc.queryForObject("select coalesce(sum(e.amount),0) from accounting_business_control_entry e join accounting_business_document d on d.id=e.root_document_id where d.sacco_id=? and d.station_id=? and d.loan_id=? and d.kind=? and d.state='POSTED' and d.effective_date<=?",BigDecimal.class,institution,branch,loan,kind,through);
    }
    public BigDecimal loanInterestReceivable(String institution,String branch,UUID loan,LocalDate through) {
        return jdbc.queryForObject("select coalesce(sum(l.debit-l.credit),0) from accounting_business_document d join gl_journal j on j.id=d.journal_id join accounting_policies p on p.id=j.policy_id join gl_journal_line l on l.journal_id=j.id where d.sacco_id=? and d.station_id=? and d.loan_id=? and d.state='POSTED' and d.effective_date<=? and l.account_id::text=p.account_mappings_json::jsonb->>'INTEREST_RECEIVABLE'",BigDecimal.class,institution,branch,loan,through);
    }
    public Optional<LoanAmounts> loan(String institution,String branch,UUID loan,LocalDate date,boolean lock) {
        if(lock) jdbc.queryForList("select loan_application_id from loan_ledgers where sacco_id=? and station_id=? and loan_application_id=? for update",institution,branch,loan);
        return jdbc.query("select l.principal-l.principal_paid principal,l.disbursement_date,coalesce(sum(case when i.due_date<=? then i.principal-i.principal_paid else 0 end),0) due_principal,coalesce(sum(case when i.due_date<=? then i.interest-i.interest_paid else 0 end),0) due_interest from loan_ledgers l join loan_ledger_installments i on i.loan_application_id=l.loan_application_id where l.sacco_id=? and l.station_id=? and l.loan_application_id=? group by l.loan_application_id",(r,n)->new LoanAmounts(r.getBigDecimal("principal"),r.getBigDecimal("due_principal"),r.getBigDecimal("due_interest"),r.getObject("disbursement_date",LocalDate.class)),date,date,institution,branch,loan).stream().findFirst();
    }
    public Optional<BigDecimal> readyLoan(String institution,String branch,UUID loan) {
        return jdbc.query("select amount from loan_applications where id=? and sacco_id=? and station_id=? and status='READY_FOR_DISBURSEMENT' and top_up_source_loan_id is null for update",(r,n)->r.getBigDecimal(1),loan,institution,branch).stream().findFirst();
    }
    public UUID supplier(String institution,String branch,UUID maker,String name,String evidence,OffsetDateTime now) {
        UUID id=UUID.randomUUID();jdbc.update("insert into accounting_supplier(id,sacco_id,station_id,maker_id,name,evidence_reference,created_at) values(?,?,?,?,?,?,?)",id,institution,branch,maker,name,evidence,now);return id;
    }
    public List<Supplier> suppliers(String institution,String branch,int offset,int limit) {
        return jdbc.query("select id,name,evidence_reference,active from accounting_supplier where sacco_id=? and station_id=? order by name,id limit ? offset ?",(r,n)->new Supplier(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4)),institution,branch,limit,offset);
    }
    public boolean supplierActive(String institution,String branch,UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_supplier where sacco_id=? and station_id=? and id=? and active)",Boolean.class,institution,branch,id));
    }
    public void asset(Document d) {
        jdbc.update("insert into accounting_fixed_asset(id,sacco_id,station_id,description,acquired_on,cost) values(?,?,?,?,?,?)",d.id(),d.institutionId(),d.branchId(),d.command().description(),d.command().effectiveDate(),d.command().amount());
    }
    public Optional<Asset> asset(String institution,String branch,UUID id,boolean lock) {
        return jdbc.query("select * from accounting_fixed_asset where sacco_id=? and station_id=? and id=?"+(lock?" for update":""),this::assetRow,institution,branch,id).stream().findFirst();
    }
    public List<Asset> assets(String institution,String branch,int offset,int limit) {
        return jdbc.query("select * from accounting_fixed_asset where sacco_id=? and station_id=? order by acquired_on desc,id limit ? offset ?",this::assetRow,institution,branch,limit,offset);
    }
    public void depreciate(UUID id,BigDecimal amount) {jdbc.update("update accounting_fixed_asset set accumulated_depreciation=accumulated_depreciation+? where id=?",amount,id);}
    public void dispose(UUID id) {jdbc.update("update accounting_fixed_asset set disposed=true where id=?",id);}
    public Optional<Document> incomingTransfer(String institution,String destination,UUID id,boolean lock) {
        return jdbc.query("select * from accounting_business_document where sacco_id=? and destination_branch=? and id=? and kind='INTERNAL_TRANSFER_OUT' and state='POSTED'"+(lock?" for update":""),this::document,institution,destination,id).stream().findFirst();
    }
    public boolean receivedTransfer(UUID id) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_business_document where related_document_id=? and kind='INTERNAL_TRANSFER_IN' and state='POSTED')",Boolean.class,id));}
    public boolean branchActive(String institution,String branch) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from sacco_stations where sacco_id=? and station_id=? and active and access_status<>'SUSPENDED')",Boolean.class,institution,branch));}
    public boolean activated(String institution) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_policies p join accounting_policy_approvals a on a.policy_id=p.id where p.sacco_id=? and p.authoritative_ledger='LOCAL_GL' and a.decision='APPROVED')",Boolean.class,institution));}
    public boolean trustedLoanCommand(String institution,String branch,UUID loan,UUID checker,String kind,UUID requestKey,String reference,BigDecimal amount,LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_business_document d join gl_journal j on j.id=d.journal_id where d.sacco_id=? and d.station_id=? and d.loan_id=? and d.checker_id=? and d.kind=? and d.state='POSTING' and j.state='POSTED' and (?::uuid is null or d.request_key=?::uuid) and (?::text is null or d.channel_reference=?::text) and d.amount=? and d.effective_date=?)",Boolean.class,institution,branch,loan,checker,kind,requestKey,requestKey,reference,reference,amount,date));
    }
    public boolean trustedReversal(String institution,String branch,UUID loan,UUID checker,UUID requestKey,LocalDate date,UUID originalTransaction) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_business_document d
                join gl_journal j on j.id=d.journal_id and j.sacco_id=d.sacco_id and j.station_id=d.station_id
                join accounting_business_document o on o.id=d.related_document_id and o.sacco_id=d.sacco_id and o.station_id=d.station_id
                where d.sacco_id=? and d.station_id=? and d.loan_id=? and d.checker_id=? and d.request_key=?
                  and d.kind='LOAN_REPAYMENT_REVERSAL' and d.state='POSTING' and d.effective_date=?
                  and j.state='POSTED' and j.source_type='SOURCE_REVERSAL' and j.reverses_id=o.journal_id
                  and o.kind='LOAN_REPAYMENT' and o.state='POSTED' and o.loan_id=d.loan_id and o.loan_transaction_id=?)
            """,Boolean.class,institution,branch,loan,checker,requestKey,date,originalTransaction));
    }
    private Document document(ResultSet r,int n)throws SQLException {
        return new Document(r.getObject("id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getObject("maker_id",UUID.class),r.getObject("checker_id",UUID.class),mapper.readValue(r.getString("command_json"),Command.class),r.getString("state"),r.getObject("journal_id",UUID.class),r.getObject("loan_transaction_id",UUID.class),r.getString("approval_evidence"),r.getObject("created_at",OffsetDateTime.class),r.getObject("posted_at",OffsetDateTime.class),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("fees"));
    }
    private Asset assetRow(ResultSet r,int n)throws SQLException {return new Asset(r.getObject("id",UUID.class),r.getString("description"),r.getObject("acquired_on",LocalDate.class),r.getBigDecimal("cost"),r.getBigDecimal("accumulated_depreciation"),r.getBoolean("disposed"));}
}
