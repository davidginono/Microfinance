package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.CreateForm;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.UserClaimService;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleGroupLoanPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    GroupLendingService groups;SimpleGroupLoanService service;UUID group;List<UUID> clients;int phone;
    static final LocalDate DAY=LoanRecordingPostgresTest.TODAY;
    @BeforeAll void start(){f.start();groups=f.context.getBean(GroupLendingService.class);service=f.context.getBean(SimpleGroupLoanService.class);}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){
        f.fixture();phone=f.jdbc.queryForObject("select coalesce(max(right(phone,6)::int),0) from members where phone ~ '^255712[0-9]{6}$'",Integer.class);var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(DAY);when(clock.now()).thenReturn(DAY.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));
        f.jdbc.update("update loan_product_settings set interest_rate=0 where id=?",f.product);
        var form=new GroupForm();form.setName("Five borrowers");form.setEvidence("Synthetic signed membership list");group=groups.create(f.actor,form);clients=new ArrayList<>();
        for(var name:List.of("Asha","Juma","Neema","Peter","Rehema"))clients.add(client(name,true));
        configure("GROUP-ACCOUNT-1");
    }
    UUID client(String name,boolean incomeRecorded){
        var c=new CreateForm();c.getProfile().setFirstName(name);c.getProfile().setLastName("Test");c.getProfile().setPhone("+255712"+String.format("%06d",++phone));
        if(incomeRecorded)c.getProfile().setMonthlyIncome(new BigDecimal("2000000.00"));
        c.getProfile().setActivity("Business");c.getProfile().setBusinessName(name+" shop");c.getProfile().setSourceOfFunds("Business");c.setEnrollGroupId(group);
        return f.context.getBean(OnboardingService.class).create(f.actor,c);
    }
    void configure(String account){var current=service.setup(f.actor,group);var setup=new SetupForm();setup.setExpectedVersion(current.version());setup.setLeaderId(current.people().getFirst().id());setup.setDestinationType("BANK_ACCOUNT");setup.setProvider("Synthetic bank");setup.setHolder("Five borrowers group");setup.setIdentifier(account);setup.setEvidence("Checked signed group bank instruction");setup.setChecked(true);service.configure(f.actor,group,setup);}
    RecordForm application(){
        var form=f.form();form.setLoanFor("GROUP");form.setClientId(null);form.setApplicationDate(DAY);form.setPrincipal(new BigDecimal("5000000.00"));form.setTenureValue(10);
        var application=form.getGroupApplication();var setup=service.setup(f.actor,group);application.setGroupId(group);application.setExpectedVersion(setup.version());application.setChecked(true);application.setSignedOn(DAY);
        return form;
    }
    MockMultipartFile paper(){try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){pdf.addPage(new PDPage());pdf.save(out);return new MockMultipartFile("groupAgreement","synthetic-signed-group.pdf","application/pdf",out.toByteArray());}catch(Exception e){throw new AssertionError(e);}}
    MockMultipartFile activityImage(){try(var out=new ByteArrayOutputStream()){javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","synthetic-income.png","image/png",out.toByteArray());}catch(Exception e){throw new AssertionError(e);}}
    UUID activityDocument(UUID client,LocalDate expires,MockMultipartFile file){
        var onboarding=f.context.getBean(OnboardingService.class);var document=new com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.DocumentForm();
        document.setRevisionId(onboarding.client(f.actor,client).revisionId());document.setCategory("ACTIVITY");document.setExpiresOn(expires);
        return onboarding.attach(f.actor,client,document,file);
    }
    UUID record(){return f.service.saveGroup(f.actor,application(),List.of(),paper());}
    @Test void loanStepNamesGroupMembersWithAnOpenApplication(){
        record();
        var names=service.setup(f.actor,group).people().stream().map(Person::name).toList();
        assertThatThrownBy(()->f.service.validateStep(f.actor,application(),"loan",List.of(),null))
            .isInstanceOfSatisfying(SimpleGroupLoanService.GroupCreditHistoryException.class,error->{
                assertThat(error.pendingApplications()).containsExactlyInAnyOrderElementsOf(names);
                assertThat(error.missingHistory()).isEmpty();
                assertThat(error.restrictedHistory()).isEmpty();
            });
    }
    UUID fund(){UUID loan=record();var post=f.post(loan,"5000000.00","D-SIMPLE-"+UUID.randomUUID());post.setEffectiveDate(DAY);post.setFirstPaymentDate(DAY.plusDays(1));f.service.disburse(f.actor,post);return loan;}
    void nextDay(){var day=DAY.plusDays(2);var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(day);when(clock.now()).thenReturn(day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
    Posting pay(UUID loan,String amount,String payer){var post=f.post(loan,amount,"P-SIMPLE-"+UUID.randomUUID());post.setEffectiveDate(DAY.plusDays(2));post.setSharedPayer(payer);return f.service.repay(f.actor,post);}
    @Test void fivePeopleCreateOneLoanWithoutRulesAuthoritiesOrPersonalShares(){
        assertThat(service.choices(f.actor,"Five borrowers",0)).singleElement().satisfies(choice->assertThat(choice.label()).isEqualTo("Five borrowers"));
        UUID loan=record();
        var agreement=f.jdbc.queryForMap("select reference,language,filename from staff_group_loan where loan_id=?",loan);
        assertThat(agreement.get("reference")).isNull();assertThat(agreement.get("language")).isNull();assertThat(agreement.get("filename")).isEqualTo("synthetic-signed-group.pdf");
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_borrower where loan_id=?",Integer.class,loan)).isEqualTo(5);
        assertThat(f.jdbc.queryForObject("select amount from loan_applications where id=?",BigDecimal.class,loan)).isEqualByComparingTo("5000000.00");
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_authority where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_policy where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_shared_allocation where cycle_id in(select cycle_id from lending_group_shared_facility where loan_id=?)",Integer.class,loan)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,f.institution)).isZero();assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).hasSize(5);
    }
    @Test void aSignedGroupCanBeRecordedReviewedAndFundedWithoutAPdf(){
        var form=application();var loan=f.service.saveGroup(f.actor,form,List.of(),null);
        assertThat(f.service.saveGroup(f.actor,form,List.of(),null)).isEqualTo(loan);
        assertThat(service.contract(f.actor,loan).orElseThrow().agreementAvailable()).isFalse();
        assertThatThrownBy(()->service.document(f.actor,loan)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(f.jdbc.queryForObject("select filename is null and body is null from staff_group_loan where loan_id=?",Boolean.class,loan)).isTrue();
        var post=f.post(loan,"5000000.00","D-WITHOUT-PDF");post.setEffectiveDate(DAY);post.setFirstPaymentDate(DAY.plusDays(1));
        f.service.disburse(f.actor,post);
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("DISBURSED");
    }
    @Test void anInvalidSuppliedPdfCannotBeRecorded(){
        var invalid=new MockMultipartFile("groupAgreement","invalid.pdf","application/pdf","%PDF-broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(()->f.service.saveGroup(f.actor,application(),List.of(),invalid)).hasMessage("simpleGroup.error.document");
    }
    @Test void firstPaymentDateIsEnteredAtFundingAndCannotBeChangedLater(){
        var form=application();form.setFirstPaymentDate(DAY.plusDays(9));
        UUID loan=f.service.saveGroup(f.actor,form,List.of(),paper());
        assertThat(f.jdbc.queryForObject("select first_repayment_date from loan_applications where id=?",LocalDate.class,loan)).isNull();
        var post=f.post(loan,"5000000.00","D-DATE");post.setEffectiveDate(DAY);
        assertThatThrownBy(()->f.service.disburse(f.actor,post)).hasMessage("recording.error.date");
        post.setFirstPaymentDate(DAY);assertThatThrownBy(()->f.service.disburse(f.actor,post)).hasMessage("recording.error.date");
        post.setFirstPaymentDate(DAY.plusDays(3));f.service.disburse(f.actor,post);
        assertThat(f.jdbc.queryForObject("select first_repayment_date from loan_applications where id=?",LocalDate.class,loan)).isEqualTo(DAY.plusDays(3));
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set first_repayment_date=? where id=?",DAY.plusDays(4),loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void firstPaymentDateCannotBeSetDuringReview(){
        UUID loan=record();f.approve(loan);
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set first_repayment_date=? where id=?",DAY.plusDays(3),loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void exactRetriesReuseTheLoanAndChangedRetriesAreRejected(){var form=application();var paper=paper();UUID first=f.service.saveGroup(f.actor,form,List.of(),paper);assertThat(f.service.saveGroup(f.actor,form,List.of(),paper)).isEqualTo(first);form.setPrincipal(new BigDecimal("4000000.00"));assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),paper)).hasMessage("simpleGroup.error.changed");}
    @Test void missingAgreementConfirmationCannotCreateAContract(){var form=application();form.getGroupApplication().setChecked(false);assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),paper())).hasMessage("simpleGroup.error.signatures");assertThat(f.jdbc.queryForObject("select count(*) from staff_group_loan where sacco_id=?",Integer.class,f.institution)).isZero();}
    @Test void changedRosterRequiresFreshEvidence(){var form=application();client("Extra",true);assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),paper())).hasMessage("simpleGroup.error.changed");}
    @Test void allBorrowersWithMissingIncomeAreNamed(){
        client("Unrecorded",false);client("Missing",false);
        assertThatThrownBy(()->f.service.saveGroup(f.actor,application(),List.of(),null))
            .isInstanceOfSatisfying(SimpleGroupLoanService.ProfileIncomeException.class,error->
                assertThat(error.clients()).containsExactlyInAnyOrder("UNRECORDED TEST","MISSING TEST"));
    }
    @Test void aBorrowerDebtAboveTheirIncomeDoesNotBlockAnAffordableGroup(){
        var form=application();var debt=new ExternalDebt();debt.setReference("Outside loan");
        debt.setMonthlyPayment(new BigDecimal("2100000.00"));debt.setClientIds(List.of(clients.getFirst()));
        form.getGroupApplication().getDebts().add(debt);
        var loan=f.service.saveGroup(f.actor,form,List.of(),null);
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_borrower where loan_id=? and expenses is null",Integer.class,loan)).isEqualTo(5);
        assertThat(f.jdbc.queryForObject("select (form_data->>'otherDebt')::numeric from loan_applications where id=?",BigDecimal.class,loan))
            .isEqualByComparingTo("2100000.00");
    }
    @Test void oneGroupInstalmentReceivesFourHundredThousandThenAnotherHundredThousand(){
        UUID loan=fund();nextDay();assertThat(f.service.detail(f.actor,loan).loan().arrears()).isEqualByComparingTo("500000.00");
        pay(loan,"400000.00","Asha and three group borrowers");assertThat(f.service.detail(f.actor,loan).loan().arrears()).isEqualByComparingTo("100000.00");
        pay(loan,"100000.00","Rehema covering the group shortfall");assertThat(f.service.detail(f.actor,loan).loan().arrears()).isZero();assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("4500000.00");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Integer.class,loan)).isEqualTo(1);
        var exposure=f.context.getBean(SharedCreditExposureService.class).summary(f.institution,clients.getFirst(),"B1");assertThat(exposure.active()).isEqualTo(1);assertThat(exposure.liability()).isEqualByComparingTo("4500000.00");
        assertThat(new com.sacco.mvp.grouplending.repository.SimpleGroupLoanRepository(f.jdbc).monthlyDebt(f.actor,group)).isEqualByComparingTo("500000.00");
    }
    @Test void oneSigningDateAndProfileReferencesAreFrozenForEveryBorrower(){
        UUID loan=record();assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).allSatisfy(b->{assertThat(b.signedOn()).isEqualTo(DAY);assertThat(b.evidence()).startsWith("CLIENT_PROFILE:");});
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_borrower b join client_onboarding_revision r on r.id=b.profile_revision where b.loan_id=? and b.evidence='CLIENT_PROFILE:'||r.id::text",Integer.class,loan)).isEqualTo(5);
    }
    @Test void missingFutureOrEarlierCommonSigningDatesCannotRecordTheLoan(){
        for(var date:Arrays.asList(null,DAY.plusDays(1),DAY.minusDays(1))){var form=application();form.getGroupApplication().setSignedOn(date);assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),paper())).hasMessage("simpleGroup.error.signatures");}
    }
    @Test void clientDocumentsAndProfileProvenanceAreReusedWithoutStaffIncomeReferences(){
        UUID client=clients.getFirst();UUID document=activityDocument(client,null,activityImage());
        UUID loan=record();String checksum=f.jdbc.queryForObject("select u.sha256 from client_onboarding_document d join stored_uploads u on u.id=d.upload_id where d.id=?",String.class,document);
        assertThat(f.jdbc.queryForObject("select income_source from staff_group_borrower where loan_id=? and client_id=?",String.class,loan,client)).isEqualTo("CLIENT_DOCUMENT:"+checksum);
        assertThat(f.jdbc.queryForObject("select income_source from staff_group_borrower where loan_id=? and client_id=?",String.class,loan,clients.get(1))).startsWith("CLIENT_PROFILE:");
        assertThat(f.jdbc.queryForObject("select form_data::text from loan_applications where id=?",String.class,loan)).contains("DECLARED");
        assertThatThrownBy(()->service.setup(f.operator("B2"),group)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void expiredDocumentsUseRecordedProfileProvenanceWithoutClaimingVerification(){
        UUID client=clients.getFirst();activityDocument(client,DAY,activityImage());
        var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(DAY.plusDays(1));
        UUID loan=record();assertThat(f.jdbc.queryForObject("select income_source from staff_group_borrower where loan_id=? and client_id=?",String.class,loan,client)).startsWith("CLIENT_PROFILE:");
    }
    @Test void duplicateSavedIncomeDocumentsCannotMultiplyTheSameIncome(){
        var shared=activityImage();activityDocument(clients.get(0),null,shared);activityDocument(clients.get(1),null,shared);
        assertThatThrownBy(()->record()).hasMessage("simpleGroup.error.incomeSource");
    }
    @Test void olderDraftDatesAreReusedOnlyWhenAllSignersAgree(){
        var json=f.context.getBean(tools.jackson.databind.ObjectMapper.class);UUID id=service.saveDraft(f.actor,application(),List.of(),paper());
        var tree=(tools.jackson.databind.node.ObjectNode)json.readTree(f.jdbc.queryForObject("select form_data::text from staff_group_draft where id=?",String.class,id));
        ((tools.jackson.databind.node.ObjectNode)tree.get("groupApplication")).remove("signedOn");var signers=((tools.jackson.databind.node.ObjectNode)tree.get("groupApplication")).putArray("signers");
        for(var client:clients)signers.addObject().put("clientId",client.toString());
        for(var signer:signers)((tools.jackson.databind.node.ObjectNode)signer).put("signedOn",DAY.toString());
        f.jdbc.update("update staff_group_draft set form_data=?::jsonb where id=?",json.writeValueAsString(tree),id);assertThat(service.draft(f.actor,id).getGroupApplication().getSignedOn()).isEqualTo(DAY);
        ((tools.jackson.databind.node.ObjectNode)signers.get(0)).put("signedOn",DAY.minusDays(1).toString());
        f.jdbc.update("update staff_group_draft set form_data=?::jsonb where id=?",json.writeValueAsString(tree),id);assertThat(service.draft(f.actor,id).getGroupApplication().getSignedOn()).isNull();
    }
    @Test void independentReversalRestoresTheGroupShortfall(){UUID loan=fund();nextDay();pay(loan,"400000.00","Asha");var covered=pay(loan,"100000.00","Rehema");assertThatThrownBy(()->f.service.reverse(f.actor,covered.id(),UUID.randomUUID(),DAY.plusDays(2),"Wrong payment")).isInstanceOf(IllegalArgumentException.class);f.service.reverse(f.operator("B1"),covered.id(),UUID.randomUUID(),DAY.plusDays(2),"Synthetic independent correction");assertThat(f.service.detail(f.actor,loan).loan().arrears()).isEqualByComparingTo("100000.00");}
    @Test void accountChangesDoNotRewriteTheSignedLoan(){UUID loan=record();String before=service.contract(f.actor,loan).orElseThrow().destination();configure("GROUP-ACCOUNT-2");assertThat(service.contract(f.actor,loan).orElseThrow().destination()).isEqualTo(before);assertThat(service.setup(f.actor,group).identifier()).isEqualTo("GROUP-ACCOUNT-2");}
    @Test void foreignBranchesCannotReadTheGroupOrItsContract(){UUID loan=record();var foreign=f.operator("B2");assertThatThrownBy(()->service.setup(foreign,group)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(service.contract(foreign,loan)).isEmpty();assertThatThrownBy(()->service.document(foreign,loan)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void recordedEvidenceIsAppendOnly(){UUID loan=record();assertThatThrownBy(()->f.jdbc.update("update staff_group_borrower set debt=0 where loan_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->f.jdbc.update("delete from staff_group_loan where loan_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);}
    @Test void withdrawalPreservesTheUnfundedContractAndReleasesThePendingObligation(){UUID loan=record();var records=new com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository(f.jdbc);var current=records.groupLoan(f.actor,loan,false);UUID key=UUID.randomUUID();f.service.withdrawGroup(f.actor,loan,key,current.status(),current.termsHash(),"Replace the application");f.service.withdrawGroup(f.actor,loan,key,current.status(),current.termsHash(),"Replace the application");assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("WITHDRAWN");assertThat(service.contract(f.actor,loan)).isPresent();assertThat(f.context.getBean(SharedCreditExposureService.class).summary(f.institution,clients.getFirst(),"B1").pending()).isZero();}
    @Test void fundedLoansCannotBeWithdrawn(){UUID loan=fund();var current=new com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository(f.jdbc).groupLoan(f.actor,loan,false);assertThatThrownBy(()->f.service.withdrawGroup(f.actor,loan,UUID.randomUUID(),current.status(),current.termsHash(),"Not allowed")).isInstanceOf(org.springframework.dao.DataAccessException.class);}
    @Test void removingARetainedAgreementCannotSilentlyRestoreItOnRecording(){
        UUID id=service.saveDraft(f.actor,application(),List.of(),paper());var edit=service.draft(f.actor,id);edit.getGroupApplication().setRemoveAgreement(true);edit.getGroupApplication().setChecked(true);
        assertThat(service.draftFiles(f.actor,edit,List.of(),null).agreement()).isNull();
        service.saveDraft(f.actor,edit,List.of(),null);assertThat(service.draftDocuments(f.actor,id)).isEmpty();
        var restored=service.draft(f.actor,id);assertThat(restored.getGroupApplication().isRemoveAgreement()).isFalse();
        restored.getGroupApplication().setChecked(true);
        var loan=f.service.saveGroup(f.actor,restored,List.of(),null);
        assertThat(service.contract(f.actor,loan).orElseThrow().agreementAvailable()).isFalse();
    }
    @Test void groupDraftsKeepUploadedEvidenceAndNeedFreshConfirmation()throws Exception{var form=application();var pdf=paper();UUID id=service.saveDraft(f.actor,form,List.of(),pdf);var restored=service.draft(f.actor,id);assertThat(restored.getGroupApplication().isChecked()).isFalse();assertThat(service.draftDocuments(f.actor,id)).hasSize(1);assertThat(service.draftDocument(f.actor,id,0).bytes()).isEqualTo(pdf.getBytes());}
    @Test void everyJointBorrowerIsExcludedFromIndependentApproval(){UUID loan=record();for(UUID client:clients)assertThat(f.jdbc.queryForObject("select loan_group_related(?,?)",Boolean.class,loan,client)).isTrue();assertThat(f.jdbc.queryForObject("select loan_group_related(?,?)",Boolean.class,loan,f.actor.getMemberId())).isFalse();}
    @Test void staffRegistrationAndMembershipRetriesCreateOneClientAndOneMembership(){
        var c=new CreateForm();c.getProfile().setFirstName("Repeat");c.getProfile().setLastName("Test");c.getProfile().setPhone("+255712"+String.format("%06d",++phone));c.setEnrollGroupId(group);var onboarding=f.context.getBean(OnboardingService.class);UUID id=onboarding.create(f.actor,c);assertThat(onboarding.create(f.actor,c)).isEqualTo(id);assertThat(f.jdbc.queryForObject("select count(*) from lending_group_member where group_id=? and client_id=?",Integer.class,group,id)).isEqualTo(1);
    }
    @Test void aLeaderMustBeAnActiveClientInThisGroup(){var setup=service.setup(f.actor,group);var change=new SetupForm();change.setExpectedVersion(setup.version());change.setLeaderId(f.client);change.setChecked(true);assertThatThrownBy(()->service.configure(f.actor,group,change)).hasMessage("simpleGroup.error.leader");}
    @Test void concurrentApplicationRetriesReturnOneContract()throws Exception{
        var form=application();var pdf=paper();try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var gate=new java.util.concurrent.CountDownLatch(1);var first=pool.submit(()->{gate.await();return f.service.saveGroup(f.actor,form,List.of(),pdf);});var second=pool.submit(()->{gate.await();return f.service.saveGroup(f.actor,form,List.of(),pdf);});gate.countDown();assertThat(first.get(90,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(90,java.util.concurrent.TimeUnit.SECONDS));}
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_loan where sacco_id=?",Integer.class,f.institution)).isEqualTo(1);
    }
    @Test void duplicateMemberPaymentPostsOnce(){UUID loan=fund();nextDay();var payment=f.post(loan,"400000.00","P-DUPLICATE");payment.setEffectiveDate(DAY.plusDays(2));payment.setSharedPayer("Asha");var first=f.service.repay(f.actor,payment);assertThat(f.service.repay(f.actor,payment).id()).isEqualTo(first.id());assertThat(f.service.detail(f.actor,loan).loan().arrears()).isEqualByComparingTo("100000.00");}
    @Test void fullEarlyPaymentAndIndependentReversalKeepOneContract(){UUID loan=fund();nextDay();var paid=pay(loan,"5000000.00","Group treasurer");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isZero();f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),DAY.plusDays(2),"Wrong settlement");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("5000000.00");assertThat(f.jdbc.queryForObject("select count(*) from staff_group_loan where loan_id=?",Integer.class,loan)).isEqualTo(1);}
    @Test void allPostingRowsReconcileToOneGroupLedger(){UUID loan=fund();nextDay();pay(loan,"400000.00","Asha");pay(loan,"100000.00","Rehema");assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,f.institution)).isZero();assertThat(f.jdbc.queryForObject("select count(*) from staff_group_funding where loan_id=?",Integer.class,loan)).isEqualTo(1);}
    @Test void materialDraftChangesCannotReuseOldSignedEvidence(){var form=application();UUID id=service.saveDraft(f.actor,form,List.of(),paper());var restored=service.draft(f.actor,id);restored.getGroupApplication().setChecked(true);restored.setPrincipal(new BigDecimal("4000000.00"));assertThatThrownBy(()->f.service.saveGroup(f.actor,restored,List.of(),null)).hasMessage("simpleGroup.error.freshAgreement");service.saveDraft(f.actor,restored,List.of(),null);assertThat(service.draftDocuments(f.actor,id)).isEmpty();}
    @Test void draftCreationRetriesReuseTheDraft(){var form=application();var pdf=paper();UUID id=service.saveDraft(f.actor,form,List.of(),pdf);assertThat(service.saveDraft(f.actor,form,List.of(),pdf)).isEqualTo(id);assertThat(service.draftDocuments(f.actor,id)).hasSize(1);}
    @Test void anUnidentifiedPayerCannotReduceTheBalance(){UUID loan=fund();nextDay();assertThatThrownBy(()->pay(loan,"100000.00","")).isInstanceOf(IllegalArgumentException.class);assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("5000000.00");}
    @Test void reportsCountTheGroupOnceAndRecognizeItsPostedOrigin(){
        UUID loan=fund();nextDay();pay(loan,"400000.00","Asha");var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(f.jdbc);
        var clock=f.context.getBean(ApplicationClock.class);
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(named);var reportParameters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(DAY,DAY.plusDays(2),clock.now(),null,null,null,"",0);
        for(var kind:List.of(com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DISBURSED_LOANS_AUDIT,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_PORTFOLIO,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_REPAYMENT_TRANSACTIONS)){
            var data=reports.read(f.actor,kind,reportParameters,false);assertThat(data.excluded()).isZero();
            if(kind==com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_PORTFOLIO)assertThat(data.totals().get("principalBalance")).isEqualByComparingTo("4600000.00");
            assertThat(data.rows()).hasSize(1);assertThat(data.rows().getFirst().get("loanId")).isEqualTo(f.service.detail(f.actor,loan).loan().number());
        }
    }
    @Test void oneOutsideDebtOwedByAllFiveIsAssessedOnce(){
        var form=application();var debt=new ExternalDebt();debt.setReference("Outside joint debt / checked statement");debt.setMonthlyPayment(new BigDecimal("100000.00"));debt.setClientIds(new ArrayList<>(clients));form.getGroupApplication().getDebts().add(debt);
        UUID loan=f.service.saveGroup(f.actor,form,List.of(),paper());assertThat(f.jdbc.queryForObject("select (form_data->>'otherDebt')::numeric from loan_applications where id=?",BigDecimal.class,loan)).isEqualByComparingTo("100000.00");
        assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).allSatisfy(person->assertThat(person.debt()).isEqualByComparingTo("100000.00"));
    }
    @Test void outsideDebtsMustIdentifyBorrowersFromTheSelectedGroup(){
        var form=application();var debt=new ExternalDebt();debt.setReference("Outside loan");
        debt.setMonthlyPayment(new BigDecimal("10000.00"));debt.setClientIds(List.of(f.client));
        form.getGroupApplication().getDebts().add(debt);
        assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),null))
            .isInstanceOfSatisfying(com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService.FieldValidation.class,
                error->assertThat(error.field()).isEqualTo("groupApplication.debts[0].clientIds"));
    }
    @Test void aRecordedJointLoanIsNotCountedFiveTimesInTheNextGroupBudget(){fund();f.jdbc.update("update loan_product_settings set allow_application_with_active_loan=true,maximum_active_loans=3 where id=?",f.product);UUID next=record();assertThat(f.jdbc.queryForObject("select (form_data->>'otherDebt')::numeric from loan_applications where id=?",BigDecimal.class,next)).isEqualByComparingTo("500000.00");}
    @Test void customisedWeeklyGroupTermsUseTheExistingCalculator(){
        var form=application();form.setLoanMode("CUSTOM");form.setProductId(null);form.setInterestPercent(new BigDecimal("12"));form.setInterestBasis("ANNUAL");form.setCustomApplicationCharge(BigDecimal.ZERO);form.setCustomProcessingCharge(BigDecimal.ZERO);form.setInsurancePremium(BigDecimal.ZERO);form.setCustomFrequency(com.sacco.mvp.domain.RepaymentFrequency.WEEKLY);form.setCustomMethod(com.sacco.mvp.domain.InterestMethod.FLAT_RATE);form.setTenureUnit("MONTHS");form.setTenureValue(3);
        UUID loan=f.service.saveGroup(f.actor,form,List.of(),paper());assertThat(f.jdbc.queryForObject("select repayment_frequency from loan_applications where id=?",String.class,loan)).isEqualTo("WEEKLY");assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).hasSize(5);
    }
    @Test void paymentCorrectionDoesNotReassessAnAlreadyFundedContractAfterAClientMoves(){UUID loan=fund();nextDay();var paid=pay(loan,"5000000.00","Group treasurer");f.jdbc.update("update members set station_id='B2' where id=?",clients.getFirst());f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),DAY.plusDays(2),"Independent correction of funded history");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("5000000.00");}
    @Test void theOrdinaryReceiptUsesOneJointBorrowerAndTheActualPayer(){
        UUID loan=fund();nextDay();UUID party=f.service.detail(f.actor,loan).loan().clientId();var form=f.receiptForm(loan);form.setClientId(party);form.setEffectiveDate(DAY.plusDays(2));form.setLoanPayer("Rehema / group payment");form.getRows().getFirst().setAmount(new BigDecimal("400000.00"));
        var receipt=f.receiptService().post(f.actor,form);assertThat(receipt.payer()).isEqualTo(form.getLoanPayer());assertThat(receipt.rows()).hasSize(1);assertThat(receipt.rows().getFirst().sharedBenefits()).isEmpty();assertThat(f.receiptService().post(f.actor,form).header().id()).isEqualTo(receipt.header().id());assertThat(f.service.statement(f.actor,party,0).principal()).isEqualByComparingTo("4600000.00");
        var checker=f.operator("B1");var claims=EnumSet.allOf(com.sacco.mvp.domain.UserClaim.class);claims.remove(com.sacco.mvp.domain.UserClaim.GROUP_LENDING_REVERSE);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThat(f.receiptService().canReverse(checker,receipt)).isFalse();
    }
    @Test void aPaymentCannotBeBackdatedBeforeAnAlreadyPostedPayment(){UUID loan=fund();nextDay();pay(loan,"400000.00","Asha");var backdated=f.post(loan,"100000.00","P-BACKDATED");backdated.setEffectiveDate(DAY.plusDays(1));backdated.setSharedPayer("Rehema");assertThatThrownBy(()->f.service.repay(f.actor,backdated)).isInstanceOf(IllegalArgumentException.class);assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("4600000.00");}
    @Test void concurrentPaymentRetriesPostOneJournal()throws Exception{
        UUID loan=fund();nextDay();var payment=f.post(loan,"400000.00","P-CONCURRENT");payment.setEffectiveDate(DAY.plusDays(2));payment.setSharedPayer("Asha");try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var gate=new java.util.concurrent.CountDownLatch(1);var first=pool.submit(()->{gate.await();return f.service.repay(f.actor,payment).id();});var second=pool.submit(()->{gate.await();return f.service.repay(f.actor,payment).id();});gate.countDown();assertThat(first.get(90,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(90,java.util.concurrent.TimeUnit.SECONDS));}assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,f.institution)).isEqualTo(2);
    }
    @Test void changedPaymentAccountInvalidatesAnUnsubmittedRosterRevision(){var form=application();configure("CHANGED-ACCOUNT");assertThatThrownBy(()->f.service.saveGroup(f.actor,form,List.of(),paper())).hasMessage("simpleGroup.error.changed");}
    @Test void endingMembershipKeepsTheSignedRosterAndJointObligation(){UUID loan=record();var frozen=service.contract(f.actor,loan).orElseThrow();var setup=service.setup(f.actor,group);var membership=groups.members(f.actor,group,0,"startsOn_desc").rows().stream().filter(x->x.clientId().equals(setup.leaderId())).findFirst().orElseThrow();groups.endMember(f.actor,group,membership.id(),DAY,"Left after signing");assertThat(service.setup(f.actor,group).leaderId()).isNull();assertThat(service.contract(f.actor,loan).orElseThrow().leader()).isEqualTo(frozen.leader());assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).hasSize(5);assertThat(f.context.getBean(SharedCreditExposureService.class).summary(f.institution,setup.leaderId(),"B1").pending()).isEqualTo(1);}
    @Test void extraGuarantorsAreOptionalWithAnIndividualProductMinimum(){
        f.jdbc.update("update loan_product_settings set guarantors_required=1 where id=?",f.product);
        UUID loan=record();
        assertThat(f.jdbc.queryForObject("select required_guarantors from loan_applications where id=?",Integer.class,loan)).isZero();
        assertThat(f.jdbc.queryForObject("select staff_group_guarantees_ready(?)",Boolean.class,loan)).isTrue();
    }
    @Test void requiredIdentityVerificationCannotBeSkippedForAGroup(){f.jdbc.update("insert into lending_credit_policy(id,sacco_id,station_id,revision,capacity_required,collateral_required,reference,request_key,payload_hash,actor_id,recorded_at,reminders_enabled,kyc_required) values(?,?,?,1,false,false,'Synthetic identity policy',?,?,?,?,false,true)",UUID.randomUUID(),f.institution,"B1",UUID.randomUUID(),"0".repeat(64),f.actor.getMemberId(),f.context.getBean(ApplicationClock.class).now());assertThatThrownBy(()->record()).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("kyc.error.");}
    @Test void jointCreditRiskTracksOnlyTheUncoveredDuePayment(){
        var today=LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam"));var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(today);when(clock.now()).thenReturn(today.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));var form=application();UUID loan=f.service.saveGroup(f.actor,form,List.of(),paper());var funding=f.post(loan,"5000000.00","D-RISK");funding.setEffectiveDate(DAY);funding.setFirstPaymentDate(today.minusDays(1));f.service.disburse(f.actor,funding);var credit=f.context.getBean(SharedCreditExposureService.class);assertThat(credit.summary(f.institution,clients.getFirst(),"B1").risk()).isEqualTo(1);
        for(var amount:List.of("400000.00","100000.00")){var payment=f.post(loan,amount,"P-RISK-"+amount);payment.setEffectiveDate(today);payment.setSharedPayer("Group treasurer");f.service.repay(f.actor,payment);}assertThat(credit.summary(f.institution,clients.getFirst(),"B1").risk()).isZero();assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("DISBURSED");
    }

    @Test void aSeparateSignedGuarantorKeepsItsOwnCapacityAndNoBorrowerShare(){
        var registration=new CreateForm();registration.getProfile().setFirstName("External");registration.getProfile().setLastName("Guarantor");registration.getProfile().setPhone("+255712"+String.format("%06d",++phone));UUID guarantor=f.context.getBean(OnboardingService.class).create(f.actor,registration);
        f.jdbc.update("update loan_product_settings set guarantors_required=1 where id=?",f.product);var form=application();if(form.getApplicationDetails()==null)form.setApplicationDetails(new ApplicationDetails());
        var person=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor();person.setFullName("External Guarantor");person.setClientNumber(f.jdbc.queryForObject("select member_no from members where id=?",String.class,guarantor));person.setGuaranteeBudget(new BigDecimal("10000000.00"));person.setGuaranteeEvidence("Synthetic checked independent guarantee capacity");person.setSignedOn(DAY);form.getApplicationDetails().getGuarantors().add(person);form.getApplicationDetails().setDocumentTypes(List.of("GUARANTOR_CONSENT"));form.getApplicationDetails().setDocumentNames(List.of("Synthetic signed external guarantee"));
        var evidence=new com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService(new com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository(f.jdbc),f.context.getBean(com.sacco.mvp.accounting.service.GeneralLedgerService.class),new com.sacco.mvp.service.FormSchemaService(f.context.getBean(com.sacco.mvp.repository.LoanProductSettingRepository.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class)),new com.sacco.mvp.service.LoanProductRequiredAttachmentService(f.context.getBean(com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository.class)),mock(com.sacco.mvp.clients.applications.service.ClientApplicationService.class),f.context.getBean(OnboardingService.class));
        Object target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(f.service);Object previous=org.springframework.test.util.ReflectionTestUtils.getField(target,"evidence");org.springframework.test.util.ReflectionTestUtils.setField(target,"evidence",evidence);
        try{
            f.service.validateStep(f.actor,application(),"guarantors",List.of(),null);
            f.service.validateStep(f.actor,form,"loan",List.of(),null);
            var consent=paper();
            person.setGuaranteeBudget(new BigDecimal("1.00"));
            assertThatThrownBy(()->f.service.validateStep(f.actor,form,"attachments",List.of(consent),null))
                .isInstanceOfSatisfying(com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService.FieldValidation.class,
                    error->assertThat(error.field()).isEqualTo("applicationDetails.guarantors[0].guaranteeBudget"));
            person.setGuaranteeBudget(new BigDecimal("10000000.00"));
            f.service.validateStep(f.actor,form,"guarantors",List.of(),null);
            f.service.validateStep(f.actor,form,"collateral",List.of(),null);
            assertThatThrownBy(()->f.service.validateStep(f.actor,form,"attachments",List.of(),null))
                .hasMessage("recording.error.guarantorConsentAttachment");
            f.service.validateStep(f.actor,form,"attachments",List.of(consent),null);
            assertThat(f.jdbc.queryForObject("select count(*) from staff_group_loan where sacco_id=?",Integer.class,f.institution)).isZero();
            UUID loan=f.service.saveGroup(f.actor,form,List.of(consent),null);
            assertThat(f.jdbc.queryForObject("select count(*) from staff_group_guarantor where loan_id=? and client_id=?",Integer.class,loan,guarantor)).isEqualTo(1);
            assertThat(service.contract(f.actor,loan).orElseThrow().borrowers()).hasSize(5);
            assertThat(f.jdbc.queryForObject("select loan_group_related(?,?)",Boolean.class,loan,guarantor)).isTrue();
            assertThat(service.guaranteesReady(f.actor,loan)).isTrue();
        }finally{org.springframework.test.util.ReflectionTestUtils.setField(target,"evidence",previous);}
    }

    @Test void draftRetriesCannotReplaceEvidenceWithDifferentBytes()throws Exception{
        var form=application();var pdf=paper();UUID id=service.saveDraft(f.actor,form,List.of(),pdf);var bytes=java.util.Arrays.copyOf(pdf.getBytes(),pdf.getBytes().length+1);bytes[bytes.length-1]='\n';var changed=new MockMultipartFile("groupAgreement",pdf.getOriginalFilename(),"application/pdf",bytes);
        assertThatThrownBy(()->service.saveDraft(f.actor,form,List.of(),changed)).hasMessage("simpleGroup.error.changed");assertThat(service.draftDocument(f.actor,id,0).bytes()).isEqualTo(pdf.getBytes());
        var edit=service.draft(f.actor,id);service.saveDraft(f.actor,edit,List.of(),pdf);assertThatThrownBy(()->service.saveDraft(f.actor,edit,List.of(),changed)).hasMessage("simpleGroup.error.changed");assertThat(service.draftDocument(f.actor,id,0).bytes()).isEqualTo(pdf.getBytes());
    }
}
