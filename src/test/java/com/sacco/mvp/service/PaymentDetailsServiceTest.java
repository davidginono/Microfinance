package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.MemberPaymentDetails;
import com.sacco.mvp.domain.PaymentDestinationType;
import com.sacco.mvp.repository.MemberPaymentDetailsRepository;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentDetailsServiceTest {
    @Mock private MemberPaymentDetailsRepository repository;
    @Mock private AuditService auditService;

    private PaymentDetailsService paymentDetailsService;

    @BeforeEach
    void setUp() {
        paymentDetailsService = new PaymentDetailsService(
            repository,
            JsonMapper.builder().findAndAddModules().build(),
            auditService
        );
    }

    @Test
    void submittedLoanUsesCapturedPaymentDetailsAfterProfileChanges() {
        UUID memberId = UUID.randomUUID();
        MemberPaymentDetails original = details(memberId, "M-Pesa", "255700000001");
        when(repository.findById(memberId)).thenReturn(Optional.of(original));
        String snapshot = paymentDetailsService.snapshotJsonForMember(memberId);

        var view = paymentDetailsService.resolveForLoan(LoanApplication.builder()
            .applicantMemberId(memberId)
            .paymentDetailsSnapshot(snapshot)
            .build());

        assertThat(view.destinationType()).isEqualTo(PaymentDestinationType.MOBILE_MONEY);
        assertThat(view.provider()).isEqualTo("M-Pesa");
        assertThat(view.accountIdentifier()).isEqualTo("255700000001");
        assertThat(view.sourceLabel()).isEqualTo("Captured when submitted");
    }

    @Test
    void updateNormalizesAndReturnsMemberOwnedPaymentDetails() {
        UUID memberId = UUID.randomUUID();
        when(repository.findById(memberId)).thenReturn(Optional.empty());
        when(repository.save(any(MemberPaymentDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var view = paymentDetailsService.update(
            memberId,
            PaymentDestinationType.MOBILE_MONEY,
            "  M-Pesa ",
            " Jane   Member ",
            " 255700000001 "
        );

        assertThat(view.provider()).isEqualTo("M-Pesa");
        assertThat(view.accountHolderName()).isEqualTo("Jane Member");
        assertThat(view.accountIdentifier()).isEqualTo("255700000001");
    }

    @Test
    void paymentDetailsViewExposesJspCompatibleBeanProperties() {
        var view = new PaymentDetailsService.PaymentDetailsView(
            true,
            PaymentDestinationType.BANK_ACCOUNT,
            "Bank Account",
            "CRDB Bank",
            "Jane Member",
            "0123456789",
            "Current member settings"
        );

        assertThat(view.isAvailable()).isTrue();
        assertThat(view.getDestinationType()).isEqualTo(PaymentDestinationType.BANK_ACCOUNT);
        assertThat(view.getDestinationTypeLabel()).isEqualTo("Bank Account");
        assertThat(view.getProvider()).isEqualTo("CRDB Bank");
        assertThat(view.getAccountHolderName()).isEqualTo("Jane Member");
        assertThat(view.getAccountIdentifier()).isEqualTo("0123456789");
        assertThat(view.getSourceLabel()).isEqualTo("Current member settings");
    }

    @Test
    void jspExpressionLanguageCanReadPaymentDetailsProperties() {
        var view = PaymentDetailsService.PaymentDetailsView.empty();
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(expressionFactory);
        context.getVariableMapper().setVariable(
            "paymentDetails",
            expressionFactory.createValueExpression(view, PaymentDetailsService.PaymentDetailsView.class)
        );

        Object provider = expressionFactory
            .createValueExpression(context, "${paymentDetails.provider}", String.class)
            .getValue(context);
        Object available = expressionFactory
            .createValueExpression(context, "${paymentDetails.available}", Boolean.class)
            .getValue(context);

        assertThat(provider).isEqualTo("");
        assertThat(available).isEqualTo(false);
    }

    private MemberPaymentDetails details(UUID memberId, String provider, String identifier) {
        return MemberPaymentDetails.builder()
            .memberId(memberId)
            .destinationType(PaymentDestinationType.MOBILE_MONEY)
            .provider(provider)
            .accountHolderName("Jane Member")
            .accountIdentifier(identifier)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
