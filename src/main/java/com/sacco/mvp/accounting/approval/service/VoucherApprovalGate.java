package com.sacco.mvp.accounting.approval.service;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Invalid;
import com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Posting policy is enforced in the financial service, including callers outside MVC. */
@Service @RequiredArgsConstructor
public class VoucherApprovalGate {
    private final VoucherWorkflowRepository repository;
    private final GeneralLedgerService access;
    public void authorize(AppUserPrincipal actor,String claim) { access.requireActor(actor,claim); }
    @Transactional(readOnly=true)
    public boolean required(AppUserPrincipal a,Type type) {
        access.requireActor(a,type==Type.JOURNAL?"ACCOUNTING_JOURNALS_CREATE":"ACCOUNTING_BUSINESS_CREATE");
        return repository.configuration(a,type).individuals();
    }
    public Proof beforePosting(AppUserPrincipal a,Type type,Form form) {
        repository.policyLock(a,false);
        var parent=repository.workflowForItem(a,form.getRequestKey());
        if(parent.isEmpty()) {
            if(repository.configuration(a,type).individuals())throw new Invalid("required");
            return null;
        }
        authorize(a,"VOUCHER_WORKSPACE_VIEW");authorize(a,"VOUCHER_WORKSPACE_POST");
        var w=repository.find(a,parent.get(),true).orElseThrow(()->new Invalid("unavailable"));
        var item=repository.item(w.id(),form.getRequestKey()).orElseThrow();
        require(w.type()==type && w.approval().approved() && "VALIDATED".equals(w.validation())
            && "POSTING".equals(w.posting()),"required");
        require(item.planHash()!=null && formHash(type,canonical(form,form.getRequestKey())).equals(formHash(type,item.form())),"changed");
        return new Proof(item.planHash());
    }
    public void verifyPlan(Proof proof, Preview plan) {
        if(proof!=null)require(proof.planHash().equals(planHash(plan)),"changed");
    }
    public String formHash(Type type,Form form) { return digest(type.name()+repository.write(form)); }
    public String planHash(Preview plan) {
        if(plan.rows().stream().anyMatch(r->r.split())){
            var rows=plan.rows().stream().map(r->List.of(r.lineNo(),r.transactionId()==null?"":r.transactionId().toString(),r.templateKey()==null?"":r.templateKey().toString(),r.component(),r.description(),r.amount().setScale(2).toPlainString(),
                r.postings().stream().map(e->List.of(e.side(),e.account().id(),e.account().code(),e.account().name(),e.amount().setScale(2).toPlainString())).toList())).toList();
            return digest("SPLIT_V1/"+repository.write(rows));
        }
        var rows=plan.rows().stream().map(r->List.of(r.lineNo(),r.transactionId()==null?"":r.transactionId().toString(),
            r.templateKey()==null?"":r.templateKey().toString(),r.component(),r.description(),r.amount().setScale(2).toPlainString(),
            r.debit().id(),r.debit().code(),r.debit().name(),r.credit().id(),r.credit().code(),r.credit().name())).toList();
        return digest(repository.write(rows));
    }
    public Form canonical(Form source,UUID key) {
        require(source!=null && key!=null && "OTHER".equals(source.getReceiptPurpose()) && source.getInterestAccountId()==null,"ordinaryOnly");
        require(source.getRows()!=null && !source.getRows().isEmpty() && source.getRows().size()<=50,"limit");
        var f=new Form(); f.setRequestKey(key);f.setEffectiveDate(source.getEffectiveDate());f.setMoneyAccountId(source.getMoneyAccountId());
        f.setClientId(source.getClientId());f.setActivityId(source.getActivityId());
        f.setEquity(source.isEquity());f.setEquityMovement(source.getEquityMovement());f.setEquityRow(source.getEquityRow());f.setEquityReason(text(source.getEquityReason(),500));
        f.setParty(text(source.getParty(),160));f.setReference(text(source.getReference(),160));
        f.setDescription(text(source.getDescription(),500));f.setEvidence(text(source.getEvidence(),500));
        var rows=new ArrayList<RowForm>();
        for(var r:source.getRows()) {
            require(r!=null && r.getLoanId()==null,"ordinaryOnly");
            require(r.getAmount()==null || (r.getAmount().scale()>=-16 && r.getAmount().scale()<=2 && r.getAmount().precision()-r.getAmount().scale()<=16),"amount");
            var copy=new RowForm();copy.setDescription(text(r.getDescription(),500));copy.setAmount(r.getAmount()==null?null:r.getAmount().setScale(2));
            copy.setDebitAccountId(r.getDebitAccountId());copy.setCreditAccountId(r.getCreditAccountId());copy.setTransactionId(r.getTransactionId());copy.setTemplateKey(r.getTemplateKey());
            copy.setComponent(text(r.getComponent(),20));
            if(r.getEntries()!=null && !r.getEntries().isEmpty()){
                require(r.getEntries().size()<=50,"limit");var entries=new ArrayList<com.sacco.mvp.accounting.dto.VoucherDtos.EntryForm>();
                for(var e:r.getEntries()){require(e!=null,"ordinaryOnly");var entry=new com.sacco.mvp.accounting.dto.VoucherDtos.EntryForm();entry.setSide(text(e.getSide(),6));entry.setAccountId(e.getAccountId());
                    require(e.getAmount()==null || (e.getAmount().scale()>=-16 && e.getAmount().scale()<=2 && e.getAmount().precision()-e.getAmount().scale()<=16),"amount");entry.setAmount(e.getAmount()==null?null:e.getAmount().setScale(2));entries.add(entry);}
                copy.setEntries(entries);
            }
            rows.add(copy);
        }
        require(rows.stream().mapToInt(r->r.getEntries().isEmpty()?2:r.getEntries().size()).sum()<=500,"limit");
        f.setRows(rows);return f;
    }
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String text(String v,int max) {String s=v==null?"":v.strip();require(s.length()<=max && s.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),"input");return s;}
    public static void require(boolean condition,String key) {if(!condition)throw new Invalid(key);}
}
