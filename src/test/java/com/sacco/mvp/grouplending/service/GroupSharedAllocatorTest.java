package com.sacco.mvp.grouplending.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GroupSharedAllocatorTest {
    private static BigDecimal money(String s){return new BigDecimal(s);}
    @Test void unequalSharesConserveSmallComponentsAndExactFinalCent(){
        assertThat(GroupSharedAllocator.split(money("0.01"),List.of(money("1"),money("2"),money("3"))))
            .containsExactly(money("0.00"),money("0.00"),money("0.01"));
        assertThat(GroupSharedAllocator.split(money("10.00"),List.of(money("1"),money("2"),money("3"))))
            .containsExactly(money("1.66"),money("3.34"),money("5.00"));
    }
    @Test void everyPositiveMinorUnitIsConservedAcrossUnequalGroups(){
        var random=new Random(20261026);
        for(int count=2;count<=50;count++){
            var weights=new ArrayList<BigDecimal>();for(int n=0;n<count;n++)weights.add(BigDecimal.valueOf(1+random.nextInt(100000),2));
            for(String component:List.of("0.00","0.01","0.99","12345.67","9999999999999999.99")){
                var amount=money(component);var shares=GroupSharedAllocator.split(amount,weights);
                assertThat(shares).hasSize(count).allMatch(v->v.signum()>=0 && v.scale()==2);
                assertThat(shares.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(amount);
            }
        }
    }
    @Test void invalidMoneyAndUnboundedGroupsCannotBeAllocated(){
        for(var amount:List.of(money("-1.00"),money("0.001")))assertThatIllegalArgumentException().isThrownBy(()->GroupSharedAllocator.split(amount,List.of(money("1"))));
        for(var weights:List.of(List.<BigDecimal>of(),List.of(BigDecimal.ZERO),Collections.nCopies(51,BigDecimal.ONE)))
            assertThatIllegalArgumentException().isThrownBy(()->GroupSharedAllocator.split(BigDecimal.ONE,weights));
    }
    @Test void repeatedDueComponentsCannotExceedRemainingPersonalBudgets(){
        var remaining=new ArrayList<>(List.of(money("0.02"),money("0.01"),money("0.02")));
        var received=new ArrayList<>(Collections.nCopies(3,money("0.00")));
        for(int period=0;period<5;period++){
            var split=GroupSharedAllocator.split(money("0.01"),remaining);
            for(int n=0;n<remaining.size();n++){
                assertThat(split.get(n)).isLessThanOrEqualTo(remaining.get(n));remaining.set(n,remaining.get(n).subtract(split.get(n)));received.set(n,received.get(n).add(split.get(n)));
            }
        }
        assertThat(remaining).allMatch(v->v.signum()==0);
        assertThat(received).containsExactly(money("0.02"),money("0.01"),money("0.02"));
        assertThat(GroupSharedAllocator.split(money("0.00"),remaining)).containsOnly(money("0.00"));
    }
}
