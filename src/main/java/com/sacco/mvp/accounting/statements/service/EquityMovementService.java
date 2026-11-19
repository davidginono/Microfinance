package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class EquityMovementService {
    private final StatementSetupRepository repository;
    private final GeneralLedgerService access;
    private final AuditService audit;
    @Transactional(readOnly=true) public List<Journal> journals(AppUserPrincipal a,Parameters p,int page){authorize(a);require(p!=null&&p.from()!=null&&p.through()!=null&&!p.through().isBefore(p.from())&&!p.through().isAfter(p.from().plusDays(366))&&page>=0&&page<=10000,"dates");return repository.journals(a,p,page);}
    @Transactional(timeout=15) public void classify(AppUserPrincipal a,UUID id,int version,Category category,String reason){
        authorize(a);require(id!=null&&category!=null&&version>=0,"setup");require(reason!=null&&reason.length()<=500&&!reason.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'),"setup");reason=reason.strip();require(!reason.isBlank(),"reason");repository.lock(a.getSaccoId()+"/"+a.getStationId()+"/"+id);
        var lines=repository.journal(a,id);if(lines.isEmpty())throw new AccessDeniedException("Journal unavailable");require(lines.size()<=2000,"limit");
        var choices=repository.accounts(a.getSaccoId());require(choices.size()<=2000,"limit");var types=new HashMap<UUID,String>();choices.forEach(c->types.put(c.id(),c.type()));
        var equity=lines.stream().filter(l->"EQUITY".equals(types.get(l.accountId()))).toList();require(!equity.isEmpty(),"movement");
        if(Set.of(Category.CONTRIBUTION,Category.DISTRIBUTION,Category.OCI).contains(category))require(lines.stream().noneMatch(l->Set.of("INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
        if(category==Category.TRANSFER)require(lines.size()==equity.size()&&equity.stream().map(Movement::signed).reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0,"movement");
        if(category==Category.EARNINGS_TRANSFER)require(lines.size()>equity.size()&&lines.stream().allMatch(l->Set.of("EQUITY","INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
        int current=repository.classificationVersion(a,id);if(current==version+1&&repository.sameClassification(a,id,category,reason))return;require(current==version,"setupStale");repository.classify(a,id,current+1,category,reason);
        audit.log("STATEMENT_SETUP",id,"CLASSIFY_EQUITY",a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"reason",reason));
    }
    private void authorize(AppUserPrincipal a){access.requireActor(a,"FINANCIAL_REPORTS_VIEW");access.requireActor(a,"FINANCIAL_REPORTS_CONFIGURE");}
    private static void require(boolean valid,String key){if(!valid)throw new IllegalArgumentException("fs.error."+key);}
}
