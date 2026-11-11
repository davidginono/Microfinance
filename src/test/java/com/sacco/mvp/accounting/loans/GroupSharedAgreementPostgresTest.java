package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
    @BeforeEach void setup(){s.setup();s.g.f.context.getBean(SaccoRegistryService.class).findActiveSacco(s.g.f.institution).orElseThrow().setSaccoName("Synthetic loan institution");cycle=s.submit();client=s.g.client(s.g.f.client);GroupPaperFixture.record(s.g.f,cycle,"TERMS",s.g.f.client);GroupPaperFixture.record(s.g.f,cycle,"TERMS",s.g.second);
        for(var id:List.of(s.g.f.client,s.g.second))s.g.f.jdbc.update("insert into member_payment_details(member_id,destination_type,provider,account_holder_name,account_identifier,created_at,updated_at) values(?,'BANK_ACCOUNT','Synthetic bank','Synthetic account holder',?,now(),now())",id,"QA-"+id);
    }
    DocumentForm form(){var d=new GroupOrganizationPostgresTest().documentForm();d.setDocumentReference("Synthetic approved shared agreement");d.setDocumentLanguage("BOTH");return d;}
    UUID document(){return agreements.record(s.g.f.actor,cycle,form(),GroupOrganizationPostgresTest.documentFile());}
    String open(){document();return "";}
    int count(String table){return s.g.f.jdbc.queryForObject("select count(*) from "+table+" where cycle_id=?",Integer.class,cycle);}

    @Test void sameDocumentRetryIsIdempotentAndAReplacementCannotOverwriteIt(){
        var form=form();var file=GroupOrganizationPostgresTest.documentFile();UUID id=agreements.record(s.g.f.actor,cycle,form,file);assertThat(agreements.record(s.g.f.actor,cycle,form,file)).isEqualTo(id);
        assertThatThrownBy(()->agreements.record(s.g.f.actor,cycle,form(),file)).hasMessage("group.error.changed");assertThat(count("lending_group_shared_document")).isEqualTo(1);
    }

    @Configuration(proxyBeanMethods=false) @Import(GroupCyclePostgresTest.Config.class) static class Config {
        @Bean GroupSharedAgreementRepository sharedAgreements(JdbcTemplate jdbc,ObjectMapper json){return new GroupSharedAgreementRepository(jdbc,json);}
        @Bean org.springframework.context.MessageSource messageSource(){var m=new org.springframework.context.support.ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean GroupSharedAgreementService sharedAgreementService(GroupSharedAgreementRepository r,GroupCycleRepository c,GeneralLedgerService access,SaccoRegistryService institutions,ApplicationClock clock,AuditService audit,ObjectMapper json,PlatformTransactionManager tx,StationOtpSettingsService otp,org.springframework.mock.env.MockEnvironment env){return new GroupSharedAgreementService(r,c,access,institutions,clock,audit,json,tx);}

    }
}
