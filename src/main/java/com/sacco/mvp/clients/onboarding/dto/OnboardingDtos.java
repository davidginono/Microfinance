package com.sacco.mvp.clients.onboarding.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;

public final class OnboardingDtos {
    private OnboardingDtos() {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record ContactCheck(boolean valid, boolean exists) {}
    public record ProfileDraft(String profile,UUID actor,String institution,String branch,UUID requestKey) implements java.io.Serializable {}
    public record Client(UUID id, String number, String name, String phone, String email, String state,
                         UUID revisionId, int sequence, LocalDate reviewDue, boolean portalEnabled) {
        public Client(UUID id,String number,String name,String phone,String email,String state,UUID revisionId,int sequence,LocalDate reviewDue) {
            this(id,number,name,phone,email,state,revisionId,sequence,reviewDue,true);
        }
    }
    public record Revision(UUID id, UUID clientId, int sequence, String mode, Profile profile,
                           UUID maker, String makerName, OffsetDateTime recordedAt) {}
    public record Decision(UUID id, String kind, String reason, String clientMessage, String screening, String screeningReference,
                           LocalDate checkedOn, LocalDate reviewDue, String policyReference,
                           UUID actor, String actorName, OffsetDateTime recordedAt) {}
    public record Document(UUID id, String category, String name, LocalDate expiresOn, String checksum,
                           String actor, OffsetDateTime recordedAt, long sizeBytes, String contentType) {}
    public record History(int sequence, String mode, String result, String actor, OffsetDateTime recordedAt) {}
    public record Download(byte[] bytes, String contentType, String name) {
        public boolean isImage() {
            return "image/jpeg".equals(contentType) || "image/png".equals(contentType);
        }
    }
    public record OwnStatus(String state, LocalDate reviewDue) {}
    public record Notice(UUID id, String version, String english, String kiswahili) {
        public static final UUID STANDARD_ID=UUID.fromString("59bf1107-d9c1-44ca-a91f-e4b266123d3c");
    }
    public record OwnDocument(UUID id, String category, String name, LocalDate expiresOn,
                              long sizeBytes, String contentType, OffsetDateTime recordedAt) {}
    public record Feedback(String kind, String message, OffsetDateTime recordedAt) {}
    public record OwnHistory(int sequence, String result, OffsetDateTime recordedAt) {}
    public record OwnRevision(int sequence, ClientProfile profile, String noticeVersion, String noticeEnglish,
                              String noticeKiswahili, boolean acknowledged, OffsetDateTime acknowledgedAt) {}
    public record OwnWorkspace(Client client, OwnRevision revision, List<OwnDocument> documents,
                               Feedback feedback, Page<OwnHistory> history, boolean historical,
                               List<EvidenceRequirement> evidenceRequirements, LocalDate validationDate) {}
    public record EvidenceRequirement(String category, String status) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    @Data public static class ClientProfile {
        private String firstName="", middleName="", lastName="", phone="", email="";
        private String gender="", maritalStatus="", familiarName="";
        private Integer dependants;
        private UUID groupId;
        private String groupName="";
        private String identityType="", issuingCountry="", identityNumber="", address="";
        private String postalAddress="", city="", ward="", villageStreet="", nearbyPlace="";
        private Boolean hasBusiness;
        private String businessName="", businessType="", businessTin="", businessLicence="", businessLocation="", businessWard="", businessCity="", otherIncomeSources="";
        private java.math.BigDecimal dailySales, dailyExpenditure;
        private String spouseName="", spouseRelationship="", spousePhone="", spouseWork="", spouseWorkLocation="";
        private String bankName="", bankAccountName="", bankAccountNumber="";
        private String activity="", activityAddress="", sourceOfFunds="";
        @com.fasterxml.jackson.annotation.JsonIgnore
        public List<String> getIncomeActivities() {
            if(activity==null||activity.isBlank())return List.of();
            var choices=new ArrayList<String>();
            for(String value:activity.split(";")) {
                String choice=switch(value.strip().toLowerCase(Locale.ROOT)) {case "business" -> "BUSINESS";case "employment" -> "EMPLOYMENT";case "other" -> "OTHER";default -> value.strip();};
                if(!choice.isEmpty()&&!choices.contains(choice))choices.add(choice);
            }
            return choices.stream().noneMatch(Set.of("BUSINESS","EMPLOYMENT","OTHER")::contains)?List.of(activity):choices;
        }
        public void setIncomeActivities(List<String> choices) {
            activity=choices==null?"":choices.stream().map(value->switch(value) {case "BUSINESS" -> "Business";case "EMPLOYMENT" -> "Employment";case "OTHER" -> "Other";default -> value;}).distinct().collect(java.util.stream.Collectors.joining("; "));
        }
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate, identityExpiresOn;
        private boolean identityNoExpiry;
        public Boolean getHasBusiness() {
            if(hasBusiness!=null)return hasBusiness;
            // Existing profiles predate this question; retain their recorded business details.
            return java.util.stream.Stream.of(businessName,businessType,businessTin,businessLicence,businessLocation,businessWard,businessCity).anyMatch(v->v!=null&&!v.isBlank()) || dailySales!=null || dailyExpenditure!=null ? Boolean.TRUE : null;
        }
    }
    @Data public static class SelfForm {
        private UUID requestKey=UUID.randomUUID(), noticeId;
        private int expectedSequence;
        private ClientProfile profile=new ClientProfile();
        private String action="DRAFT";
        private boolean noticeAcknowledged, declarationAccepted;
    }
    @Data @lombok.EqualsAndHashCode(callSuper=true) public static class Profile extends ClientProfile {
        private String fullName="", clientNumber="";
        private String noticeVersion="", lawfulBasis="", consentReference="";
        private String retentionReference="", legalHoldReference="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate consentOn, retentionReviewOn;
        private boolean selfService, noticeAcknowledged, declarationAccepted;
        private UUID noticeId;
        private String noticeEnglish="", noticeKiswahili="";
        private OffsetDateTime acknowledgedAt;
    }
    @Data public static class ProfileForm {
        private UUID requestKey=UUID.randomUUID();
        private int expectedSequence;
        private Profile profile=new Profile();
        private String action="DRAFT";
    }
    @Data public static class CreateForm {
        private UUID requestKey=UUID.randomUUID();
        private String name="", phone="", email="";
        private ClientProfile profile=new ClientProfile();
        @com.fasterxml.jackson.annotation.JsonIgnore private List<CreationDocumentForm> documents=new ArrayList<>();
        public String getName() {
            return profile!=null&&(!profile.getFirstName().isBlank()||!profile.getLastName().isBlank())
                ? java.util.stream.Stream.of(profile.getFirstName(),profile.getMiddleName(),profile.getLastName()).filter(v->v!=null&&!v.isBlank()).map(String::strip).collect(java.util.stream.Collectors.joining(" ")):name;
        }
        public String getPhone() {return profile!=null&&!profile.getPhone().isBlank()?profile.getPhone():phone;}
        public String getEmail() {return profile!=null&&!profile.getEmail().isBlank()?profile.getEmail():email;}
    }
    @Data public static class CreationDocumentForm {
        private String category="IDENTITY";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate expiresOn;
        private org.springframework.web.multipart.MultipartFile file;
    }
    public record GroupChoice(UUID id,String label) {}
    @Data public static class ReviewForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID revisionId;
        private String kind="VERIFY", reason="", clientMessage="";
        @com.fasterxml.jackson.annotation.JsonIgnore private String screening="UNAVAILABLE", screeningReference="", policyReference="";
        private boolean identityChecked, addressChecked, activityChecked, contactChecked, duplicatesChecked;
        @com.fasterxml.jackson.annotation.JsonIgnore private boolean checksConfirmed;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate checkedOn;
        @com.fasterxml.jackson.annotation.JsonIgnore private LocalDate reviewDue;

        public void confirmChecks() {
            identityChecked = addressChecked = activityChecked = contactChecked = duplicatesChecked = true;
        }
    }
    @Data public static class DocumentForm {
        public static final int MAX_FILES=10, MAX_FILE_BYTES=1024*1024, MAX_TOTAL_BYTES=MAX_FILES*MAX_FILE_BYTES;
        private UUID requestKey=UUID.randomUUID();
        private UUID revisionId;
        private String category="IDENTITY";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate expiresOn;
        private int remainingFiles=MAX_FILES;
        private long remainingBytes=MAX_TOTAL_BYTES;
        @com.fasterxml.jackson.annotation.JsonIgnore public int getRemainingFiles(){return remainingFiles;}
        @com.fasterxml.jackson.annotation.JsonIgnore public long getRemainingBytes(){return remainingBytes;}
        @com.fasterxml.jackson.annotation.JsonIgnore public int getMaxFileBytes(){return MAX_FILE_BYTES;}
    }
    public record UploadUsage(long files,long bytes) {}
}
