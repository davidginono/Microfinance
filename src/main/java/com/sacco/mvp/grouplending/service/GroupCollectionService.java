package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.*;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice;
import com.sacco.mvp.grouplending.repository.GroupCollectionRepository;
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

/** Groups loan-owned postings and cash attribution in one transaction, without an envelope journal. */
@Service @RequiredArgsConstructor
public class GroupCollectionService {
    private final GroupCollectionRepository receipts;
    private final GeneralLedgerService access;
    private final LoanRecordingService loans;
    private final VoucherService vouchers;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    public void authorize(AppUserPrincipal a,String action){
        access.requireActor(a,"GROUP_LENDING_VIEW");vouchers.authorize(a,Type.RECEIPT,"VIEW");loans.authorize(a,"VIEW");
        if(!action.equals("VIEW")){access.requireActor(a,"GROUP_LENDING_"+action);loans.authorize(a,action);
            if(action.equals("EXPORT"))vouchers.requireExport(a);else vouchers.authorize(a,Type.RECEIPT,action.equals("POST")?"CREATE":action);}
    }
    public boolean available(AppUserPrincipal a,String action){try{authorize(a,action);return true;}catch(AccessDeniedException e){return false;}}
    @Transactional(readOnly=true) public Cycle cycle(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return receipts.cycle(a,id);}
    @Transactional public String number(AppUserPrincipal a,UUID id,GroupCollectionDtos.Form form){authorize(a,"POST");cycleReady(a,id);return vouchers.reserveNumber(a,Type.RECEIPT,form.getEffectiveDate(),form.getRequestKey()).number();}
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,UUID cycle,String kind,String search,int page){
        authorize(a,"POST");cycleReady(a,cycle);check(search!=null && search.length()<=100 && page>=0 && page<=10000,"query");
        return switch(kind){case "loans"->receipts.loanChoices(a,cycle,search,page);case "payers"->receipts.payerChoices(a,cycle,search,page);default->throw new IllegalArgumentException("group.error.request");};
    }
    @Transactional(readOnly=true) public Map<UUID,String> selected(AppUserPrincipal a,UUID cycle,GroupCollectionDtos.Form form){
        authorize(a,"POST");cycleReady(a,cycle);check(form!=null && form.getRows()!=null && form.getRows().size()<=50,"request");var labels=new HashMap<UUID,String>();
        var ids=form.getRows().stream().filter(Objects::nonNull).map(Row::getLoanId).filter(Objects::nonNull).distinct().toList();
        receipts.beneficiaries(a,cycle,ids).values().forEach(b->labels.put(b.loanId(),b.loanNumber()+" - "+b.clientNumber()+" - "+b.name()));
        var payerIds=form.getRows().stream().filter(Objects::nonNull).map(Row::getPayerId).filter(Objects::nonNull).distinct().toList();
        receipts.payers(a,cycle,payerIds).values().forEach(p->labels.put(p.id(),p.number()+" - "+p.name()));
        if(form.getMoneyAccountId()!=null)labels.put(form.getMoneyAccountId(),loans.receiptMoneyAccount(a,form.getMoneyAccountId()).label());return labels;
    }
    @Transactional public GroupCollectionDtos.Preview preview(AppUserPrincipal a,UUID cycle,GroupCollectionDtos.Form form){
        authorize(a,"POST");validate(form);var c=cycleReady(a,cycle);String number=vouchers.reserveNumber(a,Type.RECEIPT,form.getEffectiveDate(),form.getRequestKey()).number();return plan(a,c,form,number);
    }
    private GroupCollectionDtos.Preview plan(AppUserPrincipal a,Cycle cycle,GroupCollectionDtos.Form form,String number){
        var amounts=totals(form);var ids=new ArrayList<>(amounts.keySet());var borrowers=receipts.beneficiaries(a,cycle.id(),ids);
        check(borrowers.size()==ids.size(),"collectionLoan");var payerIds=form.getRows().stream().map(Row::getPayerId).distinct().toList();var payers=receipts.payers(a,cycle.id(),payerIds);check(payers.size()==payerIds.size(),"collectionPayer");
        var requests=ids.stream().map(id->new CollectionAmount(id,borrowers.get(id).clientId(),amounts.get(id))).toList();var allocations=loans.previewCollections(a,requests,form.getEffectiveDate(),form.getMoneyAccountId());
        var components=new LinkedHashMap<UUID,AllocationPreview>();var rows=new ArrayList<PreviewLoan>();for(int i=0;i<ids.size();i++){components.put(ids.get(i),allocations.get(i));rows.add(new PreviewLoan(borrowers.get(ids.get(i)),amounts.get(ids.get(i)),allocations.get(i)));}
        var contributions=GroupContributionAllocator.allocate(form,borrowers,payers,components);
        String fingerprint=hash(List.of(command(cycle.id(),form),number,rows,contributions,receipts.versions(a,ids)));
        return new GroupCollectionDtos.Preview(fingerprint,number,List.copyOf(rows),contributions,amounts.values().stream().reduce(new BigDecimal("0.00"),BigDecimal::add));
    }
    @Transactional public LoanReceipt post(AppUserPrincipal a,UUID cycle,GroupCollectionDtos.Form form){
        authorize(a,"POST");validate(form);String payload=hash(command(cycle,form));receipts.requestLock(a,form.getRequestKey());var retry=receipts.retry(a,form.getRequestKey(),payload);if(retry.isPresent())return find(a,retry.get()).orElseThrow();
        var c=cycleReady(a,cycle);var amounts=totals(form);var ids=amounts.keySet().stream().sorted().toList();loans.lockReceiptLoans(a,ids,null);
        String number=vouchers.reserveNumber(a,Type.RECEIPT,form.getEffectiveDate(),form.getRequestKey()).number();var preview=plan(a,c,form,number);
        check(form.getPreviewHash()!=null && form.getPreviewHash().equals(preview.hash()),"previewChanged");
        var rows=new ArrayList<LoanReceiptRow>();int line=0;
        for(var row:preview.loans()){
            var request=new PostForm();request.setRequestKey(GroupContributionAllocator.key(form.getRequestKey(),"LOAN/"+row.beneficiary().loanId()));request.setLoanId(row.beneficiary().loanId());request.setAmount(row.amount());request.setEffectiveDate(form.getEffectiveDate());request.setMoneyAccountId(form.getMoneyAccountId());
            request.setReference(postReference(form,++line));request.setEvidence(form.getEvidence());request.setNotes(c.groupCode()+" / "+form.getRemitter());var p=loans.repay(a,request);
            check(p.principal().compareTo(row.allocation().principal())==0 && p.interest().compareTo(row.allocation().interest())==0 && p.charges().compareTo(row.allocation().charges())==0,"previewChanged");
            rows.add(new LoanReceiptRow(line,p.id(),p.loanId(),p.loanNumber(),request.getNotes(),p.amount(),p.principal(),p.interest(),p.principalBalance(),p.moneyAccount(),p.principalAccount(),p.interestAccount(),p.reference()));
        }
        var receipt=new LoanReceipt(header(a,form,c,number,preview.total(),rows.size(),null,""),"",List.copyOf(rows),new GroupReceipt(c.id(),c.groupCode(),c.groupName(),preview.contributions()));
        receipts.insert(a,receipt,form.getRequestKey(),payload,json.writeValueAsString(form));vouchers.consumeNumber(a,Type.RECEIPT,form.getRequestKey());event(a,receipt);return find(a,receipt.header().id()).orElseThrow();
    }
    @Transactional(readOnly=true) public Optional<LoanReceipt> find(AppUserPrincipal a,UUID id){
        if(!available(a,"VIEW"))return Optional.empty();return receipts.header(a,id,false).map(r->new LoanReceipt(r.header(),"",receipts.rows(a,id),new GroupReceipt(r.group().cycleId(),r.group().code(),r.group().name(),receipts.contributions(a,id),r.group().reversesNumber(),r.group().reversedByNumber())));
    }
    @Transactional public LoanReceipt reverse(AppUserPrincipal a,UUID id,UUID key,LocalDate date,String reason){
        authorize(a,"REVERSE");check(key!=null && date!=null && !date.isAfter(clock.today()),"request");text(reason,500);String payload=hash(List.of(id,date,reason));receipts.requestLock(a,key);var retry=receipts.retry(a,key,payload);if(retry.isPresent())return find(a,retry.get()).orElseThrow();
        var source=receipts.header(a,id,true).orElseThrow(()->new AccessDeniedException("Group receipt unavailable"));var original=source.header();
        check(original.reversesId()==null && original.reversedBy()==null && !a.getMemberId().equals(original.postedBy()) && !date.isBefore(original.effectiveDate()),"correction");
        var originals=receipts.rows(a,id);loans.lockReceiptLoans(a,originals.stream().map(LoanReceiptRow::loanId).sorted().toList(),null);var rows=new ArrayList<LoanReceiptRow>();
        for(var row:originals){var p=loans.reverseReceiptRow(a,id,row.postingId(),GroupContributionAllocator.key(key,"LOAN/"+row.loanId()),date,reason);
            rows.add(new LoanReceiptRow(row.lineNo(),p.id(),p.loanId(),p.loanNumber(),row.description(),p.amount(),p.principal(),p.interest(),p.principalBalance(),p.moneyAccount(),p.principalAccount(),p.interestAccount(),p.reference()));}
        var contributions=receipts.contributions(a,id).stream().map(r->new ReceiptContribution(GroupContributionAllocator.key(key,"CONTRIBUTION/"+r.lineNo()),r.lineNo(),r.loanId(),r.loanNumber(),r.payerId(),r.payerNumber(),r.payer(),r.beneficiaryId(),r.beneficiaryNumber(),r.beneficiary(),r.description(),r.amount(),r.principal(),r.interest(),r.charges(),r.id())).toList();
        var form=new GroupCollectionDtos.Form();form.setRequestKey(key);form.setEffectiveDate(date);form.setMoneyAccountId(original.moneyAccountId());form.setReference(original.reference());form.setRemitter(original.party());form.setEvidence(original.evidence());
        String number=vouchers.reserveCorrectionNumber(a,Type.RECEIPT,date,key).number();var c=new Cycle(source.group().cycleId(),source.group().code(),source.group().name(),"INDIVIDUAL","SUBMITTED");
        var receipt=new LoanReceipt(header(a,form,c,number,original.total(),rows.size(),id,reason),"",List.copyOf(rows),new GroupReceipt(c.id(),c.groupCode(),c.groupName(),contributions));
        receipts.insert(a,receipt,key,payload,json.writeValueAsString(Map.of("original",id,"reason",reason)));vouchers.consumeCorrectionNumber(a,Type.RECEIPT,key);event(a,receipt);return find(a,receipt.header().id()).orElseThrow();
    }
    private Voucher header(AppUserPrincipal a,GroupCollectionDtos.Form f,Cycle c,String number,BigDecimal total,int count,UUID original,String reason){return new Voucher(UUID.randomUUID(),number,Type.RECEIPT,f.getEffectiveDate(),f.getRemitter().strip(),f.getReference().strip(),original==null?c.groupName():reason,f.getEvidence().strip(),f.getMoneyAccountId(),total,count,null,a.getMemberId(),a.getFullName(),clock.now(),original,null,"","",List.of());}
    private Cycle cycleReady(AppUserPrincipal a,UUID id){var c=receipts.cycle(a,id);check("SUBMITTED".equals(c.state()) && "INDIVIDUAL".equals(c.model()),"collectionCycle");return c;}
    private void validate(GroupCollectionDtos.Form f){check(f!=null && f.getRequestKey()!=null && f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(clock.today()) && f.getMoneyAccountId()!=null,"request");text(f.getRemitter(),255);text(f.getReference(),76);text(f.getEvidence(),500);
        check(f.getRows()!=null && !f.getRows().isEmpty() && f.getRows().size()<=50,"request");for(var row:f.getRows()){check(row!=null && row.getLoanId()!=null && row.getPayerId()!=null,"request");var n=row.getAmount();check(n!=null && n.signum()>0 && n.scale()<=2 && n.precision()-n.scale()<=16,"amount");text(row.getDescription(),500);}}
    private LinkedHashMap<UUID,BigDecimal> totals(GroupCollectionDtos.Form f){var sums=new LinkedHashMap<UUID,BigDecimal>();f.getRows().forEach(r->sums.merge(r.getLoanId(),r.getAmount(),BigDecimal::add));return sums;}
    private Object command(UUID cycle,GroupCollectionDtos.Form f){return List.of(cycle,f.getRequestKey(),f.getEffectiveDate(),f.getMoneyAccountId(),f.getRemitter(),f.getReference(),f.getEvidence(),f.getRows());}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String postReference(GroupCollectionDtos.Form f,int line){return f.getReference().strip()+"/G"+line;}
    private void event(AppUserPrincipal a,LoanReceipt receipt){var v=receipt.header();audit.logEvent("GROUP_LENDING",v.id(),v.reversesId()==null?"GROUP_COLLECTION_POSTED":"GROUP_COLLECTION_REVERSED",a.getMemberId(),AuditEventStatus.SUCCESS,"Group receipt","VOUCHER",v.number(),a.getSaccoId(),a.getStationId(),Map.of("cycle",receipt.group().cycleId(),"amount",v.total()));}
    private static void text(String value,int max){check(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),"form");}
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("group.error."+key);}
}
