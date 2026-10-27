package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.*;
import com.sacco.mvp.grouplending.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessException;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupDisclosurePostgresTest {
    final GroupCyclePostgresTest g=new GroupCyclePostgresTest();
    @BeforeAll void start(){g.start(GroupSharedAgreementPostgresTest.Config.class);}
    @AfterAll void stop(){g.stop();}
    @BeforeEach void setup(){g.setup();g.f.context.getBean(com.sacco.mvp.service.SaccoRegistryService.class).findActiveSacco(g.f.institution).orElseThrow().setSaccoName(g.f.jdbc.queryForObject("select sacco_name from registered_saccos where sacco_id=?",String.class,g.f.institution));}
    static Rules rules(){var r=new Rules();r.setAnnualCostRequired(true);r.setDatedFeesRequired(true);r.setLanguageChoiceRequired(true);r.setMethodologyReference("Synthetic institution-approved method v1");r.setMaximumAnnualCostPercent(new BigDecimal("40.00"));r.setAgreementLanguage("BOTH");r.setApproved(true);return r;}
    static Declaration declaration(){var d=new Declaration();d.setAnnualCostPercent(new BigDecimal("26.1234"));d.setCostEvidence("Synthetic independently checked annual-cost calculation");d.setDatedFeesEvidence("Synthetic full fee amounts, dates and timing checked against the agreement");d.setCostVerified(true);d.setDatedFeesVerified(true);return d;}
    CycleForm configured(Model model){var p=g.policy();p.setModel(model);p.setReleaseRules(rules());if(model==Model.SHARED){p.setSharedLiability(SharedLiability.PROPORTIONAL);g.f.jdbc.update("update loan_product_settings set guarantors_required=0 where id=?",g.f.product);}g.policy=g.cycles.configure(g.f.actor,p);return g.form();}
    void declare(CycleForm f,Model model){if(model==Model.INDIVIDUAL)f.getParticipants().forEach(x->x.setDisclosure(declaration()));else f.setDisclosure(declaration());}
    @ParameterizedTest @EnumSource(value=Model.class,names={"INDIVIDUAL","SHARED"})
    void missingEvidenceCannotCreatePartialLoansAndEachContractRetainsItsPolicy(Model model){
        var f=configured(model);var id=g.cycles.create(g.f.actor,g.group,f);
        assertThatThrownBy(()->g.cycles.submit(g.f.actor,id,f)).hasMessage("group.error.disclosureEvidence");
        assertThat(g.f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,g.f.institution)).isZero();
        declare(f,model);g.cycles.submit(g.f.actor,id,f);g.cycles.submit(g.f.actor,id,f);
        var packet=g.consents.packet(g.client(g.f.client),id);var snap=packet.terms().groupDisclosure();assertThat(snap.policyId()).isEqualTo(g.policy);assertThat(snap.annualCostPercent()).isEqualByComparingTo("26.1234");
        assertThat(g.f.jdbc.queryForObject("select lending_group_disclosure_valid(?,?::jsonb)",Boolean.class,g.policy,g.f.context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(snap))).isTrue();
        assertThat(g.f.jdbc.queryForObject("select lending_group_disclosure_valid(?,null)",Boolean.class,g.policy)).isFalse();
        var facility=g.cycles.participants(g.f.actor,id).getFirst().loanId();
        assertThatThrownBy(()->g.f.jdbc.update("update loan_applications set financial_snapshot=financial_snapshot-'groupDisclosure' where id=?",facility)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->g.f.jdbc.update("update loan_applications set financial_snapshot=jsonb_set(financial_snapshot,'{groupDisclosure,annualCostPercent}','1'::jsonb) where id=?",facility)).isInstanceOf(DataAccessException.class);
        var revision=g.policy();revision.setModel(model);if(model==Model.SHARED)revision.setSharedLiability(SharedLiability.PROPORTIONAL);g.cycles.configure(g.f.actor,revision);
        assertThat(g.consents.packet(g.client(g.f.client),id).terms().groupDisclosure()).isEqualTo(snap);
        assertThatThrownBy(()->g.f.jdbc.update("update lending_group_policy set release_rules=null where id=?",g.policy)).isInstanceOf(DataAccessException.class);
        assertThat(g.f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,g.f.institution)).isZero();
    }
    @Test void annualCostCeilingAndVerificationCannotBeBypassed(){var f=configured(Model.INDIVIDUAL);declare(f,Model.INDIVIDUAL);var id=g.cycles.create(g.f.actor,g.group,f);var d=f.getParticipants().getFirst().getDisclosure();d.setAnnualCostPercent(new BigDecimal("40.000001"));assertThatThrownBy(()->g.cycles.submit(g.f.actor,id,f)).hasMessage("group.error.disclosureLimit");d.setAnnualCostPercent(new BigDecimal("30"));d.setCostVerified(false);assertThatThrownBy(()->g.cycles.submit(g.f.actor,id,f)).hasMessage("group.error.disclosureCost");}
    @Test void personalLanguageIsRequiredAndCannotBeForgedThroughDirectConsent(){var f=configured(Model.INDIVIDUAL);declare(f,Model.INDIVIDUAL);var id=g.cycles.create(g.f.actor,g.group,f);g.cycles.submit(g.f.actor,id,f);var a=g.client(g.f.client);
        assertThat(g.consents.accept(a,id,true,"")).isEqualTo("group.error.disclosureLanguage");
        var repo=g.f.context.getBean(com.sacco.mvp.grouplending.repository.GroupCycleRepository.class);var tx=new org.springframework.transaction.support.TransactionTemplate(g.f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(()->tx.executeWithoutResult(t->repo.consent(a,id,g.policy,"Synthetic personal signature",null,g.f.context.getBean(com.sacco.mvp.service.ApplicationClock.class).now()))).isInstanceOf(DataAccessException.class);
        assertThat(g.consents.accept(a,id,true,"","SW")).isNull();assertThat(g.f.jdbc.queryForObject("select preferred_language from lending_group_consent_evidence where cycle_id=? and client_id=?",String.class,id,g.f.client)).isEqualTo("SW");
    }
    @Test void sharedFinalAcceptanceChecksTheActualDocumentLanguage(){var f=configured(Model.SHARED);declare(f,Model.SHARED);var id=g.cycles.create(g.f.actor,g.group,f);g.cycles.submit(g.f.actor,id,f);var a=g.client(g.f.client);assertThat(g.consents.accept(a,id,true,"","SW")).isNull();
        g.f.jdbc.update("insert into member_payment_details(member_id,destination_type,provider,account_holder_name,account_identifier,created_at,updated_at) values(?,'BANK_ACCOUNT','Synthetic bank','Synthetic holder','QA-ACCOUNT',now(),now())",g.f.client);
        var svc=g.f.context.getBean(GroupSharedAgreementService.class);var document=new GroupOrganizationPostgresTest().documentForm();svc.record(g.f.actor,id,document,GroupOrganizationPostgresTest.documentFile());svc.exportClient(a,id,Locale.ENGLISH);var hash=svc.packet(a,id).row().hash();
        assertThat(svc.confirm(a,id,hash,true,"","SW")).isEqualTo("group.error.disclosureLanguage");assertThat(svc.confirm(a,id,hash,true,"","EN")).isNull();
        assertThat(g.f.jdbc.queryForObject("select preferred_language from lending_group_shared_payout where cycle_id=?",String.class,id)).isEqualTo("EN");
    }
}
