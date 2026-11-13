package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.dto.OfficeWork;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service @RequiredArgsConstructor
public class AccountOfficeWorkService {
    private final GeneralLedgerRepository records;
    private final GeneralLedgerService access;
    @Transactional(readOnly=true)
    public OfficeWork.Page read(AppUserPrincipal actor,boolean review,String search,String sort,int page) {
        access.requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");
        if(review) {
            access.requireActor(actor,"ACCOUNTING_ACCOUNT_APPROVAL_VIEW");
            access.requireActor(actor,actor.getClaims().contains("ACCOUNTING_ACCOUNT_APPROVAL_APPROVE")
                ?"ACCOUNTING_ACCOUNT_APPROVAL_APPROVE":"ACCOUNTING_ACCOUNT_APPROVAL_REJECT");
        }
        if(search==null||search.length()>100||page<0||page>10000||!Set.of("newest","oldest","number","number_desc").contains(sort))
            throw new IllegalArgumentException("accounting.error.versionInput");
        var rows=records.officeVersions(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),review,search,sort,page);
        return new OfficeWork.Page(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
}
