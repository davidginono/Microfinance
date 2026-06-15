package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberRegistrationServiceTest {

    @Test
    void ensureLocalUniquenessRejectsEmailAlreadyUsedByAnyAccount() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        MemberRegistrationService service = new MemberRegistrationService(
            memberRepository,
            Mockito.mock(RegisteredSaccoRepository.class),
            Mockito.mock(SaccoStationRepository.class),
            Mockito.mock(SaccoSettingsRepository.class),
            Mockito.mock(SavingsAccountRepository.class),
            Mockito.mock(UserSettingsRepository.class),
            Mockito.mock(PasswordEncoder.class),
            null
        );

        when(memberRepository.findByMemberNo("000001")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.ensureLocalUniqueness(
            "000001",
            "existing@example.com",
            "255700000001"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("That email address is already registered in the MVP system.");

        verify(memberRepository).existsByEmailIgnoreCase("existing@example.com");
        verify(memberRepository, never()).existsByPhone(org.mockito.ArgumentMatchers.anyString());
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verifyExternalMemberRejectsInactiveStation() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SavingsAccountRepository savingsAccountRepository = Mockito.mock(SavingsAccountRepository.class);
        UserSettingsRepository userSettingsRepository = Mockito.mock(UserSettingsRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);

        MemberRegistrationService service = new MemberRegistrationService(
            memberRepository,
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            savingsAccountRepository,
            userSettingsRepository,
            passwordEncoder,
            null
        );

        MemberRegistrationForm form = new MemberRegistrationForm();
        form.setMemberNo("000001");
        form.setEmail("member@example.com");
        form.setFullName("Jane Member");
        form.setSaccoId("sacco-1");
        form.setStationId("stn001");
        form.setSignatureText("Jane Member");

        when(registeredSaccoRepository.findById("SACCO-1")).thenReturn(Optional.of(
            RegisteredSacco.builder()
                .saccoId("SACCO-1")
                .saccoName("Example Sacco")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));
        when(saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue("SACCO-1", "STN001"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyExternalMember(form))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Select a valid station ID.");

        verify(saccoStationRepository).findBySaccoIdAndStationIdAndActiveTrue("SACCO-1", "STN001");
        verify(memberRepository, never()).findByMemberNo(org.mockito.ArgumentMatchers.anyString());
    }
}
