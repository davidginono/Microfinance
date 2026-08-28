package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminProfileServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @Test
    void platformAdminUpdatesOwnEmailAndPhoneAfterPasswordConfirmation() {
        UUID memberId = UUID.randomUUID();
        Member admin = platformAdmin(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);
        when(memberRepository.save(admin)).thenReturn(admin);

        PlatformAdminProfileService service = service();
        Member saved = service.updateContact(
            memberId,
            " Admin@Example.COM ",
            "0712 345 678",
            "correct-password"
        );

        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPhone()).isEqualTo("255712345678");
        verify(auditService).log(
            eq("STAFF_USER"),
            eq(memberId),
            eq("PLATFORM_ADMIN_UPDATE_OWN_CONTACT"),
            eq(memberId),
            any(),
            any()
        );
    }

    @Test
    void contactUpdateRejectsAnIncorrectPassword() {
        UUID memberId = UUID.randomUUID();
        Member admin = platformAdmin(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> service().updateContact(
            memberId, "admin@example.com", "255712345678", "wrong-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("current password");

        verify(memberRepository, never()).save(any());
    }

    private PlatformAdminProfileService service() {
        return new PlatformAdminProfileService(memberRepository, passwordEncoder, auditService);
    }

    private Member platformAdmin(UUID memberId) {
        return Member.builder()
            .id(memberId)
            .position(Position.ADMIN)
            .passwordHash("encoded-password")
            .build();
    }
}
