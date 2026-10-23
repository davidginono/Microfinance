package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.GroupLendingRepository;
import com.sacco.mvp.grouplending.service.GroupLendingService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reuses the existing guarded, disposable accounting fixture; never reads operational credentials. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupLendingPostgresTest {
    final LoanRecordingPostgresTest fixture=new LoanRecordingPostgresTest();
    GroupLendingService groups;
    @BeforeAll void start(){fixture.context=new AnnotationConfigApplicationContext(Config.class);fixture.jdbc=fixture.context.getBean(JdbcTemplate.class);groups=fixture.context.getBean(GroupLendingService.class);}
    @AfterAll void stop(){fixture.stop();}
    @BeforeEach void setup(){fixture.fixture();}
    GroupForm form(){var f=new GroupForm();f.setName("Synthetic trading group");f.setEvidence("Synthetic identity documents");return f;}
    MemberForm member(){var f=new MemberForm();f.setClientId(fixture.client);f.setStartsOn(LoanRecordingPostgresTest.TODAY);f.setEvidence("Synthetic appointment consent");return f;}
    @Test void registryIsScopedIdempotentAndCreatesNoFinancialRecords(){
        var f=form();UUID group=groups.create(fixture.actor,f);assertThat(groups.create(fixture.actor,f)).isEqualTo(group);
        assertThat(groups.list(fixture.actor,"trading","name",0).rows()).extracting(Group::id).containsExactly(group);
        assertThat(groups.list(fixture.actor,"missing","name",0).rows()).isEmpty();
        assertThatThrownBy(()->groups.detail(fixture.operator("B2"),group)).isInstanceOf(AccessDeniedException.class);
        f.setName("Changed payload");assertThatThrownBy(()->groups.create(fixture.actor,f)).hasMessage("group.error.retry");
        assertThat(fixture.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.institution)).isZero();
        assertThat(fixture.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,fixture.institution)).isZero();
    }
    @Test void membershipRetainsHistoryAndCannotChangeIdentityOrOverlap(){
        UUID group=groups.create(fixture.actor,form());var f=member();f.setOffice("CHAIRPERSON");UUID membership=groups.addMember(fixture.actor,group,f);
        assertThat(groups.addMember(fixture.actor,group,f)).isEqualTo(membership);assertThat(groups.detail(fixture.actor,group).members()).isEqualTo(1);
        assertThatThrownBy(()->groups.addMember(fixture.actor,group,member())).hasMessage("group.error.membership");
        assertThatThrownBy(()->fixture.jdbc.update("update lending_group_member set office='TREASURER' where id=?",membership)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        groups.endMember(fixture.actor,group,membership,LoanRecordingPostgresTest.TODAY,"Synthetic resignation");
        assertThat(groups.detail(fixture.actor,group).members()).isZero();assertThat(groups.members(fixture.actor,group,0,"startsOn_desc").rows()).singleElement().satisfies(r->assertThat(r.endReason()).isEqualTo("Synthetic resignation"));
        assertThatThrownBy(()->fixture.jdbc.update("delete from lending_group_member where id=?",membership)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->groups.addMember(fixture.actor,group,member())).hasMessage("group.error.membership");
    }
    @Test void currentClaimsAndActiveGroupAreRequired(){
        UUID group=groups.create(fixture.actor,form());var member=fixture.context.getBean(MemberDirectoryService.class).find(fixture.actor.getMemberId()).orElseThrow();
        var permissions=EnumSet.allOf(UserClaim.class);permissions.remove(UserClaim.GROUP_LENDING_UPDATE);
        var denied=new AppUserPrincipal(member,permissions,true);
        assertThatThrownBy(()->groups.addMember(denied,group,member())).isInstanceOf(AccessDeniedException.class);
        when(fixture.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(permissions);
        assertThatThrownBy(()->groups.addMember(fixture.actor,group,member())).isInstanceOf(AccessDeniedException.class);
        when(fixture.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        groups.status(fixture.actor,group,0,false);
        assertThatThrownBy(()->groups.addMember(fixture.actor,group,member())).hasMessage("group.error.inactive");
        assertThatThrownBy(()->groups.status(fixture.actor,group,0,true)).hasMessage("group.error.changed");
        groups.status(fixture.actor,group,1,true);assertThat(groups.detail(fixture.actor,group).status()).isEqualTo("ACTIVE");
    }
    @Test void auditFailureRollsBackGroupAndMembership(){
        var audit=fixture.context.getBean(AuditService.class);UUID group=groups.create(fixture.actor,form());
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_MEMBER_ADDED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->groups.addMember(fixture.actor,group,member())).isInstanceOf(IllegalStateException.class);
        assertThat(groups.detail(fixture.actor,group).members()).isZero();
    }
    @Test void concurrentIdenticalMembershipCommitsOnlyOnce() throws Exception {
        UUID group=groups.create(fixture.actor,form());var f=member();
        try(var executor=Executors.newFixedThreadPool(2)){
            var results=executor.invokeAll(List.<Callable<UUID>>of(()->groups.addMember(fixture.actor,group,f),()->groups.addMember(fixture.actor,group,f)));
            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
        }
        assertThat(groups.detail(fixture.actor,group).members()).isEqualTo(1);
    }
    @Test void membershipRejectsForeignBranchClientsAndDatabaseScopeBypasses(){
        UUID group=groups.create(fixture.actor,form());UUID foreign=UUID.randomUUID();
        fixture.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B2',?,'Foreign branch client','ACTIVE','MEMBER',true,'test-only',now())",foreign,fixture.institution,foreign.toString());
        var f=member();f.setClientId(foreign);assertThatThrownBy(()->groups.addMember(fixture.actor,group,f)).hasMessage("group.error.client");
        assertThatThrownBy(()->fixture.jdbc.update("""
            insert into lending_group_member(id,group_id,sacco_id,station_id,client_id,office,starts_on,evidence,created_by,created_at,request_key,payload_hash)
            values(?,?,?,'B1',?,'MEMBER',?,'Synthetic evidence',?,now(),?,'invalid-direct-write')
            """,UUID.randomUUID(),group,fixture.institution,foreign,LoanRecordingPostgresTest.TODAY,fixture.actor.getMemberId(),UUID.randomUUID()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(groups.detail(fixture.actor,group).members()).isZero();
    }
    @Configuration(proxyBeanMethods=false) @Import(LoanRecordingPostgresTest.Config.class)
    static class Config {
        @Bean GroupLendingRepository groupRepository(JdbcTemplate jdbc){return new GroupLendingRepository(jdbc);}
        @Bean GroupLendingService groupService(GroupLendingRepository repo,GeneralLedgerService access,AuditService audit,ApplicationClock clock,ObjectMapper json){return new GroupLendingService(repo,access,audit,clock,json);}
    }
}
