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
public class GroupPersonalGuaranteeDeliveryService {
    private final GroupPersonalGuaranteeService guarantees;
    private final StationOtpDeliveryService transport;
    private final MessageSource messages;
    @Transactional(propagation=Propagation.NOT_SUPPORTED) public void send(AppUserPrincipal a,UUID id,String hash,Locale locale){var code=guarantees.prepareCode(a,id,hash);try{transport.deliver(code.institution(),code.branch(),code.email(),code.phone(),EmailOtpPurpose.GUARANTOR_SIGNATURE,messages.getMessage("personalGuarantee.otpSubject",null,locale),messages.getMessage("personalGuarantee.otpIntro",null,locale),code.code(),code.ttlMinutes(),code.loan(),null,code.client());}catch(IllegalStateException failed){throw new IllegalArgumentException("group.error.otpDelivery");}guarantees.codeDelivered(a,id,code.id());}
}
