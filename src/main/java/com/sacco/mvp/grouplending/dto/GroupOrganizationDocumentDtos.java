package com.sacco.mvp.grouplending.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GroupOrganizationDocumentDtos {
    private GroupOrganizationDocumentDtos() {}
    @Data public static class DocumentForm {
        private UUID documentRequestKey=UUID.randomUUID(),expectedDocumentId;
        private String documentReference="",documentLanguage="",institutionAddress="",contactPhone="",contactEmail="",complaintPhone="",complaintEmail="",complaintAddress="";
        private boolean documentChecked;
        public boolean hasEnteredDetails(){return documentChecked||java.util.stream.Stream.of(documentReference,documentLanguage,institutionAddress,contactPhone,contactEmail,complaintPhone,complaintEmail,complaintAddress).anyMatch(s->s!=null&&!s.isBlank());}
    }
    public record Document(UUID id,int revision,String filename,String language,String sha256,long sizeBytes,int pageCount,String reference,
        String institutionName,String branch,String institutionAddress,String contactPhone,String contactEmail,
        String complaintPhone,String complaintEmail,String complaintAddress,OffsetDateTime recordedAt) {}
    public record Signature(String name,String number,OffsetDateTime at,boolean otpRequired) {}
}
