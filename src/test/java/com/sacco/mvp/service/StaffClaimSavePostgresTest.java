package com.sacco.mvp.service;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleForm;
import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.sacco.mvp.domain.UserClaim.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MICROFINANCE_STAFF_CLAIMS_TEST_URL",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_staff_claims_test")
@SpringBootTest(properties = {
    "spring.datasource.url=${MICROFINANCE_STAFF_CLAIMS_TEST_URL}",
    "spring.datasource.username=matrix_test", "spring.datasource.password=",
    "app.outbox.scheduler-enabled=false", "app.retention.cleanup-enabled=false"
})
class StaffClaimSavePostgresTest {
    @Autowired AdminService admin;
    @Autowired UserClaimService claims;
    @Autowired VoucherWorkflowService vouchers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;

    String institution;
    AppUserPrincipal editor;
    UUID officer;

    @BeforeEach void createStaff() {
        institution = "MATRIX-" + UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic institution',true,now(),now())", institution);
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,'B1',true,'ACTIVE',now(),now())",
            UUID.randomUUID(), institution);
        UUID editorId = staff(Position.ICT_MANAGER);
        Set<UserClaim> editorClaims = Set.of(USER_ACCESS_UPDATE, VOUCHER_RULE_VIEW, VOUCHER_RULE_CONFIGURE);
        claims.updateClaims(editorId, List.copyOf(editorClaims));
        editor = new AppUserPrincipal(members.findById(editorId).orElseThrow(), editorClaims);
        officer = staff(Position.LOAN_OFFICER);
    }

    private UUID staff(Position group) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into members(id,sacco_id,station_id,member_no,staff_no,full_name,status,position,is_member,
                                staff_access_status,password_hash,created_at)
            values(?,?,'B1',?,?,'Synthetic staff','ACTIVE',?,false,'ACTIVE','test-only',now())
            """, id, institution, id.toString(), "S-" + id.toString().substring(0,8), group.name());
        return id;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void separateAccessMatrixSavesPreserveCheckedPermissionsAndHonorRevocations(boolean journalFirst) {
        var initial = journalFirst ? List.of(ACCOUNTING_JOURNALS_VIEW) : List.of(VOUCHER_WORKSPACE_VIEW, VOUCHER_APPROVAL_APPROVE);
        var required = List.of(VOUCHER_WORKSPACE_VIEW, VOUCHER_APPROVAL_APPROVE, ACCOUNTING_JOURNALS_VIEW);
        save(initial);
        save(required);
        var rule = new RuleForm();
        rule.getRules().getFirst().getStages().getFirst().setRole(Position.LOAN_OFFICER);
        vouchers.configure(editor, rule);
        save(required);
        save(List.of(VOUCHER_WORKSPACE_VIEW, ACCOUNTING_JOURNALS_VIEW));
        save(List.of());
    }

    private void save(List<UserClaim> selected) {
        admin.updateBranchUser(editor, officer, "B1", "B1", List.of(Position.LOAN_OFFICER), MemberStatus.ACTIVE, selected);
        assertThat(claims.effectiveClaims(officer, List.of(Position.LOAN_OFFICER), false))
            .containsExactlyInAnyOrderElementsOf(selected);
    }
}
