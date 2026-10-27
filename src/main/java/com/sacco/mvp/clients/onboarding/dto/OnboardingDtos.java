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
    public record Decision(UUID id, String kind, String reason, String screening, String screeningReference,
                           LocalDate checkedOn, LocalDate reviewDue, String policyReference,
                           UUID actor, String actorName, OffsetDateTime recordedAt) {}
    public record Document(UUID id, String category, String name, LocalDate expiresOn, String checksum,
                           String actor, OffsetDateTime recordedAt) {}
    public record History(int sequence, String mode, String result, String actor, OffsetDateTime recordedAt) {}
    public record Download(byte[] bytes, String contentType, String name) {}
    public record OwnStatus(String state, LocalDate reviewDue) {}
    @Data public static class Profile {
        private String fullName="", phone="", email="", clientNumber="";
        private String identityType="", issuingCountry="", identityNumber="", address="";
        private String activity="", activityAddress="", sourceOfFunds="", noticeVersion="", lawfulBasis="", consentReference="";
        private String retentionReference="", legalHoldReference="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate, identityExpiresOn, consentOn, retentionReviewOn;
        private boolean identityNoExpiry;
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
        private String kind="VERIFY", reason="", screening="UNAVAILABLE", screeningReference="", policyReference="";
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
