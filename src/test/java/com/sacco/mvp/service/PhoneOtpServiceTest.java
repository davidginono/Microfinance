package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.EmailOtpTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class PhoneOtpServiceTest {
    final EmailOtpTokenRepository tokens=mock(EmailOtpTokenRepository.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final SmsGateway sms=mock(SmsGateway.class);
    final PhoneOtpService service=new PhoneOtpService(tokens,passwords,sms,new org.springframework.context.support.StaticMessageSource(){{addMessage("profile.contact.sms",Locale.ENGLISH,"Verification code {0}");}});
    final UUID owner=UUID.randomUUID();final String phone="255712345678";
    @Test void recentChallengesBlockRepeatedSmsDelivery(){
        when(tokens.findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(phone,EmailOtpPurpose.CLAIM_PHONE,owner)).thenReturn(Optional.of(EmailOtpToken.builder().createdAt(OffsetDateTime.now()).build()));
        assertThatThrownBy(()->service.issueClaimOtp(phone,owner)).hasMessage("profile.contact.wait");verifyNoInteractions(sms);
    }
    @Test void providerRejectionNeverClaimsThatTheCodeWasSent(){
        when(passwords.encode(anyString())).thenReturn("hash");when(sms.send(eq(phone),anyString())).thenReturn(SmsSendResult.rejected("unavailable"));
        assertThatThrownBy(()->service.issueClaimOtp(phone,owner)).hasMessage("profile.contact.sendFailed");
    }
    @Test void onlyTheUnexpiredOwnerAndPhoneBoundCodeCanBeConsumed(){
        var token=EmailOtpToken.builder().expiresAt(OffsetDateTime.now().plusMinutes(2)).codeHash("hash").build();
        when(tokens.findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(phone,EmailOtpPurpose.CLAIM_PHONE,owner)).thenReturn(Optional.of(token));
        assertThatThrownBy(()->service.consumeClaimOtp(phone,owner,"000000")).hasMessage("profile.contact.codeInvalid");assertThat(token.getConsumedAt()).isNull();
        when(passwords.matches("123456","hash")).thenReturn(true);service.consumeClaimOtp(phone,owner,"123456");assertThat(token.getConsumedAt()).isNotNull();
        assertThatThrownBy(()->service.consumeClaimOtp(phone,UUID.randomUUID(),"123456")).hasMessage("profile.contact.codeInvalid");
    }
}
