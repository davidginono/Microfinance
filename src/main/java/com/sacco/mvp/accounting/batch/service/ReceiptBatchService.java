package com.sacco.mvp.accounting.batch.service;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.Page;
import com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ReceiptEntryLoan;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.repayment.service.RepaymentAllocationCalculator;
import com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService;
import com.sacco.mvp.accounting.service.LoanReceiptService;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReceiptBatchService {
    private final ReceiptBatchRepository repository;
    private final LoanReceiptService receipts;
    private final LoanRecordingService loans;
    private final VoucherService vouchers;
    private final RepaymentPolicyService policies;
    private final AuditService audit;
    private final ApplicationClock clock;
    public void authorize(AppUserPrincipal a,boolean post){vouchers.authorize(a,Type.RECEIPT,post?"CREATE":"VIEW");loans.authorize(a,"VIEW");if(post)loans.authorize(a,"POST");}
    @Transactional(readOnly=true)
    public Page<Batch> list(AppUserPrincipal a,String search,int page){authorize(a,false);query(search,page);var rows=repository.list(a,search,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional
    public UUID create(AppUserPrincipal a,String title){authorize(a,true);require(title!=null && !title.isBlank() && title.length()<=120 && title.chars().noneMatch(c->c<32),"input");UUID id=repository.create(a,title.strip(),clock.now());event(a,id,"RECEIPT_BATCH_CREATED");return id;}
    @Transactional(readOnly=true)
    public Summary get(AppUserPrincipal a,UUID id){authorize(a,false);return repository.summary(find(a,id,false));}
    @Transactional(readOnly=true)
    public boolean payrollLinked(AppUserPrincipal a,UUID id){authorize(a,false);find(a,id,false);return repository.payrollLinked(id);}
    @Transactional(readOnly=true)
    public Page<Item> items(AppUserPrincipal a,UUID id,int page,String state){authorize(a,false);query("",page);require(Set.of("","DRAFT","READY","FAILED","POSTED").contains(state),"input");find(a,id,false);var rows=repository.items(id,page,state);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}

    @Transactional(readOnly = true)
    public List<Balances> balances(AppUserPrincipal actor, UUID loanId, LocalDate date) {
        authorize(actor, true);
        return loans.receiptLoans(actor, List.of(loanId), date).stream().map(loan -> {
            boolean available = loan.disbursed() != null && !date.isBefore(loan.disbursed())
                && !date.isAfter(clock.today()) && (loan.lastPayment() == null || !date.isBefore(loan.lastPayment()));
            return new Balances(loan.id(), loan.number(), available ? loan.outstandingPrincipal() : null,
                available ? loan.duePrincipal() : null, available ? loan.dueInterest() : null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Batch spreadsheetBatch(AppUserPrincipal actor, UUID id) {
        authorize(actor, true);
        return spreadsheetBatch(actor, id, false);
    }

    @Transactional
    public List<ReceiptEntryLoan> templateLoans(AppUserPrincipal actor, UUID id, String search) {
        authorize(actor, true);
        spreadsheetBatch(actor, id, false);
        query(search, 0);
        var results = loans.receiptEntryLoans(actor, search, List.of());
        require(!results.isEmpty(), "templateEmpty");
        require(results.size() <= ReceiptBatchWorkbook.MAX_ROWS, "templateLimit");
        event(actor, id, "RECEIPT_BATCH_TEMPLATE_REQUESTED", Map.of("loans", results.size()));
        return results;
    }

    @Transactional
    public int importDrafts(AppUserPrincipal actor, UUID id, UUID accountId, List<ImportRow> rows) {
        authorize(actor, true);
        var batch = spreadsheetBatch(actor, id, true);
        require(accountId != null, "importAccount");
        require(!rows.isEmpty() && rows.size() <= ReceiptBatchWorkbook.MAX_ROWS, "importEmpty");
        require(repository.summary(batch).receipts() + rows.size() <= 1000
            && repository.transactions(id) + rows.size() <= 2000, "limit");
        var account = loans.receiptMoneyAccount(actor, accountId);
        var references = new HashSet<>(repository.collectionReferences(id, accountId));
        var numbers = rows.stream().map(ImportRow::loanNumber).distinct().toList();
        var eligible = loans.receiptEntryLoans(actor, "", numbers).stream()
            .collect(java.util.stream.Collectors.groupingBy(ReceiptEntryLoan::number));
        var drafts = new ArrayList<ImportedDraft>(rows.size());
        var today = clock.today();
        for (var input : rows) {
            if (input.amount() == null || input.amount().signum() <= 0 || input.date() == null
                || input.reference() == null || input.reference().isBlank()) {
                throw new ImportRowError("batch.error.importRow", input.line());
            }
            var matches = eligible.getOrDefault(input.loanNumber(), List.of());
            if (matches.size() != 1) throw new ImportRowError("batch.error.importLoan", input.line());
            var loan = matches.getFirst();
            if (input.date().isAfter(today) || input.date().isBefore(loan.disbursed())) {
                throw new ImportRowError("batch.error.importDate", input.line());
            }
            if (!references.add(input.reference())) {
                throw new ImportRowError("batch.error.importReference", input.line());
            }
            var form = new Form();
            form.setClientId(loan.clientId());
            form.setMoneyAccountId(accountId);
            form.setEffectiveDate(input.date());
            String loanLabel = loan.number() + " - " + loan.product();
            var payment = new RowForm();
            payment.setLoanId(loan.id());
            payment.setAmount(input.amount());
            payment.setDescription(loanLabel);
            form.setRows(List.of(payment));
            drafts.add(new ImportedDraft(canonical(UUID.randomUUID(), form, input.reference()), loan.clientNumber() + " - " + loan.client(),
                Map.of(loan.id(), loanLabel, accountId, account.label())));
        }
        repository.insertImportedDrafts(batch, drafts);
        repository.invalidate(id);
        repository.state(id, "DRAFT", clock.now());
        event(actor, id, "RECEIPT_BATCH_DRAFTS_IMPORTED", Map.of("receipts", drafts.size()));
        return drafts.size();
    }

    private Batch spreadsheetBatch(AppUserPrincipal actor, UUID id, boolean lock) {
        var batch = editable(actor, id, lock);
        require(!repository.payrollLinked(id), "payroll");
        return batch;
    }
    @Transactional
    public Item save(AppUserPrincipal a,UUID batch,UUID id,SaveItem input){
        authorize(a,true);var b=editable(a,batch);require(!repository.payrollLinked(batch),"payroll");require(input!=null && input.form()!=null,"input");var prior=repository.item(batch,id);require(prior.isEmpty() || (!prior.get().state().equals("POSTED") && prior.get().version()==input.expectedVersion()),"stale");
        require(prior.isPresent() || input.expectedVersion()==0,"stale");require(prior.isPresent() || repository.summary(b).receipts()<1000,"limit");
        String reference=prior.map(p->p.form().getReference()).filter(r->!r.isBlank()).orElse("BATCH/"+id);
        Form f=canonical(id,input.form(),reference);int existing=prior.map(p->p.form().getRows().size()).orElse(0);require(repository.transactions(batch)-existing+f.getRows().size()<=2000,"limit");
        var labels=new HashMap<>(receipts.selected(a,f));if(f.getMoneyAccountId()!=null)labels.put(f.getMoneyAccountId(),loans.receiptMoneyAccount(a,f.getMoneyAccountId()).label());String client=f.getClientId()==null?"":loans.receiptClient(a,f.getClientId()).label();
        for(var row:f.getRows())if(row.getLoanId()!=null && row.getDescription().isBlank())row.setDescription(labels.getOrDefault(row.getLoanId(),""));
        BigDecimal total=f.getRows().stream().map(RowForm::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);require(total.precision()-total.scale()<=16,"input");
        repository.save(b,id,prior.map(Item::lineNo).orElseGet(()->repository.nextLine(batch)),input.expectedVersion(),f,client,labels,total,prior.isEmpty());repository.invalidate(batch);repository.state(batch,"DRAFT",clock.now());return repository.item(batch,id).orElseThrow();
    }
    @Transactional
    public void delete(AppUserPrincipal a,UUID batch,UUID item,int version){authorize(a,true);editable(a,batch);require(!repository.payrollLinked(batch),"payroll");var row=repository.item(batch,item).orElseThrow(()->new AccessDeniedException("Receipt unavailable"));require(row.version()==version && !row.state().equals("POSTED"),"stale");repository.delete(batch,item);repository.invalidate(batch);repository.state(batch,"DRAFT",clock.now());}
    @Transactional
    public void start(AppUserPrincipal a,UUID batch,int version,boolean post){
        authorize(a,true);var b=editable(a,batch);require(b.version()==version,"stale");var s=repository.summary(b);require(s.receipts()>s.posted(),"empty");
        if(post){require(s.ready()+s.posted()==s.receipts() && s.failed()==0,"validate");repository.state(batch,"POSTING",clock.now());event(a,batch,"RECEIPT_BATCH_POSTING_STARTED");}
        else {repository.invalidate(batch);repository.state(batch,"VALIDATING",clock.now());}
    }
    @Transactional
    public void cancel(AppUserPrincipal a,UUID id){authorize(a,true);var b=find(a,id,true);owner(a,b);require(Set.of("VALIDATING","POSTING").contains(b.state()),"busy");repository.cancel(id);event(a,id,"RECEIPT_BATCH_STOP_REQUESTED");}
    @Transactional
    public Plan estimate(AppUserPrincipal a,UUID batch,UUID item){authorize(a,true);var b=find(a,batch,false);owner(a,b);var i=repository.item(batch,item).orElseThrow(()->new AccessDeniedException("Receipt unavailable"));return plan(a,i,List.of());}
    @Transactional
    public Item linkAccounts(AppUserPrincipal a,UUID batch,UUID item,LinkAccounts request){
        authorize(a,true);editable(a,batch);
        var row=repository.item(batch,item).orElseThrow(()->new AccessDeniedException("Receipt unavailable"));
        require(request!=null && request.expectedVersion()==row.version() && !row.state().equals("POSTED"),"stale");
        require(row.form().getClientId()!=null && row.form().getRows().stream().allMatch(r->r.getLoanId()!=null),"input");
        var ids=row.form().getRows().stream().map(RowForm::getLoanId).distinct().sorted().toList();
        loans.lockReceiptLoans(a,ids,row.form().getClientId());
        for(var loan:ids)loans.linkExistingProductAccounts(a,loan);
        repository.invalidate(batch);repository.state(batch,"DRAFT",clock.now());
        return repository.item(batch,item).orElseThrow();
    }
    // Used by both synchronous estimates and the durable validator; no balances come from the browser.
    public Plan plan(AppUserPrincipal a,Item item,List<Plan> prior){
        Form f=item.form();var policy=policies.effective(a,f.getEffectiveDate(),true);f.setInterestAccountId(null);receipts.preview(a,f);
        var contexts=loans.receiptLoans(a,f.getRows().stream().map(RowForm::getLoanId).toList(),f.getEffectiveDate());var lines=new ArrayList<PlanLine>();BigDecimal principal=BigDecimal.ZERO,interest=BigDecimal.ZERO;
        for(var row:f.getRows()){
            var loan=contexts.stream().filter(l->l.id().equals(row.getLoanId())).findFirst().orElseThrow();
            if(loan.charges().signum()>0)throw new IllegalArgumentException("charges.error.batch");
            var earlier=prior.stream().flatMap(p->p.lines().stream()).filter(l->l.loanId().equals(loan.id())).map(l->{require(l.paymentDate()!=null && l.servicingAllocations()!=null,"validate");return new com.sacco.mvp.service.LoanRepaymentLedgerService.StagedPayment(l.paymentDate(),l.servicingAllocations());}).toList();
            var split=loans.stagedReceiptPreview(a,loan.id(),f.getEffectiveDate(),row.getAmount(),earlier);
            lines.add(new PlanLine(loan.id(),loan.number(),row.getAmount(),split.before().outstandingPrincipal(),split.before().duePrincipal(),split.before().dueInterest(),split.principal(),split.interest(),split.principalBalance(),loan.principalAccount(),loan.principalAccountId(),loan.interestAccountId(),loan.interestAccount(),loan.product(),f.getEffectiveDate(),split.allocations()));principal=principal.add(split.principal());interest=interest.add(split.interest());
        }
        return new Plan(policy.id(),policy.version(),policy.allocationOrder(),loans.receiptMoneyAccount(a,f.getMoneyAccountId()).label(),"",lines,principal.add(interest),principal,interest);
    }
    public static boolean samePlan(Plan expected,Plan actual){
        if(expected==null || !Objects.equals(expected.policyId(),actual.policyId()) || !expected.moneyAccount().equals(actual.moneyAccount()) || !expected.interestAccount().equals(actual.interestAccount()) || expected.lines().size()!=actual.lines().size())return false;
        for(int n=0;n<actual.lines().size();n++){var e=expected.lines().get(n);var a=actual.lines().get(n);if(!e.loanId().equals(a.loanId()) || e.servicingAllocations()==null || !Objects.equals(e.servicingAllocations(),a.servicingAllocations()) || !Objects.equals(e.paymentDate(),a.paymentDate()) || !Objects.equals(e.principalAccountId(),a.principalAccountId()) || !Objects.equals(e.interestAccountId(),a.interestAccountId()) || e.principalAccountId()==null || e.interestAccountId()==null || !Objects.equals(e.principalAccount(),a.principalAccount()) || e.amount().compareTo(a.amount())!=0 || e.principal().compareTo(a.principal())!=0 || e.interest().compareTo(a.interest())!=0 || e.principalBalance().compareTo(a.principalBalance())!=0 || e.duePrincipal().compareTo(a.duePrincipal())!=0 || e.dueInterest().compareTo(a.dueInterest())!=0)return false;}return true;
    }
    Batch find(AppUserPrincipal a,UUID id,boolean lock){return repository.find(a,id,lock).orElseThrow(()->new AccessDeniedException("Batch unavailable"));}
    private Batch editable(AppUserPrincipal a,UUID id){return editable(a,id,true);}
    private Batch editable(AppUserPrincipal a,UUID id,boolean lock){var b=find(a,id,lock);owner(a,b);require(!Set.of("VALIDATING","POSTING","COMPLETED").contains(b.state()),"busy");return b;}
    private static void owner(AppUserPrincipal a,Batch b){if(!a.getMemberId().equals(b.owner()))throw new AccessDeniedException("Batch owner required");}
    private static Form canonical(UUID id,Form source,String reference){
        require(source.getRows()!=null && !source.getRows().isEmpty() && source.getRows().size()<=50,"limit");var f=new Form();f.setRequestKey(id);f.setReceiptPurpose("LOAN_REPAYMENT");f.setClientId(source.getClientId());f.setEffectiveDate(source.getEffectiveDate());f.setMoneyAccountId(source.getMoneyAccountId());
        f.setReference(text(reference,80));f.setDescription(text(source.getDescription(),500));f.setEvidence(text(source.getEvidence(),500));var rows=new ArrayList<RowForm>();
        for(var input:source.getRows()){require(input!=null && input.getTransactionId()==null && input.getDebitAccountId()==null && input.getCreditAccountId()==null && input.getTemplateKey()==null,"input");var r=new RowForm();r.setLoanId(input.getLoanId());r.setAmount(input.getAmount());require(r.getAmount()==null || (r.getAmount().scale()>=-16 && r.getAmount().scale()<=2 && (long)r.getAmount().precision()-r.getAmount().scale()<=16),"input");if(r.getAmount()!=null)r.setAmount(r.getAmount().setScale(2));r.setDescription(text(input.getDescription(),500));rows.add(r);}f.setRows(rows);return f;
    }
    private static String text(String value,int max){String s=value==null?"":value.strip();require(s.length()<=max && s.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),"input");return s;}
    private static void query(String search,int page){require(search!=null && search.length()<=100 && page>=0 && page<=10000,"input");}
    private static void require(boolean condition,String key){if(!condition)throw new IllegalArgumentException("batch.error."+key);}
    private void event(AppUserPrincipal a,UUID id,String action){event(a,id,action,Map.of());}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,Object> details){audit.logEvent("ACCOUNTING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,"Receipt batch","RECEIPT_BATCH",id.toString(),a.getSaccoId(),a.getStationId(),details);}
}
