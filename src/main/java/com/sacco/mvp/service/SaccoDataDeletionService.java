package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.reporting.OperationalReportTemplateService;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaccoDataDeletionService {
    private static final String INVITED_ACCOUNT_PASSWORD_PLACEHOLDER = "OTP_ONLY_LOGIN";

    private final JdbcTemplate jdbcTemplate;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final MemberRepository memberRepository;
    private final SaccoRegistryService saccoRegistryService;
    private final SaccoLogoStorageService saccoLogoStorageService;
    private final StoredUploadStorageService storedUploadStorageService;
    private final AccountingPolicyService accountingPolicies;
    private final GeneralLedgerService generalLedger;
    private final OperationalReportTemplateService reportTemplates;

    @Transactional
    public void deleteSacco(String saccoId, String confirmation) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalArgumentException("SACCO not found."));
        requireExactConfirmation(confirmation, "delete " + sacco.getSaccoName() + " and all its data");

        deleteSaccoScopedRows(normalizedSaccoId);
        saccoLogoStorageService.deleteSaccoFiles(normalizedSaccoId);
        saccoRegistryService.invalidateRegisteredSaccoCache();
    }

    @Transactional
    public void deleteRevokedMinorAdmin(UUID accountId, String confirmation) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }
        if (member.getStatus() == MemberStatus.ACTIVE || member.getStatus() == MemberStatus.INVITED) {
            throw new IllegalStateException("Only revoked or inactive SACCOS Admin records can be deleted.");
        }
        requireExactConfirmation(confirmation, "delete " + member.getFullName());

        deleteMemberScopedRows(member.getId(), member.getEmail());
    }

    @Transactional
    public void deleteInactiveStaffMember(String saccoId,
                                          String stationId,
                                          Set<Position> actorRoles,
                                          UUID accountId,
                                          String confirmation) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Staff member record not found."));
        if (!normalizedSaccoId.equalsIgnoreCase(member.getSaccoId())) {
            throw new IllegalArgumentException("Staff member record not found in this SACCO.");
        }
        if (normalizedStationId != null && !normalizedStationId.equalsIgnoreCase(normalizeOptional(member.getStationId()))) {
            throw new IllegalArgumentException("Staff member record not found in this station.");
        }
        if (member.isMemberAccess() || member.getStaffRolesResolved().isEmpty()) {
            throw new IllegalStateException("Only staff invitation records can be deleted here.");
        }
        boolean actorIsSuperAdmin = Position.containsSuperAdminRole(actorRoles);
        if (!actorIsSuperAdmin && member.getStaffRolesResolved().contains(Position.ADMIN)) {
            throw new IllegalStateException("Only super admins can delete Super Admin invitation records.");
        }
        if (member.getStatus() != MemberStatus.INACTIVE || !INVITED_ACCOUNT_PASSWORD_PLACEHOLDER.equals(member.getPasswordHash())) {
            throw new IllegalStateException("Cancel the staff invitation before deleting this record.");
        }
        requireExactConfirmation(confirmation, "delete " + member.getFullName());

        deleteMemberScopedRows(member.getId(), member.getEmail());
    }

    private void deleteSaccoScopedRows(String saccoId) {
        if (accountingPolicies.hasInstitutionHistory(saccoId) || generalLedger.hasInstitutionHistory(saccoId)
            || reportTemplates.hasInstitutionHistory(saccoId)
            || Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "select exists(select 1 from loan_ledgers where sacco_id = ?)", Boolean.class, saccoId))) {
            throw new IllegalStateException("This institution has posted financial records and cannot be deleted. Retain or deactivate it instead.");
        }
        deleteStoredUploadFilesForSacco(saccoId);
        update("""
            delete from stored_uploads
            where (owner_type = 'MEMBER' and owner_id in (select id::text from members where sacco_id = ?))
               or (owner_type = 'LOAN_APPLICATION' and owner_id in (select id::text from loan_applications where sacco_id = ?))
            """, saccoId, saccoId);

        update("""
            delete from admin_incidents
            where sacco_id = ?
               or reported_by_member_id in (select id from members where sacco_id = ?)
               or resolved_by_member_id in (select id from members where sacco_id = ?)
               or related_notification_id in (
                   select id from notifications
                   where recipient_member_id in (select id from members where sacco_id = ?)
               )
            """, saccoId, saccoId, saccoId, saccoId);

        update("""
            delete from outbox_events
            where aggregate_id in (select id from members where sacco_id = ?)
               or aggregate_id in (select id from loan_applications where sacco_id = ?)
               or aggregate_id in (select id from loan_product_settings where sacco_id = ?)
               or aggregate_id in (
                   select id from notifications
                   where recipient_member_id in (select id from members where sacco_id = ?)
               )
            """, saccoId, saccoId, saccoId, saccoId);

        update("""
            delete from audit_log
            where actor_member_id in (select id from members where sacco_id = ?)
               or entity_id in (select id from members where sacco_id = ?)
               or entity_id in (select id from loan_applications where sacco_id = ?)
               or entity_id in (select id from loan_product_settings where sacco_id = ?)
            """, saccoId, saccoId, saccoId, saccoId);

        update("""
            delete from email_otp_tokens
            where member_id in (select id from members where sacco_id = ?)
               or lower(email) in (
                   select lower(email) from members
                   where sacco_id = ? and email is not null and email <> ''
               )
            """, saccoId, saccoId);
        update("delete from minor_admin_invitations where member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from notifications where recipient_member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from app_usage_events where sacco_id = ? or member_id in (select id from members where sacco_id = ?)", saccoId, saccoId);
        update("delete from app_usage_page_metrics where sacco_id = ?", saccoId);

        update("delete from reversal_requests where sacco_id = ? or loan_application_id in (select id from loan_applications where sacco_id = ?)", saccoId, saccoId);
        update("delete from board_reviews where loan_application_id in (select id from loan_applications where sacco_id = ?) or board_member_id in (select id from members where sacco_id = ?)", saccoId, saccoId);
        update("delete from manager_reviews where loan_application_id in (select id from loan_applications where sacco_id = ?) or manager_member_id in (select id from members where sacco_id = ?)", saccoId, saccoId);
        update("delete from guarantor_requests where loan_application_id in (select id from loan_applications where sacco_id = ?) or guarantor_member_id in (select id from members where sacco_id = ?)", saccoId, saccoId);
        update("delete from loan_applications where sacco_id = ?", saccoId);
        update("delete from sacco_loan_app_counter where sacco_id = ?", saccoId);

        update("delete from loan_product_required_attachments where loan_product_setting_id in (select id from loan_product_settings where sacco_id = ?)", saccoId);
        update("delete from loan_product_board_reviewers where sacco_id = ? or loan_product_setting_id in (select id from loan_product_settings where sacco_id = ?) or board_member_id in (select id from members where sacco_id = ?)", saccoId, saccoId, saccoId);
        update("delete from loan_product_settings where sacco_id = ?", saccoId);

        update("delete from accounts_savings where member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from member_payment_details where member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from user_settings where member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from member_staff_roles where member_id in (select id from members where sacco_id = ?)", saccoId);
        update("delete from members where sacco_id = ?", saccoId);

        update("delete from sms_usage_ledger where sacco_id = ? or account_id in (select id from station_sms_accounts where sacco_id = ?)", saccoId, saccoId);
        update("delete from station_sms_accounts where sacco_id = ?", saccoId);
        update("delete from sacco_station_policies where sacco_id = ?", saccoId);
        update("delete from sacco_stations where sacco_id = ?", saccoId);
        update("delete from sacco_settings where sacco_id = ?", saccoId);
        update("delete from registered_saccos where sacco_id = ?", saccoId);
    }

    private void deleteMemberScopedRows(UUID memberId, String email) {
        if (accountingPolicies.hasMemberHistory(memberId) || generalLedger.hasMemberHistory(memberId)
            || reportTemplates.hasMemberHistory(memberId)
            || Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            select exists(select 1 from loan_ledgers where applicant_member_id = ?
                union all select 1 from loan_repayment_transactions where actor_member_id = ?)
            """, Boolean.class, memberId, memberId))) {
            throw new IllegalStateException("This account is linked to posted financial records and cannot be deleted. Deactivate access instead.");
        }
        String memberIdText = memberId.toString();
        deleteStoredUploadFilesForMember(memberIdText);
        update("delete from stored_uploads where owner_type = 'MEMBER' and owner_id = ?", memberIdText);
        update("delete from admin_incidents where reported_by_member_id = ? or resolved_by_member_id = ?", memberId, memberId);
        update("delete from outbox_events where aggregate_id = ?", memberId);
        update("delete from audit_log where actor_member_id = ? or entity_id = ?", memberId, memberId);
        if (email != null && !email.isBlank()) {
            update("delete from email_otp_tokens where member_id = ? or lower(email) = lower(?)", memberId, email.trim());
        } else {
            update("delete from email_otp_tokens where member_id = ?", memberId);
        }
        update("delete from minor_admin_invitations where member_id = ?", memberId);
        update("delete from notifications where recipient_member_id = ?", memberId);
        update("delete from app_usage_events where member_id = ?", memberId);
        update("delete from accounts_savings where member_id = ?", memberId);
        update("delete from member_payment_details where member_id = ?", memberId);
        update("delete from user_settings where member_id = ?", memberId);
        update("delete from member_staff_roles where member_id = ?", memberId);
        update("delete from members where id = ?", memberId);
    }

    private void requireExactConfirmation(String submitted, String expected) {
        if (submitted == null || !submitted.trim().equals(expected)) {
            throw new IllegalStateException("Type the confirmation phrase exactly to continue.");
        }
    }

    private void update(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
    }

    private void deleteStoredUploadFilesForSacco(String saccoId) {
        List<String> storageKeys = jdbcTemplate.queryForList("""
            select storage_key
            from stored_uploads
            where storage_key is not null
              and storage_key <> ''
              and (
                  (owner_type = 'MEMBER' and owner_id in (select id::text from members where sacco_id = ?))
                  or
                  (owner_type = 'LOAN_APPLICATION' and owner_id in (select id::text from loan_applications where sacco_id = ?))
              )
            """, String.class, saccoId, saccoId);
        storedUploadStorageService.deleteLocalFilesAfterCommit(storageKeys);
    }

    private void deleteStoredUploadFilesForMember(String memberIdText) {
        List<String> storageKeys = jdbcTemplate.queryForList("""
            select storage_key
            from stored_uploads
            where owner_type = 'MEMBER'
              and owner_id = ?
              and storage_key is not null
              and storage_key <> ''
            """, String.class, memberIdText);
        storedUploadStorageService.deleteLocalFilesAfterCommit(storageKeys);
    }

    private String normalizeSaccoId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SACCO ID is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
