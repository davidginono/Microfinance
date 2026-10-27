package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.Snapshot;
import org.springframework.context.MessageSource;
import java.io.IOException;
import java.util.Locale;

final class GroupDisclosurePdf {
    private GroupDisclosurePdf() {}
    static void render(AccountingPdfDocument page,Snapshot s,MessageSource messages,Locale locale)throws IOException {
        if(s==null)return;
        page.section(messages.getMessage("groupDisclosure.title",null,locale));
        line(page,messages,locale,"authority.revision",Long.toString(s.revision()));
        line(page,messages,locale,"groupDisclosure.methodology",s.rules().getMethodologyReference());
        if(s.annualCostPercent()!=null){line(page,messages,locale,"groupDisclosure.annualCost",s.annualCostPercent().stripTrailingZeros().toPlainString()+"%");line(page,messages,locale,"groupDisclosure.costEvidence",s.costEvidence());}
        if(!s.datedFeesEvidence().isBlank())line(page,messages,locale,"groupDisclosure.feeEvidence",s.datedFeesEvidence());
        line(page,messages,locale,"groupDisclosure.allowedLanguages",messages.getMessage("orgDocument.language."+s.rules().getAgreementLanguage(),null,locale));
    }
    private static void line(AccountingPdfDocument p,MessageSource m,Locale l,String key,String value)throws IOException{p.text(m.getMessage(key,null,l)+": "+value);}
}
