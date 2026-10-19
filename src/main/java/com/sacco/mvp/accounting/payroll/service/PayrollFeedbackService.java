package com.sacco.mvp.accounting.payroll.service;

import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.*;
import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.repository.*;
import com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchService;
import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.dto.VoucherDtos.RowForm;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

@Service @RequiredArgsConstructor
public class PayrollFeedbackService {
    private final PayrollService payroll;
    private final PayrollRepository source;
    private final PayrollFeedbackRepository repository;
    private final PayrollFeedbackWorkbook workbook;
    private final ReceiptBatchService batches;
    private final ReceiptBatchRepository batchRepository;
    private final LoanRecordingService loans;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    @Transactional(timeout=30)
    public UUID upload(AppUserPrincipal a,UUID batch,String filename,byte[] content,String evidence) {
        payroll.authorize(a,"IMPORT");text(evidence,500);check(filename!=null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"),"file");
        var inputs=workbook.read(content);source.batch(a,batch);source.lock(a,"EMPLOYER/"+source.employerForBatch(a,batch));
        var b=source.batch(a,batch);String checksum=PayrollService.hash(content);
        String semantic=PayrollService.hash(json.writeValueAsString(inputs.stream().sorted(Comparator.comparing(Input::reference)).map(i->List.of(i.batchReference(),i.employee(),i.reference(),i.status(),i.amount(),Objects.toString(i.date(),""),i.employerReference())).toList()));
        var retry=repository.retry(batch,checksum,semantic);if(retry.isPresent())return retry.get();
        check(b.state().equals("SENT"),"sent");
        var lines=source.lines(a,batch,0,1001);check(lines.size()<=1000,"rows");
        var grouped=new LinkedHashMap<String,List<Line>>();
        for(var line:lines)if(line.amount().signum()>0)grouped.computeIfAbsent(reference(batch,line.clientId()),ignored->new ArrayList<>()).add(line);
        var prior=repository.successes(batch);var matched=new ArrayList<Matched>();
        for(var i:inputs) {
            try {
                check(i.batchReference().equals(b.reference()),"batch");var group=grouped.get(i.reference());check(group!=null && group.getFirst().employeeNumber().equals(i.employee()),"match");
                var total=group.stream().map(Line::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
                check(i.amount().compareTo(total)<=0,"amount");check(i.date()==null || (!i.date().isAfter(clock.today()) && YearMonth.from(i.date()).equals(YearMonth.from(b.month()))),"date");
                check(!i.status().equals("SUCCESS") || !prior.contains(group.getFirst().clientId()),"duplicate");
                matched.add(new Matched(i,group.getFirst().clientId(),group.getFirst().clientName(),total));
            }catch(IllegalArgumentException ex){throw new PayrollFeedbackWorkbook.RowError(ex.getMessage(),i.line());}
        }
        String name=filename.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);check(name.length()<=150 && name.chars().noneMatch(c->c<32),"file");
        UUID id=repository.insert(a,batch,name,content,checksum,semantic,evidence.strip(),matched,clock.now());
        event(a,id,"PAYROLL_FEEDBACK_IMPORTED",Map.of("batch",batch,"checksum",checksum,"rows",matched.size()));return id;
    }
    @Transactional(readOnly=true)
    public Page<Feedback> list(AppUserPrincipal a,UUID batch,int page,String sort,String direction){payroll.authorize(a,"VIEW");source.batch(a,batch);page(page);var rows=repository.list(a,batch,page,sort,direction);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true)
    public Feedback get(AppUserPrincipal a,UUID batch,UUID id){payroll.authorize(a,"VIEW");return repository.get(a,batch,id);}
    @Transactional(readOnly=true)
    public Page<Result> rows(AppUserPrincipal a,UUID batch,UUID id,int page){get(a,batch,id);page(page);var rows=repository.rows(id,page*25,26);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    public void authorizeRemittance(AppUserPrincipal a){payroll.authorize(a,"POST");batches.authorize(a,false);batches.authorize(a,true);}
    @Transactional(readOnly=true)
    public Optional<Remittance> remittance(AppUserPrincipal a,UUID batch,UUID id){get(a,batch,id);return repository.remittance(id);}
    @Transactional(timeout=30)
    public UUID prepare(AppUserPrincipal a,UUID batch,UUID feedback,RemittanceForm f) {
        authorizeRemittance(a);var payrollBatch=source.batch(a,batch);source.lock(a,"FEEDBACK/"+feedback);var upload=repository.get(a,batch,feedback);
        // Preparation is immutable. Repeated commands return the original sealed receipt batch.
        if(upload.receiptBatchId()!=null){check(repository.sameRemittance(a,feedback,f),"retry");return upload.receiptBatchId();}
        check(f!=null && f.isVerified() && f.getMoneyAccountId()!=null,"remittance");text(f.getReference(),80);text(f.getEvidence(),500);
        check(f.getReceivedAmount()!=null && f.getReceivedAmount().compareTo(upload.total())==0 && upload.total().signum()>0,"remittanceAmount");
        check(f.getReceivedDate()!=null && !f.getReceivedDate().isAfter(clock.today()),"date");
        var money=loans.receiptMoneyAccount(a,f.getMoneyAccountId());var rows=repository.rows(feedback,0,1001);check(rows.size()<=1000,"rows");
        check(rows.stream().filter(r->r.status().equals("SUCCESS")).noneMatch(r->r.date().isAfter(f.getReceivedDate())),"date");
        var all=source.lines(a,batch,0,1001);check(all.size()<=1000,"rows");
        String title=payrollBatch.employer().substring(0,Math.min(80,payrollBatch.employer().length()))+" / "+YearMonth.from(payrollBatch.month())+" / "+feedback.toString().substring(0,8);
        UUID receiptBatch=batches.create(a,title);var target=batchRepository.find(a,receiptBatch,true).orElseThrow();int n=0;
        for(var r:rows)if(r.status().equals("SUCCESS")) {
            var included=all.stream().filter(l->l.clientId().equals(r.clientId()) && l.amount().signum()>0).toList();
            var form=new Form();UUID item=UUID.randomUUID();form.setRequestKey(item);form.setReceiptPurpose("LOAN_REPAYMENT");form.setClientId(r.clientId());form.setMoneyAccountId(f.getMoneyAccountId());form.setEffectiveDate(f.getReceivedDate());
            form.setReference("PAY/"+r.reference());form.setDescription("Payroll "+batch+" / "+r.employerReference());form.setEvidence(f.getEvidence().strip());
            var transactions=new ArrayList<RowForm>();var labels=new HashMap<UUID,String>();labels.put(money.id(),money.label());var remaining=r.amount();
            // Explicit deterministic allocation across this employee's frozen loan rows; the receipt preview shows each split.
            for(var line:included){if(remaining.signum()==0)break;var amount=remaining.min(line.amount());var t=new RowForm();t.setLoanId(line.loanId());t.setAmount(amount);t.setDescription("Payroll "+r.employee()+" / "+line.loanNumber());transactions.add(t);labels.put(line.loanId(),line.loanNumber());remaining=remaining.subtract(amount);}
            check(remaining.signum()==0 && !transactions.isEmpty() && transactions.size()<=50,"allocation");form.setRows(transactions);
            batchRepository.save(target,item,++n,0,form,r.clientName(),labels,r.amount(),true);repository.link(feedback,r.line(),item);
        }
        repository.remit(a,feedback,receiptBatch,f,clock.now());
        event(a,feedback,"PAYROLL_REMITTANCE_VERIFIED",Map.of("batch",batch,"receiptBatch",receiptBatch,"reference",f.getReference().strip(),"receivedAmount",f.getReceivedAmount()));return receiptBatch;
    }
    public static String reference(UUID batch,UUID client){return UUID.nameUUIDFromBytes((batch+"/"+client).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().toUpperCase(Locale.ROOT);}
    private static void text(String value,int max){check(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),"input");}
    private static void page(int page){check(page>=0 && page<=10000,"input");}
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("payroll.feedback.error."+key);}
    private void event(AppUserPrincipal a,UUID id,String action,Object data){audit.logEvent("ACCOUNTING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,"Payroll feedback","PAYROLL_FEEDBACK",id.toString(),a.getSaccoId(),a.getStationId(),data);}
}
