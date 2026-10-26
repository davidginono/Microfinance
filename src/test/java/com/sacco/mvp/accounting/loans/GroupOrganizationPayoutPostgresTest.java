package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.SignerForm;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm;
import com.sacco.mvp.grouplending.repository.GroupOrganizationPayoutRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationPayoutPostgresTest {
    final GroupOrganizationAgreementPostgresTest s=new GroupOrganizationAgreementPostgresTest();
    final GroupOrganizationPostgresTest o=s.o;
    GroupOrganizationPayoutService service;
    @BeforeAll void start(){s.start(Config.class);service=o.f.context.getBean(GroupOrganizationPayoutService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void fixture(){s.fixture();}
    void ready(){assertThat(s.service.sign(s.signer,s.cycle,s.hash,true,"")).isNull();}
    void otp(){s.otp();}
    void withdraw(){var f=new TerminationForm();f.setExpectedVersion(o.cycles.detail(o.f.actor,s.cycle).version());f.setReason("Synthetic payout instruction withdrawal");o.cycles.withdraw(o.f.actor,s.cycle,f);}
    AppUserPrincipal person(){var id=UUID.randomUUID();o.f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Synthetic receiver','ACTIVE','MEMBER',true,'test-only',now())",id,o.f.institution,id.toString());return s.client(id);}
    AppUserPrincipal receiveOnly(){
        var receiver=person();withdraw();var next=o.authorityForm();next.getSigners().getFirst().setMayReceive(false);
        var row=new SignerForm();row.setClientId(receiver.getMemberId());row.setMayReceive(true);next.getSigners().add(row);o.authority=o.authorities.record(o.f.actor,o.group,next);
        var form=o.form();s.cycle=o.create(form);s.loan=o.service.submit(o.f.actor,s.cycle,form);s.hash=s.service.packet(s.signer,s.cycle,0).hash();s.openDocument(s.signer,s.cycle);ready();return receiver;
    }
    @Test void exactInstructionIsSeparateFromAgreementSignaturePersonalDebtAndCash(){
        assertThat(service.packet(s.signer,s.cycle).canConfirm()).isFalse();assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.payoutClosed");
        ready();var packet=service.packet(s.signer,s.cycle);assertThat(packet.number()).isNotBlank().isEqualTo(o.f.service.detail(o.f.actor,s.loan).loan().number());assertThat(packet.canConfirm()).isTrue();assertThat(packet.destination().accountIdentifier()).isEqualTo("QA-ACCOUNT");
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isNull();assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"bad")).isNull();
        var confirmed=service.packet(s.signer,s.cycle);assertThat(confirmed.confirmation().client()).isEqualTo(o.f.client);assertThat(confirmed.net()).isEqualByComparingTo("1000.00");assertThat(confirmed.canConfirm()).isFalse();
        assertThat(o.count("lending_group_org_payout")).isEqualTo(1);assertThat(o.count("lending_group_org_signature")).isEqualTo(1);
        assertThat(o.f.jdbc.queryForObject("select applicant_member_id from loan_applications where id=?",UUID.class,s.loan)).isNull();
        assertThat(o.count("loan_ledgers")).isZero();assertThat(o.count("gl_journal")).isZero();assertThat(o.count("lending_group_guarantee")).isZero();
        assertThatThrownBy(()->o.f.service.disburse(o.f.actor,o.f.post(s.loan,"1000.00","PAYOUT-IS-NOT-FUNDING"))).hasMessage("recording.error.organizationFundingPending");
    }
    @Test void payoutOnlyAuthorityDoesNotGrantAgreementSigningOrPersonalLoanAccess(){
        var receiver=receiveOnly();assertThatThrownBy(()->service.packet(s.signer,s.cycle)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->s.service.packet(receiver,s.cycle,0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->s.service.sign(receiver,s.cycle,s.hash,true,"")).isInstanceOf(AccessDeniedException.class);
        assertThat(service.packet(receiver,s.cycle).canConfirm()).isTrue();assertThat(service.confirm(receiver,s.cycle,s.hash,true,"")).isNull();
        assertThat(o.f.context.getBean(GroupConsentService.class).list(receiver,0).rows()).anySatisfy(r->{assertThat(r.id()).isEqualTo(s.cycle);assertThat(r.payoutOnly()).isTrue();assertThat(r.accepted()).isTrue();});
        assertThat(o.f.context.getBean(GroupConsentService.class).list(s.signer,0).rows()).anySatisfy(r->{assertThat(r.id()).isEqualTo(s.cycle);assertThat(r.payoutOnly()).isFalse();});
    }
    @Test void currentPersonClaimsBranchAndExplicitAcceptanceAreRequired(){
        ready();assertThatThrownBy(()->service.packet(o.f.actor,s.cycle)).isInstanceOf(AccessDeniedException.class);
        var stranger=person();assertThatThrownBy(()->service.packet(stranger,s.cycle)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.confirm(s.signer,s.cycle,s.hash,false,"")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.confirm(s.signer,s.cycle,"0".repeat(64),true,"")).hasMessage("group.error.changed");
        var member=o.f.context.getBean(MemberDirectoryService.class).find(o.f.client).orElseThrow();
        var limited=new AppUserPrincipal(member,Set.of(UserClaim.MEMBER_LOANS_VIEW),false);assertThatThrownBy(()->service.confirm(limited,s.cycle,s.hash,true,"")).isInstanceOf(AccessDeniedException.class);
        member.setStationId("B2");var foreign=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),false);assertThatThrownBy(()->service.packet(foreign,s.cycle)).isInstanceOf(AccessDeniedException.class);
        member.setStationId("B1");when(o.f.context.getBean(UserClaimService.class).effectiveClaims(any(),anySet(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.packet(s.signer,s.cycle)).isInstanceOf(AccessDeniedException.class);
        assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Test void aCodeCannotBeIssuedBeforeEveryRequiredSignature(){
        otp();assertThatThrownBy(()->service.prepareCode(s.signer,s.cycle,s.hash)).hasMessage("group.error.payoutClosed");assertThat(o.count("lending_group_org_payout_challenge")).isZero();
    }
    @Test void deliveredPayoutCodeIsDistinctFromSigningAndFailedAttemptsCommit(){
        ready();otp();assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.otpRequired");
        var code=service.prepareCode(s.signer,s.cycle,s.hash);assertThat(service.confirm(s.signer,s.cycle,s.hash,true,code.code())).isEqualTo("group.error.otpExpired");service.codeDelivered(s.signer,s.cycle,code.id());
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"bad")).isEqualTo("group.error.otpInvalid");assertThat(service.confirm(s.signer,s.cycle,s.hash,true,code.code())).isNull();
        assertThat(o.f.jdbc.queryForObject("select p.authorized_at=c.consumed_at and c.attempts=2 and p.otp_required from lending_group_org_payout p join lending_group_org_payout_challenge c on c.id=p.challenge_id where p.cycle_id=?",Boolean.class,s.cycle)).isTrue();
        assertThatThrownBy(()->o.f.jdbc.update("delete from lending_group_org_payout_challenge where id=?",code.id())).isInstanceOf(DataAccessException.class);
    }
    @Test void consumingAContractSigningCodeDoesNotAuthorizeThePayout(){
        otp();var code=s.service.prepareCode(s.signer,s.cycle,s.hash);s.service.codeDelivered(s.signer,s.cycle,code.id());assertThat(s.service.sign(s.signer,s.cycle,s.hash,true,code.code())).isNull();
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,code.code())).isEqualTo("group.error.otpRequired");assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Test void aPayoutInstructionUsesFrozenDeductionsRatherThanTheGrossPrincipal(){
        withdraw();o.f.chargePolicy("DEDUCT");o.authority=o.authorities.record(o.f.actor,o.group,o.authorityForm());var form=o.form();form.getOrganization().setInsurancePremium(new java.math.BigDecimal("10.00"));form.getOrganization().setInsuranceEvidence("Synthetic verified insurer premium");
        s.cycle=o.create(form);s.loan=o.service.submit(o.f.actor,s.cycle,form);s.hash=s.service.packet(s.signer,s.cycle,0).hash();s.openDocument(s.signer,s.cycle);ready();
        var p=service.packet(s.signer,s.cycle);assertThat(p.principal()).isEqualByComparingTo("1000.00");assertThat(p.deductions()).isEqualByComparingTo("30.00");assertThat(p.net()).isEqualByComparingTo("970.00");
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isNull();assertThat(o.f.jdbc.queryForObject("select net_amount from lending_group_org_payout where cycle_id=?",java.math.BigDecimal.class,s.cycle)).isEqualByComparingTo("970.00");assertThat(o.count("loan_charge_event")).isZero();
    }
    @Test void expiredAndExhaustedCodesCannotConfirmOrResetTheirAttempts(){
        ready();otp();var code=service.prepareCode(s.signer,s.cycle,s.hash);service.codeDelivered(s.signer,s.cycle,code.id());
        assertThatThrownBy(()->service.prepareCode(s.signer,s.cycle,s.hash)).hasMessage("group.error.otpWait");
        for(int i=0;i<5;i++)assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"bad")).isEqualTo("group.error.otpInvalid");
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,code.code())).isEqualTo("group.error.otpExpired");
        assertThatThrownBy(()->o.f.jdbc.update("update lending_group_org_payout_challenge set attempts=0 where id=?",code.id())).isInstanceOf(DataAccessException.class);assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Test void expiredCodeCannotBeConsumed(){
        ready();otp();var code=service.prepareCode(s.signer,s.cycle,s.hash);service.codeDelivered(s.signer,s.cycle,code.id());
        when(o.f.context.getBean(ApplicationClock.class).now()).thenReturn(OffsetDateTime.parse("2026-10-08T12:11:00+03:00"));
        assertThat(service.confirm(s.signer,s.cycle,s.hash,true,code.code())).isEqualTo("group.error.otpExpired");assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Test void auditFailureRollsBackInstructionAndCodeConsumption(){
        ready();otp();var code=service.prepareCode(s.signer,s.cycle,s.hash);service.codeDelivered(s.signer,s.cycle,code.id());
        doThrow(new IllegalStateException("Synthetic payout audit failure")).when(o.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("ORGANIZATION_PAYOUT_AUTHORIZED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.confirm(s.signer,s.cycle,s.hash,true,code.code())).hasMessage("Synthetic payout audit failure");assertThat(o.count("lending_group_org_payout")).isZero();
        assertThat(o.f.jdbc.queryForObject("select consumed_at is null and attempts=0 from lending_group_org_payout_challenge where id=?",Boolean.class,code.id())).isTrue();
    }
    @Test void simultaneousRetriesRecordOneInstructionAndOneAudit()throws Exception{
        ready();try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var one=pool.submit(()->{gate.await();return service.confirm(s.signer,s.cycle,s.hash,true,"");});var two=pool.submit(()->{gate.await();return service.confirm(s.signer,s.cycle,s.hash,true,"");});gate.countDown();assertThat(one.get(30,TimeUnit.SECONDS)).isNull();assertThat(two.get(30,TimeUnit.SECONDS)).isNull();}
        assertThat(o.count("lending_group_org_payout")).isEqualTo(1);verify(o.f.context.getBean(AuditService.class),times(1)).logEvent(eq("GROUP_LENDING"),eq(s.cycle),eq("ORGANIZATION_PAYOUT_AUTHORIZED"),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void authorityRevocationClosesConfirmationWithoutErasingTheAgreement(){
        ready();o.authorities.revoke(o.f.actor,o.group,o.authority,"Synthetic payout authority revoked");assertThat(service.packet(s.signer,s.cycle).canConfirm()).isFalse();assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.payoutClosed");assertThat(o.count("lending_group_org_signature")).isEqualTo(1);
    }
    @Test void supersededAuthorityAndInactiveOrganizationCannotConfirm(){
        ready();o.authorities.record(o.f.actor,o.group,o.authorityForm());assertThat(service.packet(s.signer,s.cycle).canConfirm()).isFalse();assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.payoutClosed");
        o.groups.status(o.f.actor,o.group,o.groups.detail(o.f.actor,o.group).version(),false);assertThat(service.packet(s.signer,s.cycle).canConfirm()).isFalse();
    }
    @Test void withdrawalRetainsConfirmedInstructionAndPreventsAnyFunding(){
        ready();service.confirm(s.signer,s.cycle,s.hash,true,"");withdraw();assertThat(service.packet(s.signer,s.cycle).confirmation()).isNotNull();assertThat(service.packet(s.signer,s.cycle).canConfirm()).isFalse();
        for(var sql:List.of("delete from lending_group_org_payout where cycle_id=?","update lending_group_org_payout set net_amount=net_amount+1 where cycle_id=?"))assertThatThrownBy(()->o.f.jdbc.update(sql,s.cycle)).isInstanceOf(DataAccessException.class);
        assertThat(o.count("loan_ledgers")).isZero();assertThat(o.count("gl_journal")).isZero();
    }
    @Test void databaseRejectsAChangedDestinationEvenForANamedReceiver(){
        ready();assertThatThrownBy(()->o.f.jdbc.update("""
            insert into lending_group_org_payout(cycle_id,sacco_id,station_id,client_id,packet_hash,principal,deductions,net_amount,destination,client_name,client_number,signature_text,authorized_at,otp_required)
            select p.cycle_id,p.sacco_id,p.station_id,m.id,p.packet_hash,(p.snapshot->'loan'->>'principal')::numeric,0,(p.snapshot->'loan'->>'principal')::numeric,
              jsonb_set(p.snapshot->'payout','{accountIdentifier}','"FORGED-DESTINATION"'),m.full_name,m.member_no,m.signature_text,p.prepared_at,false
            from lending_group_org_agreement p join members m on m.id=? where p.cycle_id=?
            """,o.f.client,s.cycle)).isInstanceOf(DataAccessException.class).hasMessageContaining("exact frozen destination");assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Test void transportRunsOutsideTransactionsAndAttributesTheActualReceiver(){
        ready();otp();var transport=o.f.context.getBean(StationOtpDeliveryService.class);var captured=new java.util.concurrent.atomic.AtomicReference<String>();
        when(transport.deliver(anyString(),anyString(),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),anyString(),eq(10),any(),any(),any())).thenAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();captured.set(i.getArgument(7));return new StationOtpDeliveryService.DeliveryReceipt(OtpDeliveryChannel.EMAIL,"Synthetic payout delivery");});
        new TransactionTemplate(o.f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->o.f.context.getBean(GroupOrganizationPayoutDeliveryService.class).send(s.signer,s.cycle,s.hash,Locale.ENGLISH));
        verify(transport).deliver(eq(o.f.institution),eq("B1"),any(),any(),eq(EmailOtpPurpose.APPLICANT_SIGNATURE),anyString(),anyString(),eq(captured.get()),eq(10),eq(s.loan),isNull(),eq(o.f.client));assertThat(service.confirm(s.signer,s.cycle,s.hash,true,captured.get())).isNull();
    }
    @Test void failedDeliveryCannotBeMistakenForConfirmedPayout(){
        ready();otp();when(o.f.context.getBean(StationOtpDeliveryService.class).deliver(anyString(),anyString(),any(),any(),any(),anyString(),anyString(),anyString(),anyInt(),any(),any(),any())).thenThrow(new IllegalStateException("Synthetic payout transport failure"));
        assertThatThrownBy(()->o.f.context.getBean(GroupOrganizationPayoutDeliveryService.class).send(s.signer,s.cycle,s.hash,Locale.ENGLISH)).hasMessage("group.error.otpDelivery");assertThat(service.confirm(s.signer,s.cycle,s.hash,true,"123456")).isEqualTo("group.error.otpExpired");assertThat(o.count("lending_group_org_payout")).isZero();
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupOrganizationAgreementPostgresTest.Config.class) static class Config {
        @Bean GroupOrganizationPayoutRepository payoutRepository(JdbcTemplate j,ObjectMapper json){return new GroupOrganizationPayoutRepository(j,json);}
        @Bean GroupOrganizationPayoutService payouts(GroupOrganizationPayoutRepository r,GroupOrganizationAgreementService s,StationOtpSettingsService otp,ApplicationClock clock,AuditService audit,org.springframework.mock.env.MockEnvironment env){return new GroupOrganizationPayoutService(r,s,otp,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4),clock,audit,env);}
        @Bean GroupOrganizationPayoutDeliveryService payoutDelivery(GroupOrganizationPayoutService s,StationOtpDeliveryService d,org.springframework.context.MessageSource m){return new GroupOrganizationPayoutDeliveryService(s,d,m);}
    }
}
