package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StaffClientClaimsTest {
    MemberAccessClaimRepository claims = mock(MemberAccessClaimRepository.class);
    UserSettingsRepository settings = mock(UserSettingsRepository.class);
    UserClaimService service = new UserClaimService(claims, settings, JsonMapper.builder().build());
    UUID person = UUID.randomUUID();

    @Test void existingExplicitClaimsAreNeverDeletedOrRecreated() {
        when(claims.existsByIdMemberId(person)).thenReturn(true);
        service.preserveStaffClaimsForClientAccess(person, Set.of(Position.MANAGER));
        verify(claims, never()).deleteByMemberId(any());
        verify(claims, never()).save(any());
        verifyNoInteractions(settings);
    }

    @Test void existingLegacyAndEmptyAssignmentsStayExplicit() {
        for (String assignment : List.of("{\"claims\":[]}", "{\"claims\":[\"MANAGER_QUEUE_VIEW\"]}")) {
            when(settings.findById(person)).thenReturn(Optional.of(UserSettings.builder().memberId(person).notificationPrefs(assignment).build()));
            service.preserveStaffClaimsForClientAccess(person, Set.of(Position.MANAGER));
        }
        verify(claims, never()).deleteByMemberId(any());
        verify(claims, never()).save(any());
        verify(settings, never()).save(any());
    }

    @Test void implicitStaffDefaultsAreRetainedWithoutAddingClientPowersToStaffLogin() {
        when(settings.findById(person)).thenReturn(Optional.of(UserSettings.builder().memberId(person).notificationPrefs("{}").build()));
        service.preserveStaffClaimsForClientAccess(person, Set.of(Position.MANAGER));
        var saved = ArgumentCaptor.forClass(MemberAccessClaim.class);
        verify(claims, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().stream().map(c -> c.getId().getClaimName()).toList())
            .contains(UserClaim.MANAGER_QUEUE_VIEW.name())
            .doesNotContain(UserClaim.MEMBER_LOANS_CREATE.name(), UserClaim.USER_ACCESS_UPDATE.name());
    }
}
