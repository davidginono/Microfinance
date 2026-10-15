package com.sacco.mvp.accounting.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.UUID;

/** Read-only deletion protection for evidence retained after retiring accounting modules. */
@Repository @RequiredArgsConstructor
public class RetainedFinancialHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public boolean hasInstitutionHistory(String institution) {
        return institution != null && Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_policies where sacco_id=:id)
            or exists(select 1 from reconciliation_statement where sacco_id=:id)
            or exists(select 1 from reconciliation_format where sacco_id=:id)
            or exists(select 1 from accounting_close_review where sacco_id=:id)
            or exists(select 1 from financial_statement_templates where sacco_id=:id)
            or exists(select 1 from regulatory_statement_submissions where sacco_id=:id)
            or exists(select 1 from accounting_business_document where sacco_id=:id)
            or exists(select 1 from accounting_supplier where sacco_id=:id)
            or exists(select 1 from accounting_business_opening where sacco_id=:id)
            or exists(select 1 from cash_flow_allocations where sacco_id=:id)
            or exists(select 1 from accounting_release_requests where sacco_id=:id)
            or exists(select 1 from statement_output_sets where sacco_id=:id)
            """, Map.of("id", institution), Boolean.class));
    }
    public boolean hasMemberHistory(UUID member) {
        return member != null && Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_policies where maker_id=:id)
            or exists(select 1 from accounting_policy_approvals where checker_id=:id)
            or exists(select 1 from reconciliation_format where maker_id=:id or checker_id=:id)
            or exists(select 1 from reconciliation_statement where maker_id=:id)
            or exists(select 1 from reconciliation_match where maker_id=:id)
            or exists(select 1 from reconciliation_match_decision where checker_id=:id)
            or exists(select 1 from reconciliation_exception where maker_id=:id or assigned_to=:id)
            or exists(select 1 from reconciliation_exception_decision where checker_id=:id)
            or exists(select 1 from reconciliation_certificate where maker_id=:id)
            or exists(select 1 from reconciliation_certificate_decision where checker_id=:id)
            or exists(select 1 from accounting_close_review where maker_id=:id)
            or exists(select 1 from accounting_close_decision where checker_id=:id)
            or exists(select 1 from financial_statement_versions where made_by=:id or checked_by=:id)
            or exists(select 1 from financial_statement_results where made_by=:id)
            or exists(select 1 from regulatory_statement_submissions where made_by=:id)
            or exists(select 1 from regulatory_statement_reviews where checker_id=:id)
            or exists(select 1 from accounting_business_document where maker_id=:id or checker_id=:id)
            or exists(select 1 from accounting_supplier where maker_id=:id)
            or exists(select 1 from accounting_business_opening where maker_id=:id)
            or exists(select 1 from accounting_business_opening_review where reviewer_id=:id)
            or exists(select 1 from cash_flow_allocations where made_by=:id)
            or exists(select 1 from cash_flow_allocation_reviews where checker_id=:id)
            or exists(select 1 from accounting_release_requests where requested_by=:id)
            or exists(select 1 from accounting_release_decisions where reviewer=:id)
            or exists(select 1 from accounting_release_invalidations where actor_id=:id)
            or exists(select 1 from statement_output_sets where generated_by=:id)
            or exists(select 1 from statement_output_reviews where reviewer=:id)
            """, Map.of("id", member), Boolean.class));
    }
}
