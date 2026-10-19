package com.sacco.mvp.accounting.payroll.repository;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PayrollRepository {
    private final JdbcTemplate jdbc;
    public boolean hasFeedback(UUID batch){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payroll_feedback where batch_id=?)",Boolean.class,batch));}
    public void lock(AppUserPrincipal a, String resource) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", "PAYROLL/"+a.getSaccoId()+"/"+a.getStationId()+"/"+resource);
    }
    public Optional<Map<String,Object>> employerRequest(AppUserPrincipal a, UUID id) {
        return jdbc.queryForList("select * from payroll_employer where id=? and sacco_id=? and station_id=?",id,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public Employer employer(AppUserPrincipal a, UUID id) {
        return jdbc.query("select id,name,agreement_reference from payroll_employer where id=? and sacco_id=? and station_id=?",
            (r,n)->new Employer(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),id,a.getSaccoId(),a.getStationId())
            .stream().findFirst().orElseThrow(()->new AccessDeniedException("Employer unavailable"));
    }
    public boolean employerNameExists(AppUserPrincipal a,String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payroll_employer where sacco_id=? and station_id=? and lower(name)=lower(?))",Boolean.class,a.getSaccoId(),a.getStationId(),name));
    }
    public void insertEmployer(AppUserPrincipal a,EmployerForm f,OffsetDateTime now) {
        jdbc.update("insert into payroll_employer(id,sacco_id,station_id,name,agreement_reference,actor_id,created_at) values(?,?,?,?,?,?,?)",
            f.getRequestKey(),a.getSaccoId(),a.getStationId(),f.getName(),f.getAgreementReference(),a.getMemberId(),now);
    }
    public List<Employer> employers(AppUserPrincipal a,String search,int page,String sort,String direction) {
        String order=order(sort,direction,Map.of("name","name","agreement","agreement_reference"),"id");
        return jdbc.query("select id,name,agreement_reference from payroll_employer where sacco_id=? and station_id=? and strpos(lower(name),?)>0 order by "+order+" limit 26 offset ?",
            (r,n)->new Employer(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    private static final String LOAN_CHOICES="""
        select a.id,a.applicant_member_id,coalesce(a.loan_id,l.loan_id) loan_number,m.full_name,m.member_no,m.employee_number
        from loan_applications a join loan_ledgers l on l.loan_application_id=a.id and l.sacco_id=a.sacco_id and l.station_id=a.station_id and l.applicant_member_id=a.applicant_member_id
        join members m on m.id=a.applicant_member_id and m.sacco_id=a.sacco_id
        where a.sacco_id=? and a.station_id=? and a.status in ('DISBURSED','PAR','DEFAULTED') and m.status='ACTIVE'
        """;
    private LoanChoice loanChoice(ResultSet r,int n)throws SQLException {
        return new LoanChoice(r.getObject("id",UUID.class),r.getObject("applicant_member_id",UUID.class),r.getString("loan_number")+" — "+r.getString("member_no")+" — "+r.getString("full_name"),r.getString("employee_number"));
    }
    public List<LoanChoice> loans(AppUserPrincipal a,String search,int page) {
        return jdbc.query(LOAN_CHOICES+" and strpos(lower(coalesce(a.loan_id,l.loan_id)||' '||m.member_no||' '||m.full_name),?)>0 order by a.loan_id,a.id limit 26 offset ?",
            (RowMapper<LoanChoice>)this::loanChoice,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public LoanChoice loan(AppUserPrincipal a,UUID id) {
        return jdbc.query(LOAN_CHOICES+" and a.id=?",(RowMapper<LoanChoice>)this::loanChoice,a.getSaccoId(),a.getStationId(),id)
            .stream().findFirst().orElseThrow(()->new IllegalArgumentException("payroll.error.loan"));
    }
    public Optional<Map<String,Object>> instructionRequest(AppUserPrincipal a,UUID id) {
        return jdbc.queryForList("select * from payroll_instruction where id=? and sacco_id=? and station_id=?",id,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public boolean conflictingInstruction(AppUserPrincipal a,UUID employer,LoanChoice loan) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from payroll_instruction i where i.sacco_id=? and i.station_id=?
             and not exists(select 1 from payroll_instruction_stop s where s.instruction_id=i.id)
             and (i.loan_id=? or (i.employer_id=? and
               ((lower(i.employee_number)=lower(?) and i.client_id<>?) or (i.client_id=? and i.employee_number<>?)))))
            """,Boolean.class,a.getSaccoId(),a.getStationId(),loan.id(),employer,loan.employeeNumber(),loan.clientId(),loan.clientId(),loan.employeeNumber()));
    }
    public void insertInstruction(AppUserPrincipal a,InstructionForm f,LoanChoice loan,OffsetDateTime now) {
        jdbc.update("""
            insert into payroll_instruction(id,sacco_id,station_id,employer_id,loan_id,client_id,employee_number,monthly_limit,effective_from,effective_to,evidence,actor_id,created_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,f.getRequestKey(),a.getSaccoId(),a.getStationId(),f.getEmployerId(),loan.id(),loan.clientId(),loan.employeeNumber(),f.getMonthlyLimit(),f.getEffectiveFrom(),f.getEffectiveTo(),f.getEvidence(),a.getMemberId(),now);
    }
    private static final String INSTRUCTIONS="""
        select i.*,e.name employer,m.full_name,a.loan_id loan_number,exists(select 1 from payroll_instruction_stop s where s.instruction_id=i.id) stopped
        from payroll_instruction i join payroll_employer e on e.id=i.employer_id join members m on m.id=i.client_id and m.sacco_id=i.sacco_id join loan_applications a on a.id=i.loan_id and a.sacco_id=i.sacco_id and a.station_id=i.station_id
        where i.sacco_id=? and i.station_id=?
        """;
    private Instruction instruction(ResultSet r,int n)throws SQLException {
        return new Instruction(r.getObject("id",UUID.class),r.getObject("employer_id",UUID.class),r.getString("employer"),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("full_name"),r.getString("employee_number"),r.getBigDecimal("monthly_limit"),r.getObject("effective_from",LocalDate.class),r.getObject("effective_to",LocalDate.class),r.getString("evidence"),r.getBoolean("stopped"));
    }
    public Instruction instruction(AppUserPrincipal a,UUID id) {
        return jdbc.query(INSTRUCTIONS+" and i.id=?",(RowMapper<Instruction>)this::instruction,a.getSaccoId(),a.getStationId(),id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Instruction unavailable"));
    }
    public List<Instruction> instructions(AppUserPrincipal a,String search,int page,String sort,String direction) {
        String ordering=order(sort,direction,Map.of("employer","e.name","loan","a.loan_id","client","m.full_name","employee","i.employee_number","limit","i.monthly_limit","from","i.effective_from","to","i.effective_to","state","stopped"),"i.id");
        return jdbc.query(INSTRUCTIONS+" and strpos(lower(e.name||' '||m.full_name||' '||i.employee_number||' '||a.loan_id),?)>0 order by "+ordering+" limit 26 offset ?",
            (RowMapper<Instruction>)this::instruction,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public void stop(AppUserPrincipal a,UUID id,String reason,OffsetDateTime now) {
        jdbc.update("insert into payroll_instruction_stop(instruction_id,actor_id,reason,created_at) values(?,?,?,?) on conflict do nothing",id,a.getMemberId(),reason,now);
    }
    /** One bounded statement captures current ledger counters and all exception reasons together. */
    public List<Line> preview(AppUserPrincipal a,BatchForm f,LocalDate month) {
        return jdbc.query("""
            with candidates as (
              select i.*,coalesce(m.full_name,'—') full_name,coalesce(m.member_no,'—') member_no,m.employee_number current_employee,m.status client_status,
               a.loan_id loan_number,a.status loan_status,a.sacco_id loan_institution,a.station_id loan_branch,
               a.applicant_member_id current_client,l.applicant_member_id ledger_client,l.sacco_id ledger_institution,l.station_id ledger_branch,l.disbursement_date,l.last_payment_date
              from payroll_instruction i left join members m on m.id=i.client_id and m.sacco_id=i.sacco_id
              join loan_applications a on a.id=i.loan_id left join loan_ledgers l on l.loan_application_id=i.loan_id
              where i.sacco_id=? and i.station_id=? and i.employer_id=?
               and not exists(select 1 from payroll_instruction_stop s where s.instruction_id=i.id)
              order by i.id limit 1001
            ), amounts as (
              select c.*,coalesce(d.principal,0) principal,coalesce(d.interest,0) interest,
               case when c.employee_number is distinct from c.current_employee then 'employeeChanged'
                when ?::date<c.effective_from or ?::date>c.effective_to then 'expired'
                when c.client_status is distinct from 'ACTIVE' then 'inactiveClient'
                when c.loan_status not in ('DISBURSED','PAR','DEFAULTED') or c.ledger_institution is distinct from c.sacco_id
                  or c.current_client is distinct from c.client_id or c.ledger_client is distinct from c.client_id
                  or c.ledger_branch is distinct from c.station_id or c.loan_institution is distinct from c.sacco_id
                  or c.loan_branch is distinct from c.station_id or c.disbursement_date>?::date or c.last_payment_date>?::date then 'loanUnavailable'
                when exists(select 1 from payroll_batch_line x join payroll_batch b on b.id=x.batch_id
                  where x.loan_id=c.loan_id and x.amount>0 and b.payroll_month=?::date
                   and not exists(select 1 from payroll_batch_event v where v.batch_id=b.id and v.kind='CANCELLED')) then 'overlap'
                when coalesce(d.principal,0)+coalesce(d.interest,0)<=0 then 'noDue' else '' end issue
              from candidates c left join lateral (
                select sum(i.principal-i.principal_paid) principal,
                 sum(greatest(0,i.interest-i.interest_paid-i.interest_excluded-i.interest_waived)) interest
                from loan_ledger_installments i where i.loan_application_id=c.loan_id and i.due_date<=?::date
                 and (? or i.due_date>=?::date)
              ) d on true
            ) select *,case when issue='' then least(monthly_limit,principal+interest) else 0 end amount
              from amounts order by employee_number,client_id,loan_number,id
            """,(r,n)->new Line(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("client_id",UUID.class),r.getString("employee_number"),r.getString("full_name"),r.getString("member_no"),r.getString("loan_number"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("amount"),r.getString("issue")),
            a.getSaccoId(),a.getStationId(),f.getEmployerId(),f.getDeductionDate(),f.getDeductionDate(),f.getDeductionDate(),f.getDeductionDate(),month,f.getDeductionDate(),f.isIncludeArrears(),month);
    }
    public Optional<Map<String,Object>> batchRequest(AppUserPrincipal a,UUID key) {
        return jdbc.queryForList("select id,payload_hash,actor_id from payroll_batch where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key).stream().findFirst();
    }
    public boolean activeBatch(AppUserPrincipal a,UUID employer,LocalDate month) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payroll_batch b where sacco_id=? and station_id=? and employer_id=? and payroll_month=? and not exists(select 1 from payroll_batch_event e where e.batch_id=b.id and e.kind='CANCELLED'))",Boolean.class,a.getSaccoId(),a.getStationId(),employer,month));
    }
    public Optional<UUID> latestBatch(AppUserPrincipal a,UUID employer,LocalDate month) {
        return jdbc.query("select id from payroll_batch where sacco_id=? and station_id=? and employer_id=? and payroll_month=? order by cutoff desc,id desc limit 1",
            (r,n)->r.getObject(1,UUID.class),a.getSaccoId(),a.getStationId(),employer,month).stream().findFirst();
    }
    public void insertBatch(AppUserPrincipal a,UUID id,BatchForm f,Preview p,String payload,OffsetDateTime now,LocalDate month) {
        var identity=jdbc.queryForMap("select i.sacco_name,coalesce(nullif(s.address_location,''),s.station_id) branch_name,m.full_name from registered_saccos i join sacco_stations s on s.sacco_id=i.sacco_id join members m on m.id=? where i.sacco_id=? and s.station_id=?",a.getMemberId(),a.getSaccoId(),a.getStationId());
        jdbc.update("""
            insert into payroll_batch(id,sacco_id,station_id,employer_id,employer_name,agreement_reference,institution_name,branch_name,payroll_month,deduction_date,include_arrears,cutoff,request_key,payload_hash,snapshot_hash,actor_id,actor_name,replaces_id,frozen_rows,frozen_total)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,a.getSaccoId(),a.getStationId(),p.employer().id(),p.employer().name(),p.employer().agreementReference(),identity.get("sacco_name"),identity.get("branch_name"),month,f.getDeductionDate(),f.isIncludeArrears(),now,f.getRequestKey(),payload,p.hash(),a.getMemberId(),identity.get("full_name"),f.getReplacesId(),p.rows().size(),p.total());
        var arguments=new ArrayList<Object[]>();int number=0;
        for(var line:p.rows())arguments.add(new Object[]{id,++number,line.instructionId(),line.loanId(),line.clientId(),line.employeeNumber(),line.clientName(),line.clientNumber(),line.loanNumber(),line.principal(),line.interest(),line.amount(),line.issue()});
        jdbc.batchUpdate("insert into payroll_batch_line(batch_id,line_number,instruction_id,loan_id,client_id,employee_number,client_name,client_number,loan_number,principal,interest,amount,issue) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",arguments);
    }
    private static final String BATCHES="""
        select b.*,case when exists(select 1 from payroll_batch_event e where e.batch_id=b.id and e.kind='CANCELLED') then 'CANCELLED'
         when exists(select 1 from payroll_batch_event e where e.batch_id=b.id and e.kind='SENT') then 'SENT' else 'FROZEN' end state,
         b.frozen_total total from payroll_batch b
         where b.sacco_id=? and b.station_id=?
        """;
    private static final String BATCH_COUNTS="""
        join lateral(select count(distinct client_id) filter(where amount>0) employees,
         count(*) filter(where issue<>'') exceptions from payroll_batch_line x where x.batch_id=q.id) t on true
        """;
    private Batch batch(ResultSet r,int n)throws SQLException {
        return new Batch(r.getObject("id",UUID.class),r.getString("employer_name"),r.getObject("payroll_month",LocalDate.class),r.getObject("deduction_date",LocalDate.class),r.getBoolean("include_arrears"),r.getObject("cutoff",OffsetDateTime.class),r.getString("actor_name"),r.getString("institution_name"),r.getString("branch_name"),r.getString("snapshot_hash"),r.getObject("replaces_id",UUID.class),r.getString("state"),r.getBigDecimal("total"),r.getLong("employees"),r.getLong("exceptions"));
    }
    public Batch batch(AppUserPrincipal a,UUID id) {
        return jdbc.query("select q.*,t.employees,t.exceptions from ("+BATCHES+" and b.id=?) q "+BATCH_COUNTS,(RowMapper<Batch>)this::batch,a.getSaccoId(),a.getStationId(),id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Batch unavailable"));
    }
    public List<Batch> batches(AppUserPrincipal a,String search,String state,String month,int page,String sort,String direction) {
        String ordering=order(sort,direction,Map.of("reference","id","employer","employer_name","month","payroll_month","date","deduction_date","state","state","total","total"),"id");
        return jdbc.query("select q.*,t.employees,t.exceptions from (select * from ("+BATCHES+") scope where (?='' or strpos(lower(employer_name||' '||id::text),?)>0) and (?='' or state=?) and (?='' or payroll_month::text=?) order by "+ordering+" limit 26 offset ?) q "+BATCH_COUNTS+" order by "+ordering,
            (RowMapper<Batch>)this::batch,a.getSaccoId(),a.getStationId(),search,search.toLowerCase(Locale.ROOT),state,state,month,month,page*25);
    }
    public List<Line> lines(AppUserPrincipal a,UUID id,int page,int limit) {
        return jdbc.query("select x.* from payroll_batch_line x join payroll_batch b on b.id=x.batch_id where b.id=? and b.sacco_id=? and b.station_id=? order by x.line_number limit ? offset ?",
            (r,n)->new Line(r.getObject("instruction_id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("client_id",UUID.class),r.getString("employee_number"),r.getString("client_name"),r.getString("client_number"),r.getString("loan_number"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("amount"),r.getString("issue")),id,a.getSaccoId(),a.getStationId(),limit,page*25);
    }
    public void event(AppUserPrincipal a,UUID id,String kind,String evidence,OffsetDateTime now) {
        jdbc.update("insert into payroll_batch_event(id,batch_id,kind,actor_id,evidence,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),id,kind,a.getMemberId(),evidence,now);
    }
    public List<Event> events(AppUserPrincipal a,UUID id,int page) {
        return jdbc.query("select e.kind,m.full_name,e.evidence,e.created_at from payroll_batch_event e join payroll_batch b on b.id=e.batch_id join members m on m.id=e.actor_id where b.id=? and b.sacco_id=? and b.station_id=? order by e.created_at desc,e.id limit 26 offset ?",
            (r,n)->new Event(r.getString(1),r.getString(2),r.getString(3),r.getObject(4,OffsetDateTime.class)),id,a.getSaccoId(),a.getStationId(),page*25);
    }
    public Optional<Document> document(AppUserPrincipal a,UUID id,String language) {
        return jdbc.query("select d.content,d.checksum from payroll_batch_document d join payroll_batch b on b.id=d.batch_id where b.id=? and b.sacco_id=? and b.station_id=? and d.language=?",
            (r,n)->new Document(r.getBytes(1),r.getString(2)),id,a.getSaccoId(),a.getStationId(),language).stream().findFirst();
    }
    public void document(UUID id,String language,Document d,OffsetDateTime now) {
        jdbc.update("insert into payroll_batch_document(batch_id,language,content,checksum,created_at) values(?,?,?,?,?) on conflict do nothing",id,language,d.content(),d.checksum(),now);
    }
    public UUID employerForBatch(AppUserPrincipal a,UUID id) {
        return jdbc.queryForObject("select employer_id from payroll_batch where id=? and sacco_id=? and station_id=?",UUID.class,id,a.getSaccoId(),a.getStationId());
    }
    private static String order(String sort,String direction,Map<String,String> fields,String tie) {
        String column=fields.get(sort);
        if(column==null || !Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("payroll.error.input");
        return column+" "+direction+","+tie;
    }
}
