package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.AdminOfficeWorkRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminOfficeWorkService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficeAdminPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    AdminOfficeWorkService service;
    org.springframework.jdbc.core.JdbcTemplate measured;
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.fixture();measured=spy(f.jdbc);service=new AdminOfficeWorkService(new AdminOfficeWorkRepository(measured),f.context.getBean(GeneralLedgerService.class));}
    @Test void staffInvitationsArePagedBeforeReturningAndExcludeClientsAndOtherBranches() {
        String prefix="I"+UUID.randomUUID().toString().substring(0,6)+"-";
        for(int n=0;n<27;n++) {
            var staff=f.operator("B1");invite(staff,prefix+String.format("%03d",n));
        }
        invite(f.operator("B2"),prefix+"999");
        f.jdbc.update("update members set status='INVITED' where id=?",f.client);
        clearInvocations(measured);
        var first=service.read(f.actor,true,"","number",0);
        assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();
        assertThat(first.rows()).allMatch(t->t.number().startsWith(prefix));
        assertThat(mockingDetails(measured).getInvocations().stream().filter(i->i.getMethod().getName().equals("query")
            && i.getMethod().getParameterCount()==3 && i.getMethod().getParameterTypes()[1]==org.springframework.jdbc.core.RowMapper.class).count()).isEqualTo(1);
        assertThat(service.read(f.actor,true,"","number",1).rows()).hasSize(2);
        assertThat(service.read(f.actor,true,prefix+"005","number",0).rows()).hasSize(1);
        assertThat(service.read(f.actor,true,"","number_desc",0).rows().getFirst().number()).isEqualTo(prefix+"026");
    }
    private void invite(AppUserPrincipal staff,String number) {
        f.jdbc.update("update members set status='INVITED',staff_access_status='PENDING_ACKNOWLEDGEMENT',staff_access_assigned_at=now(),staff_no=? where id=?",number,staff.getMemberId());
    }
    @Test void supportQueuesOmitUnscopedAndOtherBranchReports() {
        var local=f.operator("B1");var foreign=f.operator("B2");
        incident(local.getMemberId(),"Current branch");incident(foreign.getMemberId(),"Other branch");incident(null,"Unscoped report");
        assertThat(service.read(f.actor,false,"","number",0).rows()).extracting(com.sacco.mvp.staffoffice.dto.OfficeWork.Task::name).containsExactly("Current branch");
        assertThat(service.read(f.actor,false,"Other","number",0).rows()).isEmpty();
        var limited=new AppUserPrincipal(f.context.getBean(com.sacco.mvp.service.MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow(),Set.of(UserClaim.LOAN_RECORDING_VIEW));
        assertThatThrownBy(()->service.read(limited,false,"","number",0)).isInstanceOf(AccessDeniedException.class);
    }
    private void incident(UUID reporter,String subject) {
        f.jdbc.update("insert into admin_incidents(id,sacco_id,reported_by_member_id,category,source,subject,message,severity,status,created_at,updated_at) values(?,?,?,'SUPPORT_MESSAGE','Synthetic office test',?,'Synthetic evidence','LOW','OPEN',now(),now())",UUID.randomUUID(),f.institution,reporter,subject);
    }
}
