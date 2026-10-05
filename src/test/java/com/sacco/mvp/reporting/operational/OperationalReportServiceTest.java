package com.sacco.mvp.reporting.operational;

import com.sacco.mvp.reporting.operational.dto.ReportDefinition;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate;
import com.sacco.mvp.reporting.operational.model.OperationalReportTemplate.*;
import com.sacco.mvp.reporting.operational.repository.*;
import com.sacco.mvp.reporting.operational.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationalReportServiceTest {
    private final OperationalReportCatalog catalog=new OperationalReportCatalog();
    private ApplicationClock clock;
    private ReportDefinitionValidator validator;
    private OperationalTemplateRepository templates;
    private OperationalReportRepository reports;
    private OperationalReportService service;
    private AppUserPrincipal actor;
    private ReportDefinition d;
    @BeforeEach void setup() {
        clock=mock(ApplicationClock.class); when(clock.today()).thenReturn(LocalDate.of(2026,10,4));
        when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-04T12:00:00+03:00"));
        validator=new ReportDefinitionValidator(catalog,clock); templates=mock(OperationalTemplateRepository.class);
        reports=mock(OperationalReportRepository.class); actor=actor("I1","B1",true,"REPORTS_RUN","REPORT_TEMPLATES_VIEW","LOAN_REPAYMENTS_VIEW");
        service=new OperationalReportService(templates,reports,catalog,validator,new AccessControlService(),clock,JsonMapper.builder().build(),new StaticMessageSource());
        d=catalog.system(Dataset.COLLECTIONS,clock.today());
    }
    @Test void everySystemTemplateHasTypedVersionedDefinitionAndSupportedTotals() {
        for (var dataset:Dataset.values()) assertThat(validator.validate(catalog.system(dataset,clock.today()))).isNotNull();
    }
    @Test void arbitraryFieldsFormatsAndCurrencyHidingAreRejected() {
        assertInvalid(copy(d,2,d.metricVersion(),d.columns(),d.dateFrom(),d.dateTo(),d.groupBy(),d.totals(),d.title()));
        assertInvalid(copy(d,1,9,d.columns(),d.dateFrom(),d.dateTo(),d.groupBy(),d.totals(),d.title()));
        assertInvalid(copy(d,1,1,List.of(new Column(Field.PRINCIPAL,"",140,Format.TEXT)),d.dateFrom(),d.dateTo(),List.of(),List.of(),""));
        assertInvalid(copy(d,1,1,List.of(new Column(Field.STATUS,"",140,Format.TEXT)),d.dateFrom(),d.dateTo(),List.of(),List.of(),""));
        assertInvalid(copy(d,1,1,List.of(new Column(Field.PRINCIPAL,"",140,Format.MONEY),new Column(Field.PRINCIPAL,"",140,Format.MONEY)),d.dateFrom(),d.dateTo(),List.of(),List.of(),""));
    }
    @Test void unsafeTextAndUnsupportedDateRangesFailClosed() {
        assertInvalid(copy(d,1,1,d.columns(),d.dateFrom(),d.dateTo(),List.of(),d.totals(),"<script>alert(1)</script>"));
        assertInvalid(copy(d,1,1,d.columns(),d.dateFrom().minusYears(2),d.dateTo(),List.of(),d.totals(),""));
        assertInvalid(copy(d,1,1,d.columns(),d.dateFrom(),d.dateTo().plusDays(1),List.of(),d.totals(),""));
        assertInvalid(copy(d,1,1,d.columns(),d.dateFrom(),d.dateTo(),List.of(Field.PRINCIPAL),d.totals(),""));
        assertInvalid(copy(d,1,1,d.columns(),d.dateFrom(),d.dateTo(),List.of(),List.of(Field.RECEIPT),""));
    }
    @Test void jsonRejectsUnknownQueryLanguageAndInvalidEnums() {
        String json=service.encode(d);
        assertThat(service.parse(json)).isEqualTo(d);
        assertThatThrownBy(() -> service.parse(json.substring(0,json.length()-1)+",\"sql\":\"select * from members\"}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parse(json.replace("PAYMENT_DATE","password_hash"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parse(" ".repeat(16385))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void roleNameDoesNotGrantReportsAndMissingBranchNeverWidensScope() {
        assertThatThrownBy(() -> service.preview(actor("I1","B1",true,"LOAN_REPAYMENTS_VIEW"),d,0,25)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(actor("I1","",true,"REPORTS_RUN","REPORT_TEMPLATES_VIEW","LOAN_REPAYMENTS_VIEW"),d,0,25)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(actor("I1","B1",false,"REPORTS_RUN","REPORT_TEMPLATES_VIEW","LOAN_REPAYMENTS_VIEW"),d,0,25)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reports);
    }
    @Test void templatePermissionDoesNotGrantDatasetAndBoundsAreCheckedBeforeQuery() {
        assertThatThrownBy(() -> service.preview(actor("I1","B1",true,"REPORTS_RUN","REPORT_TEMPLATES_VIEW"),d,0,25)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(actor,d,0,101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(actor,d,1001,25)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reports);
    }
    @Test void privateAndForeignTemplatesCannotBeReadOrRun() {
        UUID id=UUID.randomUUID(); var template=template(id); template.setCreatorId(UUID.randomUUID());
        when(templates.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.definition(actor,id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.run(actor,id,0,25)).isInstanceOf(AccessDeniedException.class);
        when(templates.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.definition(actor,id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reports);
    }
    @Test void publishedDefinitionsCannotBeSavedAndStaleDraftVersionCannotWin() {
        actor=actor("I1","B1",true,"REPORT_TEMPLATES_UPDATE","LOAN_REPAYMENTS_VIEW");
        UUID id=UUID.randomUUID(); var template=template(id); template.setState(State.PUBLISHED);
        when(templates.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.saveDraft(actor,id,0L,"Collections",d)).hasMessage("opreport.error.immutable");
        template.setState(State.DRAFT);
        assertThatThrownBy(() -> service.saveDraft(actor,id,99L,"Collections",d)).hasMessage("opreport.error.conflict");
        verify(templates,never()).saveAndFlush(any());
    }
    @Test void sharingDoesNotGrantRunningOrSourcePermission() {
        UUID id=UUID.randomUUID(); var template=template(id);
        when(templates.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.share(actor,id,0L,true)).isInstanceOf(AccessDeniedException.class);
        actor=actor("I1","B1",true,"REPORT_TEMPLATES_SHARE"); template.setCreatorId(actor.getMemberId());
        assertThatThrownBy(() -> service.share(actor,id,0L,true)).isInstanceOf(AccessDeniedException.class);
        verify(templates,never()).saveAndFlush(any());
    }
    @Test void publishedRunRejectsDraftAndFutureCutoff() {
        UUID id=UUID.randomUUID(); var template=template(id); when(templates.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.run(actor,id,0,25)).hasMessage("opreport.error.publishFirst");
        assertThatThrownBy(() -> service.runAt(actor,id,0,25,clock.now().plusSeconds(1))).hasMessage("opreport.error.definition");
        verifyNoInteractions(reports);
    }
    private void assertInvalid(ReportDefinition invalid) { assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(IllegalArgumentException.class); }
    private ReportDefinition copy(ReportDefinition d,int schema,int metric,List<Column> columns,LocalDate from,LocalDate to,List<Field> groups,List<Field> totals,String title) {
        return new ReportDefinition(schema,metric,d.dataset(),columns,from,to,d.loanId(),d.channel(),d.status(),groups,totals,d.sortBy(),d.direction(),title,d.footer(),d.language(),d.orientation(),d.paper(),d.showInstitutionBranding());
    }
    private OperationalReportTemplate template(UUID id) {
        var t=new OperationalReportTemplate(); t.setId(id); t.setSaccoId("I1");t.setFamilyId(id);t.setReportVersion(1);
        t.setVersion(0L);t.setVisibility(Visibility.PRIVATE);t.setState(State.DRAFT);t.setCreatorId(actor.getMemberId());t.setDataset("COLLECTIONS");
        t.setDefinitionJson(service.encode(d));return t;
    }
    private AppUserPrincipal actor(String institution,String branch,boolean staff,String... claims) {
        var actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn(institution);when(actor.getStationId()).thenReturn(branch);
        when(actor.isStaffSession()).thenReturn(staff);when(actor.getMemberId()).thenReturn(UUID.randomUUID());when(actor.getClaims()).thenReturn(Set.of(claims));return actor;
    }
}
