package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.grouplending.dto.SharedCreditExposure;
import com.sacco.mvp.grouplending.repository.SharedCreditExposureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SharedCreditExposureService {
    private final SharedCreditExposureRepository exposure;
    private final MessageSource messages;
    /** Called by the authorized underwriting use case. Null branch returns only this client's aggregate risk. */
    @Transactional(readOnly=true) public SharedCreditExposure summary(String institution,UUID client,String branch){
        if(institution==null||institution.isBlank()||client==null)throw new org.springframework.security.access.AccessDeniedException("Credit scope required");
        return exposure.summary(institution,client,branch);
    }
    public String message(String key){return messages.getMessage("group.error."+key,null,LocaleContextHolder.getLocale());}
    public void requireKnown(SharedCreditExposure facts){if(!facts.known())throw new IllegalStateException(message("sharedCreditHistory"));}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public SharedCreditExposure lockForRecording(com.sacco.mvp.security.AppUserPrincipal actor,UUID client){
        if(actor==null||!actor.isStaffSession()||!actor.getClaims().contains(com.sacco.mvp.domain.UserClaim.LOAN_RECORDING_CREATE.name())
            ||!exposure.lock(actor.getSaccoId(),actor.getStationId(),client))throw new org.springframework.security.access.AccessDeniedException("Credit scope required");
        var facts=summary(actor.getSaccoId(),client,actor.getStationId());
        if(!facts.known())throw new IllegalArgumentException("recording.error.sharedCreditHistory");
        if(facts.pending()>0)throw new IllegalArgumentException("recording.error.sharedApplicationPending");
        return facts;
    }
}
