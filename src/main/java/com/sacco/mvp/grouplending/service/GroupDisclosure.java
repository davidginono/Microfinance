package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.Policy;
import com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.*;
import java.math.BigDecimal;
import java.util.Set;

/** Pure validation shared by the three origination paths; no independent pricing engine. */
public final class GroupDisclosure {
    private GroupDisclosure() {}
    public static Rules rules(Rules r) {
        if(r==null)return null;
        boolean active=r.isAnnualCostRequired()||r.isDatedFeesRequired()||r.isLanguageChoiceRequired();
        if(!active){check(r.getMaximumAnnualCostPercent()==null,"disclosureRules");return null;}
        check(r.isApproved(),"disclosureApproval");text(r.getMethodologyReference(),true);
        check(Set.of("EN","SW","BOTH").contains(r.getAgreementLanguage()),"disclosureRules");
        if(r.getMaximumAnnualCostPercent()!=null){check(r.isAnnualCostRequired(),"disclosureRules");percent(r.getMaximumAnnualCostPercent());}
        r.setMethodologyReference(r.getMethodologyReference().strip());return r;
    }
    public static Snapshot snapshot(Policy p,Declaration d) {
        var r=p.releaseRules();if(r==null){check(d==null||empty(d),"disclosureRules");return null;}
        if(d==null)d=new Declaration();
        text(d.getCostEvidence(),r.isAnnualCostRequired());text(d.getDatedFeesEvidence(),r.isDatedFeesRequired());
        if(r.isAnnualCostRequired())check(d.getAnnualCostPercent()!=null&&d.isCostVerified(),"disclosureCost");
        if(d.getAnnualCostPercent()!=null){percent(d.getAnnualCostPercent());check(d.isCostVerified()&&!d.getCostEvidence().isBlank(),"disclosureCost");}
        if(r.getMaximumAnnualCostPercent()!=null)check(d.getAnnualCostPercent().compareTo(r.getMaximumAnnualCostPercent())<=0,"disclosureLimit");
        if(r.isDatedFeesRequired())check(d.isDatedFeesVerified(),"disclosureFees");
        return new Snapshot(p.id(),p.revision(),r,d.getAnnualCostPercent(),d.getCostEvidence().strip(),d.getDatedFeesEvidence().strip(),d.isCostVerified(),d.isDatedFeesVerified());
    }
    public static String language(Snapshot s,String selected,String documentLanguage) {
        if(s==null||!s.rules().isLanguageChoiceRequired())return selected==null||selected.isBlank()?null:validLanguage(selected);
        if(selected==null||!Set.of("EN","SW").contains(selected))throw new IllegalArgumentException("group.error.disclosureLanguage");
        String configured=s.rules().getAgreementLanguage();
        check("BOTH".equals(configured)||configured.equals(selected),"disclosureLanguage");
        if(documentLanguage!=null)check("BOTH".equals(documentLanguage)||documentLanguage.equals(selected),"disclosureLanguage");
        return selected;
    }
    private static String validLanguage(String selected){check(Set.of("EN","SW").contains(selected),"disclosureLanguage");return selected;}
    private static boolean empty(Declaration d){return d.getAnnualCostPercent()==null&&!d.isCostVerified()&&!d.isDatedFeesVerified()&&(d.getCostEvidence()==null||d.getCostEvidence().isBlank())&&(d.getDatedFeesEvidence()==null||d.getDatedFeesEvidence().isBlank());}
    private static void percent(BigDecimal p){check(p.signum()>=0&&p.compareTo(new BigDecimal("100000"))<=0&&p.scale()<=6,"disclosureCost");}
    private static void text(String s,boolean required){check(s!=null&&s.length()<=500&&s.chars().noneMatch(Character::isISOControl)&&(!required||!s.isBlank()),"disclosureEvidence");}
    private static void check(boolean b,String key){if(!b)throw new IllegalArgumentException("group.error."+key);}
}
