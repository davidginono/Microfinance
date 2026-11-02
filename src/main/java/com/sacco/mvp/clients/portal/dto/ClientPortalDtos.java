package com.sacco.mvp.clients.portal.dto;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.CreateForm;
import lombok.Data;
import java.io.Serializable;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ClientPortalDtos {
    private ClientPortalDtos() {}
    public record Session(UUID clientId,String institution,String branch,String contactHash,boolean loanAccess,OffsetDateTime expiresAt) implements Principal,Serializable {
        public String getName(){return clientId.toString();}
    }
    @Data public static class EntryForm {
        private String clientNumber="", intent="PROFILE", institution="", branch="";
        private CreateForm registration=new CreateForm();
    }
    public record Choice(String id,String label) {}
    public record Challenge(UUID id,UUID clientId,String institution,String branch,String intent,String contactHash,String payload,String codeHash,OffsetDateTime expiresAt,OffsetDateTime deliveredAt,OffsetDateTime consumedAt,int attempts) {}
}
