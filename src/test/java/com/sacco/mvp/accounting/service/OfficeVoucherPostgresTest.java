package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.StepRow;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_VOUCHER_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_vouchers_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficeVoucherPostgresTest {
    final VoucherPostgresTest f=new VoucherPostgresTest();
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){if(f.dataSource!=null)f.dataSource.close();}
    @BeforeEach void fixture(){f.fixture();}
    @Test void queueMatchesTheFrozenStageAndIndependentPersonCount() {
        var first=f.approver(Position.MANAGER);var second=f.approver(Position.MANAGER);var accountant=f.approver(Position.ACCOUNTANT);
        var rule=f.rule("SINGLE",false,Position.MANAGER,2);var next=new StepRow();next.setRole(Position.ACCOUNTANT);rule.getStages().add(next);f.configure(rule);
        UUID id=f.workflows.capture(f.actor,Type.JOURNAL,f.form(Type.JOURNAL));
        assertThat(f.workflows.officeWork(first,StaffOffice.GENERAL_MANAGER,true,"","number",0).rows()).hasSize(1);
        assertThat(f.workflows.officeWork(accountant,StaffOffice.CHIEF_ACCOUNTANT,true,"","number",0).rows()).isEmpty();
        assertThat(f.workflows.officeWork(f.actor,StaffOffice.CHIEF_ACCOUNTANT,true,"","number",0).rows()).isEmpty();
        f.command(first,id,"APPROVE");
        assertThat(f.workflows.officeWork(first,StaffOffice.GENERAL_MANAGER,true,"","number",0).rows()).isEmpty();
        assertThat(f.workflows.officeWork(second,StaffOffice.GENERAL_MANAGER,true,"","number",0).rows()).hasSize(1);
        f.command(second,id,"APPROVE");
        assertThat(f.workflows.officeWork(accountant,StaffOffice.CHIEF_ACCOUNTANT,true,"","number",0).rows()).hasSize(1);
        f.command(accountant,id,"APPROVE");
        assertThat(f.workflows.officeWork(accountant,StaffOffice.CHIEF_ACCOUNTANT,true,"","number",0).rows()).isEmpty();
        assertThat(f.workflows.officeWork(f.actor,StaffOffice.CHIEF_ACCOUNTANT,false,"","number",0).rows()).hasSize(1);
        assertThat(f.workflows.officeWork(f.operator("B2"),StaffOffice.CHIEF_ACCOUNTANT,false,"","number",0).rows()).isEmpty();
    }
    @Test void aRevokedDecisionClaimCannotLeaveAnActionableVoucherInTheQueue() {
        var reviewer=f.approver(Position.MANAGER);f.configure(f.rule("SINGLE",false,Position.MANAGER,1));
        f.workflows.capture(f.actor,Type.JOURNAL,f.form(Type.JOURNAL));
        var retained=EnumSet.allOf(UserClaim.class);retained.remove(UserClaim.VOUCHER_APPROVAL_APPROVE);
        when(f.claims.effectiveClaims(eq(reviewer.getMemberId()),anyCollection(),anyBoolean())).thenReturn(retained);
        assertThat(f.workflows.officeWork(reviewer,StaffOffice.GENERAL_MANAGER,true,"","number",0).rows()).isEmpty();
    }
}
