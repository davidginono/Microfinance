package com.sacco.mvp.grouplending;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.*;
import com.sacco.mvp.grouplending.service.GroupDisclosure;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class GroupDisclosureTest {
    Rules rules(){var r=new Rules();r.setAnnualCostRequired(true);r.setLanguageChoiceRequired(true);r.setApproved(true);r.setMethodologyReference("Approved method v2");return r;}
    Policy policy(Rules r){return new Policy(UUID.randomUUID(),UUID.randomUUID(),"Product",Model.SHARED,2,5,new BigDecimal("10000"),new BigDecimal("10000"),1,true,true,"Policy","Agreement",OffsetDateTime.now(),2,r);}
    Declaration declaration(){var d=new Declaration();d.setAnnualCostPercent(new BigDecimal("27.123456"));d.setCostEvidence("Checked original calculation");d.setCostVerified(true);return d;}
    @Test void onlyExplicitlyApprovedMethodsCanCreateRequirements(){var r=rules();r.setApproved(false);assertThatThrownBy(()->GroupDisclosure.rules(r)).hasMessage("group.error.disclosureApproval");r.setApproved(true);r.setMethodologyReference(" ");assertThatThrownBy(()->GroupDisclosure.rules(r)).hasMessage("group.error.disclosureEvidence");}
    @Test void languageOnlyPoliciesStillRequireAnApprovedReference(){var r=rules();r.setAnnualCostRequired(false);assertThat(GroupDisclosure.rules(r)).isSameAs(r);assertThat(GroupDisclosure.snapshot(policy(r),null).annualCostPercent()).isNull();}
    @Test void annualCostMustUseExactBoundedPercentAndEvidence(){var p=policy(rules());for(var invalid:new BigDecimal[]{new BigDecimal("-1"),new BigDecimal("0.1234567"),new BigDecimal("100001")}){var d=declaration();d.setAnnualCostPercent(invalid);assertThatThrownBy(()->GroupDisclosure.snapshot(p,d)).hasMessage("group.error.disclosureCost");}var d=declaration();d.setCostEvidence("bad\ntext");assertThatThrownBy(()->GroupDisclosure.snapshot(p,d)).hasMessage("group.error.disclosureEvidence");}
    @Test void nullLegacyRulesCannotSilentlyRecordUnapprovedCostDeclarations(){assertThat(GroupDisclosure.snapshot(policy(null),null)).isNull();assertThat(GroupDisclosure.rules(new Rules())).isNull();assertThatThrownBy(()->GroupDisclosure.snapshot(policy(null),declaration())).hasMessage("group.error.disclosureRules");}
    @Test void datedFeeVerificationAndActualLanguageAreIndependent(){var r=rules();r.setDatedFeesRequired(true);var d=declaration();assertThatThrownBy(()->GroupDisclosure.snapshot(policy(r),d)).hasMessage("group.error.disclosureEvidence");d.setDatedFeesEvidence("Dates and amounts checked");assertThatThrownBy(()->GroupDisclosure.snapshot(policy(r),d)).hasMessage("group.error.disclosureFees");d.setDatedFeesVerified(true);var s=GroupDisclosure.snapshot(policy(r),d);assertThatThrownBy(()->GroupDisclosure.language(s,"SW","EN")).hasMessage("group.error.disclosureLanguage");assertThat(GroupDisclosure.language(s,"SW","BOTH")).isEqualTo("SW");assertThatThrownBy(()->GroupDisclosure.language(s,"en","BOTH")).hasMessage("group.error.disclosureLanguage");}
    @Test void aCeilingRequiresAnAnnualCostRequirement(){var r=rules();r.setAnnualCostRequired(false);r.setMaximumAnnualCostPercent(BigDecimal.TEN);assertThatThrownBy(()->GroupDisclosure.rules(r)).hasMessage("group.error.disclosureRules");}
}
