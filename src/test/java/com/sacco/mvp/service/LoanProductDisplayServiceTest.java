package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanProductDisplayServiceTest {
    @Mock private LoanProductSettingRepository repository;
    @InjectMocks private LoanProductDisplayService service;

    @Test
    void queuePreservesDistinctProductsOfTheSameTypeIncludingInactiveProducts() {
        LoanProductSetting school = product("School Fees", true);
        LoanProductSetting emergency = product("Emergency", false);
        LoanApplication first = application("IAA", school.getId());
        LoanApplication second = application("IAA", emergency.getId());
        LoanApplication repeated = application("IAA", school.getId());
        when(repository.findBySaccoIdAndIdIn("IAA", List.of(school.getId(), emergency.getId())))
            .thenReturn(List.of(school, emergency));

        assertThat(service.namesForApplications("IAA", List.of(first, second, repeated)))
            .containsEntry(first.getId(), "School Fees")
            .containsEntry(second.getId(), "Emergency")
            .containsEntry(repeated.getId(), "School Fees");
        verify(repository).findBySaccoIdAndIdIn("IAA", List.of(school.getId(), emergency.getId()));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingProductsUseGenericTypeAndOtherSaccosAreExcluded() {
        LoanApplication missing = application("IAA", UUID.randomUUID());
        LoanApplication legacy = application("IAA", null);
        LoanApplication otherSacco = application("OTHER", UUID.randomUUID());
        when(repository.findBySaccoIdAndIdIn("IAA", List.of(missing.getLoanProductSettingId())))
            .thenReturn(List.of());

        assertThat(service.namesForApplications("IAA", List.of(missing, legacy, otherSacco)))
            .hasSize(2)
            .containsEntry(missing.getId(), LoanType.CUSTOMIZED_LOAN.getDisplayLabel())
            .containsEntry(legacy.getId(), LoanType.CUSTOMIZED_LOAN.getDisplayLabel());
        verify(repository).findBySaccoIdAndIdIn("IAA", List.of(missing.getLoanProductSettingId()));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyQueueDoesNotQueryProducts() {
        assertThat(service.namesForApplications("IAA", List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void detailRetainsInactiveSelectedProductName() {
        LoanProductSetting product = product("Retired Product", false);
        LoanApplication application = application("IAA", product.getId());
        when(repository.findByIdAndSaccoId(product.getId(), "IAA")).thenReturn(Optional.of(product));

        assertThat(service.displayName(application)).isEqualTo("Retired Product");
        verify(repository).findByIdAndSaccoId(product.getId(), "IAA");
        verifyNoMoreInteractions(repository);
    }

    private LoanProductSetting product(String name, boolean active) {
        return LoanProductSetting.builder().id(UUID.randomUUID()).saccoId("IAA")
            .loanType(LoanType.CUSTOMIZED_LOAN).productName(name).active(active).build();
    }

    private LoanApplication application(String saccoId, UUID productId) {
        return LoanApplication.builder().id(UUID.randomUUID()).saccoId(saccoId)
            .loanType(LoanType.CUSTOMIZED_LOAN).loanProductSettingId(productId).build();
    }
}
