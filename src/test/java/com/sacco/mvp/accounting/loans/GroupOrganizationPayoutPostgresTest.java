package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
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
    @BeforeAll void start(){s.start(Config.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void fixture(){s.fixture();}
    void ready(){s.sign(s.signer,s.cycle);o.f.approve(s.loan);}

    void withdraw(){var f=new TerminationForm();f.setExpectedVersion(o.cycles.detail(o.f.actor,s.cycle).version());f.setReason("Synthetic payout instruction withdrawal");o.cycles.withdraw(o.f.actor,s.cycle,f);}
    AppUserPrincipal person(){var id=UUID.randomUUID();o.f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Synthetic receiver','ACTIVE','MEMBER',true,'test-only',now())",id,o.f.institution,id.toString());return s.client(id);}

    void confirm(){GroupPaperFixture.record(o.f,s.cycle,"ORG_PAYOUT",s.signer.getMemberId());}
    @Test void paperPayoutKeepsItsNamedReceiverAndCreatesNoMoney(){ready();confirm();assertThat(o.count("lending_group_org_payout")).isEqualTo(1);assertThat(o.count("loan_ledgers")).isZero();}
    @Configuration(proxyBeanMethods=false) @Import(GroupOrganizationAgreementPostgresTest.Config.class) static class Config {

    }
}
