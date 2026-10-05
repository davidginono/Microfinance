package com.sacco.mvp.reporting.operational.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="operational_report_templates")
@Getter @Setter @NoArgsConstructor
public class OperationalReportTemplate {
    public enum State { DRAFT, PUBLISHED, RETIRED }
    public enum Visibility { PRIVATE, INSTITUTION }
    @Id private UUID id;
    @Column(name="sacco_id", nullable=false) private String saccoId;
    @Column(name="family_id", nullable=false) private UUID familyId;
    @Column(name="report_version", nullable=false) private int reportVersion;
    @Column(nullable=false, length=120) private String name;
    @Column(name="definition_json", nullable=false, columnDefinition="text") private String definitionJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private State state;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Visibility visibility;
    @Column(nullable=false, length=24) private String dataset;
    @Column(name="creator_id", nullable=false) private UUID creatorId;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="publisher_id") private UUID publisherId;
    @Column(name="published_at") private OffsetDateTime publishedAt;
    @Version private Long version;
}
