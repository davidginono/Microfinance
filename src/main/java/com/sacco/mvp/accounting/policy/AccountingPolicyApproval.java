package com.sacco.mvp.accounting.policy;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "accounting_policy_approvals")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class AccountingPolicyApproval {
    @Id @Column(name = "policy_id") private UUID policyId;
    @Column(name = "sacco_id", nullable = false) private String saccoId;
    @Column(name = "policy_version", nullable = false) private int policyVersion;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "checker_id", nullable = false) private UUID checkerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountingPolicyService.Decision decision;
    @Column(name = "evidence_reference", nullable = false, columnDefinition = "text") private String evidenceReference;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(name = "decided_at", nullable = false) private OffsetDateTime decidedAt;
}
