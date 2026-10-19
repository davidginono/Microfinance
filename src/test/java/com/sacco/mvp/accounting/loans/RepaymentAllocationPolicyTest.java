package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.repayment.service.RepaymentAllocationCalculator;
import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.AllocationOrder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class RepaymentAllocationPolicyTest {
    private static BigDecimal money(String v){return new BigDecimal(v);}
    @Test void partialPaymentHonoursEachComponentPriorityAndRetainsCents(){
        var interestFirst=RepaymentAllocationCalculator.allocate(money("50.03"),money("80.00"),money("20.02"),money("300.00"),AllocationOrder.INTEREST_FIRST);
        assertThat(interestFirst.interest()).isEqualByComparingTo("20.02");assertThat(interestFirst.principal()).isEqualByComparingTo("30.01");assertThat(interestFirst.principalBalance()).isEqualByComparingTo("269.99");
        var principalFirst=RepaymentAllocationCalculator.allocate(money("50.03"),money("80.00"),money("20.02"),money("300.00"),AllocationOrder.PRINCIPAL_FIRST);
        assertThat(principalFirst.principal()).isEqualByComparingTo("50.03");assertThat(principalFirst.interest()).isZero();assertThat(principalFirst.principalBalance()).isEqualByComparingTo("249.97");
    }
    @Test void principalFirstSpillsIntoDueInterestThenFuturePrincipalAndRejectsAmountsAboveDebt(){
        var split=RepaymentAllocationCalculator.allocate(money("100.02"),money("80.00"),money("20.02"),money("300.00"),AllocationOrder.PRINCIPAL_FIRST);assertThat(split.principal()).isEqualByComparingTo("80.00");assertThat(split.interest()).isEqualByComparingTo("20.02");
        var advance=RepaymentAllocationCalculator.allocate(money("100.03"),money("80.00"),money("20.02"),money("300.00"),AllocationOrder.PRINCIPAL_FIRST);
        assertThat(advance.principal()).isEqualByComparingTo("80.01");assertThat(advance.interest()).isEqualByComparingTo("20.02");
        assertThatThrownBy(()->RepaymentAllocationCalculator.allocate(money("320.03"),money("80.00"),money("20.02"),money("300.00"),AllocationOrder.PRINCIPAL_FIRST)).hasMessage("repayment.error.advance");
        assertThatThrownBy(()->RepaymentAllocationCalculator.allocate(money("1.00"),null,money("0.00"),money("300.00"),AllocationOrder.INTEREST_FIRST)).hasMessage("repayment.error.advance");
    }
    @Test void acceptsAnyPositiveCentsAmountBeforeTheFirstDueDateWithoutNegativePrincipal(){
        for(var order:AllocationOrder.values()){
            var split=RepaymentAllocationCalculator.allocate(money("123.47"),money("0.00"),money("0.00"),money("300.00"),order);
            assertThat(split.principal()).isEqualByComparingTo("123.47");assertThat(split.interest()).isZero();assertThat(split.principalBalance()).isEqualByComparingTo("176.53");
        }
        assertThatThrownBy(()->RepaymentAllocationCalculator.allocate(money("300.01"),money("0.00"),money("0.00"),money("300.00"),AllocationOrder.INTEREST_FIRST)).hasMessage("repayment.error.advance");
    }
}
