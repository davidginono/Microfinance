package com.sacco.mvp.accounting.repayment;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import com.sacco.mvp.accounting.repayment.repository.RepaymentPolicyRepository;
import com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepaymentPolicyServiceTest {
    final RepaymentPolicyRepository repository=mock(RepaymentPolicyRepository.class);
    final RepaymentPolicyService service=new RepaymentPolicyService(repository);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final LocalDate date=LocalDate.of(2026,10,20);

    @Test void batchAndIndividualReceiptsUseInterestFirstWithoutSettings(){
        when(actor.getSaccoId()).thenReturn("I1");
        when(repository.standards("I1")).thenReturn(true);
        when(repository.effective("I1",date)).thenReturn(Optional.empty());
        for(boolean batch:new boolean[]{false,true}){
            var policy=service.effective(actor,date,batch);
            assertThat(policy.allocationOrder()).isEqualTo(AllocationOrder.INTEREST_FIRST);
            assertThat(policy.id()).isNull();
            assertThat(policy.interestAccountId()).isNull();
        }
        verify(repository,times(2)).effective("I1",date);
    }
    @Test void historicalEvidenceRemainsScopedAndIsNotRewritten(){
        when(actor.getSaccoId()).thenReturn("I1");
        var historical=new Policy(UUID.randomUUID(),1,date,AllocationOrder.INTEREST_FIRST,null,"","Historical evidence",null);
        when(repository.effective("I1",date)).thenReturn(Optional.of(historical));
        assertThat(service.effective(actor,date,false)).isSameAs(historical);
        verify(repository).effective("I1",date);
        verifyNoMoreInteractions(repository);
    }
    @Test void missingSystemDefinitionsAndInvalidDatesStillFailClosed(){
        when(actor.getSaccoId()).thenReturn("I1");
        assertThatThrownBy(()->service.effective(actor,date,true)).hasMessage("batch.error.policy");
        assertThatThrownBy(()->service.effective(actor,null,false)).hasMessage("batch.error.date");
        verify(repository).standards("I1");
        verifyNoMoreInteractions(repository);
    }
}
