package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_product_required_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProductRequiredAttachment {
    @Id
    private UUID id;

    @Column(name = "loan_product_setting_id", nullable = false)
    private UUID loanProductSettingId;

    @Column(name = "attachment_name", nullable = false, length = 120)
    private String attachmentName;

    @Column(name = "max_size_mb", precision = 8, scale = 2)
    private BigDecimal maxSizeMb;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean isActive() {
        return active == null || active;
    }
}
