package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanProductAccountService {
    private final LoanProductAccountRepository repository;
    private final ApplicationClock clock;
    private final AuditService audit;
    public record Accounts(UUID principalId,UUID interestId) {}
    @Transactional(readOnly=true)
    public List<Choice> choices(String institution,String category,String search,int page,UUID selected){
        require(Set.of("PRINCIPAL","INTEREST").contains(category) && search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");
        return repository.choices(institution,search,page,selected);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void configure(LoanProductSetting product,UUID actor,UUID principal,UUID interest){
        require((principal==null)==(interest==null),"productAccountsPair");
        if(principal!=null)validate(product.getSaccoId(),new Accounts(principal,interest));
        if(Objects.equals(product.getPrincipalGlAccountId(),principal) && Objects.equals(product.getInterestGlAccountId(),interest))return;
        var evidence=new LinkedHashMap<String,Object>();evidence.put("oldPrincipal",product.getPrincipalGlAccountId());evidence.put("oldInterest",product.getInterestGlAccountId());evidence.put("principal",principal);evidence.put("interest",interest);
        product.setPrincipalGlAccountId(principal);product.setInterestGlAccountId(interest);
        audit.logEvent("ACCOUNTING",product.getId(),"LOAN_PRODUCT_ACCOUNTS_CONFIGURED",actor,AuditEventStatus.SUCCESS,"Loan product accounts","LOAN_PRODUCT",product.getDisplayCode(),product.getSaccoId(),null,evidence);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Accounts configured(String institution,UUID product){
        var row=repository.product(institution,product).orElseThrow(()->new IllegalArgumentException("recording.error.product"));
        var accounts=new Accounts((UUID)row.get("principal_gl_account_id"),(UUID)row.get("interest_gl_account_id"));
        require(accounts.principalId()!=null && accounts.interestId()!=null,"productAccountsMissing");validate(institution,accounts);return accounts;
    }
    @Transactional(readOnly=true)
    public Optional<Accounts> find(AppUserPrincipal a,UUID loan){return repository.binding(a,loan).map(row->new Accounts((UUID)row.get("principal_account_id"),(UUID)row.get("interest_account_id")));}
    @Transactional(propagation=Propagation.MANDATORY)
    public Accounts required(AppUserPrincipal a,UUID loan){var accounts=find(a,loan).orElseThrow(()->new IllegalArgumentException("recording.error.loanAccountsMissing"));return accounts;}
    @Transactional(propagation=Propagation.MANDATORY)
    public Accounts bind(AppUserPrincipal a,UUID loan,UUID product,UUID originalPrincipal,String source){
        var existing=find(a,loan);if(existing.isPresent())return existing.get();
        var accounts=configured(a.getSaccoId(),product);require(originalPrincipal==null || originalPrincipal.equals(accounts.principalId()),"productPrincipalMismatch");
        repository.bind(a,loan,product,accounts.principalId(),accounts.interestId(),source,clock.now());
        audit.logEvent("ACCOUNTING",loan,"LOAN_PRODUCT_ACCOUNTS_LINKED",a.getMemberId(),AuditEventStatus.SUCCESS,"Loan accounts","LOAN",loan.toString(),a.getSaccoId(),a.getStationId(),Map.of("product",product,"principal",accounts.principalId(),"interest",accounts.interestId(),"source",source));return accounts;
    }
    private void validate(String institution,Accounts accounts){require(repository.valid(institution,accounts.principalId()) && repository.valid(institution,accounts.interestId()),"productAccountsInvalid");}
    private static void require(boolean ok,String key){if(!ok)throw new IllegalArgumentException("recording.error."+key);}
}
