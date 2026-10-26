package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.repository.LoanReceiptRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** A document envelope, not a second money-posting engine. */
@Service @RequiredArgsConstructor
public class LoanReceiptService {
    private final LoanReceiptRepository receipts;
    private final VoucherRepository vouchers;
    private final VoucherService ordinary;
    private final LoanRecordingService loans;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final com.sacco.mvp.grouplending.service.GroupCollectionService groups;
    public boolean available(AppUserPrincipal a,String action){try{loans.authorize(a,action);return true;}catch(AccessDeniedException denied){return false;}}
    @Transactional(readOnly=true)
    public com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.Policy defaults(AppUserPrincipal a,Form f){authorize(a,"POST");return loans.receiptDefaults(a,f);}
    private void authorize(AppUserPrincipal a,String action){ordinary.authorize(a,Type.RECEIPT,action.equals("POST")?"CREATE":action);loans.authorize(a,action);}
    @Transactional(readOnly=true)
    public void prefill(AppUserPrincipal a,Form f,UUID loanId){authorize(a,"POST");var loan=loans.receiptLoan(a,loanId);f.setClientId(loan.clientId());f.getRows().getFirst().setLoanId(loanId);}
    @Transactional(readOnly=true)
    public Page list(AppUserPrincipal a,Filter f){ordinary.authorize(a,Type.RECEIPT,"VIEW");VoucherService.validateFilter(f);var rows=receipts.list(a,f,available(a,"VIEW"),groups.available(a,"VIEW"));return new Page(rows.stream().limit(f.size()).toList(),f.page(),rows.size()>f.size());}
    @Transactional(readOnly=true)
    public Optional<LoanReceipt> find(AppUserPrincipal a,UUID id){ordinary.authorize(a,Type.RECEIPT,"VIEW");var source=receipts.find(a,id,false);if(source.isEmpty()){var legacy=receipts.legacy(a,id);if(legacy.isPresent()){loans.authorize(a,"VIEW");return legacy;}return groups.find(a,id);}loans.authorize(a,"VIEW");var r=source.get();return Optional.of(new LoanReceipt(r.header(),r.clientNumber(),receipts.rows(a,id),null,r.payer()));}
    @Transactional(readOnly=true)
    public LoanReceipt export(AppUserPrincipal a,UUID id){ordinary.requireExport(a);loans.authorize(a,"EXPORT");var receipt=find(a,id).orElseThrow(()->new AccessDeniedException("Receipt unavailable"));if(receipt.group()!=null)groups.authorize(a,"EXPORT");return receipt;}
    public boolean canReverse(AppUserPrincipal a,LoanReceipt receipt){return receipt.group()==null?available(a,"REVERSE"):groups.available(a,"REVERSE");}
    @Transactional(readOnly=true)
    public List<Choice> choices(AppUserPrincipal a,String category,UUID client,String search,int page){authorize(a,"POST");return category.equals("loans")?loans.repaymentLoans(a,client,search,page):loans.choices(a,switch(category){case "clients"->"ledger-clients";case "interest"->"interest";default->throw new Invalid("","filter");},search,page);}
    @Transactional(readOnly=true)
    public Map<UUID,String> selected(AppUserPrincipal a,Form f){authorize(a,"POST");var labels=new HashMap<UUID,String>();if(f.getRows()==null || f.getRows().size()>50)return labels;
        if(f.getClientId()!=null)labels.put(f.getClientId(),loans.receiptClient(a,f.getClientId()).label());
        if(f.getInterestAccountId()!=null)labels.put(f.getInterestAccountId(),loans.receiptInterestAccount(a,f.getInterestAccountId()).label());
        var ids=f.getRows().stream().filter(Objects::nonNull).map(RowForm::getLoanId).filter(Objects::nonNull).distinct().toList();
        for(var loan:loans.receiptLoans(a,ids,f.getEffectiveDate()==null?clock.today():f.getEffectiveDate()))labels.put(loan.id(),loan.number()+" - "+loan.product());return labels;}
    @Transactional(readOnly=true)
    public List<LoanReceiptRow> preview(AppUserPrincipal a,Form f){authorize(a,"POST");validate(f);loans.receiptDefaults(a,f);var rows=new ArrayList<LoanReceiptRow>();var allocations=loans.receiptPreview(a,f);requirePayer(a,f);var names=selected(a,f);int n=0;
        for(var row:f.getRows()){var p=allocations.get(n++);rows.add(new LoanReceiptRow(n,null,row.getLoanId(),names.get(row.getLoanId()),row.getDescription(),row.getAmount(),p.principal(),p.interest(),p.principalBalance(),"","","",reference(f,n)));}return rows;}
    @Transactional
    public LoanReceipt post(AppUserPrincipal a,Form f){
        authorize(a,"POST");validate(f);loans.receiptDefaults(a,f);String hash=hash(f);vouchers.lockRequest(a.getSaccoId(),a.getStationId(),f.getRequestKey());var retry=receipts.retry(a,f.getRequestKey(),hash);if(retry.isPresent())return find(a,retry.get()).orElseThrow();
        check(vouchers.byRequest(a.getSaccoId(),a.getStationId(),f.getRequestKey()).isEmpty(),"","retry");
        // Lock all loan sources in deterministic order before invoking their owning posting use case.
        loans.lockReceiptLoans(a,f.getRows().stream().map(RowForm::getLoanId).sorted().toList(),f.getClientId());requirePayer(a,f);
        var result=new ArrayList<LoanReceiptRow>();var client=loans.receiptLoan(a,f.getRows().getFirst().getLoanId());int n=0;
        for(var row:f.getRows()){var p=loans.repay(a,command(f,row,++n));result.add(new LoanReceiptRow(n,p.id(),p.loanId(),p.loanNumber(),row.getDescription(),p.amount(),p.principal(),p.interest(),p.principalBalance(),p.moneyAccount(),p.principalAccount(),p.interestAccount(),p.reference()));}
        var v=header(a,f,client.client(),total(result),null);receipts.insert(a,v,f.getRequestKey(),f.getClientId(),client.clientNumber(),hash,result,f.getLoanPayer());ordinary.consumeNumber(a,Type.RECEIPT,f.getRequestKey());event(a,v);return find(a,v.id()).orElseThrow();
    }
    @Transactional
    public LoanReceipt reverse(AppUserPrincipal a,UUID id,UUID key,LocalDate date,String reason){
        authorize(a,"REVERSE");check(key!=null && date!=null && !date.isAfter(clock.today()),"","request");text(reason,500,true,"description");
        vouchers.lockRequest(a.getSaccoId(),a.getStationId(),key);var aggregate=receipts.find(a,id,true);
        if(aggregate.isEmpty()){var legacy=receipts.legacy(a,id);if(legacy.isEmpty())return groups.reverse(a,id,key,date,reason);var p=loans.reverse(a,legacy.get().rows().getFirst().postingId(),key,date,reason);return find(a,p.id()).orElseThrow();}
        var source=aggregate.get();
        var original=source.header();check(!a.getMemberId().equals(original.postedBy()),"","independentCorrection");
        String hash=hash(List.of(id,key,date,reason));var prior=receipts.retry(a,key,hash);if(prior.isPresent())return find(a,prior.get()).orElseThrow();
        check(original.reversesId()==null && original.reversedBy()==null && !date.isBefore(original.effectiveDate()),"","reversal");
        var rows=receipts.rows(a,id);loans.lockReceiptLoans(a,rows.stream().map(LoanReceiptRow::loanId).sorted().toList(),null);
        var reversed=new ArrayList<LoanReceiptRow>();for(var r:rows){var p=loans.reverseReceiptRow(a,id,r.postingId(),rowKey(key,r.lineNo()),date,reason);reversed.add(new LoanReceiptRow(r.lineNo(),p.id(),r.loanId(),r.loanNumber(),r.description(),p.amount(),p.principal(),p.interest(),p.principalBalance(),p.moneyAccount(),p.principalAccount(),p.interestAccount(),p.reference()));}
        var f=new Form();f.setRequestKey(key);f.setEffectiveDate(date);f.setMoneyAccountId(original.moneyAccountId());f.setReference(original.reference());f.setDescription(reason);f.setEvidence(original.evidence());
        var v=header(a,f,original.party(),original.total(),id);var client=loans.receiptLoan(a,rows.getFirst().loanId());receipts.insert(a,v,key,client.clientId(),source.clientNumber(),hash,reversed,source.payer());event(a,v);return find(a,v.id()).orElseThrow();
    }
    private Voucher header(AppUserPrincipal a,Form f,String party,BigDecimal total,UUID reverses){var number=reverses==null?ordinary.reserveNumber(a,Type.RECEIPT,f.getEffectiveDate(),f.getRequestKey()).number():vouchers.number(Type.RECEIPT,f.getEffectiveDate());return new Voucher(UUID.randomUUID(),number,Type.RECEIPT,f.getEffectiveDate(),party,f.getReference().strip(),f.getDescription().strip(),f.getEvidence().strip(),f.getMoneyAccountId(),total,f.getRows().size(),null,a.getMemberId(),a.getFullName(),clock.now(),reverses,null,"","",List.of());}
    private void event(AppUserPrincipal a,Voucher v){audit.logEvent("ACCOUNTING",v.id(),v.reversesId()==null?"LOAN_RECEIPT_POSTED":"LOAN_RECEIPT_REVERSED",a.getMemberId(),AuditEventStatus.SUCCESS,"Loan receipt","VOUCHER",v.number(),a.getSaccoId(),a.getStationId(),Map.of("amount",v.total()));}
    private PostForm command(Form f,RowForm row,int n){var p=new PostForm();p.setRequestKey(rowKey(f.getRequestKey(),n));p.setLoanId(row.getLoanId());p.setAmount(row.getAmount());p.setEffectiveDate(f.getEffectiveDate());p.setMoneyAccountId(f.getMoneyAccountId());p.setInterestAccountId(f.getInterestAccountId());p.setReference(reference(f,n));p.setEvidence(f.getEvidence());p.setNotes(row.getDescription());return p;}
    private static UUID rowKey(UUID key,int n){return UUID.nameUUIDFromBytes(("LOAN_RECEIPT/"+key+"/"+n).getBytes(StandardCharsets.UTF_8));}
    private static String reference(Form f,int n){return f.getReference().strip()+"/"+n;}
    private BigDecimal total(List<LoanReceiptRow> rows){var total=rows.stream().map(LoanReceiptRow::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);check(total.precision()<=18,"","amount");return total;}
    private void validate(Form f){check(f!=null && "LOAN_REPAYMENT".equals(f.getReceiptPurpose()) && f.getActivityId()==null && f.getRequestKey()!=null,"","request");check(f.getClientId()!=null,"clientId","loanClient");check(f.getMoneyAccountId()!=null,"moneyAccountId","moneyAccount");check(f.getEffectiveDate()!=null && !f.getEffectiveDate().isAfter(clock.today()),"effectiveDate","date");text(f.getReference(),80,true,"reference");text(f.getDescription(),500,false,"description");text(f.getEvidence(),500,false,"evidence");if(f.getLoanPayer()!=null)text(f.getLoanPayer(),160,false,"loanPayer");check(f.getRows()!=null && !f.getRows().isEmpty() && f.getRows().size()<=50,"","rows");var ids=new HashSet<UUID>();
        for(int n=0;n<f.getRows().size();n++){var r=f.getRows().get(n);String field="rows["+n+"]";check(r!=null && r.getLoanId()!=null && ids.add(r.getLoanId()),field+".loanId","loanRow");check(r.getTransactionId()==null && r.getDebitAccountId()==null && r.getCreditAccountId()==null,"","request");text(r.getDescription(),500,true,field+".description");var v=r.getAmount();check(v!=null && v.signum()>0 && v.scale()<=2 && v.precision()-v.scale()<=16,field+".amount","amount");}
    }
    private void requirePayer(AppUserPrincipal a,Form f){if("ORGANIZATION".equals(loans.receiptClient(a,f.getClientId()).purpose()))text(f.getLoanPayer(),160,true,"loanPayer");}
    private static void text(String s,int max,boolean required,String field){check(s!=null && s.length()<=max && (!required || !s.isBlank()) && s.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),field,"text");}
    private static void check(boolean value,String field,String key){if(!value)throw new Invalid(field,key);}
    private String hash(Object source){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(source).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
