package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "platform_branding_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformBrandingSettings {
    public static final String DEFAULT_ID = "DEFAULT";

    @Id
    private String id;

    @Column(name = "logo_min_width_px", nullable = false)
    private int logoMinWidthPx;

    @Column(name = "logo_min_height_px", nullable = false)
    private int logoMinHeightPx;

    @Column(name = "logo_max_width_px", nullable = false)
    private int logoMaxWidthPx;

    @Column(name = "logo_max_height_px", nullable = false)
    private int logoMaxHeightPx;

    @Column(name = "logo_max_file_size_kb", nullable = false)
    private int logoMaxFileSizeKb;

    @Column(name = "updated_by_member_id")
    private UUID updatedByMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
