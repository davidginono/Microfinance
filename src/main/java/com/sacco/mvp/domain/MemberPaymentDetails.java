package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_payment_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberPaymentDetails {
    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 32)
    private PaymentDestinationType destinationType;

    @Column(nullable = false, length = 120)
    private String provider;

    @Column(name = "account_holder_name", nullable = false, length = 160)
    private String accountHolderName;

    @Column(name = "account_identifier", nullable = false, length = 120)
    private String accountIdentifier;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
