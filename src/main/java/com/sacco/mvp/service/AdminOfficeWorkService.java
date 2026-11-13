package com.sacco.mvp.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.repository.AdminOfficeWorkRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.dto.OfficeWork;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service @RequiredArgsConstructor
public class AdminOfficeWorkService {
    private final AdminOfficeWorkRepository records;
    private final GeneralLedgerService access;
    @Transactional(readOnly=true)
    public OfficeWork.Page read(AppUserPrincipal actor,boolean staff,String search,String sort,int page) {
        access.requireActor(actor,staff?"USER_ACCESS_VIEW":"SUPPORT_VIEW");
        if(search==null||search.length()>100||page<0||page>10000||!Set.of("newest","oldest","number","number_desc").contains(sort))
            throw new IllegalArgumentException("office.invalidFilter");
        var rows=staff?records.staff(actor,search,sort,page):records.support(actor,search,sort,page);
        return new OfficeWork.Page(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
}
