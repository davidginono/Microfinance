package com.sacco.mvp.clients.onboarding.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;

public final class OnboardingDtos {
    private OnboardingDtos() {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record Client(UUID id, String number, String name, String phone, String email, String state,
                         UUID revisionId, int sequence, LocalDate reviewDue) {}
    public record Revision(UUID id, UUID clientId, int sequence, String mode, Profile profile,
                           UUID maker, String makerName, OffsetDateTime recordedAt) {}
    public record Decision(UUID id, String kind, String reason, String clientMessage, String screening, String screeningReference,
                           LocalDate checkedOn, LocalDate reviewDue, String policyReference,
                           UUID actor, String actorName, OffsetDateTime recordedAt) {}
    public record Document(UUID id, String category, String name, LocalDate expiresOn, String checksum,
                           String actor, OffsetDateTime recordedAt) {}
    public record History(int sequence, String mode, String result, String actor, OffsetDateTime recordedAt) {}
    public record Download(byte[] bytes, String contentType, String name) {}
    public record OwnStatus(String state, LocalDate reviewDue) {}
    public record Notice(UUID id, int sequence, boolean enabled, String version, String english, String kiswahili,
                         String lawfulBasis, String retentionReference, LocalDate retentionReviewOn) {}
    public record OwnDocument(UUID id, String category, String name, LocalDate expiresOn) {}
    public record Feedback(String kind, String message, OffsetDateTime recordedAt) {}
    public record OwnHistory(int sequence, String result, OffsetDateTime recordedAt) {}
    public record OwnRevision(int sequence, ClientProfile profile, String noticeVersion, String noticeEnglish,
                              String noticeKiswahili, boolean acknowledged, OffsetDateTime acknowledgedAt) {}
    public record OwnWorkspace(Client client, OwnRevision revision, List<OwnDocument> documents,
                               Feedback feedback, Page<OwnHistory> history, boolean historical) {}
    @Data public static class ClientProfile {
        private String identityType="", issuingCountry="", identityNumber="", address="";
        private String activity="", activityAddress="", sourceOfFunds="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate, identityExpiresOn;
        private boolean identityNoExpiry;
    }
    @Data public static class SelfForm {
        private UUID requestKey=UUID.randomUUID(), noticeId;
        private int expectedSequence;
        private ClientProfile profile=new ClientProfile();
        private String action="DRAFT";
        private boolean noticeAcknowledged, declarationAccepted;
    }
    @Data public static class NoticeForm {
        private UUID requestKey=UUID.randomUUID();
        private int expectedSequence;
        private boolean enabled;
        private String version="", english="", kiswahili="", lawfulBasis="", retentionReference="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate retentionReviewOn;
    }
    @Data public static class Profile {
        private String fullName="", phone="", email="", clientNumber="";
        private String identityType="", issuingCountry="", identityNumber="", address="";
        private String activity="", activityAddress="", sourceOfFunds="", noticeVersion="", lawfulBasis="", consentReference="";
        private String retentionReference="", legalHoldReference="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate, identityExpiresOn, consentOn, retentionReviewOn;
        private boolean identityNoExpiry;
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
        private String number="", name="", phone="", email="";
    }
    @Data public static class ReviewForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID revisionId;
        private String kind="VERIFY", reason="", clientMessage="", screening="UNAVAILABLE", screeningReference="", policyReference="";
        private boolean identityChecked, addressChecked, activityChecked, contactChecked, duplicatesChecked;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate checkedOn, reviewDue;
    }
    @Data public static class DocumentForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID revisionId;
        private String category="IDENTITY";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate expiresOn;
    }
}
