package com.sacco.mvp.accounting.payroll.service;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.repository.PayrollRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.domain.AuditEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayrollService {
    private final PayrollRepository records;
    private final GeneralLedgerService guard;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    public void authorize(AppUserPrincipal a,String action) {
        guard.requireActor(a,"PAYROLL_DEDUCTIONS_VIEW");
        if(!action.equals("VIEW"))guard.requireActor(a,"PAYROLL_DEDUCTIONS_"+action);
    }
    @Transactional(readOnly=true)
    public Page<Employer> employers(AppUserPrincipal a,String search,int page,String sort,String direction) {
        authorize(a,"VIEW");filter(search,page);return page(records.employers(a,search,page,sort,direction),page);
    }
    @Transactional(readOnly=true)
    public Employer employer(AppUserPrincipal a,UUID id) {authorize(a,"VIEW");return records.employer(a,id);}
    @Transactional(readOnly=true)
    public List<Choice> employerChoices(AppUserPrincipal a,String search,int page) {
        authorize(a,"CREATE");filter(search,page);return records.employers(a,search,page,"name","asc").stream().map(e->new Choice(e.id(),e.name())).toList();
    }
    @Transactional
    public UUID createEmployer(AppUserPrincipal a,EmployerForm f) {
        authorize(a,"CREATE");check(f.getRequestKey()!=null,"input");text(f.getName(),160);text(f.getAgreementReference(),250);
        f.setName(f.getName().trim());f.setAgreementReference(f.getAgreementReference().trim());
        records.lock(a,"EMPLOYER-NAME/"+f.getName().toLowerCase(Locale.ROOT));
        var retry=records.employerRequest(a,f.getRequestKey());
        if(retry.isPresent()) {
            var old=retry.get();check(a.getMemberId().equals(old.get("actor_id")) && f.getName().equals(old.get("name")) && f.getAgreementReference().equals(old.get("agreement_reference")),"retry");
            return f.getRequestKey();
        }
        check(!records.employerNameExists(a,f.getName()),"employerExists");records.insertEmployer(a,f,clock.now());
        event(a,f.getRequestKey(),"EMPLOYER_CREATED",Map.of("name",f.getName(),"agreementReference",f.getAgreementReference()));return f.getRequestKey();
    }
    @Transactional(readOnly=true)
    public Page<Instruction> instructions(AppUserPrincipal a,String search,int page,String sort,String direction) {
        authorize(a,"VIEW");filter(search,page);return page(records.instructions(a,search,page,sort,direction),page);
    }
    @Transactional(readOnly=true)
    public Instruction instruction(AppUserPrincipal a,UUID id) {authorize(a,"VIEW");return records.instruction(a,id);}
    @Transactional(readOnly=true)
    public List<LoanChoice> loanChoices(AppUserPrincipal a,String search,int page) {authorize(a,"CREATE");filter(search,page);return records.loans(a,search,page);}
    @Transactional(readOnly=true)
    public LoanChoice selectedLoan(AppUserPrincipal a,UUID id) {authorize(a,"CREATE");return id==null?null:records.loan(a,id);}
    @Transactional
    public UUID createInstruction(AppUserPrincipal a,InstructionForm f) {
        authorize(a,"CREATE");check(f.getRequestKey()!=null && f.getEmployerId()!=null && f.getLoanId()!=null,"input");evidence(f.getEvidence());
        check(f.getMonthlyLimit()!=null && f.getMonthlyLimit().signum()>0 && f.getMonthlyLimit().scale()<=2 && f.getMonthlyLimit().compareTo(new BigDecimal("9999999999999999.99"))<=0,"limit");
        check(f.getEffectiveFrom()!=null && f.getEffectiveTo()!=null && f.getEffectiveFrom().getYear()>=1900
            && !f.getEffectiveTo().isBefore(f.getEffectiveFrom()) && f.getEffectiveTo().getYear()<=2200,"dates");
        records.employer(a,f.getEmployerId());records.lock(a,"EMPLOYER/"+f.getEmployerId());records.lock(a,"LOAN/"+f.getLoanId());
        var retry=records.instructionRequest(a,f.getRequestKey());
        if(retry.isPresent()) {
            var old=retry.get();check(a.getMemberId().equals(old.get("actor_id")) && f.getEmployerId().equals(old.get("employer_id"))
                && f.getLoanId().equals(old.get("loan_id")) && f.getEvidence().equals(old.get("evidence"))
                && f.getMonthlyLimit().compareTo((BigDecimal)old.get("monthly_limit"))==0
                && f.getEffectiveFrom().equals(((java.sql.Date)old.get("effective_from")).toLocalDate())
                && f.getEffectiveTo().equals(((java.sql.Date)old.get("effective_to")).toLocalDate()),"retry");return f.getRequestKey();
        }
        var loan=records.loan(a,f.getLoanId());check(loan.employeeNumber()!=null && !loan.employeeNumber().isBlank(),"employeeMissing");
        check(!records.conflictingInstruction(a,f.getEmployerId(),loan),"instructionExists");
        records.insertInstruction(a,f,loan,clock.now());event(a,f.getRequestKey(),"INSTRUCTION_CREATED",f);return f.getRequestKey();
    }
    @Transactional
    public void stop(AppUserPrincipal a,UUID id,String reason) {
        authorize(a,"UPDATE");evidence(reason);var instruction=records.instruction(a,id);records.lock(a,"EMPLOYER/"+instruction.employerId());
        if(!records.instruction(a,id).stopped()){records.stop(a,id,reason,clock.now());event(a,id,"INSTRUCTION_STOPPED",Map.of("reason",reason));}
    }
    @Transactional(readOnly=true)
    public Preview preview(AppUserPrincipal a,BatchForm f) {authorize(a,"CREATE");return calculate(a,f);}
    private Preview calculate(AppUserPrincipal a,BatchForm f) {
        LocalDate month=validate(f);var employer=records.employer(a,f.getEmployerId());
        var rows=records.preview(a,f,month);check(rows.size()<=1000,"large");
        var total=rows.stream().map(Line::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
        long employees=rows.stream().filter(r->r.amount().signum()>0).map(Line::clientId).distinct().count();
        long exceptions=rows.stream().filter(r->!r.issue().isEmpty()).count();
        String hash=hash(json.writeValueAsString(List.of(employer,f.getMonth(),f.getDeductionDate(),f.isIncludeArrears(),rows)));
        return new Preview(employer,rows,total,employees,exceptions,hash);
    }
    @Transactional(timeout=30)
    public UUID freeze(AppUserPrincipal a,BatchForm f) {
        authorize(a,"CREATE");validate(f);check(f.getRequestKey()!=null,"input");
        records.lock(a,"REQUEST/"+f.getRequestKey());
        String payload=hash(json.writeValueAsString(f));var retry=records.batchRequest(a,f.getRequestKey());
        if(retry.isPresent()) {
            check(a.getMemberId().equals(retry.get().get("actor_id")) && payload.equals(retry.get().get("payload_hash")),"retry");return (UUID)retry.get().get("id");
        }
        records.employer(a,f.getEmployerId());records.lock(a,"EMPLOYER/"+f.getEmployerId());
        check(!records.activeBatch(a,f.getEmployerId(),YearMonth.parse(f.getMonth()).atDay(1)),"batchExists");
        check(records.latestBatch(a,f.getEmployerId(),YearMonth.parse(f.getMonth()).atDay(1)).equals(Optional.ofNullable(f.getReplacesId())),"replacement");
        if(f.getReplacesId()!=null) {
            var previous=records.batch(a,f.getReplacesId());check(previous.state().equals("CANCELLED") && previous.month().equals(YearMonth.parse(f.getMonth()).atDay(1))
                && records.employerForBatch(a,f.getReplacesId()).equals(f.getEmployerId()),"replacement");
        }
        var preview=calculate(a,f);check(preview.hash().equals(f.getPreviewHash()),"stale");check(preview.total().signum()>0,"empty");
        check(preview.exceptions()==0 || f.isAcceptExceptions(),"exceptions");
        UUID id=UUID.randomUUID();records.insertBatch(a,id,f,preview,payload,clock.now(),YearMonth.parse(f.getMonth()).atDay(1));
        event(a,id,"BATCH_FROZEN",Map.of("snapshotHash",preview.hash(),"total",preview.total(),"exceptions",preview.exceptions()));return id;
    }
    @Transactional(readOnly=true)
    public Page<Batch> batches(AppUserPrincipal a,String search,String state,String month,int page,String sort,String direction) {
        authorize(a,"VIEW");filter(search,page);check(Set.of("","FROZEN","SENT","CANCELLED").contains(state),"input");
        String period="";if(!month.isEmpty())try{period=YearMonth.parse(month).atDay(1).toString();}catch(DateTimeException ex){throw error("dates");}
        return page(records.batches(a,search,state,period,page,sort,direction),page);
    }
    @Transactional(readOnly=true)
    public Batch batch(AppUserPrincipal a,UUID id) {authorize(a,"VIEW");return records.batch(a,id);}
    @Transactional(readOnly=true)
    public Page<Line> lines(AppUserPrincipal a,UUID id,int page) {authorize(a,"VIEW");filter("",page);records.batch(a,id);return page(records.lines(a,id,page,26),page);}
    @Transactional(readOnly=true)
    public Page<Event> events(AppUserPrincipal a,UUID id,int page) {authorize(a,"VIEW");filter("",page);records.batch(a,id);return page(records.events(a,id,page),page);}
    @Transactional
    public void changeState(AppUserPrincipal a,UUID id,String kind,String evidence,boolean withdrawalConfirmed) {
        authorize(a,"UPDATE");check(Set.of("SENT","CANCELLED").contains(kind),"input");evidence(evidence);
        records.batch(a,id);records.lock(a,"EMPLOYER/"+records.employerForBatch(a,id));var old=records.batch(a,id);
        if(old.state().equals(kind))return;
        check(!old.state().equals("CANCELLED"),"state");
        check(!kind.equals("CANCELLED") || !records.hasFeedback(id),"feedback");
        check(!kind.equals("CANCELLED") || !old.state().equals("SENT") || withdrawalConfirmed,"withdrawal");
        records.event(a,id,kind,evidence,clock.now());event(a,id,"BATCH_"+kind,Map.of("evidence",evidence,"withdrawalConfirmed",withdrawalConfirmed));
    }
    @Transactional(readOnly=true)
    public List<Line> exportLines(AppUserPrincipal a,UUID id) {authorize(a,"EXPORT");records.batch(a,id);var rows=records.lines(a,id,0,1001);check(rows.size()<=1000,"large");return rows;}
    @Transactional(readOnly=true)
    public Optional<Document> document(AppUserPrincipal a,UUID id,String language) {authorize(a,"EXPORT");records.batch(a,id);return records.document(a,id,language);}
    @Transactional
    public Document downloaded(AppUserPrincipal a,UUID id,String language,Document document) {
        authorize(a,"EXPORT");check(Set.of("en","sw").contains(language),"input");records.batch(a,id);
        records.document(id,language,document,clock.now());var saved=records.document(a,id,language).orElseThrow();
        records.event(a,id,"DOWNLOAD",language+" / SHA-256 "+saved.checksum(),clock.now());return saved;
    }
    private LocalDate validate(BatchForm f) {
        check(f!=null && f.getEmployerId()!=null && f.getMonth()!=null,"input");
        try {
            var month=YearMonth.parse(f.getMonth());check(!month.isBefore(clock.currentYearMonth()) && !month.isAfter(clock.currentYearMonth().plusMonths(1)),"dates");
            check(f.getDeductionDate()!=null && YearMonth.from(f.getDeductionDate()).equals(month) && !f.getDeductionDate().isBefore(clock.today()),"dates");return month.atDay(1);
        }catch(DateTimeException failure){throw error("dates");}
    }
    private void event(AppUserPrincipal a,UUID id,String action,Object details) {
        audit.logEvent("PAYROLL",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"PAYROLL",id.toString(),a.getSaccoId(),a.getStationId(),details);
    }
    public static String hash(String value) {return hash(value.getBytes(StandardCharsets.UTF_8));}
    public static String hash(byte[] value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static <T> Page<T> page(List<T> rows,int page) {return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private static void filter(String search,int page) {check(search!=null && search.length()<=100 && search.chars().noneMatch(Character::isISOControl) && page>=0 && page<=10000,"input");}
    private static void text(String value,int max) {check(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(Character::isISOControl),"input");}
    private static void evidence(String value) {text(value==null?null:value.replace('\r',' ').replace('\n',' ').replace('\t',' '),500);}
    private static void check(boolean condition,String key) {if(!condition)throw error(key);}
    private static IllegalArgumentException error(String key) {return new IllegalArgumentException("payroll.error."+key);}
}
