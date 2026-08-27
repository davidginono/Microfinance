package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    long countByRecipientMemberId(UUID recipientMemberId);

    @Query(
        value = """
            select count(*)
            from notifications n
            where n.recipient_member_id = :recipientId
              and n.read_at is null
              and upper(coalesce(n.payload -> 'details' ->> 'source', '')) <> 'SYNC'
            """,
        nativeQuery = true
    )
    long countUnreadExcludingRepaymentSync(@Param("recipientId") UUID recipientId);

    @Query(
        value = """
            select count(*)
            from notifications n
            where n.recipient_member_id = :recipientId
              and n.read_at is null
              and n.type not in (:types)
              and upper(coalesce(n.payload -> 'details' ->> 'source', '')) <> 'SYNC'
            """,
        nativeQuery = true
    )
    long countUnreadExcludingRepaymentSyncAndTypeNotIn(@Param("recipientId") UUID recipientId,
                                                       @Param("types") Collection<String> types);

    long countByStatus(com.sacco.mvp.domain.NotificationStatus status);

    List<Notification> findTop50ByRecipientMemberIdOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop5ByRecipientMemberIdOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop100ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(UUID recipientMemberId, String type);
    List<Notification> findTop200ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(UUID recipientMemberId, String type);


    @Query(
        value = """
            select exists (
                select 1
                from notifications n
                where n.recipient_member_id = :recipientId
                  and n.type = :type
                  and n.payload = cast(:payload as jsonb)
            )
            """,
        nativeQuery = true
    )
    boolean existsDeliveredDuplicate(@Param("recipientId") UUID recipientId,
                                     @Param("type") String type,
                                     @Param("payload") String payload);

    @Query(
        value = """
            select exists (
                select 1
                from notifications n
                where n.recipient_member_id = :recipientId
                  and n.type = :type
                  and n.payload -> 'details' ->> 'loanId' = :loanId
                  and n.payload -> 'details' ->> 'reviewStage' = :reviewStage
            )
            """,
        nativeQuery = true
    )
    boolean existsDeliveredStaffReviewDuplicate(@Param("recipientId") UUID recipientId,
                                                @Param("type") String type,
                                                @Param("loanId") String loanId,
                                                @Param("reviewStage") String reviewStage);

    @Query(
        value = """
            select exists (
                select 1
                from notifications n
                where n.recipient_member_id = :recipientId
                  and n.type = 'REPAYMENT_REMINDER'
                  and n.created_at >= :since
                  and n.payload -> 'details' ->> 'loanId' = :loanId
                  and n.payload -> 'details' ->> 'daysLeft' = :daysLeft
            )
            """,
        nativeQuery = true
    )
    boolean existsRepaymentReminder(@Param("recipientId") UUID recipientId,
                                    @Param("loanId") String loanId,
                                    @Param("daysLeft") String daysLeft,
                                    @Param("since") OffsetDateTime since);

    @Query(
        value = """
            select distinct cast(n.payload -> 'details' ->> 'incidentId' as uuid)
            from notifications n
            where n.type = 'SUPPORT_MESSAGE'
              and n.read_at is not null
              and cast(n.payload -> 'details' ->> 'incidentId' as uuid) in (:incidentIds)
            """,
        nativeQuery = true
    )
    List<UUID> findReadSupportIncidentIds(@Param("incidentIds") Collection<UUID> incidentIds);

    @Modifying
    @Query(
        value = """
            update notifications n
            set read_at = now()
            where n.recipient_member_id = :recipientId
              and n.type = 'SUPPORT_MESSAGE'
              and n.read_at is null
              and cast(n.payload -> 'details' ->> 'incidentId' as uuid) = :incidentId
            """,
        nativeQuery = true
    )
    int markSupportIncidentAsRead(@Param("recipientId") UUID recipientId,
                                  @Param("incidentId") UUID incidentId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.recipientMemberId = :memberId and n.readAt is null")
    int markAllAsReadForMember(@Param("memberId") UUID memberId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.recipientMemberId = :memberId and n.type in :types and n.readAt is null")
    int markAllAsReadForMemberAndTypes(@Param("memberId") UUID memberId, @Param("types") Collection<String> types);

    @Modifying
    long deleteByReadAtIsNotNullAndCreatedAtBefore(OffsetDateTime cutoff);
}
