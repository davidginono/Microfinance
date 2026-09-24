package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sacco_loan_app_counter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaccoLoanAppCounter {
    @Id
    @Column(name = "sacco_id", nullable = false, length = 64)
    private String saccoId;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber;
}
