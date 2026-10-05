package com.sacco.mvp.accounting.policy;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "accounting_policies")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class AccountingPolicy {
    @Id private UUID id;
    @Column(name = "sacco_id", nullable = false) private String saccoId;
    @Column(name = "policy_version", nullable = false) private int policyVersion;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "opening_date", nullable = false) private LocalDate openingDate;
    @Enumerated(EnumType.STRING) @Column(name = "authoritative_ledger", nullable = false)
    private AccountingPolicyService.AuthoritativeLedger authoritativeLedger;
    @Column(name = "decisions_json", nullable = false, columnDefinition = "text") private String decisionsJson;
    @Column(name = "posting_matrix_json", nullable = false, columnDefinition = "text") private String postingMatrixJson;
    @Column(name = "account_mappings_json", nullable = false, columnDefinition = "text") private String accountMappingsJson;
    @Column(name = "evidence_reference", nullable = false, columnDefinition = "text") private String evidenceReference;
    @Column(name = "maker_id", nullable = false) private UUID makerId;
    @Column(name = "request_key", nullable = false) private UUID requestKey;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
