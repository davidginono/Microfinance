package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.StationOtpDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

/** Commits a scoped challenge before calling the configured email/SMS transport. */
@Service @RequiredArgsConstructor
public class GroupConsentDeliveryService {
    private final GroupConsentService consents;
    private final StationOtpDeliveryService delivery;
    private final MessageSource messages;
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void send(AppUserPrincipal actor,UUID cycle,Locale locale){
        var challenge=consents.prepareCode(actor,cycle);
        try{
            delivery.deliver(challenge.institution(),challenge.branch(),challenge.email(),challenge.phone(),EmailOtpPurpose.GUARANTOR_SIGNATURE,
                messages.getMessage("group.otpSubject",null,locale),messages.getMessage("group.otpIntro",null,locale),challenge.code(),challenge.ttlMinutes(),challenge.loan(),challenge.client(),challenge.client());
        }catch(IllegalStateException failure){throw new IllegalArgumentException("group.error.otpDelivery");}
        consents.codeDelivered(actor,cycle,challenge.id());
    }
}
