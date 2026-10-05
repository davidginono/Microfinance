package com.sacco.mvp.reporting.operational.repository;

import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface OperationalTemplateRepository extends JpaRepository<OperationalReportTemplate, UUID> {
    Optional<OperationalReportTemplate> findByIdAndSaccoId(UUID id, String saccoId);
    @Query("select t from OperationalReportTemplate t where t.saccoId=:institution and t.state<>:retired and t.dataset in :datasets and (t.visibility=:visibility or t.creatorId=:actor or t.publisherId=:actor)")
    Page<OperationalReportTemplate> visible(@Param("institution") String institution, @Param("actor") UUID actor,
                                           @Param("datasets") Collection<String> datasets,
                                           @Param("retired") OperationalReportTemplate.State retired,
                                           @Param("visibility") OperationalReportTemplate.Visibility visibility, Pageable page);
    @Query("select coalesce(max(t.reportVersion), 0) from OperationalReportTemplate t where t.saccoId=:institution and t.familyId=:family")
    int latestVersion(@Param("institution") String institution, @Param("family") UUID family);
}
