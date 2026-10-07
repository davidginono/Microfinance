package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Page;
import com.sacco.mvp.accounting.policy.PostingEvent;
import com.sacco.mvp.accounting.repository.AccountingCodeLibraryRepository;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountingCodeLibraryService {
    public static final List<String> COMPONENTS=List.of("TOTAL","PRINCIPAL","INTEREST","FEES","TAX");
    private final AccountingCodeLibraryRepository library;
    private final GeneralLedgerService ledger;
    private final AuditService audit;
    private final ApplicationClock clock;
    @Transactional(readOnly=true)
    public Page<Activity> activities(AppUserPrincipal actor,String search,String state,int page) {
        authorize(actor,"VIEW");validateQuery(search,state,page);return slice(library.activities(actor.getSaccoId(),term(search),state,page*25),page);
    }
    @Transactional(readOnly=true)
    public Activity activity(AppUserPrincipal actor,UUID id) {authorize(actor,"VIEW");return activity(actor,id,false);}
    private Activity activity(AppUserPrincipal actor,UUID id,boolean lock) {
        return library.activity(actor.getSaccoId(),id,lock).orElseThrow(()->new AccessDeniedException("Library record unavailable"));
    }
    @Transactional
    public UUID createActivity(AppUserPrincipal actor,CodeForm form) {
        authorize(actor,"CREATE");validateCode(form);UUID id=library.createActivity(actor.getSaccoId(),form,actor.getMemberId(),clock.now());
        event(actor,id,"ACTIVITY_CREATED");return id;
    }
    @Transactional(readOnly=true)
    public Page<TransactionCode> transactions(AppUserPrincipal actor,UUID activity,String search,String state,int page) {
        authorize(actor,"VIEW");activity(actor,activity,false);validateQuery(search,state,page);
        return slice(library.transactions(actor.getSaccoId(),activity,term(search),state,page*25),page);
    }
    @Transactional(readOnly=true)
    public Page<TransactionCode> transactionRegister(AppUserPrincipal actor,UUID activity,String search,String state,int page) {
        return transactionRegister(actor,activity,search,state,"",page);
    }
    @Transactional(readOnly=true)
    public Page<TransactionCode> transactionRegister(AppUserPrincipal actor,UUID activity,String search,String state,String sourceEvent,int page) {
        authorize(actor,"VIEW");if(activity!=null)activity(actor,activity,false);validateQuery(search,state,page);
        require(sourceEvent!=null && (sourceEvent.isEmpty() || Arrays.stream(PostingEvent.values()).anyMatch(e->e.name().equals(sourceEvent))),"sourceEvent");
        return slice(library.transactionRegister(actor.getSaccoId(),activity,term(search),state,sourceEvent,page*25),page);
    }
    @Transactional
    public UUID onboardTransaction(AppUserPrincipal actor,TransactionForm form) {
        authorize(actor,"CREATE");authorize(actor,"UPDATE");
        require(form!=null && form.getTemplate()!=null,"validation");validateCode(form);validateTemplate(form.getTemplate());
        require(form.getTemplate().getExpectedRequestKey()==null,"staleTemplate");
        form.setActivityCode(clean(form.getActivityCode()).toUpperCase(Locale.ROOT));
        var parent=library.activityByCode(actor.getSaccoId(),form.getActivityCode()).orElseThrow(()->new IllegalArgumentException("library.error.activityCode"));
        form.setActivityId(parent.id());
        UUID id=createTransaction(actor,form);saveTemplate(actor,id,form.getTemplate());return id;
    }
    @Transactional(readOnly=true)
    public TransactionCode transaction(AppUserPrincipal actor,UUID id) {authorize(actor,"VIEW");return transaction(actor,id,false);}
    private TransactionCode transaction(AppUserPrincipal actor,UUID id,boolean lock) {
        return library.transaction(actor.getSaccoId(),id,lock).orElseThrow(()->new AccessDeniedException("Library record unavailable"));
    }
    @Transactional
    public UUID createTransaction(AppUserPrincipal actor,CodeForm form) {
        authorize(actor,"CREATE");validateCode(form);require(activity(actor,form.getActivityId(),true).active(),"inactiveActivity");
        require(Arrays.stream(PostingEvent.values()).anyMatch(e->e.name().equals(form.getSourceEvent())),"sourceEvent");
        UUID id=library.createTransaction(actor.getSaccoId(),form,actor.getMemberId(),clock.now());event(actor,id,"TRANSACTION_CODE_CREATED");return id;
    }
    @Transactional
    public void activityState(AppUserPrincipal actor,UUID id,boolean active) {
        authorize(actor,"UPDATE");activity(actor,id,true);
        require(active || !library.activeTransactions(actor.getSaccoId(),id),"activeTransactions");
        library.activityState(actor.getSaccoId(),id,active);event(actor,id,active?"ACTIVITY_REACTIVATED":"ACTIVITY_DEACTIVATED");
    }
    @Transactional
    public void transactionState(AppUserPrincipal actor,UUID id,boolean active) {
        authorize(actor,"UPDATE");var code=transaction(actor,id,false);var parent=activity(actor,code.activityId(),true);transaction(actor,id,true);
        require(!active || parent.active(),"inactiveActivity");library.transactionState(actor.getSaccoId(),id,active);
        event(actor,id,active?"TRANSACTION_CODE_REACTIVATED":"TRANSACTION_CODE_DEACTIVATED");
    }
    @Transactional(readOnly=true)
    public Page<AccountChoice> accounts(AppUserPrincipal actor,String search,int page) {
        authorize(actor,"VIEW");validateQuery(search,"",page);return slice(library.accounts(actor.getSaccoId(),term(search),page*25),page);
    }
    @Transactional(readOnly=true)
    public Template template(AppUserPrincipal actor,UUID transaction) {
        authorize(actor,"VIEW");transaction(actor,transaction,false);
        return library.template(actor.getSaccoId(),transaction)
            .map(t->new Template(t.transactionId(),t.requestKey(),library.rules(actor.getSaccoId(),transaction))).orElse(null);
    }
    @Transactional
    public UUID saveTemplate(AppUserPrincipal actor,UUID id,TemplateForm form) {
        authorize(actor,"UPDATE");validateTemplate(form);
        var initial=transaction(actor,id,false);var parent=activity(actor,initial.activityId(),true);var code=transaction(actor,id,true);
        String hash=hash(form);var prior=library.request(actor.getSaccoId(),id,form.getRequestKey());
        if(prior.isPresent()) {require(prior.get().actor().equals(actor.getMemberId()) && prior.get().hash().equals(hash),"changedRetry");return id;}
        require(parent.active() && code.active(),"inactiveTransaction");var current=library.template(actor.getSaccoId(),id);
        require(Objects.equals(form.getExpectedRequestKey(),current.map(Template::requestKey).orElse(null)),"staleTemplate");
        var accountCodes=form.getRules().stream().flatMap(r->java.util.stream.Stream.of(r.getDebitCode(),r.getCreditCode())).distinct().sorted().toList();
        var accounts=library.lockAccounts(actor.getSaccoId(),accountCodes);
        require(accounts.size()==accountCodes.size() && accounts.stream().allMatch(a->a.active() && !"HEADING".equals(a.kind())),"templateAccount");
        require(!"MANUAL_JOURNAL".equals(code.sourceEvent()) || accounts.stream().noneMatch(a->"CONTROL".equals(a.kind())),"manualControl");
        library.saveTemplate(actor.getSaccoId(),code,form,hash,accounts,actor.getMemberId());event(actor,id,"TEMPLATE_SAVED");return id;
    }
    private void authorize(AppUserPrincipal actor,String action) {ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_"+action);}
    private void event(AppUserPrincipal actor,UUID id,String action) {
        audit.logEvent("ACCOUNTING",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"ACCOUNTING_LIBRARY",id.toString(),actor.getSaccoId(),actor.getStationId(),Map.of());
    }
    private static <T> Page<T> slice(List<T> rows,int page) {return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private static String term(String value) {return value.strip().toLowerCase(Locale.ROOT);}
    private static void validateQuery(String search,String state,int page) {
        require(search!=null && search.length()<=100 && state!=null && Set.of("","ACTIVE","INACTIVE").contains(state) && page>=0 && page<=10000,"query");
    }
    private static void validateCode(CodeForm form) {
        require(form!=null,"validation");form.setCode(clean(form.getCode()).toUpperCase(Locale.ROOT));form.setName(clean(form.getName()));form.setNameSw(clean(form.getNameSw()));form.setDescription(clean(form.getDescription()));
        require(form.getCode().matches("[A-Z0-9][A-Z0-9_.-]{0,39}"),"code");text(form.getName(),160,true,"name");text(form.getNameSw(),160,false,"nameSw");text(form.getDescription(),500,false,"description");
    }
    static void validateTemplate(TemplateForm form) {
        require(form!=null && form.getRequestKey()!=null,"validation");
        require(form.getRules()!=null && !form.getRules().isEmpty() && form.getRules().size()<=5,"rules");var components=new HashSet<String>();
        for(var rule:form.getRules()) {
            require(rule!=null && rule.getComponent()!=null && COMPONENTS.contains(rule.getComponent()) && components.add(rule.getComponent()),"components");
            rule.setDebitCode(clean(rule.getDebitCode()).toUpperCase(Locale.ROOT));rule.setCreditCode(clean(rule.getCreditCode()).toUpperCase(Locale.ROOT));
            require(rule.getDebitCode().matches("[A-Z0-9][A-Z0-9_.-]{0,39}") && rule.getCreditCode().matches("[A-Z0-9][A-Z0-9_.-]{0,39}") && !rule.getDebitCode().equals(rule.getCreditCode()),"templateAccount");
        }
        require(!components.contains("TOTAL") || components.size()==1,"totalOverlap");
    }
    private static String clean(String value) {return value==null?"":value.strip();}
    private static void text(String value,int max,boolean required,String key) {require((!required || !value.isBlank()) && value.length()<=max && value.chars().noneMatch(c->c<32),key);}
    private static String hash(TemplateForm form) {
        try {
            var canonical=new StringBuilder(String.valueOf(form.getExpectedRequestKey()));
            form.getRules().stream().sorted(Comparator.comparing(RuleForm::getComponent)).forEach(r->canonical.append('|').append(r.getComponent()).append(':').append(r.getDebitCode()).append(':').append(r.getCreditCode()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException failure) {throw new IllegalStateException(failure);}
    }
    private static void require(boolean condition,String key) {if(!condition)throw new IllegalArgumentException("library.error."+key);}
}
