package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientMemberIdOrderByCreatedAtDesc(UUID recipientMemberId);
    long countByRecipientMemberId(UUID recipientMemberId);
    long countByRecipientMemberIdAndReadAtIsNull(UUID recipientMemberId);
    long countByRecipientMemberIdAndReadAtIsNullAndTypeNotIn(UUID recipientMemberId, Collection<String> types);

    long countByStatus(com.sacco.mvp.domain.NotificationStatus status);

    List<Notification> findTop50ByRecipientMemberIdOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop5ByRecipientMemberIdOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientMemberId);
    List<Notification> findTop100ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(UUID recipientMemberId, String type);
    List<Notification> findTop200ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(UUID recipientMemberId, String type);

    List<Notification> findByRecipientMemberIdAndTypeOrderByCreatedAtDesc(UUID recipientMemberId, String type);
    List<Notification> findByTypeOrderByCreatedAtDesc(String type);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.recipientMemberId = :memberId and n.readAt is null")
    int markAllAsReadForMember(@Param("memberId") UUID memberId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.recipientMemberId = :memberId and n.type in :types and n.readAt is null")
    int markAllAsReadForMemberAndTypes(@Param("memberId") UUID memberId, @Param("types") Collection<String> types);
}
