package com.sacco.mvp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserClaimTest {
    @Test void allAdministratorDefaultsIncludeChargeAccountSetup() {
        for(var role:java.util.List.of(Position.ADMIN,Position.MINOR_ADMIN,Position.ICT_MANAGER)) {
            assertThat(UserClaim.defaultClaims(java.util.List.of(role),false)).as(role.name())
                .contains(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE)
                .doesNotContain(UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_APPROVE,UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_REJECT,
                    UserClaim.LOAN_CHARGES_CUSTOM,UserClaim.LOAN_RECORDING_POST);
        }
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.ICT_MANAGER),false))
            .containsAll(UserClaim.lockedClaims(java.util.List.of(Position.ICT_MANAGER)))
            .doesNotContain(UserClaim.INSTITUTION_BRANCH_ADMIN,UserClaim.OTHER_BRANCHES_VIEW);
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.LOAN_OFFICER),false))
            .doesNotContain(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE);
    }
    @Test void groupLendingClaimsRequireExplicitAssignmentAndPermitTheirOwnLanding() {
        var groupClaims=java.util.Arrays.stream(UserClaim.values()).filter(c->c.getFeature()==AccessFeature.GROUP_LENDING).toList();
        assertThat(groupClaims).hasSize(9);
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()),true)).doesNotContainAnyElementsOf(groupClaims);
        assertThat(UserClaim.matrixRows()).anyMatch(row->row.feature()==AccessFeature.GROUP_LENDING
            && row.claimsByAction().size()==groupClaims.stream().filter(UserClaim::isAvailable).count());
        var member=Member.builder().id(java.util.UUID.randomUUID()).saccoId("I").stationId("B").position(Position.ACCOUNTANT)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();
        var actor=new com.sacco.mvp.security.AppUserPrincipal(member,java.util.Set.of(UserClaim.GROUP_LENDING_VIEW));
        assertThat(com.sacco.mvp.security.WorkspaceLanding.authenticatedDefault(actor)).isEqualTo("/staff/dashboard");
    }
    @Test
    void obsoleteReportClaimsKeepIdentifiersButCannotBeAssignedOrLoaded() {
        var retired = java.util.List.of(UserClaim.CLIENT_ONBOARDING_REVIEW, UserClaim.REPORT_TEMPLATE_DESIGN, UserClaim.REPORT_TEMPLATE_PUBLISH,
            UserClaim.REPORT_TEMPLATE_SHARE, UserClaim.REPORT_RUN, UserClaim.REPORT_EXPORT,
            UserClaim.REPORT_RUN_APPROVE, UserClaim.REPORT_JOBS_REVIEW, UserClaim.FINANCIAL_REPORTS_FINALIZE,
            UserClaim.ACCESS_MATRIX_VIEW, UserClaim.ACCESS_MATRIX_UPDATE, UserClaim.LOAN_DOCUMENTS_CREATE);
        for (var claim : retired) {
            assertThat(UserClaim.valueOf(claim.name())).isSameAs(claim);
            assertThat(claim.isAvailable()).isFalse();
            assertThat(UserClaim.fromStoredName(claim.name())).isEmpty();
            assertThat(UserClaim.expandLegacy(java.util.List.of(claim))).isEmpty();
            assertThat(UserClaim.forFeatureAction(claim.getFeature(), claim.getAction())).isEmpty();
            assertThat(UserClaim.matrixRows()).noneMatch(row -> row.claimsByAction().containsValue(claim));
        }
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true)).doesNotContainAnyElementsOf(retired);
        assertThat(UserClaim.matrixRows()).noneMatch(row -> row.feature() == AccessFeature.REPORT_BUILDER
            || row.feature() == AccessFeature.REPORT_EXECUTION);
        for (var active : java.util.List.of(UserClaim.FINANCIAL_REPORTS_VIEW, UserClaim.FINANCIAL_REPORTS_EXPORT,
            UserClaim.FINANCIAL_REPORTS_INSTITUTION, UserClaim.ACCOUNTING_RECONCILIATION_VIEW,
            UserClaim.VOUCHER_WORKSPACE_CREATE, UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_APPROVE,
            UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_REJECT)) {
            assertThat(UserClaim.fromStoredName(active.name())).containsExactly(active);
            assertThat(UserClaim.matrixRows()).anyMatch(row -> row.claimsByAction().containsValue(active));
        }
    }

    @Test
    void voucherWorkflowPermissionsAreExplicitAndUseAccurateActions() {
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true))
            .doesNotContain(UserClaim.VOUCHER_WORKSPACE_VIEW, UserClaim.VOUCHER_WORKSPACE_POST,
                UserClaim.VOUCHER_APPROVAL_APPROVE, UserClaim.VOUCHER_APPROVAL_FORCE, UserClaim.VOUCHER_RULE_CONFIGURE);
        assertThat(UserClaim.forFeatureAction(AccessFeature.VOUCHER_WORKSPACE, AccessAction.POST))
            .contains(UserClaim.VOUCHER_WORKSPACE_POST);
        assertThat(UserClaim.fromStoredName("VOUCHER_APPROVAL_FORCE")).contains(UserClaim.VOUCHER_APPROVAL_FORCE);
    }

    @Test
    void voucherApprovalsShareOneRowWhileRulesHaveIndependentPermissions() {
        var rows = UserClaim.matrixRows();
        assertThat(rows).noneMatch(row -> row.feature() == AccessFeature.VOUCHER_APPROVAL);
        var voucher = rows.stream().filter(row -> row.feature() == AccessFeature.VOUCHER_WORKSPACE).findFirst().orElseThrow();
        assertThat(voucher.claimsByAction().get(AccessAction.VIEW))
            .isEqualTo(UserClaim.VOUCHER_WORKSPACE_VIEW);
        assertThat(voucher.claimsByAction().get(AccessAction.APPROVE)).isEqualTo(UserClaim.VOUCHER_APPROVAL_APPROVE);
        assertThat(voucher.claimsByAction()).doesNotContainKey(AccessAction.CONFIGURE);
        var rules = rows.stream().filter(row -> row.feature() == AccessFeature.VOUCHER_RULES).findFirst().orElseThrow();
        assertThat(rules.claimsByAction()).containsOnly(
            java.util.Map.entry(AccessAction.VIEW, UserClaim.VOUCHER_RULE_VIEW),
            java.util.Map.entry(AccessAction.CONFIGURE, UserClaim.VOUCHER_RULE_CONFIGURE));
        java.util.List<UserClaim> displayed = new java.util.ArrayList<>();
        for (var row : rows) displayed.addAll(row.claimsByAction().values());
        java.util.List<UserClaim> available = java.util.Arrays.stream(UserClaim.values()).filter(UserClaim::isAvailable)
            .filter(claim -> claim != UserClaim.INSTITUTION_BRANCH_ADMIN).toList();
        assertThat(displayed).doesNotHaveDuplicates();
        assertThat(displayed).containsExactlyInAnyOrderElementsOf(available);
    }

    @Test
    void staffWithOnlyVoucherWorkspaceClaimsCanReachTheirWorkspaceAfterLogin() {
        var member = Member.builder().id(java.util.UUID.randomUUID()).saccoId("I").stationId("B")
            .position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();
        var principal = new com.sacco.mvp.security.AppUserPrincipal(member,java.util.Set.of(UserClaim.VOUCHER_WORKSPACE_VIEW, UserClaim.ACCOUNTING_JOURNALS_VIEW));
        assertThat(com.sacco.mvp.security.WorkspaceLanding.authenticatedDefault(principal)).isEqualTo("/staff/dashboard");
    }

    @Test
    void removedAccountingPermissionsCannotBeAssignedOrRestoredFromStorage() {
        var retired = java.util.List.of(UserClaim.ACCOUNTING_POLICIES_VIEW, UserClaim.ACCOUNTING_OPENINGS_CREATE,
            UserClaim.ACCOUNTING_PERIODS_CREATE, UserClaim.ACCOUNTING_RELEASE_VIEW,
            UserClaim.ACCOUNTING_BUSINESS_APPROVE, UserClaim.ACCOUNTING_JOURNALS_APPROVE);
        for (var claim : retired) {
            assertThat(UserClaim.fromStoredName(claim.name())).isEmpty();
            assertThat(UserClaim.expandLegacy(java.util.List.of(claim))).isEmpty();
            assertThat(UserClaim.matrixRows()).noneMatch(row -> row.claimsByAction().containsValue(claim));
        }
        assertThat(UserClaim.fromStoredName("ACCOUNTING_BUSINESS_CREATE")).contains(UserClaim.ACCOUNTING_BUSINESS_CREATE);
        assertThat(UserClaim.fromStoredName("ACCOUNTING_JOURNALS_CREATE")).contains(UserClaim.ACCOUNTING_JOURNALS_CREATE);
    }

    @Test
    void roleLabelsUseMicrofinanceLanguageWithoutRenamingStoredRoles() {
        assertThat(Position.MEMBER.getDisplayName()).isEqualTo("Client");
        assertThat(Position.MINOR_ADMIN.getDisplayName()).isEqualTo("Institution Admin");
        assertThat(Position.valueOf("MEMBER")).isSameAs(Position.MEMBER);
        assertThat(Position.valueOf("MINOR_ADMIN")).isSameAs(Position.MINOR_ADMIN);
    }

    @Test
    void repaymentPermissionsAreExplicitRatherThanAutomaticallyGranted() {
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true))
            .doesNotContain(UserClaim.LOAN_REPAYMENTS_VIEW, UserClaim.LOAN_REPAYMENTS_CREATE, UserClaim.LOAN_REPAYMENTS_REVERSE);
        assertThat(UserClaim.forFeatureAction(AccessFeature.LOAN_REPAYMENTS, AccessAction.REVERSE))
            .contains(UserClaim.LOAN_REPAYMENTS_REVERSE);
    }

    @Test
    void matrixRowsKeepUnsupportedActionCellsAsMissingClaims() {
        UserClaim.AccessMatrixRow accessMatrix = UserClaim.matrixRows().stream()
            .filter(row -> row.feature() == AccessFeature.LOAN_DOCUMENTS)
            .findFirst()
            .orElseThrow();

        assertThat(accessMatrix.claimsByAction().get(AccessAction.VIEW))
            .isEqualTo(UserClaim.LOAN_DOCUMENTS_VIEW);
        assertThat(accessMatrix.claimsByAction().get(AccessAction.ADD))
            .isNull();
        assertThat(UserClaim.forFeatureAction(AccessFeature.LOAN_DOCUMENTS, AccessAction.ADD))
            .isEmpty();
    }

    @Test
    void reviewQueueMatrixDoesNotExposeUnimplementedClaims() {
        assertThat(UserClaim.forFeatureAction(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.MANAGER_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.MANAGER_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.BOARD_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.BOARD_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.DISBURSEMENT_QUEUE, AccessAction.UPDATE)).isEmpty();
    }

    @Test
    void defaultsAndLegacyFallbackDoNotSeedUnimplementedClaims() {
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true))
            .extracting(UserClaim::name)
            .doesNotContain(
                "LOAN_OFFICER_QUEUE_UPDATE",
                "MANAGER_QUEUE_ASSIGN",
                "MANAGER_QUEUE_UPDATE",
                "ACCOUNTANT_QUEUE_ASSIGN",
                "ACCOUNTANT_QUEUE_UPDATE",
                "BOARD_QUEUE_ASSIGN",
                "BOARD_QUEUE_UPDATE",
                "CHAIRPERSON_QUEUE_ASSIGN",
                "CHAIRPERSON_QUEUE_UPDATE",
                "CREDIT_COMMITTEE_QUEUE_ASSIGN",
                "CREDIT_COMMITTEE_QUEUE_UPDATE",
                "DISBURSEMENT_QUEUE_UPDATE"
            );
        assertThat(UserClaim.fromStoredName("MANAGER_QUEUE_UPDATE")).isEmpty();
    }

    @Test
    void administratorRecoveryClaimsKeepTheAccessMatrixReachable() {
        assertThat(UserClaim.administratorRecoveryClaims()).containsExactlyInAnyOrder(
            UserClaim.ADMIN_DASHBOARD_VIEW,
            UserClaim.USER_ACCESS_VIEW,
            UserClaim.USER_ACCESS_UPDATE
        );
    }

}
