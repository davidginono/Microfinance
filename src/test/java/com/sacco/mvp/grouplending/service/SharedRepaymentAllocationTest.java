package com.sacco.mvp.grouplending.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SharedRepaymentAllocationTest {
    @Test void unequalCentSharesAndZeroSharesConserveMoneyAcrossRepeatedPayments(){
        var random=new Random(91);
        for(int trial=0;trial<200;trial++){
            var remaining=new ArrayList<BigDecimal>();for(int n=0;n<8;n++)remaining.add(BigDecimal.valueOf(random.nextInt(10000),2));remaining.set(2,new BigDecimal("0.00"));
            for(int payment=0;payment<10;payment++){
                var total=remaining.stream().reduce(BigDecimal.ZERO,BigDecimal::add);var amount=payment==9?total:total.divide(new BigDecimal("7"),2,java.math.RoundingMode.DOWN);
                var split=GroupSharedLoanService.split(amount,remaining);assertThat(split.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(amount);
                for(int n=0;n<remaining.size();n++){assertThat(split.get(n)).isBetween(BigDecimal.ZERO,remaining.get(n));remaining.set(n,remaining.get(n).subtract(split.get(n)));}
            }
            assertThat(remaining).allSatisfy(x->assertThat(x).isZero());
        }
    }
    @Test void cannotAllocateMoreThanTheRemainingComponentOrUseNegativeShares(){
        assertThatThrownBy(()->GroupSharedLoanService.split(new BigDecimal("1.01"),List.of(new BigDecimal("1.00")))).hasMessage("recording.error.sharedAttribution");
        assertThatThrownBy(()->GroupSharedLoanService.split(BigDecimal.ZERO,List.of(new BigDecimal("-1.00")))).hasMessage("recording.error.sharedAttribution");
    }
}
