package com.sacco.mvp.reporting.operational.service;

import com.sacco.mvp.reporting.operational.dto.*;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate;
import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate.State;
import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate.Visibility;
import com.sacco.mvp.reporting.operational.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service @RequiredArgsConstructor
public class OperationalReportService {
    private final OperationalTemplateRepository templates;
    private final OperationalReportRepository reports;
    private final OperationalReportCatalog catalog;
    private final ReportDefinitionValidator validator;
    private final AccessControlService access;
    private final ApplicationClock clock;
    private final ObjectMapper mapper;
    private final MessageSource messages;

    public List<OperationalReportCatalog.DatasetSpec> catalog(AppUserPrincipal actor) {
        scope(actor, "REPORT_TEMPLATES_VIEW");
        return Arrays.stream(Dataset.values()).map(catalog::dataset).filter(d -> access.has(actor, d.requiredClaim())).toList();
    }
    public ReportDefinition system(AppUserPrincipal actor, Dataset dataset) {
        scope(actor, "REPORT_TEMPLATES_VIEW"); datasetAccess(actor, dataset);
        return catalog.system(dataset, clock.today());
    }
    @Transactional(readOnly=true)
    public Page<OperationalTemplateView> templates(AppUserPrincipal actor, int page) {
        scope(actor, "REPORT_TEMPLATES_VIEW"); page(page, 25);
        var allowed = Arrays.stream(Dataset.values()).filter(d -> access.has(actor,catalog.dataset(d).requiredClaim())).map(Enum::name).toList();
        if (allowed.isEmpty()) return Page.empty();
        return templates.visible(actor.getSaccoId(), actor.getMemberId(), allowed, State.RETIRED, Visibility.INSTITUTION,
            PageRequest.of(page, 25, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))).map(this::view);
    }
    @Transactional(readOnly=true)
    public OperationalTemplateView definition(AppUserPrincipal actor, UUID id) {
        scope(actor, "REPORT_TEMPLATES_VIEW");
        var template = owned(actor, id); var result = view(template);
        datasetAccess(actor, result.definition().dataset()); return result;
    }
    @Transactional
    public OperationalTemplateView saveDraft(AppUserPrincipal actor, UUID id, Long expectedVersion, String name, ReportDefinition definition) {
        scope(actor, id == null ? "REPORT_TEMPLATES_CREATE" : "REPORT_TEMPLATES_UPDATE");
        ReportDefinitionValidator.safeText(name, 120);
        if (name.isBlank()) throw new IllegalArgumentException("opreport.error.definition");
        var validated = validator.validate(definition); datasetAccess(actor, validated.dataset());
        OperationalReportTemplate entity;
        if (id == null) {
            entity = new OperationalReportTemplate(); entity.setId(UUID.randomUUID()); entity.setFamilyId(entity.getId());
            entity.setReportVersion(1); entity.setSaccoId(actor.getSaccoId()); entity.setCreatorId(actor.getMemberId());
            entity.setCreatedAt(clock.now()); entity.setState(State.DRAFT);
            entity.setVisibility(Visibility.PRIVATE);
        } else {
            entity = owned(actor, id); requireVersion(entity, expectedVersion);
            if (entity.getState() != State.DRAFT) throw new IllegalArgumentException("opreport.error.immutable");
        }
        entity.setName(name); entity.setDefinitionJson(encode(validated)); entity.setDataset(validated.dataset().name());
        templates.saveAndFlush(entity);
        reports.event(entity.getId(), actor.getSaccoId(), actor.getMemberId(), id == null ? "CREATED" : "UPDATED", entity.getDefinitionJson(), clock.now());
        return view(entity);
    }
    @Transactional
    public OperationalTemplateView cloneVersion(AppUserPrincipal actor, UUID id) {
        scope(actor, "REPORT_TEMPLATES_CREATE");
        var source = owned(actor, id); var definition = decode(source.getDefinitionJson()); datasetAccess(actor, definition.dataset());
        validator.validate(definition);
        var clone = new OperationalReportTemplate(); clone.setId(UUID.randomUUID()); clone.setSaccoId(actor.getSaccoId());
        clone.setFamilyId(source.getFamilyId()); clone.setReportVersion(templates.latestVersion(actor.getSaccoId(), source.getFamilyId()) + 1);
        clone.setName(source.getName()); clone.setDefinitionJson(source.getDefinitionJson()); clone.setCreatorId(actor.getMemberId());
        clone.setCreatedAt(clock.now()); clone.setState(State.DRAFT); clone.setVisibility(Visibility.PRIVATE);
        clone.setDataset(source.getDataset()); templates.saveAndFlush(clone);
        reports.event(clone.getId(), actor.getSaccoId(), actor.getMemberId(), "CLONED", clone.getDefinitionJson(), clock.now()); return view(clone);
    }
    @Transactional
    public OperationalTemplateView publish(AppUserPrincipal actor, UUID id, Long expectedVersion) {
        scope(actor, "REPORT_TEMPLATES_PUBLISH");
        var entity = owned(actor, id); requireVersion(entity, expectedVersion);
        if (entity.getState() != State.DRAFT) throw new IllegalArgumentException("opreport.error.immutable");
        var d = validator.validate(decode(entity.getDefinitionJson())); datasetAccess(actor, d.dataset());
        entity.setState(State.PUBLISHED); entity.setPublisherId(actor.getMemberId()); entity.setPublishedAt(clock.now());
        templates.saveAndFlush(entity);
        reports.event(id, actor.getSaccoId(), actor.getMemberId(), "PUBLISHED", entity.getDefinitionJson(), clock.now()); return view(entity);
    }
    @Transactional
    public void retire(AppUserPrincipal actor, UUID id, Long expectedVersion) {
        scope(actor, "REPORT_TEMPLATES_PUBLISH"); var entity = owned(actor, id); requireVersion(entity, expectedVersion);
        datasetAccess(actor, decode(entity.getDefinitionJson()).dataset());
        if (entity.getState() != State.PUBLISHED) throw new IllegalArgumentException("opreport.error.immutable");
        entity.setState(State.RETIRED); templates.saveAndFlush(entity);
        reports.event(id, actor.getSaccoId(), actor.getMemberId(), "RETIRED", entity.getDefinitionJson(), clock.now());
    }
    @Transactional
    public void share(AppUserPrincipal actor, UUID id, Long expectedVersion, boolean institutionVisible) {
        scope(actor,"REPORT_TEMPLATES_SHARE"); var entity=owned(actor,id); requireVersion(entity,expectedVersion);
        datasetAccess(actor,decode(entity.getDefinitionJson()).dataset());
        if (entity.getState()==State.RETIRED) throw new IllegalArgumentException("opreport.error.immutable");
        entity.setVisibility(institutionVisible ? Visibility.INSTITUTION : Visibility.PRIVATE); templates.saveAndFlush(entity);
        reports.event(id,actor.getSaccoId(),actor.getMemberId(),institutionVisible ? "SHARED" : "UNSHARED",entity.getDefinitionJson(),clock.now());
    }
    /** Rechecks claims and dataset scope on every page/job invocation. Repeatable read keeps page/totals coherent. */
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public OperationalReportResult run(AppUserPrincipal actor, UUID id, int page, int size) {
        scope(actor, "REPORTS_RUN"); page(page, size);
        var template = owned(actor, id);
        if (template.getState() != State.PUBLISHED) throw new IllegalArgumentException("opreport.error.publishFirst");
        return execute(actor, id, template.getReportVersion(), decode(template.getDefinitionJson()), clock.now(), page, size);
    }
    /** Export workers reuse the first authorized cutoff and recheck publication/dataset/branch on every batch. */
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public OperationalReportResult runAt(AppUserPrincipal actor, UUID id, int page, int size, java.time.OffsetDateTime cutoff) {
        scope(actor, "REPORTS_RUN"); page(page, size);
        if (cutoff == null || cutoff.isAfter(clock.now())) throw new IllegalArgumentException("opreport.error.definition");
        var template = owned(actor, id);
        if (template.getState() != State.PUBLISHED) throw new IllegalArgumentException("opreport.error.publishFirst");
        return execute(actor, id, template.getReportVersion(), decode(template.getDefinitionJson()), cutoff, page, size);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public OperationalReportResult preview(AppUserPrincipal actor, ReportDefinition definition, int page, int size) {
        scope(actor, "REPORTS_RUN"); scope(actor, "REPORT_TEMPLATES_VIEW"); page(page, size);
        return execute(actor, null, 0, definition, clock.now(), page, size);
    }
    private OperationalReportResult execute(AppUserPrincipal actor, UUID id, int version, ReportDefinition definition, java.time.OffsetDateTime cutoff, int page, int size) {
        var d = validator.validate(definition); datasetAccess(actor, d.dataset());
        var query = reports.query(actor.getSaccoId(), actor.getStationId(), d, cutoff, page, size);
        Locale language = Locale.forLanguageTag(d.language().name().toLowerCase(Locale.ROOT));
        var columns = d.columns().stream().map(c -> {
            String label = messages.getMessage(catalog.field(d.dataset(), c.field()).messageKey(), null, language);
            if (!c.label().isBlank()) label = c.label() + " · " + label;
            if (c.format() == Format.MONEY) label += " (TZS)";
            return new OperationalReportResult.Column(OperationalReportRepository.key(c.field()), label, c.format(), c.width());
        }).toList();
        Map<String,String> labels=new LinkedHashMap<>();
        for(var field:catalog.dataset(d.dataset()).fields()) {
            String label=messages.getMessage(field.messageKey(),null,language)+(field.format()==Format.MONEY ? " (TZS)" : "");
            labels.put("field."+OperationalReportRepository.key(field.id()),label);
        }
        for(String value:List.of("CASH","BANK","MOBILE_MONEY","PAYMENT","REVERSAL","LOCAL_LEDGER","UNAVAILABLE","DISBURSED","PAR","DEFAULTED","PAID"))
            labels.put("value."+value,messages.getMessage("opreport.value."+value,null,language));
        for(String key:List.of("version","cutoff","totals","fullTotals","filteredRows","groups","missingRows","unavailable","tracked","untracked","unknownDates"))
            labels.put(key,messages.getMessage("opreport."+key,null,language));
        labels.put("title",messages.getMessage(catalog.dataset(d.dataset()).messageKey(),null,language));
        labels.put("coverage",messages.getMessage(query.coverage().messageKey(),null,language));
        return new OperationalReportResult(id, version, d, cutoff, actor.getSaccoId(), actor.getStationId(), columns,
            query.rows(), query.count(), page, size, query.totals(), query.groups(), query.coverage(),Collections.unmodifiableMap(labels));
    }
    private OperationalReportTemplate owned(AppUserPrincipal actor, UUID id) {
        var result = templates.findByIdAndSaccoId(id, actor.getSaccoId()).orElseThrow(() -> new AccessDeniedException("Report unavailable"));
        if (result.getVisibility()!=Visibility.INSTITUTION && !actor.getMemberId().equals(result.getCreatorId())
            && !actor.getMemberId().equals(result.getPublisherId())) throw new AccessDeniedException("Report unavailable");
        return result;
    }
    private void scope(AppUserPrincipal actor, String claim) {
        if (actor == null || !actor.isStaffSession() || actor.isPlatformIdentity() || !access.has(actor, claim)
            || actor.getSaccoId() == null || actor.getSaccoId().isBlank() || actor.getStationId() == null || actor.getStationId().isBlank())
            throw new AccessDeniedException("Report access requires an assigned branch and permission");
    }
    private void datasetAccess(AppUserPrincipal actor, Dataset dataset) {
        if (!access.has(actor, catalog.dataset(dataset).requiredClaim())) throw new AccessDeniedException("Dataset unavailable");
    }
    private void page(int page, int size) { if (page < 0 || page > 1000 || size < 1 || size > 100) throw new IllegalArgumentException("opreport.error.definition"); }
    private void requireVersion(OperationalReportTemplate entity, Long version) {
        if (version == null || !version.equals(entity.getVersion())) throw new IllegalArgumentException("opreport.error.conflict");
    }
    public ReportDefinition parse(String json) {
        if (json == null || json.length() > 16384) throw new IllegalArgumentException("opreport.error.definition");
        return validator.validate(decode(json));
    }
    private ReportDefinition decode(String json) {
        try { return mapper.readerFor(ReportDefinition.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("opreport.error.definition"); }
    }
    public String encode(ReportDefinition d) { return mapper.writeValueAsString(d); }
    private OperationalTemplateView view(OperationalReportTemplate entity) {
        return new OperationalTemplateView(entity.getId(), entity.getName(), entity.getReportVersion(), entity.getState().name(), entity.getVersion(),
            entity.getVisibility().name(),entity.getCreatorId(), entity.getCreatedAt(), entity.getPublisherId(), entity.getPublishedAt(), decode(entity.getDefinitionJson()));
    }
}
