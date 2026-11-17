package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdditionalUserClaimsTest {
    final MemberAccessClaimRepository rows=mock(MemberAccessClaimRepository.class);
    final UserSettingsRepository settings=mock(UserSettingsRepository.class);
    final UserClaimService service=new UserClaimService(rows,settings,tools.jackson.databind.json.JsonMapper.builder().build());
    final UUID id=UUID.randomUUID();
    MemberAccessClaim row(String name){return MemberAccessClaim.builder().id(new MemberAccessClaimId(id,name)).build();}
    @Test void addsOnlyRequestedClaimsAndPreservesLegacyRowsAndPreferences(){
        when(rows.findByIdMemberId(id)).thenReturn(List.of(row("LEGACY_STORED_PERMISSION"),row("ACCOUNTING_ACCOUNTS_UPDATE")));
        service.addExplicitClaims(id,List.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE));
        verify(rows).save(argThat(c->c.getId().getMemberId().equals(id)&&c.getId().getClaimName().equals("ACCOUNTING_ACCOUNTS_VIEW")));
        verify(rows).save(argThat(c->c.getId().getMemberId().equals(id)&&c.getId().getClaimName().equals("ACCOUNTING_ACCOUNTS_CREATE")));
        verify(rows,never()).deleteByMemberId(any());verifyNoInteractions(settings);
    }
    @Test void existingAndRepeatedClaimsAreNotRewritten(){
        when(rows.findByIdMemberId(id)).thenReturn(List.of(row("ACCOUNTING_ACCOUNTS_VIEW")));
        service.addExplicitClaims(id,List.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE,UserClaim.ACCOUNTING_ACCOUNTS_CREATE));
        verify(rows,times(1)).save(any());verify(rows).save(argThat(c->c.getId().getClaimName().equals("ACCOUNTING_ACCOUNTS_CREATE")));verifyNoInteractions(settings);
    }
    @Test void doesNotMaterializeOrReplaceAnImplicitOrLegacyJsonAssignment(){
        when(rows.findByIdMemberId(id)).thenReturn(List.of());
        assertThatThrownBy(()->service.addExplicitClaims(id,List.of(UserClaim.ACCOUNTING_ACCOUNTS_CREATE))).isInstanceOf(IllegalStateException.class);
        verify(rows,never()).save(any());verifyNoInteractions(settings);
    }
    @Test void invalidRequestsCannotChangeAccess(){
        assertThatThrownBy(()->service.addExplicitClaims(null,List.of(UserClaim.ACCOUNTING_ACCOUNTS_CREATE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.addExplicitClaims(id,Arrays.asList((UserClaim)null))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rows,settings);
    }
}
