package com.sacco.mvp.grouplending.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GroupOrganizationDocumentDtos {
    private GroupOrganizationDocumentDtos() {}
    public record Document(UUID id,int revision,String filename,String language,String sha256,long sizeBytes,int pageCount,String reference,
        String institutionName,String branch,String institutionAddress,String contactPhone,String contactEmail,
        String complaintPhone,String complaintEmail,String complaintAddress,OffsetDateTime recordedAt) {}
    public record Signature(String name,String number,OffsetDateTime at,boolean otpRequired,boolean lender,String preferredLanguage,boolean paper) {
        public Signature(String name,String number,OffsetDateTime at,boolean otpRequired,boolean lender,String preferredLanguage){this(name,number,at,otpRequired,lender,preferredLanguage,false);}
        public Signature(String name,String number,OffsetDateTime at,boolean otpRequired,boolean lender){this(name,number,at,otpRequired,lender,null);}
        public Signature(String name,String number,OffsetDateTime at,boolean otpRequired){this(name,number,at,otpRequired,false);}
    }
}
