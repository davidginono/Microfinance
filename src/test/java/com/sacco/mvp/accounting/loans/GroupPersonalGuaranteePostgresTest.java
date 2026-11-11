package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupPersonalGuaranteePostgresTest {
    final GroupOrganizationFundingPostgresTest w=new GroupOrganizationFundingPostgresTest();
    final GroupOrganizationAgreementPostgresTest s=w.s;final GroupOrganizationPostgresTest o=w.o;final LoanRecordingPostgresTest f=w.f;
    GroupPersonalGuaranteeService guarantees;

    static GroupPersonalGuaranteeService register(LoanRecordingPostgresTest f){
        var r=new GroupPersonalGuaranteeRepository(f.jdbc);
        return new GroupPersonalGuaranteeService(r,f.context.getBean(GroupCycleRepository.class),f.context.getBean(com.sacco.mvp.accounting.service.GeneralLedgerService.class),f.context.getBean(LoanQualificationPolicyService.class),f.service,f.context.getBean(ApplicationClock.class),f.context.getBean(AuditService.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class),f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class),new ReportExportLimiter(1));
    }
    @BeforeAll void start(){w.start();guarantees=register(f);}
    @AfterAll void stop(){w.stop();}
    @BeforeEach void fixture(){fixture(LoanRecordingPostgresTest.TODAY.plusMonths(1));}
    void fixture(LocalDate firstPaymentDate){o.fixture();reset(f.context.getBean(StationOtpSettingsService.class));reset(f.context.getBean(StationOtpDeliveryService.class));f.jdbc.update("update loan_product_settings set guarantors_required=1 where id=?",f.product);var form=o.form();form.setFirstPaymentDate(firstPaymentDate);s.cycle=o.create(form);s.loan=o.service.submit(f.actor,s.cycle,form);s.signer=s.client(f.client);s.hash=s.packetHash(s.cycle);f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow().setSignatureText("Synthetic lender signature");f.jdbc.update("update members set signature_text='Synthetic lender signature' where id=?",f.actor.getMemberId());}
    Form form(){var form=new Form();form.setClientId(f.client);form.setCapacity(new BigDecimal("10000.00"));form.setCapacityEvidence("Synthetic independently verified total guarantee capacity");form.setValidThrough(LocalDate.now(java.time.ZoneId.of("Africa/Dar_es_Salaam")).plusDays(30));form.setDocument(o.documentForm());return form;}
    UUID offer(){return guarantees.record(f.actor,s.cycle,form(),GroupOrganizationPostgresTest.documentFile());}
    Packet own(UUID id){return guarantees.staffPacket(f.actor,s.cycle,id);}
    void accept(UUID id){GroupPaperFixture.record(f,s.cycle,"PERSONAL",f.client);}
    void fund(UUID id){accept(id);guarantees.startReview(f.actor,s.cycle);w.p.ready();w.p.confirm();f.service.disburse(f.actor,w.funding());}
    long count(String table){return f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Long.class,f.institution);}

    @Test void pendingOffersReserveOnceAndCheckedWholeCorrectionsRestoreTheSamePromise(){
        UUID id=offer();assertThatThrownBy(this::offer).hasMessage("group.error.personalGuaranteesClosed");assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).reserved()).isEqualByComparingTo("1020.00");var requests=f.context.getBean(com.sacco.mvp.repository.GuarantorRequestRepository.class);assertThat(requests.sumActiveGuaranteedAmount(f.client,f.institution,"B1")).isEqualByComparingTo("1000.00");assertThat(requests.countActiveGuarantees(f.client,f.institution,"B1")).isEqualTo(1);
        fund(id);var paid=f.service.repay(f.actor,f.post(s.loan,"1000.00","PERSONAL-GUARANTEE-PAID"));assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).reserved()).isZero();f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic independent full-payment correction");assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).reserved()).isEqualByComparingTo("1020.00");assertThat(own(id).offer().state()).isEqualTo("ACCEPTED");assertThat(f.jdbc.queryForObject("select applicant_member_id from loan_ledgers where loan_application_id=?",UUID.class,s.loan)).isNull();assertThat(count("loan_ledgers")).isEqualTo(1);
    }

    @Test void immutableOfferDecisionAndDocumentEvidenceCannotBeRewritten(){
        UUID id=offer();accept(id);for(var sql:List.of("update lending_group_personal_guarantee set capacity=999999 where id=?","delete from lending_group_personal_guarantee where id=?","delete from lending_group_personal_decision where offer_id=?","update lending_group_personal_decision set decision='REFUSED' where offer_id=?","delete from lending_group_signed_paper where offer_id=?"))assertThatThrownBy(()->f.jdbc.update(sql,id)).isInstanceOf(DataAccessException.class);assertThatThrownBy(()->guarantees.cancel(f.actor,s.cycle,id,"Cannot erase accepted liability")).hasMessage("group.error.personalGuaranteesClosed");assertThat(count("loan_ledgers")).isZero();
    }

    @Test void movedInactiveNomineeCanBeCancelledWithoutRevivingOrErasingTheOffer(){
        UUID id=offer();f.jdbc.update("update members set status='INACTIVE',station_id='B2' where id=?",f.client);guarantees.cancel(f.actor,s.cycle,id,"Synthetic nominee no longer eligible");assertThat(guarantees.staffPacket(f.actor,s.cycle,id).offer().state()).isEqualTo("CANCELLED");assertThat(f.jdbc.queryForObject("select count(*) from lending_group_personal_decision where offer_id=?",Integer.class,id)).isEqualTo(1);
    }

    UUID anotherOrganizationCycle(){var groupForm=new com.sacco.mvp.grouplending.dto.GroupLendingDtos.GroupForm();groupForm.setName("Synthetic second organization");groupForm.setLegalForm("ORGANIZATION");groupForm.setEvidence("Synthetic distinct legal identity");o.group=o.groups.create(f.actor,groupForm);o.authority=o.authorities.record(f.actor,o.group,o.authorityForm());var form=o.form();UUID cycle=o.create(form);o.service.submit(f.actor,cycle,form);return cycle;}
    @Test void competingOffersForDifferentLoansCannotOverbookOneVerifiedBudget()throws Exception{
        UUID other=anotherOrganizationCycle();var first=form();first.setCapacity(new BigDecimal("1500.00"));var second=form();second.setCapacity(new BigDecimal("1500.00"));var pdf=GroupOrganizationPostgresTest.documentFile();try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var one=pool.submit(()->{start.await();try{guarantees.record(f.actor,s.cycle,first,pdf);return true;}catch(IllegalArgumentException denied){return false;}});var two=pool.submit(()->{start.await();try{guarantees.record(f.actor,other,second,pdf);return true;}catch(IllegalArgumentException denied){return false;}});start.countDown();assertThat(one.get(45,TimeUnit.SECONDS)).isNotEqualTo(two.get(45,TimeUnit.SECONDS));}assertThat(count("lending_group_personal_guarantee")).isEqualTo(1);assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).reserved()).isEqualByComparingTo("1020.00");
    }

    @Test void expiryReleasesOnlyUnacceptedOffersAndRetainsTheirRecordedEvidence(){
        var request=form();request.setValidThrough(LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")).minusDays(1));UUID id=guarantees.record(f.actor,s.cycle,request,GroupOrganizationPostgresTest.documentFile());assertThat(f.jdbc.queryForObject("select count(*) from lending_group_personal_commitment where id=?",Integer.class,id)).isZero();assertThat(count("lending_group_personal_guarantee")).isEqualTo(1);assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).reserved()).isZero();
    }
    @Test void signedDisclosureBindsActualFundingDateIdentityTermsAndSignatureInBothLanguages()throws Exception{
        UUID id=offer();assertThatThrownBy(()->guarantees.evidence(f.actor,s.cycle,id)).hasMessage("group.error.personalGuaranteesClosed");fund(id);var data=guarantees.evidence(f.actor,s.cycle,id);assertThat(data.packet().source().ratePercent()).isEqualByComparingTo("24");assertThat(data.packet().source().fundedDate()).isEqualTo(LoanRecordingPostgresTest.TODAY);var messages=new org.springframework.context.support.ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");var pdf=new GroupPersonalGuaranteePdf(messages);for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){byte[] bytes=pdf.render(data,locale);try(var doc=org.apache.pdfbox.Loader.loadPDF(bytes)){var text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);assertThat(text).contains("Synthetic organization <Company>","Signed paper /","24%","1,020.00","SYNTHETIC APPROVED TERMS - AUTOMATED TEST ONLY","08/10/2026",data.packet().offer().code());assertThat(doc.getNumberOfPages()).isGreaterThan(1);}java.nio.file.Files.write(java.nio.file.Path.of("target/personal-guarantee-"+locale.getLanguage()+".pdf"),bytes);}
    }
    @Test void ordinaryApprovalsCannotConsumeAnAlreadyReservedPersonalBudget(){
        var form=form();form.setCapacity(new BigDecimal("1020.00"));guarantees.record(f.actor,s.cycle,form,GroupOrganizationPostgresTest.documentFile());var borrower=w.p.person();var ordinary=f.form();ordinary.setClientId(borrower.getMemberId());var loan=f.service.save(f.actor,ordinary);assertThatThrownBy(()->f.jdbc.update("insert into guarantor_requests(id,loan_application_id,guarantor_member_id,status,created_at,version) values(?,?,?,'APPROVED',now(),0)",UUID.randomUUID(),loan,f.client)).isInstanceOf(DataAccessException.class).hasMessageContaining("capacity exceeded");assertThat(count("lending_group_personal_guarantee")).isEqualTo(1);
    }
}
