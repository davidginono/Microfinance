package com.sacco.mvp.repository;

import com.sacco.mvp.domain.StoredUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredUploadRepository extends JpaRepository<StoredUpload, UUID> {
    boolean existsByOwnerTypeAndOwnerIdAndCategory(String ownerType, String ownerId, String category);

    Optional<StoredUpload> findByIdAndOwnerTypeAndOwnerId(UUID id, String ownerType, String ownerId);

    Optional<StoredUpload> findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByUpdatedAtDesc(
        String ownerType, String ownerId, String category);

    void deleteByOwnerTypeAndOwnerId(String ownerType, String ownerId);

    void deleteByOwnerTypeAndOwnerIdAndCategory(String ownerType, String ownerId, String category);
}
