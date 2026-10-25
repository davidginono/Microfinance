package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationAgreementPostgresTest {
    final GroupOrganizationPostgresTest o=new GroupOrganizationPostgresTest();
    GroupOrganizationAgreementService service;AppUserPrincipal signer;UUID cycle,loan;String hash;
    @BeforeAll void start(){o.f.context=new AnnotationConfigApplicationContext(Config.class);o.f.jdbc=o.f.context.getBean(JdbcTemplate.class);o.f.service=o.f.context.getBean(com.sacco.mvp.accounting.loans.service.LoanRecordingService.class);
        o.service=o.f.context.getBean(GroupOrganizationService.class);o.authorities=o.f.context.getBean(GroupAuthorityService.class);o.cycles=o.f.context.getBean(GroupCycleService.class);o.groups=o.f.context.getBean(GroupLendingService.class);service=o.f.context.getBean(GroupOrganizationAgreementService.class);}
    @AfterAll void stop(){o.stop();}
    @BeforeEach void fixture(){o.fixture();reset(o.f.context.getBean(StationOtpSettingsService.class));reset(o.f.context.getBean(StationOtpDeliveryService.class));
        o.f.context.getBean(org.springframework.mock.env.MockEnvironment.class).setProperty("app.auth.otp.ttl-minutes","10");
        var form=o.form();cycle=o.create(form);loan=o.service.submit(o.f.actor,cycle,form);signer=client(o.f.client);
        hash=service.packet(signer,cycle,0).hash();}
    AppUserPrincipal client(UUID id){var member=Member.builder().id(id).saccoId(o.f.institution).stationId("B1").memberNo(id.toString()).fullName("Synthetic named signatory").status(MemberStatus.ACTIVE).position(Position.MEMBER).memberAccount(true).signatureText("Synthetic representative signature").email("signer@example.invalid").build();
        o.f.jdbc.update("update members set signature_text=?,signature_registered_at=now() where id=?",member.getSignatureText(),id);
        when(o.f.context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(member));return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),false);}
    int count(String table){return o.count(table);}
    void otp(){when(o.f.context.getBean(StationOtpSettingsService.class).requiresApprovalOtp(any(),anyString(),anyString())).thenReturn(true);}
    @Test void namedNonMemberSignsFrozenCompanyTermsWithoutPersonalDebtOrMoney(){
        var packet=service.packet(signer,cycle,0);assertThat(packet.snapshot().organization().groupId()).isEqualTo(o.group);assertThat(packet.schedule()).hasSize(1);assertThat(packet.canSign()).isTrue();
        assertThat(count("lending_group_member")).isZero();assertThat(service.sign(signer,cycle,hash,true,"")).isNull();assertThat(service.sign(signer,cycle,hash,true,"")).isNull();
        assertThat(service.packet(signer,cycle,0).signed()).isTrue();assertThat(count("lending_group_org_signature")).isEqualTo(1);
        assertThat(o.f.jdbc.queryForMap("select applicant_member_id,applicant_signature_text from loan_applications where id=?",loan).values()).containsOnlyNulls();
        assertThat(count("lending_group_guarantee")).isZero();assertThat(count("loan_ledgers")).isZero();
        assertThat(o.f.context.getBean(GroupConsentService.class).list(signer,0).rows()).anySatisfy(r->{assertThat(r.id()).isEqualTo(cycle);assertThat(r.model()).isEqualTo(com.sacco.mvp.grouplending.dto.GroupCycleDtos.Model.ORGANIZATION);assertThat(r.accepted()).isTrue();});
    }
    @Test void packetAndSignatureHistoryCannotBeChanged(){
        o.f.jdbc.update("update loan_product_settings set interest_rate=0.99 where id=?",o.f.product);assertThat(service.packet(signer,cycle,0).hash()).isEqualTo(hash);
        assertThatThrownBy(()->service.sign(signer,cycle,"0".repeat(64),true,"")).hasMessage("group.error.changed");
        assertThat(service.sign(signer,cycle,hash,true,"")).isNull();
        assertThatThrownBy(()->o.f.jdbc.update("update lending_group_org_agreement set packet_hash=? where cycle_id=?","0".repeat(64),cycle)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->o.f.jdbc.update("delete from lending_group_org_signature where cycle_id=?",cycle)).isInstanceOf(DataAccessException.class);
    }
    @Test void currentClaimsOwnPersonAndBranchAreRequired(){
        assertThatThrownBy(()->service.packet(o.f.actor,cycle,0)).isInstanceOf(AccessDeniedException.class);
        var directory=o.f.context.getBean(MemberDirectoryService.class);var person=directory.find(o.f.client).orElseThrow();
        var unclaimed=new AppUserPrincipal(person,Set.of(UserClaim.MEMBER_LOANS_VIEW),false);assertThatThrownBy(()->service.sign(unclaimed,cycle,hash,true,"")).isInstanceOf(AccessDeniedException.class);
        when(o.f.context.getBean(UserClaimService.class).effectiveClaims(any(),anySet(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.packet(signer,cycle,0)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void supersededAuthorityClosesUnsignedPacket(){
        var next=o.authorityForm();o.authorities.record(o.f.actor,o.group,next);assertThat(service.packet(signer,cycle,0).canSign()).isFalse();assertThat(service.sign(signer,cycle,hash,true,"")).isEqualTo("group.error.organizationSigningClosed");
        assertThat(count("lending_group_org_signature")).isZero();
    }
    @Test void revokedAuthorityCannotAcceptASignatureOrIssueACode(){
        otp();o.authorities.revoke(o.f.actor,o.group,o.authority,"Synthetic authority revoked before signing");
        assertThat(service.packet(signer,cycle,0).canSign()).isFalse();assertThat(service.sign(signer,cycle,hash,true,"")).isEqualTo("group.error.organizationSigningClosed");
        assertThatThrownBy(()->service.prepareCode(signer,cycle,hash)).hasMessage("group.error.organizationSigningClosed");assertThat(count("lending_group_org_signature")).isZero();
    }
    @Test void inactiveOrganizationCannotBeSigned(){
        o.groups.status(o.f.actor,o.group,o.groups.detail(o.f.actor,o.group).version(),false);
        assertThat(service.packet(signer,cycle,0).canSign()).isFalse();assertThat(service.sign(signer,cycle,hash,true,"")).isEqualTo("group.error.organizationSigningClosed");
        assertThat(count("lending_group_org_signature")).isZero();
    }
    @Test void deliveredOtpBindsThisContractAndCountsWrongAttempts(){
        otp();assertThat(service.sign(signer,cycle,hash,true,"")).isEqualTo("group.error.otpRequired");var code=service.prepareCode(signer,cycle,hash);
        assertThat(service.sign(signer,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");service.codeDelivered(signer,cycle,code.id());
        assertThat(service.sign(signer,cycle,hash,true,"not-a-code")).isEqualTo("group.error.otpInvalid");assertThat(service.sign(signer,cycle,hash,true,code.code())).isNull();
        assertThat(o.f.jdbc.queryForObject("select s.signed_at=c.consumed_at and c.attempts=2 from lending_group_org_signature s join lending_group_org_challenge c on c.id=s.challenge_id where s.cycle_id=?",Boolean.class,cycle)).isTrue();
    }
    @Test void fiveWrongAttemptsCannotBeReplayedOrReset(){
        otp();var code=service.prepareCode(signer,cycle,hash);service.codeDelivered(signer,cycle,code.id());
        for(int i=0;i<5;i++)assertThat(service.sign(signer,cycle,hash,true,"bad")).isEqualTo("group.error.otpInvalid");
        assertThat(service.sign(signer,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");
        assertThatThrownBy(()->service.prepareCode(signer,cycle,hash)).hasMessage("group.error.otpWait");assertThat(count("lending_group_org_signature")).isZero();
    }
    @Test void expiredCodeAndMissingProfileSignatureFailClosed(){
        otp();var code=service.prepareCode(signer,cycle,hash);service.codeDelivered(signer,cycle,code.id());var clock=o.f.context.getBean(ApplicationClock.class);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-08T12:11:00+03:00"));
        assertThat(service.sign(signer,cycle,hash,true,code.code())).isEqualTo("group.error.otpExpired");
        var person=o.f.context.getBean(MemberDirectoryService.class).find(o.f.client).orElseThrow();person.setSignatureText(null);assertThat(service.sign(signer,cycle,hash,true,code.code())).isEqualTo("group.error.signature");
    }
    @Test void signatureAuditFailureRollsBackOtpAndAcceptance(){
        otp();var code=service.prepareCode(signer,cycle,hash);service.codeDelivered(signer,cycle,code.id());
        doThrow(new IllegalStateException("Synthetic audit failure")).when(o.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("ORGANIZATION_AGREEMENT_SIGNED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.sign(signer,cycle,hash,true,code.code())).hasMessage("Synthetic audit failure");assertThat(count("lending_group_org_signature")).isZero();
        assertThat(o.f.jdbc.queryForObject("select consumed_at is null and attempts=0 from lending_group_org_challenge where id=?",Boolean.class,code.id())).isTrue();
    }
    @Test void concurrentDuplicateRequestsCreateOneSignature()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var jobs=List.of(pool.submit(()->{gate.await();return service.sign(signer,cycle,hash,true,"");}),pool.submit(()->{gate.await();return service.sign(signer,cycle,hash,true,"");}));gate.countDown();for(var job:jobs)assertThat(job.get(20,TimeUnit.SECONDS)).isNull();}
        assertThat(count("lending_group_org_signature")).isEqualTo(1);
    }
    @Test void twoNamedPeopleCanSignIndependentlyAndMeetTheRecordedThreshold()throws Exception{
        var second=UUID.randomUUID();o.f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Second named signatory','ACTIVE','MEMBER',true,'test-only',now())",second,o.f.institution,second.toString());
        var withdrawal=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();withdrawal.setExpectedVersion(o.f.jdbc.queryForObject("select version from lending_group_cycle where id=?",Integer.class,cycle));withdrawal.setReason("Synthetic replacement for two-signatory authority");o.cycles.withdraw(o.f.actor,cycle,withdrawal);
        var next=o.authorityForm();next.setRequiredSignatures(2);var named=new com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.SignerForm();named.setClientId(second);named.setMaySign(true);next.getSigners().add(named);o.authority=o.authorities.record(o.f.actor,o.group,next);
        var request=o.form();var otherCycle=o.create(request);o.service.submit(o.f.actor,otherCycle,request);var otherSigner=client(second);var otherHash=service.packet(signer,otherCycle,0).hash();
        try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var jobs=List.of(pool.submit(()->{gate.await();return service.sign(signer,otherCycle,otherHash,true,"");}),pool.submit(()->{gate.await();return service.sign(otherSigner,otherCycle,otherHash,true,"");}));gate.countDown();for(var job:jobs)assertThat(job.get(20,TimeUnit.SECONDS)).isNull();}
        assertThat(service.packet(signer,otherCycle,0).signedCount()).isEqualTo(2);assertThat(service.packet(otherSigner,otherCycle,0).snapshot().authority().requiredSignatures()).isEqualTo(2);
        assertThat(count("loan_ledgers")).isZero();
    }
    @Test void unlistedAndForeignBranchPeopleCannotReadOrSign(){
        var stranger=UUID.randomUUID();o.f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Unlisted person','ACTIVE','MEMBER',true,'test-only',now())",stranger,o.f.institution,stranger.toString());
        var other=client(stranger);assertThatThrownBy(()->service.packet(other,cycle,0)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.sign(other,cycle,hash,true,"")).isInstanceOf(AccessDeniedException.class);
        var person=o.f.context.getBean(MemberDirectoryService.class).find(o.f.client).orElseThrow();person.setStationId("B2");var foreign=new AppUserPrincipal(person,EnumSet.allOf(UserClaim.class),false);
        assertThatThrownBy(()->service.packet(foreign,cycle,0)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.sign(foreign,cycle,hash,true,"")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void transportRunsOutsideTransactionsAndTheDeliveredCodeCanSign(){
        otp();var transport=o.f.context.getBean(StationOtpDeliveryService.class);var captured=new java.util.concurrent.atomic.AtomicReference<String>();
        when(transport.deliver(anyString(),anyString(),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),anyString(),eq(10),any(),any(),any())).thenAnswer(i->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();captured.set(i.getArgument(7));return new StationOtpDeliveryService.DeliveryReceipt(OtpDeliveryChannel.EMAIL,"Synthetic delivery only");});
        new org.springframework.transaction.support.TransactionTemplate(o.f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->o.f.context.getBean(GroupOrganizationAgreementDeliveryService.class).send(signer,cycle,hash,Locale.ENGLISH));
        verify(transport).deliver(eq(o.f.institution),eq("B1"),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),eq(captured.get()),eq(10),eq(loan),isNull(),eq(o.f.client));
        assertThat(service.sign(signer,cycle,hash,true,captured.get())).isNull();
    }
    @Test void failedTransportLeavesAnUndeliveredChallengeThatCannotSign(){
        otp();var transport=o.f.context.getBean(StationOtpDeliveryService.class);
        when(transport.deliver(anyString(),anyString(),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),anyString(),eq(10),any(),any(),any())).thenThrow(new IllegalStateException("Synthetic transport failure"));
        assertThatThrownBy(()->o.f.context.getBean(GroupOrganizationAgreementDeliveryService.class).send(signer,cycle,hash,Locale.ENGLISH)).hasMessage("group.error.otpDelivery");
        assertThat(o.f.jdbc.queryForObject("select delivered_at is null and consumed_at is null and attempts=0 from lending_group_org_challenge where cycle_id=?",Boolean.class,cycle)).isTrue();
        assertThat(service.sign(signer,cycle,hash,true,"123456")).isEqualTo("group.error.otpExpired");assertThat(count("lending_group_org_signature")).isZero();
    }
    @Test void withdrawalRetainsTheUnsignedDocumentAndClosesSigning(){
        var withdrawal=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();withdrawal.setExpectedVersion(o.f.jdbc.queryForObject("select version from lending_group_cycle where id=?",Integer.class,cycle));withdrawal.setReason("Synthetic unsigned company withdrawal");o.cycles.withdraw(o.f.actor,cycle,withdrawal);
        assertThat(service.packet(signer,cycle,0).hash()).isEqualTo(hash);assertThat(service.packet(signer,cycle,0).canSign()).isFalse();assertThat(service.sign(signer,cycle,hash,true,"")).isEqualTo("group.error.organizationSigningClosed");assertThat(count("lending_group_org_signature")).isZero();
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupOrganizationPostgresTest.Config.class) static class Config {
        @Bean GroupOrganizationAgreementRepository agreements(JdbcTemplate j,ObjectMapper json){return new GroupOrganizationAgreementRepository(j,json);}
        @Bean GroupOrganizationAgreementService signing(GroupOrganizationAgreementRepository r,MemberDirectoryService m,UserClaimService c,SaccoRegistryService i,ApplicationClock clock,AuditService a,StationOtpSettingsService otp,org.springframework.mock.env.MockEnvironment env){return new GroupOrganizationAgreementService(r,m,new AccessControlService(),c,i,clock,a,otp,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4),env);}
        @Bean GroupOrganizationAgreementDeliveryService organizationDelivery(GroupOrganizationAgreementService s,StationOtpDeliveryService d){var m=new org.springframework.context.support.ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return new GroupOrganizationAgreementDeliveryService(s,d,m);}
    }
}
