package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.GroupForm;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    GroupOrganizationService service;GroupAuthorityService authorities;GroupCycleService cycles;GroupLendingService groups;UUID group,authority,policy;
    @BeforeAll void start(){f.context=new AnnotationConfigApplicationContext(Config.class);f.jdbc=f.context.getBean(JdbcTemplate.class);f.service=f.context.getBean(LoanRecordingService.class);service=f.context.getBean(GroupOrganizationService.class);authorities=f.context.getBean(GroupAuthorityService.class);cycles=f.context.getBean(GroupCycleService.class);groups=f.context.getBean(GroupLendingService.class);}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){
        f.fixture();f.context.getBean(SaccoRegistryService.class).findActiveSacco(f.institution).orElseThrow().setSaccoName(f.jdbc.queryForObject("select sacco_name from registered_saccos where sacco_id=?",String.class,f.institution));var clock=f.context.getBean(ApplicationClock.class);reset(clock);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-08T12:00:00+03:00"));
        f.jdbc.update("update loan_product_settings set interest_method='REDUCING_BALANCE',max_repayment_to_disposable_income_ratio=0.4 where id=?",f.product);
        var g=new GroupForm();g.setName("Synthetic organization <Company>");g.setLegalForm("ORGANIZATION");g.setEvidence("Synthetic legal identity");group=groups.create(f.actor,g);authority=authorities.record(f.actor,group,authorityForm());policy=cycles.configure(f.actor,policyForm());
    }
    AuthorityForm authorityForm(){var h=authorities.editor(f.actor,group,null).form();h.setValidFrom(LocalDate.of(2026,10,1));h.setValidThrough(LocalDate.of(2027,10,1));h.setMaxPrincipal(new BigDecimal("100000.00"));h.setRequiredSignatures(1);h.setCapacityEvidence("Synthetic legal capacity");h.setResolutionEvidence("Synthetic borrowing resolution");h.setSignatureEvidence("Synthetic original signatures checked");h.setPayoutType(PaymentDestinationType.BANK_ACCOUNT);h.setPayoutProvider("Synthetic bank");h.setPayoutHolder("Synthetic organization");h.setPayoutIdentifier("QA-ACCOUNT");h.setPayoutEvidence("Synthetic account verification");h.setVerified(true);var s=h.getSigners().getFirst();s.setClientId(f.client);s.setMayApply(true);s.setMaySign(true);s.setMayReceive(true);return h;}
    PolicyForm policyForm(){var p=new PolicyForm();p.setProductId(f.product);p.setModel(Model.ORGANIZATION);p.setMinMembers(1);p.setMaxMembers(5);p.setMaxPrincipal(new BigDecimal("10000.00"));p.setMaxGuarantee(new BigDecimal("5000.00"));p.setMaxActiveCycles(2);p.setEnabled(true);p.setPolicyReference("Synthetic approved organization policy");p.setAgreementReference("Synthetic approved organization agreement");return p;}
    CycleForm form(){var c=new CycleForm();c.setPolicyId(policy);c.setApplicationDate(LoanRecordingPostgresTest.TODAY);c.setFirstPaymentDate(LoanRecordingPostgresTest.TODAY.plusMonths(1));c.setTenureValue(1);c.setPurpose("Organization trading stock");var o=new OrganizationForm();o.setAuthorityId(authority);o.setRepresentativeId(f.client);o.setPrincipal(new BigDecimal("1000.00"));o.setIncome(new BigDecimal("10000.00"));o.setExpenses(new BigDecimal("1000.00"));o.setOtherDebt(BigDecimal.ZERO);o.setBusiness("Synthetic organization trading");o.setAssessmentEvidence("Synthetic verified organization income, expenses and debt");c.setOrganization(o);return c;}
    int count(String table){return f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Integer.class,f.institution);}
    UUID create(CycleForm form){var cycle=cycles.create(f.actor,group,form);f.context.getBean(GroupOrganizationDocumentService.class).record(f.actor,cycle,form,documentForm(),documentFile());form.setExpectedVersion(cycles.detail(f.actor,cycle).version());return cycle;}
    com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm documentForm(){var d=new com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm();d.setDocumentReference("Synthetic approved organization agreement");d.setDocumentLanguage("EN");d.setInstitutionAddress("Synthetic institution address");d.setContactEmail("office@example.invalid");d.setComplaintEmail("complaints@example.invalid");d.setDocumentChecked(true);return d;}
    static org.springframework.mock.web.MockMultipartFile documentFile(){
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var text=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){text.beginText();text.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);text.newLineAtOffset(45,740);text.showText("SYNTHETIC APPROVED TERMS - AUTOMATED TEST ONLY");text.endText();}pdf.save(out);
            return new org.springframework.mock.web.MockMultipartFile("agreementPdf","synthetic-approved-terms.pdf","application/pdf",out.toByteArray());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    String status(UUID loan){return f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan);}
    void advance(UUID id,ApprovalWorkflowStage stage){new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->{var r=f.context.getBean(com.sacco.mvp.repository.LoanApplicationRepository.class);var l=r.findForDisbursement(id).orElseThrow();f.context.getBean(WorkflowRoutingService.class).advanceAfterApproval(l,stage,f.actor.getMemberId());r.saveAndFlush(l);});}
    UUID submit(){var form=form();return service.submit(f.actor,create(form),form);}
    @Test void oneOrganizationOwnsOneContractWithoutPersonalDebtMembershipOrMoney(){
        var form=form();var cycle=create(form);var loan=service.submit(f.actor,cycle,form);assertThat(service.submit(f.actor,cycle,form)).isEqualTo(loan);
        var row=f.jdbc.queryForMap("select l.applicant_member_id,b.kind,b.group_id,l.payment_details_snapshot->>'accountIdentifier' payout from loan_applications l join loan_borrower_party b on b.id=l.borrower_party_id where l.id=?",loan);
        assertThat(row).containsEntry("applicant_member_id",null).containsEntry("kind","ORGANIZATION").containsEntry("group_id",group).containsEntry("payout","QA-ACCOUNT");
        assertThat(groups.detail(f.actor,group).members()).isZero();assertThat(count("loan_applications")).isEqualTo(1);assertThat(count("lending_group_facility")).isEqualTo(1);
        for(String table:List.of("loan_ledgers","gl_journal","lending_group_guarantee","lending_group_participant"))assertThat(count(table)).as(table).isZero();
        var detail=f.service.detail(f.actor,loan);assertThat(detail.loan().clientId()).isNull();assertThat(detail.loan().client()).isEqualTo("Synthetic organization <Company>");assertThat(detail.contractualInterest()).isEqualByComparingTo("20.00");
        assertThat(service.workspace(f.actor,cycle,null).loan().loanId()).isEqualTo(loan);assertThat(status(loan)).isEqualTo("READY_FOR_DISBURSEMENT");
    }
    @Test void productReviewOrderAndFrozenPricingRemainAuthoritative(){
        f.jdbc.update("update loan_product_settings set manager_review_required=true,manager_priority=1,accountant_review_required=true,accountant_priority=6 where id=?",f.product);var loan=submit();assertThat(status(loan)).isEqualTo("READY_FOR_MANAGER");
        f.jdbc.update("update loan_product_settings set interest_rate=0.9,manager_review_required=false,accountant_review_required=false where id=?",f.product);
        advance(loan,ApprovalWorkflowStage.MANAGER);assertThat(status(loan)).isEqualTo("AWAITING_ACCOUNTANT");advance(loan,ApprovalWorkflowStage.ACCOUNTANT);assertThat(status(loan)).isEqualTo("READY_FOR_DISBURSEMENT");assertThat(f.service.detail(f.actor,loan).contractualInterest()).isEqualByComparingTo("20.00");
    }
    @Test void fundingCannotBypassPendingOrganizationContractAndPayoutImplementation(){
        var loan=submit();assertThatThrownBy(()->f.service.disburse(f.actor,f.post(loan,"1000.00","ORG-BLOCKED"))).hasMessage("recording.error.organizationFundingPending");
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='DISBURSED' where id=?",loan)).isInstanceOf(DataAccessException.class);
        assertThat(count("loan_ledgers")).isZero();assertThat(count("gl_journal")).isZero();assertThat(status(loan)).isEqualTo("READY_FOR_DISBURSEMENT");
    }
    @Test void scopedAuthorityAndCurrentClaimsAreCheckedWithAnOldSession(){
        var form=form();var cycle=create(form);assertThatThrownBy(()->service.workspace(f.operator("B2"),cycle,null)).isInstanceOf(AccessDeniedException.class);
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_AUTHORITY_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->service.submit(f.actor,cycle,form)).isInstanceOf(AccessDeniedException.class);assertThat(count("loan_applications")).isZero();
    }
    @Test void revokedSupersededAndAlreadyUsedAuthorityCannotCreateAnotherApplication(){
        var form=form();var cycle=create(form);authorities.revoke(f.actor,group,authority,"Synthetic revoked resolution");assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.organizationAuthority");
        authority=authorities.record(f.actor,group,authorityForm());form.getOrganization().setAuthorityId(authority);service.submit(f.actor,cycle,form);
        var next=form();var nextCycle=create(next);assertThatThrownBy(()->service.submit(f.actor,nextCycle,next)).hasMessage("group.error.organizationAuthority");assertThat(count("loan_applications")).isEqualTo(1);
    }
    @Test void arbitraryRegisteredPersonAndGroupOfficeDoNotGiveBorrowingPower(){
        var form=form();var cycle=create(form);form.getOrganization().setRepresentativeId(f.actor.getMemberId());assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.authoritySigners");assertThat(count("loan_applications")).isZero();
    }
    @Test void missingDocumentedCashFlowAndInsufficientIncomeDoNotPass(){
        var form=form();var cycle=create(form);form.getOrganization().setExpenses(null);assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.amount");form.getOrganization().setExpenses(new BigDecimal("1000.00"));form.getOrganization().setIncome(new BigDecimal("1100.00"));assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.affordability");
        form.getOrganization().setIncome(new BigDecimal("10000.00"));form.getOrganization().setAssessmentEvidence("");assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.form");assertThat(count("loan_applications")).isZero();
    }
    @Test void legacyOrganizationConfigurationDoesNotAutomaticallyEnableOrigination(){
        var legacy=UUID.randomUUID();f.jdbc.update("""
            insert into lending_group_policy(id,sacco_id,station_id,product_id,model,min_members,max_members,max_principal,max_guarantee,max_active_cycles,own_loan_guarantors,policy_reference,agreement_reference,enabled,request_key,payload_hash,created_by,created_at)
            select ?,sacco_id,station_id,product_id,model,2,max_members,max_principal,max_guarantee,max_active_cycles,own_loan_guarantors,policy_reference,agreement_reference,enabled,?,payload_hash,created_by,created_at from lending_group_policy where id=?
            """,legacy,UUID.randomUUID(),policy);
        var form=form();form.setPolicyId(legacy);var cycle=create(form);assertThat(service.workspace(f.actor,cycle,null).supported()).isFalse();
        assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.organizationPolicy");assertThat(count("loan_applications")).isZero();
    }
    @Test void mandatoryPersonalGuaranteesCannotBeReplacedByOrganizationSignatories(){
        f.jdbc.update("update loan_product_settings set guarantors_required=1 where id=?",f.product);var form=form();var cycle=create(form);assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.organizationGuarantors");assertThat(count("loan_applications")).isZero();
    }
    @Test void backdatingBeforeAuthorityVerificationAndExceedingLimitsAreRejected(){
        var form=form();var cycle=create(form);form.setApplicationDate(LoanRecordingPostgresTest.TODAY.minusDays(1));assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.date");form.setApplicationDate(LoanRecordingPostgresTest.TODAY);form.getOrganization().setPrincipal(new BigDecimal("10000.01"));assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.principalLimit");
    }
    @Test void savedDraftsRetainOrganizationValuesAndStaleEditsFail(){
        var form=form();var cycle=create(form);form.getOrganization().setBusiness("Updated organization activity");service.save(f.actor,cycle,form);assertThat(service.workspace(f.actor,cycle,null).form().getOrganization().getBusiness()).isEqualTo("Updated organization activity");
        assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("group.error.changed");assertThatThrownBy(()->cycles.save(f.actor,cycle,form)).hasMessage("group.error.organizationEditor");
        form.setExpectedVersion(cycles.detail(f.actor,cycle).version());service.submit(f.actor,cycle,form);assertThat(count("loan_applications")).isEqualTo(1);
    }
    @Test void simultaneousApplicationsCannotConsumeOneAuthorityVersionTwice()throws Exception{
        var one=form();var two=form();var c1=create(one);var c2=create(two);var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){var jobs=List.of(workers.submit(()->result(start,c1,one)),workers.submit(()->result(start,c2,two)));start.countDown();assertThat(List.of(jobs.getFirst().get(60,TimeUnit.SECONDS),jobs.getLast().get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder("SUBMITTED","group.error.organizationAuthority");}
        assertThat(count("loan_applications")).isEqualTo(1);assertThat(count("lending_group_facility")).isEqualTo(1);
    }
    String result(CountDownLatch start,UUID cycle,CycleForm form)throws Exception{start.await();try{service.submit(f.actor,cycle,form);return "SUBMITTED";}catch(IllegalArgumentException e){return e.getMessage();}}
    @Test void auditFailureRollsBackLoanIdentityChargesAndSourceTogether(){
        var form=form();var cycle=create(form);doThrow(new IllegalStateException("Synthetic audit rollback")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(cycle),eq("ORGANIZATION_LOAN_SUBMITTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.submit(f.actor,cycle,form)).hasMessage("Synthetic audit rollback");assertThat(count("loan_applications")).isZero();assertThat(count("loan_borrower_party")).isZero();assertThat(count("lending_group_facility")).isZero();assertThat(cycles.detail(f.actor,cycle).state()).isEqualTo("DRAFT");
    }
    @Test void unfundedWholeCycleWithdrawalRetainsTheOriginalCompanyContract(){
        var form=form();var cycle=create(form);var loan=service.submit(f.actor,cycle,form);var termination=new TerminationForm();termination.setExpectedVersion(cycles.detail(f.actor,cycle).version());termination.setReason("Synthetic organization withdraws application");cycles.withdraw(f.actor,cycle,termination);cycles.withdraw(f.actor,cycle,termination);
        assertThat(status(loan)).isEqualTo("WITHDRAWN");assertThat(cycles.detail(f.actor,cycle).state()).isEqualTo("CANCELLED");assertThat(count("lending_group_facility")).isEqualTo(1);assertThat(count("accountant_loan_record")).isEqualTo(1);
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",loan)).isInstanceOf(DataAccessException.class);assertThat(count("gl_journal")).isZero();
    }
    @Test void staffExposureFollowsTheTypedBorrowerAndFirstClassBranch(){
        var company=submit();var personal=f.service.save(f.actor,f.form());f.service.disburse(f.actor,f.post(personal,"1000.00","D-PERSONAL"));
        var repository=f.context.getBean(com.sacco.mvp.repository.LoanApplicationRepository.class);var statuses=List.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED,LoanStatus.WRITTEN_OFF);
        var page=org.springframework.data.domain.PageRequest.of(0,101);
        assertThat(repository.findActiveBorrowerLoansForReference(company,f.institution,"B1",statuses,page)).isEmpty();
        var other=f.service.save(f.actor,f.form());assertThat(repository.findActiveBorrowerLoansForReference(other,f.institution,"B1",statuses,page)).extracting(LoanApplication::getId).containsExactly(personal);
        assertThat(repository.findActiveBorrowerLoansForReference(other,f.institution,"B2",statuses,page)).isEmpty();
        assertThat(repository.findActiveBorrowerLoansForReference(other,"FOREIGN","B1",statuses,page)).isEmpty();
    }
    @Test void refreshingAuthorityPreservesEditedTermsAndDoesNotCreateALoan(){
        var form=form();var cycle=create(form);var next=authorities.record(f.actor,group,authorityForm());form.getOrganization().setBusiness("Retained draft business");service.useCurrentAuthority(f.actor,cycle,form);
        assertThat(form.getOrganization().getAuthorityId()).isEqualTo(next);assertThat(form.getOrganization().getBusiness()).isEqualTo("Retained draft business");assertThat(count("loan_applications")).isZero();
    }
    @Test void anOrganizationApplicationCannotCommitWithoutItsFacility(){
        var party=f.context.getBean(GroupOrganizationRepository.class).party(f.actor,group);var command=f.form();command.setClientId(null);var h=authorities.detail(f.actor,group,authority);
        var payout=new PaymentDetailsService.PaymentDetailsView(true,h.payoutType(),h.payoutType().name(),h.payoutProvider(),h.payoutHolder(),h.payoutIdentifier(),"GROUP_AUTHORITY");
        assertThatThrownBy(()->f.service.saveOrganization(f.actor,command,party,f.context.getBean(ObjectMapper.class).writeValueAsString(payout))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Organization application requires its group facility");
        assertThat(count("loan_applications")).isZero();assertThat(count("accountant_loan_record")).isZero();assertThat(count("gl_journal")).isZero();
    }
    @Test void authorityValidityUsesTanzaniaDateInUtcAndOtherSqlSessions(){
        for(String sessionZone:List.of("UTC","Pacific/Kiritimati")){
            var date=LoanRecordingPostgresTest.TODAY.plusDays(sessionZone.equals("UTC")?1:2);var clock=f.context.getBean(ApplicationClock.class);
            when(clock.today()).thenReturn(date);when(clock.now()).thenReturn(date.atTime(sessionZone.equals("UTC")?0:23,30).atOffset(ZoneOffset.ofHours(3)));
            var form=authorityForm();form.setValidFrom(date);form.setValidThrough(date);authority=authorities.record(f.actor,group,form);
            var request=form();request.setApplicationDate(date);request.setFirstPaymentDate(date.plusMonths(1));var cycle=create(request);
            // A second active application is allowed only by this explicitly configured product/policy.
            f.jdbc.update("update loan_product_settings set allow_application_with_active_loan=true,maximum_active_loans=3 where id=?",f.product);
            new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->{
                f.jdbc.execute("set local time zone '"+sessionZone+"'");service.submit(f.actor,cycle,request);
            });
        }
        assertThat(count("lending_group_facility")).isEqualTo(2);
    }
    @Test void databaseRejectsPayoutReassignmentAndFacilityHistoryDeletion(){
        var loan=submit();assertThatThrownBy(()->f.jdbc.update("update loan_applications set payment_details_snapshot=jsonb_set(payment_details_snapshot,'{accountIdentifier}','\"OTHER-ACCOUNT\"') where id=?",loan)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("delete from lending_group_facility where loan_id=?",loan)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set applicant_member_id=? where id=?",f.client,loan)).isInstanceOf(DataAccessException.class);
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupCyclePostgresTest.Config.class) static class Config {
        @Bean GroupAuthorityRepository authorityRepository(JdbcTemplate jdbc){return new GroupAuthorityRepository(jdbc);}
        @Bean GroupAuthorityService authorityService(GroupAuthorityRepository r,GroupLendingRepository g,GeneralLedgerService access,ApplicationClock clock,AuditService audit,ObjectMapper json){return new GroupAuthorityService(r,g,access,clock,audit,json);}
        @Bean GroupOrganizationRepository organizationRepository(JdbcTemplate jdbc){return new GroupOrganizationRepository(jdbc);}
        @Bean GroupOrganizationDocumentRepository documentRepository(JdbcTemplate jdbc){return new GroupOrganizationDocumentRepository(jdbc);}
        @Bean GroupOrganizationService organizationService(GroupCycleRepository c,GroupLendingRepository g,GroupAuthorityRepository h,GroupOrganizationRepository o,GroupOrganizationDocumentRepository d,GeneralLedgerService a,LoanRecordingService l,LoanQualificationPolicyService q,ApplicationClock clock,AuditService audit,ObjectMapper json){return new GroupOrganizationService(c,g,h,o,d,a,l,q,clock,audit,json);}
        @Bean GroupOrganizationDocumentService documentService(GroupOrganizationDocumentRepository d,GroupCycleRepository c,GroupOrganizationService o,GeneralLedgerService a,SaccoRegistryService i,ApplicationClock clock,AuditService audit,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager tx){return new GroupOrganizationDocumentService(d,c,o,a,i,clock,audit,json,tx);}
    }
}
