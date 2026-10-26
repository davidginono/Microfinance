package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.ExportData;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.grouplending.repository.GroupOrganizationDocumentRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import com.sacco.mvp.domain.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationDocumentPostgresTest {
    final GroupOrganizationAgreementPostgresTest s=new GroupOrganizationAgreementPostgresTest();
    final GroupOrganizationPostgresTest o=s.o;
    GroupOrganizationDocumentService documents;GroupOrganizationAgreementExportService exports;
    @BeforeAll void start(){s.start(Config.class);documents=o.f.context.getBean(GroupOrganizationDocumentService.class);exports=o.f.context.getBean(GroupOrganizationAgreementExportService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void fixture(){o.fixture();reset(o.f.context.getBean(StationOtpSettingsService.class));}
    UUID bare(com.sacco.mvp.grouplending.dto.GroupCycleDtos.CycleForm form){return o.cycles.create(o.f.actor,o.group,form);}
    UUID loan(){var f=o.form();s.cycle=o.create(f);var loan=o.service.submit(o.f.actor,s.cycle,f);s.signer=s.client(o.f.client);s.hash=s.service.packet(s.signer,s.cycle,0).hash();return loan;}
    Document latest(UUID cycle){return o.f.context.getBean(GroupOrganizationDocumentRepository.class).latest(o.f.actor,cycle).orElseThrow();}
    @Test void originationCannotFreezeAnIncompleteAgreement(){var form=o.form();var cycle=bare(form);assertThatThrownBy(()->o.service.submit(o.f.actor,cycle,form)).hasMessage("group.error.documentRequired");assertThat(o.count("loan_applications")).isZero();}
    @Test void recordingKeepsDraftEditsAndExactApprovedBytes()throws Exception{
        var form=o.form();var cycle=bare(form);form.getOrganization().setBusiness("Draft edits retained during document recording");var file=GroupOrganizationPostgresTest.documentFile();
        var id=documents.record(o.f.actor,cycle,form,o.documentForm(),file);var doc=latest(cycle);
        assertThat(doc.id()).isEqualTo(id);assertThat(doc.revision()).isEqualTo(1);assertThat(doc.language()).isEqualTo("EN");assertThat(doc.institutionName()).isEqualTo(o.f.jdbc.queryForObject("select sacco_name from registered_saccos where sacco_id=?",String.class,o.f.institution));
        assertThat(doc.complaintEmail()).isEqualTo("complaints@example.invalid");assertThat(documents.original(o.f.actor,cycle,id)).isEqualTo(file.getBytes());
        assertThat(o.service.workspace(o.f.actor,cycle,null).form().getOrganization().getBusiness()).isEqualTo("Draft edits retained during document recording");assertThat(o.cycles.detail(o.f.actor,cycle).version()).isEqualTo(1);assertThat(o.count("loan_applications")).isZero();
    }
    @Test void fileReadsAndParsingAreOutsideTheCallerTransaction()throws Exception{
        var form=o.form();var cycle=bare(form);var real=GroupOrganizationPostgresTest.documentFile();var file=spy(real);
        doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return real.getBytes();}).when(file).getBytes();
        new TransactionTemplate(o.f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->documents.record(o.f.actor,cycle,form,o.documentForm(),file));assertThat(latest(cycle)).isNotNull();
    }
    @Test void badMissingAndOversizedPdfsDoNotSaveTheDraft(){
        var form=o.form();var cycle=bare(form);for(var file:List.of(new MockMultipartFile("agreementPdf","text.pdf","application/pdf","not a PDF".getBytes()),new MockMultipartFile("agreementPdf","empty.pdf","application/pdf",new byte[0]),new MockMultipartFile("agreementPdf","large.pdf","application/pdf",new byte[GroupOrganizationDocumentService.MAX_BYTES+1])))
            assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,o.documentForm(),file)).hasMessage("group.error.documentPdf");
        assertThat(o.count("lending_group_org_terms")).isZero();assertThat(o.cycles.detail(o.f.actor,cycle).version()).isZero();
    }
    @Test void activeScriptsFormsAndPasswordsAreRejected()throws Exception{
        var form=o.form();var cycle=bare(form);for(int mode=0;mode<3;mode++)try(var pdf=Loader.loadPDF(GroupOrganizationPostgresTest.documentFile().getBytes());var out=new java.io.ByteArrayOutputStream()){
            if(mode==0)pdf.getDocumentCatalog().setOpenAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert('test')"));
            if(mode==1)pdf.getDocumentCatalog().setAcroForm(new org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm(pdf));
            if(mode==2)pdf.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("synthetic-owner","synthetic-reader",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            pdf.save(out);var file=new MockMultipartFile("agreementPdf","active.pdf","application/pdf",out.toByteArray());assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,o.documentForm(),file)).hasMessage("group.error.documentPdf");
        }assertThat(o.count("lending_group_org_terms")).isZero();
    }
    @Test void pageLimitAppliesBeforeAnyDocumentWrite()throws Exception{
        var form=o.form();var cycle=bare(form);try(var pdf=new PDDocument();var out=new java.io.ByteArrayOutputStream()){for(int i=0;i<51;i++)pdf.addPage(new PDPage());pdf.save(out);assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,o.documentForm(),new MockMultipartFile("agreementPdf","too-many-pages.pdf","application/pdf",out.toByteArray()))).hasMessage("group.error.documentPdf");}assertThat(o.count("lending_group_org_terms")).isZero();
    }
    @Test void aMissingAttestationOrComplaintContactCannotBeRecorded(){
        var form=o.form();var cycle=bare(form);var doc=o.documentForm();doc.setDocumentChecked(false);assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,doc,GroupOrganizationPostgresTest.documentFile())).hasMessage("group.error.documentChecked");
        doc.setDocumentChecked(true);doc.setComplaintEmail("");assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,doc,GroupOrganizationPostgresTest.documentFile())).hasMessage("group.error.documentContacts");assertThat(o.count("lending_group_org_terms")).isZero();
    }
    @Test void concurrentRetriesStoreOneDocumentAndOneDraftVersion()throws Exception{
        var form=o.form();var cycle=bare(form);var doc=o.documentForm();var file=GroupOrganizationPostgresTest.documentFile();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(()->{start.await();return documents.record(o.f.actor,cycle,form,doc,file);});var two=pool.submit(()->{start.await();return documents.record(o.f.actor,cycle,form,doc,file);});start.countDown();assertThat(one.get(60,TimeUnit.SECONDS)).isEqualTo(two.get(60,TimeUnit.SECONDS));}
        assertThat(o.count("lending_group_org_terms")).isEqualTo(1);assertThat(o.cycles.detail(o.f.actor,cycle).version()).isEqualTo(1);
    }
    @Test void anotherStaffMemberCannotReuseTheRecordedRequest(){
        var form=o.form();var cycle=bare(form);var doc=o.documentForm();var file=GroupOrganizationPostgresTest.documentFile();documents.record(o.f.actor,cycle,form,doc,file);
        var other=o.f.operator("B1");assertThatThrownBy(()->documents.record(other,cycle,form,doc,file)).hasMessage("group.error.changed");assertThat(o.count("lending_group_org_terms")).isEqualTo(1);
    }
    @Test void excessConcurrentUploadsFailBeforeReadingThePdfAndCanRetry()throws Exception{
        var form=o.form();var cycle=bare(form);var doc=o.documentForm();var real=GroupOrganizationPostgresTest.documentFile();var file=spy(real);
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertThat(release.await(30,TimeUnit.SECONDS)).isTrue();return real.getBytes();}).when(file).getBytes();
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->documents.record(o.f.actor,cycle,form,doc,file));var two=pool.submit(()->documents.record(o.f.actor,cycle,form,doc,file));
            try{assertThat(entered.await(30,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,doc,real)).hasMessage("group.error.documentBusy");}
            finally{release.countDown();}
            assertThat(one.get(60,TimeUnit.SECONDS)).isEqualTo(two.get(60,TimeUnit.SECONDS));
        }
        assertThat(documents.record(o.f.actor,cycle,form,doc,real)).isEqualTo(latest(cycle).id());assertThat(o.count("lending_group_org_terms")).isEqualTo(1);
    }
    @Test void revisionsHaveOnePredecessorAndKeepTheEarlierPdf(){
        var form=o.form();var cycle=bare(form);var first=documents.record(o.f.actor,cycle,form,o.documentForm(),GroupOrganizationPostgresTest.documentFile());form.setExpectedVersion(1);var next=o.documentForm();next.setDocumentReference("Synthetic second approved reference");
        assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,next,GroupOrganizationPostgresTest.documentFile())).hasMessage("group.error.changed");next.setExpectedDocumentId(first);var second=documents.record(o.f.actor,cycle,form,next,GroupOrganizationPostgresTest.documentFile());
        assertThat(latest(cycle).revision()).isEqualTo(2);assertThat(documents.original(o.f.actor,cycle,first)).isNotEmpty();assertThat(o.count("lending_group_org_terms")).isEqualTo(2);
        form.setExpectedVersion(2);o.service.submit(o.f.actor,cycle,form);
        assertThat(o.f.jdbc.queryForObject("select snapshot->'document'->>'id' from lending_group_org_agreement where cycle_id=?",String.class,cycle)).isEqualTo(second.toString());
    }
    @Test void documentAuditFailureRollsBackTheDraftAndPdf(){
        var form=o.form();var cycle=bare(form);doThrow(new IllegalStateException("Synthetic document audit failure")).when(o.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(cycle),eq("ORGANIZATION_DOCUMENT_RECORDED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->documents.record(o.f.actor,cycle,form,o.documentForm(),GroupOrganizationPostgresTest.documentFile())).hasMessage("Synthetic document audit failure");assertThat(o.count("lending_group_org_terms")).isZero();assertThat(o.cycles.detail(o.f.actor,cycle).version()).isZero();
    }
    @Test void submittedAgreementAndPdfAreImmutable(){
        loan();var doc=latest(s.cycle);assertThat(s.service.packet(s.signer,s.cycle,0).snapshot().document().id()).isEqualTo(doc.id());
        assertThatThrownBy(()->o.f.jdbc.update("update lending_group_org_terms set reference='changed' where id=?",doc.id())).isInstanceOf(DataAccessException.class);assertThatThrownBy(()->o.f.jdbc.update("delete from lending_group_org_terms where id=?",doc.id())).isInstanceOf(DataAccessException.class);
        var form=o.service.workspace(o.f.actor,s.cycle,null).form();var next=o.documentForm();next.setExpectedDocumentId(doc.id());assertThatThrownBy(()->documents.record(o.f.actor,s.cycle,form,next,GroupOrganizationPostgresTest.documentFile())).hasMessage("group.error.changed");
    }
    @Test void aSignatoryMustOpenTheExactFullPacketBeforeCodeOrSignature(){
        loan();assertThat(s.service.sign(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.documentOpen");when(o.f.context.getBean(StationOtpSettingsService.class).requiresApprovalOtp(any(),anyString(),anyString())).thenReturn(true);
        assertThatThrownBy(()->s.service.prepareCode(s.signer,s.cycle,s.hash)).hasMessage("group.error.documentOpen");assertThat(o.count("lending_group_org_challenge")).isZero();
        exports.client(s.signer,s.cycle,Locale.ENGLISH);assertThat(s.service.packet(s.signer,s.cycle,0).documentOpened()).isTrue();assertThat(o.count("lending_group_org_document_access")).isEqualTo(1);
    }
    @Test void failedRenderingAndFailedAccessAuditCannotSatisfySigning(){
        loan();var limiter=o.f.context.getBean(ReportExportLimiter.class);assertThat(limiter.tryAcquire()).isTrue();try{assertThatThrownBy(()->exports.client(s.signer,s.cycle,Locale.ENGLISH)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}finally{limiter.release();}
        assertThat(o.count("lending_group_org_document_access")).isZero();doThrow(new IllegalStateException("Synthetic access audit failure")).when(o.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(s.cycle),eq("ORGANIZATION_DOCUMENT_REQUESTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->exports.client(s.signer,s.cycle,Locale.ENGLISH)).hasMessage("Synthetic access audit failure");assertThat(o.count("lending_group_org_document_access")).isZero();assertThat(s.service.sign(s.signer,s.cycle,s.hash,true,"")).isEqualTo("group.error.documentOpen");
    }
    @Test void fullPdfContainsFrozenTermsContactsAndRealSignatureEvidenceInBothLanguages()throws Exception{
        var loan=loan();exports.client(s.signer,s.cycle,Locale.ENGLISH);assertThat(s.service.sign(s.signer,s.cycle,s.hash,true,"")).isNull();
        for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){byte[] bytes=exports.client(s.signer,s.cycle,locale);var file=Path.of("target/group-org-full-agreement-"+locale.getLanguage()+".pdf");Files.write(file,bytes);
            try(var pdf=Loader.loadPDF(bytes)){var text=new PDFTextStripper().getText(pdf);assertThat(pdf.getNumberOfPages()).isGreaterThan(1);assertThat(text).contains("SYNTHETIC APPROVED TERMS - AUTOMATED TEST ONLY","1,000.00","20.00","complaints@example.invalid",s.hash).doesNotContain("Synthetic verified organization income");for(int p=0;p<pdf.getNumberOfPages();p++)javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(p,105),"png",Path.of("target/group-org-full-agreement-"+locale.getLanguage()+"-"+p+".png").toFile());}
        }
        assertThat(exports.staffLoan(o.f.actor,loan,Locale.ENGLISH)).isPresent();try(var physical=Loader.loadPDF(exports.staffLoan(o.f.actor,loan,Locale.ENGLISH,false).orElseThrow())){assertThat(new PDFTextStripper().getText(physical)).contains("Physical signature copy");}
        assertThat(o.count("loan_ledgers")).isZero();assertThat(s.service.packet(s.signer,s.cycle,0).hash()).isEqualTo(s.hash);
    }
    @Test void scopeAndFreshExportClaimsProtectAllPdfReaders(){
        var loan=loan();var doc=latest(s.cycle);assertThatThrownBy(()->documents.original(o.f.operator("B2"),s.cycle,doc.id())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->exports.staffLoan(o.f.operator("B2"),loan,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);
        var allowed=EnumSet.allOf(UserClaim.class);allowed.remove(UserClaim.LOAN_DOCUMENTS_EXPORT);when(o.f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(allowed);
        assertThatThrownBy(()->exports.client(s.signer,s.cycle,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->exports.staffLoan(o.f.actor,loan,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);assertThat(o.count("lending_group_org_document_access")).isZero();
    }
    @Test void existingStaffPrintRouteUsesTheOrganizationDocumentInsteadOfAPersonalApplicant()throws Exception{
        var loan=loan();var controller=new com.sacco.mvp.web.LoanDocumentController(o.f.context.getBean(MemberDirectoryService.class),o.f.context.getBean(SaccoRegistryService.class),mock(LoanPresentationService.class),mock(LoanAttachmentService.class),mock(LoanReportService.class),mock(MemberProfileImageService.class),mock(SaccoLogoStorageService.class),o.f.context.getBean(AuditService.class),new AccessControlService(),mock(AdminScopeService.class),mock(SmsUsageManagementService.class),exports);
        var response=controller.downloadPrintable(loan,o.f.actor,"signed");try(var pdf=Loader.loadPDF(response.getBody())){assertThat(new PDFTextStripper().getText(pdf)).contains("SYNTHETIC APPROVED TERMS - AUTOMATED TEST ONLY","Synthetic organization <Company>");}
        verify(o.f.context.getBean(MemberDirectoryService.class),never()).find(isNull());
    }
    @Test void ordinaryPrintFallbackRetainsItsExistingExportPermission(){
        var loan=o.f.service.save(o.f.actor,o.f.form());
        var allowed=EnumSet.allOf(UserClaim.class);allowed.remove(UserClaim.LOAN_DOCUMENTS_VIEW);
        when(o.f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(allowed);
        assertThat(exports.staffLoan(o.f.actor,loan,Locale.ENGLISH)).isEmpty();
    }
    @Test void revokedDocumentViewBlocksOriginalAndCombinedOrganizationPdfs(){
        var loan=loan();var doc=latest(s.cycle);var allowed=EnumSet.allOf(UserClaim.class);allowed.remove(UserClaim.LOAN_DOCUMENTS_VIEW);
        when(o.f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(allowed);
        assertThatThrownBy(()->documents.original(o.f.actor,s.cycle,doc.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->exports.staffLoan(o.f.actor,loan,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->exports.client(s.signer,s.cycle,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);
        assertThat(o.count("lending_group_org_document_access")).isZero();
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupOrganizationAgreementPostgresTest.Config.class) static class Config {
        @Bean @Primary GroupOrganizationAgreementPdf verifiedPdf(org.springframework.context.MessageSource m,ReportExportLimiter limiter){return new GroupOrganizationAgreementPdf(m){
            @Override public byte[] render(ExportData data,Locale locale,boolean signatures){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return super.render(data,locale,signatures);}
        };}
    }
}
