package com.sacco.mvp.repository;

import com.sacco.mvp.domain.MemberPaymentDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberPaymentDetailsRepository extends JpaRepository<MemberPaymentDetails, UUID> {
}
