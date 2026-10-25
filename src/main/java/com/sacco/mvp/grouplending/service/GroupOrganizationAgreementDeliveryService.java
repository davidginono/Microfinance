package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.StationOtpDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationAgreementDeliveryService {
    private final GroupOrganizationAgreementService agreements;
    private final StationOtpDeliveryService transport;
    private final MessageSource messages;
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void send(AppUserPrincipal actor,UUID cycle,String hash,Locale locale){
        var code=agreements.prepareCode(actor,cycle,hash);
        try{transport.deliver(code.institution(),code.branch(),code.email(),code.phone(),EmailOtpPurpose.APPLICANT_SIGNATURE,
            messages.getMessage("orgAgreement.otpSubject",null,locale),messages.getMessage("orgAgreement.otpIntro",null,locale),code.code(),code.ttlMinutes(),code.loan(),null,code.client());}
        catch(IllegalStateException failure){throw new IllegalArgumentException("group.error.otpDelivery");}
        agreements.codeDelivered(actor,cycle,code.id());
    }
}
