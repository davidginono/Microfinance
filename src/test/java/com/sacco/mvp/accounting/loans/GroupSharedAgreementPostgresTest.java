package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.Export;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedAgreementPostgresTest {
    final GroupSharedFacilityPostgresTest s=new GroupSharedFacilityPostgresTest();
    GroupSharedAgreementService agreements;UUID cycle;com.sacco.mvp.security.AppUserPrincipal client;
    @BeforeAll void start(){s.g.start(Config.class);agreements=s.g.f.context.getBean(GroupSharedAgreementService.class);}
    @AfterAll void stop(){s.g.stop();}
    @BeforeEach void setup(){s.setup();s.g.f.context.getBean(SaccoRegistryService.class).findActiveSacco(s.g.f.institution).orElseThrow().setSaccoName("Synthetic loan institution");cycle=s.submit();client=s.g.client(s.g.f.client);assertThat(s.g.consents.accept(client,cycle,true,"")).isNull();assertThat(s.g.consents.accept(s.g.client(s.g.second),cycle,true,"")).isNull();
        for(var id:List.of(s.g.f.client,s.g.second))s.g.f.jdbc.update("insert into member_payment_details(member_id,destination_type,provider,account_holder_name,account_identifier,created_at,updated_at) values(?,'BANK_ACCOUNT','Synthetic bank','Synthetic account holder',?,now(),now())",id,"QA-"+id);
    }
    DocumentForm form(){var d=new GroupOrganizationPostgresTest().documentForm();d.setDocumentReference("Synthetic approved shared agreement");d.setDocumentLanguage("BOTH");return d;}
    UUID document(){return agreements.record(s.g.f.actor,cycle,form(),GroupOrganizationPostgresTest.documentFile());}
    String open(){document();agreements.exportClient(client,cycle,Locale.ENGLISH);return agreements.packet(client,cycle).row().hash();}
    int count(String table){return s.g.f.jdbc.queryForObject("select count(*) from "+table+" where cycle_id=?",Integer.class,cycle);}
    void requireOtp(){when(s.g.f.context.getBean(StationOtpSettingsService.class).requiresApprovalOtp(any(),any(),any())).thenReturn(true);}
    @Test void exactFullSourceAndOwnPayoutAreSignedWithoutCreatingCash()throws Exception{
        String hash=open();var before=agreements.packet(client,cycle);assertThat(before.row().opened()).isTrue();assertThat(before.row().obligation().allocation().net()).isEqualByComparingTo("123.45");
        assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();
        assertThat(count("lending_group_shared_payout")).isEqualTo(1);assertThat(agreements.packet(client,cycle).row().confirmation()).isNotNull();
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){try(var pdf=Loader.loadPDF(agreements.exportClient(client,cycle,locale))){assertThat(new PDFTextStripper().getText(pdf)).contains("SYNTHETIC APPROVED TERMS - AUTOMATED TEST ONLY","123.45","Synthetic bank");assertThat(pdf.getNumberOfPages()).isGreaterThan(1);}}
        assertThat(s.count("loan_ledgers")).isZero();assertThat(s.count("gl_journal")).isZero();
    }
    @Test void neitherSummarySignatureNorUnreadDocumentCanAuthorizePayout(){
        assertThat(agreements.packet(client,cycle).row().document()).isNull();assertThat(agreements.confirm(client,cycle,null,true,"")).isEqualTo("group.error.payoutClosed");
        document();var p=agreements.packet(client,cycle);assertThat(p.row().consented()).isTrue();assertThat(agreements.confirm(client,cycle,p.row().hash(),true,"")).isEqualTo("group.error.sharedReadDocument");assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void destinationChangesInvalidateThePacketAndItsOpenedEvidence(){
        String hash=open();s.g.f.jdbc.update("update member_payment_details set account_identifier='QA-CHANGED' where member_id=?",s.g.f.client);
        var p=agreements.packet(client,cycle);assertThat(p.row().hash()).isNotEqualTo(hash);assertThat(p.row().opened()).isFalse();assertThatThrownBy(()->agreements.confirm(client,cycle,hash,true,"")).hasMessage("group.error.changed");assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void laterProfileChangesDoNotRewriteTheConfirmedDestination(){
        String hash=open();assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();String destination=agreements.packet(client,cycle).row().destination().accountIdentifier();
        s.g.f.jdbc.update("update member_payment_details set account_identifier='QA-FUTURE-ACCOUNT' where member_id=?",s.g.f.client);
        var p=agreements.packet(client,cycle);assertThat(p.row().destination().accountIdentifier()).isEqualTo(destination);assertThat(p.row().hash()).isEqualTo(hash);assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();
    }
    @Test void personalDeliveredCodeIsRequiredAndBadAttemptsAreRetained(){
        String hash=open();requireOtp();var code=agreements.prepareCode(client,cycle,hash);
        assertThat(agreements.confirm(client,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");agreements.codeDelivered(client,cycle,code.id());
        assertThat(agreements.confirm(client,cycle,hash,true,"000000")).isEqualTo("group.error.otpInvalid");assertThat(s.g.f.jdbc.queryForObject("select attempts from lending_group_shared_payout_challenge where id=?",Integer.class,code.id())).isEqualTo(1);
        assertThat(agreements.confirm(client,cycle,hash,true,code.code())).isNull();assertThat(s.g.f.jdbc.queryForObject("select p.accepted_at=q.consumed_at from lending_group_shared_payout p join lending_group_shared_payout_challenge q on q.id=p.challenge_id where p.cycle_id=?",Boolean.class,cycle)).isTrue();
    }
    @Test void stalePacketCodeCannotSignAChangedDestination(){
        String hash=open();requireOtp();var code=agreements.prepareCode(client,cycle,hash);agreements.codeDelivered(client,cycle,code.id());
        s.g.f.jdbc.update("update member_payment_details set account_identifier='QA-SECOND-DESTINATION' where member_id=?",s.g.f.client);agreements.exportClient(client,cycle,Locale.ENGLISH);String updated=agreements.packet(client,cycle).row().hash();
        assertThat(agreements.confirm(client,cycle,updated,true,code.code())).isEqualTo("group.error.changed");assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void attemptExpiryAndResendBoundsAreEnforced(){
        String hash=open();requireOtp();var code=agreements.prepareCode(client,cycle,hash);agreements.codeDelivered(client,cycle,code.id());
        assertThatThrownBy(()->agreements.prepareCode(client,cycle,hash)).hasMessage("group.error.otpWait");
        for(int n=0;n<5;n++)assertThat(agreements.confirm(client,cycle,hash,true,"000000")).isEqualTo("group.error.otpInvalid");assertThat(agreements.confirm(client,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");
        var clock=s.g.f.context.getBean(ApplicationClock.class);var at=clock.now();when(clock.now()).thenReturn(at.plusMinutes(11));assertThat(agreements.confirm(client,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");
    }
    @Test void simultaneousRetriesProduceOneInstruction()throws Exception{
        String hash=open();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<String>>();for(int n=0;n<2;n++)jobs.add(workers.submit(()->{gate.await();return agreements.confirm(client,cycle,hash,true,"");}));gate.countDown();for(var job:jobs)assertThat(job.get(45,TimeUnit.SECONDS)).isNull();}assertThat(count("lending_group_shared_payout")).isEqualTo(1);
    }
    @Test void failedAuditRollsBackSignatureAndCodeConsumption(){
        String hash=open();requireOtp();var code=agreements.prepareCode(client,cycle,hash);agreements.codeDelivered(client,cycle,code.id());
        doThrow(new IllegalStateException("Synthetic shared payout audit failure")).when(s.g.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(cycle),eq("SHARED_PAYOUT_AUTHORIZED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->agreements.confirm(client,cycle,hash,true,code.code())).hasMessage("Synthetic shared payout audit failure");assertThat(count("lending_group_shared_payout")).isZero();assertThat(s.g.f.jdbc.queryForObject("select consumed_at is null and attempts=0 from lending_group_shared_payout_challenge where id=?",Boolean.class,code.id())).isTrue();
    }
    @Test void onlyTheNamedClientAndFreshSigningClaimsCanConfirm(){
        String hash=open();assertThatThrownBy(()->agreements.packet(s.g.f.actor,cycle)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->agreements.packet(s.g.client(UUID.randomUUID()),cycle)).isInstanceOf(AccessDeniedException.class);
        var effective=EnumSet.allOf(UserClaim.class);effective.remove(UserClaim.MEMBER_LOANS_UPDATE);when(s.g.f.context.getBean(UserClaimService.class).effectiveClaims(eq(s.g.f.client),anyCollection(),anyBoolean())).thenReturn(effective);
        assertThatThrownBy(()->agreements.confirm(client,cycle,hash,true,"")).isInstanceOf(AccessDeniedException.class);assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void cancelledCycleKeepsEvidenceButCannotCreateNewInstructions(){
        String hash=open();assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();var second=s.g.client(s.g.second);agreements.exportClient(second,cycle,Locale.ENGLISH);String other=agreements.packet(second,cycle).row().hash();
        var f=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();f.setExpectedVersion(1);f.setReason("Synthetic withdrawal before funding");s.g.cycles.withdraw(s.g.f.actor,cycle,f);
        assertThat(agreements.packet(client,cycle).row().confirmation()).isNotNull();assertThat(agreements.confirm(second,cycle,other,true,"")).isEqualTo("group.error.payoutClosed");assertThat(count("lending_group_shared_payout")).isEqualTo(1);
    }
    @Test void missingPayoutProfileCannotCreateAuthority(){
        document();s.g.f.jdbc.update("delete from member_payment_details where member_id=?",s.g.f.client);assertThatThrownBy(()->agreements.exportClient(client,cycle,Locale.ENGLISH)).hasMessage("group.error.sharedDestination");assertThat(agreements.packet(client,cycle).row().destination().available()).isFalse();
        assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void freshMissingSignatureCannotAuthorizePayout(){
        String hash=open();s.g.f.context.getBean(MemberDirectoryService.class).find(s.g.f.client).orElseThrow().setSignatureText("");
        assertThat(agreements.confirm(client,cycle,hash,true,"")).isEqualTo("group.error.signature");assertThat(count("lending_group_shared_payout")).isZero();
    }
    @Test void documentAndInstructionHistoryCannotBeChangedOrDeleted(){
        String hash=open();assertThat(agreements.confirm(client,cycle,hash,true,"")).isNull();
        for(var table:List.of("lending_group_shared_document","lending_group_shared_opened","lending_group_shared_payout"))assertThatThrownBy(()->s.g.f.jdbc.update("delete from "+table+" where cycle_id=?",cycle)).hasMessageContaining("append-only");
        assertThat(agreements.packet(client,cycle).row().confirmation()).isNotNull();
    }
    @Test void sameDocumentRetryIsIdempotentAndAReplacementCannotOverwriteIt(){
        var form=form();var file=GroupOrganizationPostgresTest.documentFile();UUID id=agreements.record(s.g.f.actor,cycle,form,file);assertThat(agreements.record(s.g.f.actor,cycle,form,file)).isEqualTo(id);
        assertThatThrownBy(()->agreements.record(s.g.f.actor,cycle,form(),file)).hasMessage("group.error.changed");assertThat(count("lending_group_shared_document")).isEqualTo(1);
    }
    @Test void codeTransportRunsAfterCommitAndFailureDoesNotMarkDelivery(){
        String hash=open();requireOtp();var transport=s.g.f.context.getBean(StationOtpDeliveryService.class);
        when(transport.deliver(anyString(),anyString(),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),anyString(),anyInt(),any(),any(),any())).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();throw new IllegalStateException("Synthetic delivery failure");});
        assertThatThrownBy(()->s.g.f.context.getBean(GroupSharedAgreementDeliveryService.class).send(client,cycle,hash,Locale.ENGLISH)).hasMessage("group.error.otpDelivery");assertThat(s.g.f.jdbc.queryForObject("select delivered_at is null from lending_group_shared_payout_challenge where cycle_id=?",Boolean.class,cycle)).isTrue();
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupCyclePostgresTest.Config.class) static class Config {
        @Bean GroupSharedAgreementRepository sharedAgreements(JdbcTemplate jdbc,ObjectMapper json){return new GroupSharedAgreementRepository(jdbc,json);}
        @Bean org.springframework.context.MessageSource messageSource(){var m=new org.springframework.context.support.ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean GroupSharedAgreementPdf sharedPdf(org.springframework.context.MessageSource messages){return new GroupSharedAgreementPdf(messages){@Override public byte[] render(Export data,Locale locale){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return super.render(data,locale);}};}
        @Bean GroupSharedAgreementService sharedAgreementService(GroupSharedAgreementRepository r,GroupCycleRepository c,GroupConsentService people,GeneralLedgerService access,SaccoRegistryService institutions,ApplicationClock clock,AuditService audit,ObjectMapper json,PlatformTransactionManager tx,StationOtpSettingsService otp,org.springframework.mock.env.MockEnvironment env,GroupSharedAgreementPdf pdf){return new GroupSharedAgreementService(r,c,people,access,institutions,clock,audit,json,tx,otp,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4),env,pdf);}
        @Bean GroupSharedAgreementDeliveryService sharedDelivery(GroupSharedAgreementService service,StationOtpDeliveryService transport,org.springframework.context.MessageSource messages){return new GroupSharedAgreementDeliveryService(service,transport,messages);}
    }
}
