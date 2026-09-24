package com.sacco.mvp.repository;

import com.sacco.mvp.domain.StoredUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredUploadRepository extends JpaRepository<StoredUpload, UUID> {
    Optional<StoredUpload> findByIdAndOwnerTypeAndOwnerId(UUID id, String ownerType, String ownerId);
    Optional<StoredUpload> findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByUpdatedAtDesc(
        String ownerType, String ownerId, String category);
    List<StoredUpload> findByOwnerTypeAndOwnerId(String ownerType, String ownerId);
    List<StoredUpload> findByOwnerTypeAndOwnerIdAndCategory(String ownerType, String ownerId, String category);
    boolean existsByOwnerTypeAndOwnerIdAndCategory(String ownerType, String ownerId, String category);
    long deleteByOwnerTypeAndOwnerId(String ownerType, String ownerId);
    long deleteByOwnerTypeAndOwnerIdAndCategory(String ownerType, String ownerId, String category);

    @Query("""
        select upload
        from StoredUpload upload
        where upload.content is not null
          and (upload.storageKey is null or upload.storageKey = '')
        order by upload.updatedAt asc
        """)
    List<StoredUpload> findDatabaseBackedUploads(org.springframework.data.domain.Pageable pageable);
}
