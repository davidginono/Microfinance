package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
    AppUserPrincipal signer;UUID cycle,loan;String hash;
    @BeforeAll void start(){start(Config.class);}
    void start(Class<?> configuration){o.f.context=new AnnotationConfigApplicationContext(configuration);o.f.jdbc=o.f.context.getBean(JdbcTemplate.class);o.f.service=o.f.context.getBean(com.sacco.mvp.accounting.loans.service.LoanRecordingService.class);
        o.service=o.f.context.getBean(GroupOrganizationService.class);o.authorities=o.f.context.getBean(GroupAuthorityService.class);o.cycles=o.f.context.getBean(GroupCycleService.class);o.groups=o.f.context.getBean(GroupLendingService.class);}
    @AfterAll void stop(){o.stop();}
    @BeforeEach void fixture(){o.fixture();reset(o.f.context.getBean(StationOtpSettingsService.class));reset(o.f.context.getBean(StationOtpDeliveryService.class));
        o.f.context.getBean(org.springframework.mock.env.MockEnvironment.class).setProperty("app.auth.otp.ttl-minutes","10");
        var form=o.form();cycle=o.create(form);loan=o.service.submit(o.f.actor,cycle,form);signer=client(o.f.client);
        hash=packetHash(cycle);}

    AppUserPrincipal client(UUID id){var member=Member.builder().id(id).saccoId(o.f.institution).stationId("B1").memberNo(id.toString()).fullName("Synthetic named signatory").status(MemberStatus.ACTIVE).position(Position.MEMBER).memberAccount(true).signatureText("Synthetic representative signature").email("signer@example.invalid").build();
        o.f.jdbc.update("update members set signature_text=?,signature_registered_at=now() where id=?",member.getSignatureText(),id);
        when(o.f.context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(member));return com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(member,EnumSet.allOf(UserClaim.class));}
    int count(String table){return o.count(table);}

    String packetHash(UUID id){return o.f.jdbc.queryForObject("select packet_hash from lending_group_org_agreement where cycle_id=?",String.class,id);}
    void sign(AppUserPrincipal person,UUID id){GroupPaperFixture.record(o.f,id,"ORG_AGREEMENT",person.getMemberId());}
    @Test void staffRecordsPaperWithoutAClientLoginOrPostingMoney(){
        o.f.jdbc.update("update members set signature_text=null,signature_registered_at=null where id=?",signer.getMemberId());
        sign(signer,cycle);
        assertThat(count("lending_group_org_signature")).isEqualTo(1);
        assertThat(count("lending_group_signed_paper")).isEqualTo(1);
        assertThat(count("loan_ledgers")).isZero();
        assertThat(o.f.jdbc.queryForObject("select recorded_by from lending_group_signed_paper where cycle_id=?",UUID.class,cycle)).isEqualTo(o.f.actor.getMemberId());
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupOrganizationPostgresTest.Config.class) static class Config {
        @Bean GroupOrganizationAgreementRepository agreements(JdbcTemplate j,ObjectMapper json){return new GroupOrganizationAgreementRepository(j,json);}

        @Bean org.springframework.context.support.ResourceBundleMessageSource messageSource(){var m=new org.springframework.context.support.ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean ReportExportLimiter exportLimiter(){return new ReportExportLimiter(1);}
        @Bean GroupOrganizationAgreementPdf pdf(org.springframework.context.MessageSource messages,ReportExportLimiter limiter){return new GroupOrganizationAgreementPdf(messages);}
        @Bean com.sacco.mvp.security.AuthzService authz(com.sacco.mvp.repository.LoanApplicationRepository l,com.sacco.mvp.repository.GuarantorRequestRepository g,com.sacco.mvp.repository.BoardReviewRepository b){return new com.sacco.mvp.security.AuthzService(l,new AccessControlService());}
        @Bean GroupOrganizationAgreementExportService exports(GroupOrganizationAgreementRepository r,GroupOrganizationDocumentRepository d,GroupOrganizationAgreementPdf pdf,com.sacco.mvp.accounting.service.GeneralLedgerService a,com.sacco.mvp.security.AuthzService authz,org.springframework.transaction.PlatformTransactionManager tx,ApplicationClock clock,AuditService audit,ReportExportLimiter limiter){return new GroupOrganizationAgreementExportService(r,d,pdf,a,authz,tx,clock,audit,limiter);}
    }
}
