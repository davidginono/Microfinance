package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.PolicyForm;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/microfinance_recording_test")
class ClientOnboardingPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    OnboardingService service;LocalDate day=LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam"));byte[] image;String clientPhone;
    @BeforeAll void start()throws Exception {
        f.start();service=f.context.getBean(OnboardingService.class);
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);image=bytes.toByteArray();
    }
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.fixture();clientPhone=phone();f.jdbc.update("update members set phone=?,client_portal_enabled=false,password_hash='!NO_PORTAL_ACCESS' where id=?",clientPhone,f.client);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day);when(f.context.getBean(ApplicationClock.class).now()).thenReturn(day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
    String phone(){return "2557"+String.format("%08d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000));}
    ProfileForm profile(UUID client,String identity) {
        var form=new ProfileForm();form.setExpectedSequence(service.client(f.actor,client).sequence());var p=form.getProfile();
        p.setIdentityType("NATIONAL_ID");p.setIssuingCountry("TZ");p.setIdentityNumber(identity);p.setBirthDate(day.minusYears(30));p.setIdentityExpiresOn(day.plusYears(1));
        p.setAddress("Synthetic checked residence");p.setActivity("Synthetic shop");p.setActivityAddress("Synthetic market");p.setSourceOfFunds("Synthetic shop sales");p.setNoticeVersion("Synthetic approved notice 1");p.setLawfulBasis("CONSENT");p.setConsentReference("Synthetic signed notice and purpose-specific consent");p.setConsentOn(day);p.setRetentionReference("Synthetic approved retention schedule");p.setRetentionReviewOn(day.plusYears(2));return form;
    }
    MockMultipartFile image(){return new MockMultipartFile("file","identity.png","image/png",image);}
    UUID draft(UUID client,String identity){return service.save(f.actor,client,profile(client,identity));}
    void documents(UUID client,UUID revision) {
        for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY","CONSENT")){var form=new DocumentForm();form.setRevisionId(revision);form.setCategory(category);service.attach(f.actor,client,form,image());}
    }
    UUID submitted(UUID client,String identity){UUID draft=draft(client,identity);documents(client,draft);var form=profile(client,identity);form.setAction("SUBMITTED");return service.save(f.actor,client,form);}
    ReviewForm decision(UUID revision){var form=new ReviewForm();form.setRevisionId(revision);form.setIdentityChecked(true);form.setAddressChecked(true);form.setActivityChecked(true);form.setContactChecked(true);form.setDuplicatesChecked(true);form.setReason("Synthetic independent evidence review");form.setClientMessage("Please provide a current address document.");form.setScreening("CLEARED");form.setScreeningReference("Synthetic manually checked PEP and sanctions sources, dated result");form.setPolicyReference("Synthetic approved KYC policy");form.setCheckedOn(day);form.setReviewDue(day.plusMonths(6));return form;}
    void policy(){var controls=f.context.getBean(CreditControlService.class);var p=new PolicyForm();p.setExpectedRevision(controls.policy(f.actor).revision());p.setKycRequired(true);p.setReference("Synthetic approved branch KYC gate");p.setVerified(true);controls.configure(f.actor,p);}
    String gate(){return f.jdbc.queryForObject("select client_kyc_error(?,?,?,?)",String.class,f.client,f.institution,"B1",day);}
    @Test void workspaceStatusTracksIndependentVerificationExpiryContactChangesAndRevocation() {
        var owner=owner(f.client);
        assertThat(service.ownStatus(owner).state()).isEqualTo("NOT_STARTED");
        UUID revision=submitted(f.client,"TZ-WORKSPACE-"+UUID.randomUUID());
        assertThat(service.ownStatus(owner).state()).isEqualTo("SUBMITTED");
        var reviewer=f.operator("B1");
        service.review(reviewer,f.client,decision(revision));
        assertThat(service.ownStatus(owner).state()).isEqualTo("VERIFY");
        f.jdbc.update("update members set phone=? where id=?",phone(),f.client);
        assertThat(service.ownStatus(owner).state()).isEqualTo("STALE");
        f.jdbc.update("update members set phone=? where id=?",clientPhone,f.client);
        when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day.plusMonths(7));
        assertThat(service.ownStatus(owner).state()).isEqualTo("EXPIRED");
        when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day);
        var revoked=decision(revision);revoked.setKind("REVOKE");service.review(reviewer,f.client,revoked);
        assertThat(service.ownStatus(owner).state()).isEqualTo("REVOKE");
        f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        assertThatThrownBy(()->service.ownStatus(owner)).isInstanceOf(AccessDeniedException.class);
    }
    UUID otherClient(String branch){var id=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,phone,status,position,is_member,password_hash,client_portal_enabled,created_at) values(?,?,?,?,'Synthetic second client',?,'ACTIVE','MEMBER',true,'!NO_PORTAL_ACCESS',false,now())",id,f.institution,branch,id.toString(),phone());return id;}

    @Test void existingRegistrationIsUnverifiedAndAssistedCreationDoesNotFabricateOtpOrConsent(){
        assertThat(service.client(f.actor,f.client).state()).isEqualTo("NOT_STARTED");assertThat(gate()).isEqualTo("kyc.error.required");
        var form=new CreateForm();form.setName("Synthetic New Client");form.setPhone("2557"+String.format("%08d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000)));form.setEmail(UUID.randomUUID()+"@example.invalid");
        UUID id=service.create(f.actor,form);assertThat(service.create(f.actor,form)).isEqualTo(id);assertThat(service.client(f.actor,id).state()).isEqualTo("NOT_STARTED");assertThat(f.jdbc.queryForObject("select phone_verified_at from members where id=?",OffsetDateTime.class,id)).isNull();assertThat(f.jdbc.queryForObject("select count(*) from client_onboarding_revision where client_id=?",Integer.class,id)).isZero();
        assertThat(f.jdbc.queryForObject("select signature_text from members where id=?",String.class,id)).isNull();assertThat(f.jdbc.queryForObject("select count(*) from sacco_settings where sacco_id=?",Integer.class,f.institution)).isZero();
    }
    @Test void draftsNeedCurrentEvidenceAndVerificationNeedsIndependentStaff(){
        var empty=new ProfileForm();UUID draft=service.save(f.actor,f.client,empty);assertThat(service.save(f.actor,f.client,empty)).isEqualTo(draft);
        f.jdbc.update("update members set phone=null where id=?",f.client);var missingContact=profile(f.client,"TZ-0000-1234");missingContact.setAction("SUBMITTED");assertThatThrownBy(()->service.save(f.actor,f.client,missingContact)).hasMessage("kyc.error.phone");f.jdbc.update("update members set phone=? where id=?",clientPhone,f.client);
        var form=profile(f.client,"TZ-0000-1234");form.setAction("SUBMITTED");assertThatThrownBy(()->service.save(f.actor,f.client,form)).hasMessage("kyc.error.documents");
        UUID submitted=submitted(f.client,"TZ-0000-1234");var review=decision(submitted);assertThatThrownBy(()->service.review(f.actor,f.client,review)).hasMessage("kyc.error.independent");
        var selfMember=Member.builder().id(f.client).saccoId(f.institution).stationId("B1").memberNo(f.client.toString()).memberAccount(true).fullName("Synthetic client").phone(clientPhone).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();f.jdbc.update("update members set position='ACCOUNTANT',staff_access_status='ACTIVE',client_portal_enabled=true,password_hash='test-only' where id=?",f.client);when(f.context.getBean(MemberDirectoryService.class).find(f.client)).thenReturn(Optional.of(selfMember));var selfReviewer=new AppUserPrincipal(selfMember,EnumSet.allOf(UserClaim.class),true);assertThatThrownBy(()->service.review(selfReviewer,f.client,review)).hasMessage("kyc.error.independent");
        var verifier=f.operator("B1");UUID saved=service.review(verifier,f.client,review);assertThat(service.review(verifier,f.client,review)).isEqualTo(saved);assertThat(gate()).isNull();assertThat(service.documents(f.actor,f.client)).hasSize(4);
        assertThatThrownBy(()->f.jdbc.update("delete from client_onboarding_decision where id=?",saved)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.execute("truncate client_onboarding_revision cascade")).isInstanceOf(DataAccessException.class);
    }
    @Test void unavailableScreeningCannotBecomeAFalseCleanRecordAndReturnedDraftCanBeCorrected(){
        UUID submitted=submitted(f.client,"TZ-1234");var verifier=f.operator("B1");var review=decision(submitted);review.setScreening("UNAVAILABLE");assertThatThrownBy(()->service.review(verifier,f.client,review)).hasMessage("kyc.error.screening");
        review.setKind("RETURN");service.review(verifier,f.client,review);assertThat(gate()).isEqualTo("kyc.error.required");
        UUID corrected=submitted(f.client,"TZ-5678");service.review(verifier,f.client,decision(corrected));assertThat(gate()).isNull();assertThat(service.versionDecisions(f.actor,f.client,2).getFirst().kind()).isEqualTo("RETURN");assertThat(service.history(f.actor,f.client,0).rows()).hasSize(4);
    }
    @Test void changedContactsExpiryAndRevocationBlockFurtherCreditButKeepEvidence(){
        UUID submitted=submitted(f.client,"TZ-REVIEW");var verifier=f.operator("B1");service.review(verifier,f.client,decision(submitted));
        f.jdbc.update("update members set phone='255700000111' where id=?",f.client);assertThat(gate()).isEqualTo("kyc.error.contactChanged");assertThat(service.client(f.actor,f.client).state()).isEqualTo("STALE");
        f.jdbc.update("update members set phone=? where id=?",clientPhone,f.client);assertThat(f.jdbc.queryForObject("select client_kyc_error(?,?,?,?)",String.class,f.client,f.institution,"B1",day.plusYears(1))).isEqualTo("kyc.error.expired");
        var revoke=decision(submitted);revoke.setKind("REVOKE");service.review(verifier,f.client,revoke);assertThat(gate()).isEqualTo("kyc.error.required");assertThat(service.decisions(f.actor,f.client)).hasSize(2);
    }
    @Test void exactNormalizedIdentityDuplicatesAcrossBranchesAreBlockedWithoutLeakingTheOtherProfile(){
        UUID submitted=submitted(f.client,"TZ-1234/5678");service.review(f.operator("B1"),f.client,decision(submitted));
        UUID other=otherClient("B1");UUID duplicate=submitted(other,"tz12345678");assertThat(service.duplicate(f.actor,other)).isTrue();assertThatThrownBy(()->service.review(f.operator("B1"),other,decision(duplicate))).hasMessage("kyc.error.duplicate");
        assertThatThrownBy(()->service.client(f.operator("B2"),f.client)).isInstanceOf(AccessDeniedException.class);
        f.jdbc.update("update members set station_id='B2' where id=?",f.client);var branchTwo=f.operator("B2");assertThat(service.latest(branchTwo,f.client)).isEmpty();assertThatThrownBy(()->service.version(branchTwo,f.client,2)).isInstanceOf(AccessDeniedException.class);assertThat(service.history(branchTwo,f.client,0).rows()).isEmpty();
    }
    @Test void competingDuplicateIdentityReviewsCannotVerifyTwoPeople()throws Exception {
        UUID first=submitted(f.client,"TZ-CONCURRENT"),other=otherClient("B1"),second=submitted(other,"TZ-CONCURRENT");var verifier=f.operator("B1");
        try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var one=pool.submit(()->{latch.await();try{service.review(verifier,f.client,decision(first));return true;}catch(IllegalArgumentException expected){return false;}});var two=pool.submit(()->{latch.await();try{service.review(verifier,other,decision(second));return true;}catch(IllegalArgumentException expected){return false;}});latch.countDown();assertThat(one.get(30,TimeUnit.SECONDS)&&two.get(30,TimeUnit.SECONDS)).isFalse();}
        var reject=decision(second);reject.setKind("REJECT");service.review(verifier,other,reject);service.review(verifier,f.client,decision(first));assertThat(gate()).isNull();
    }
    @Test void lendingGateAppliesToSubmissionAndFundingWithoutChangingRepaymentPaths(){
        UUID loan=f.service.save(f.actor,f.form());policy();assertThatThrownBy(()->f.service.disburse(f.actor,f.post(loan,"1000.00","D-KYC"))).hasMessage("kyc.error.required");
        assertThatThrownBy(()->f.service.save(f.actor,f.form())).hasMessage("kyc.error.required");
        UUID submitted=submitted(f.client,"TZ-LOAN");var verifier=f.operator("B1");service.review(verifier,f.client,decision(submitted));f.service.disburse(f.actor,f.post(loan,"1000.00","D-KYC"));
        var revoke=decision(submitted);revoke.setKind("REVOKE");service.review(verifier,f.client,revoke);var receipt=f.post(loan,"100.00","R-KYC");receipt.setInterestAccountId(f.interest);f.service.repay(f.actor,receipt);assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isLessThan(new java.math.BigDecimal("1000.00"));
    }
    @Test void documentValidationScopeRetriesAndAuditRollbackAreEnforced(){
        UUID draft=draft(f.client,"TZ-DOCS");var form=new DocumentForm();form.setRevisionId(draft);
        assertThatThrownBy(()->service.attach(f.actor,f.client,form,new MockMultipartFile("file","fake.pdf","application/pdf","bad".getBytes()))).hasMessage("kyc.error.file");
        UUID doc=service.attach(f.actor,f.client,form,image());assertThat(service.attach(f.actor,f.client,form,image())).isEqualTo(doc);assertThat(service.download(f.actor,f.client,doc).bytes()).isEqualTo(image);
        assertThatThrownBy(()->service.download(f.operator("B2"),f.client,doc)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->f.jdbc.update("delete from stored_uploads where id=(select upload_id from client_onboarding_document where id=?)",doc)).isInstanceOf(DataAccessException.class);
        doThrow(new IllegalStateException("Synthetic audit unavailable")).when(f.context.getBean(AuditService.class)).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        var changed=profile(f.client,"TZ-CORRECTION");assertThatThrownBy(()->service.save(f.actor,f.client,changed)).hasMessage("Synthetic audit unavailable");assertThat(service.client(f.actor,f.client).sequence()).isEqualTo(1);
    }
    @Test void staleWritesChangedRetriesAndFreshPermissionsFailClosed(){
        var form=profile(f.client,"TZ-RETRY");service.save(f.actor,f.client,form);form.getProfile().setAddress("Changed payload");assertThatThrownBy(()->service.save(f.actor,f.client,form)).hasMessage("kyc.error.retry");
        var stale=profile(f.client,"TZ-STALE");stale.setExpectedSequence(0);assertThatThrownBy(()->service.save(f.actor,f.client,stale)).hasMessage("kyc.error.changed");
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.CLIENT_ONBOARDING_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(claims);assertThatThrownBy(()->service.client(f.actor,f.client)).isInstanceOf(AccessDeniedException.class);
    }
    AppUserPrincipal owner(UUID id) {
        var member=Member.builder().id(id).saccoId(f.institution).stationId("B1").memberNo(id.toString()).fullName("Synthetic client").phone(clientPhone).position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build();
        member.setClientPortalEnabled(false);when(f.context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(member));return AppUserPrincipal.forClientPortal(member);
    }
    UUID legacyNotice(boolean enabled,LocalDate reviewOn) {
        UUID id=UUID.randomUUID();
        f.jdbc.update("insert into client_onboarding_notice values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,f.institution,"B1",1,enabled,"Legacy notice","Legacy English notice","Taarifa ya awali","CONSENT","Legacy retention reference",reviewOn,UUID.randomUUID(),"0".repeat(64),f.actor.getMemberId(),day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));
        return id;
    }
    SelfForm ownProfile(AppUserPrincipal owner) {
        var form=service.ownForm(owner);var p=form.getProfile();p.setIdentityType("NATIONAL_ID");p.setIssuingCountry("TZ");p.setIdentityNumber("TZ-SELF-1234");p.setBirthDate(day.minusYears(30));p.setIdentityExpiresOn(null);p.setIdentityNoExpiry(true);p.setAddress("Synthetic home");p.setActivity("Synthetic business");p.setActivityAddress("Synthetic market");p.setSourceOfFunds("Synthetic verified sales");return form;
    }
    UUID submitOwn(AppUserPrincipal owner) {
        var form=ownProfile(owner);form.setAction("SUBMITTED");form.setNoticeAcknowledged(true);form.setDeclarationAccepted(true);return service.saveOwn(owner,form);
    }
    @Test void clientCompletesOwnDraftUploadsSubmitsReceivesCorrectionsAndIsIndependentlyVerified() {
        var owner=owner(f.client);UUID legacyId=legacyNotice(false,day.minusDays(1));UUID noticeId=service.ownNotice(owner).id();
        var draftForm=ownProfile(owner);UUID draft=service.saveOwn(owner,draftForm);assertThat(service.saveOwn(owner,draftForm)).isEqualTo(draft);
        var superseded=service.ownDocumentForm(owner);UUID oldDocument=service.attachOwn(owner,superseded,image());
        for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY")){var upload=service.ownDocumentForm(owner);upload.setCategory(category);UUID document=service.attachOwn(owner,upload,image());assertThat(service.attachOwn(owner,upload,image())).isEqualTo(document);assertThat(service.downloadOwn(owner,document).bytes()).isEqualTo(image);}
        UUID submitted=submitOwn(owner);assertThat(service.ownWorkspace(owner,0,null).client().state()).isEqualTo("SUBMITTED");
        assertThatThrownBy(()->f.jdbc.update("insert into client_onboarding_document_link values(?,?,?,?,?)",submitted,oldDocument,f.client,f.institution,"B1")).isInstanceOf(DataAccessException.class);
        assertThat(service.ownForm(owner).getProfile().getIdentityNumber()).isEqualTo(draftForm.getProfile().getIdentityNumber());var frozen=ownProfileFromDraft(draftForm);frozen.setExpectedSequence(2);assertThatThrownBy(()->service.saveOwn(owner,frozen)).hasMessage("kyc.error.state");
        var upload=new DocumentForm();upload.setRevisionId(submitted);assertThatThrownBy(()->service.attachOwn(owner,upload,image())).hasMessage("kyc.error.changed");
        var verifier=f.operator("B1");var review=decision(submitted);review.setKind("RETURN");review.setReason("Internal screening notes must not appear in the client workspace");service.review(verifier,f.client,review);
        var returned=service.ownWorkspace(owner,0,null);assertThat(returned.feedback().message()).isEqualTo(review.getClientMessage());assertThat(returned.revision().profile().getAddress()).isEqualTo("Synthetic home");
        assertThat(f.context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(returned)).doesNotContain(review.getReason(),"screeningReference","retentionReference");
        UUID correctedDraft=service.saveOwn(owner,ownProfile(owner));var replacement=service.ownDocumentForm(owner);replacement.setCategory("ADDRESS");service.attachOwn(owner,replacement,image());
        UUID corrected=submitOwn(owner);var accepted=decision(corrected);accepted.setClientMessage("");service.review(verifier,f.client,accepted);
        assertThat(service.ownWorkspace(owner,0,null).client().state()).isEqualTo("VERIFY");assertThat(service.ownWorkspace(owner,0,null).feedback()).isNull();assertThat(gate()).isNull();
        assertThat(service.ownWorkspace(owner,0,2).feedback().message()).isEqualTo(review.getClientMessage());assertThat(service.ownWorkspace(owner,0,2).documents()).hasSize(4);
        assertThat(service.version(f.actor,f.client,2).profile().getNoticeId()).isEqualTo(noticeId);assertThat(service.version(f.actor,f.client,2).profile().getAcknowledgedAt()).isNotNull();
        assertThat(f.jdbc.queryForObject("select actor_id from client_onboarding_revision where id=?",UUID.class,correctedDraft)).isEqualTo(f.client);
        assertThatThrownBy(()->f.jdbc.update("update client_onboarding_notice set english='changed' where id=?",legacyId)).isInstanceOf(DataAccessException.class);
    }
    SelfForm ownProfileFromDraft(SelfForm draft) {var f=new SelfForm();f.setProfile(draft.getProfile());return f;}
    @Test void removingAnAttachmentKeepsEarlierSubmissionAndFallsBackToRemainingEvidence() {
        var owner=owner(f.client);
        service.saveOwn(owner,ownProfile(owner));
        var photo=service.ownDocumentForm(owner);
        photo.setCategory("PROFILE_PHOTO");
        service.attachOwn(owner,photo,image());
        assertThat(service.ownWorkspace(owner,0,null).evidenceRequirements()).containsExactly(
            new EvidenceRequirement("IDENTITY","missing"),new EvidenceRequirement("ADDRESS","missing"),
            new EvidenceRequirement("ACTIVITY","missing"));
        UUID older=service.attachOwn(owner,service.ownDocumentForm(owner),image());
        UUID selected=service.attachOwn(owner,service.ownDocumentForm(owner),image());
        for (String category:List.of("ADDRESS","ACTIVITY")) {
            var document=service.ownDocumentForm(owner);
            document.setCategory(category);
            service.attachOwn(owner,document,image());
        }
        UUID submitted=submitOwn(owner);
        assertThatThrownBy(()->service.removeOwnAttachment(owner,submitted,selected)).hasMessage("kyc.error.changed");
        var returned=decision(submitted);
        returned.setKind("RETURN");
        service.review(f.operator("B1"),f.client,returned);
        UUID draft=service.saveOwn(owner,ownProfile(owner));
        service.removeOwnAttachment(owner,draft,selected);
        service.removeOwnAttachment(owner,draft,selected);
        assertThat(service.ownWorkspace(owner,0,null).documents()).extracting(OwnDocument::id)
            .contains(older).doesNotContain(selected);
        assertThat(service.ownWorkspace(owner,0,2).documents()).extracting(OwnDocument::id).contains(selected);
        assertThatThrownBy(()->service.removeOwnAttachment(owner,draft,UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);
        UUID corrected=submitOwn(owner);
        assertThat(f.jdbc.queryForList("select document_id from client_onboarding_document_link where revision_id=?",UUID.class,corrected))
            .contains(older).doesNotContain(selected);
        assertThat(service.download(f.actor,f.client,selected).bytes()).isEqualTo(image);
    }
    @Test void repeatedDocumentsAndActualConfirmationsSurviveSavingAnotherDraft() {
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));
        for(String category:List.of("IDENTITY","IDENTITY","ADDRESS","ACTIVITY")){var doc=service.ownDocumentForm(owner);doc.setCategory(category);service.attachOwn(owner,doc,image());}
        assertThat(service.ownWorkspace(owner,0,null).documents()).hasSize(4);
        var draft=ownProfile(owner);draft.setNoticeAcknowledged(true);draft.setDeclarationAccepted(true);service.saveOwn(owner,draft);
        assertThat(service.ownWorkspace(owner,0,null).documents()).hasSize(4);
        assertThat(service.ownForm(owner).isNoticeAcknowledged()).isTrue();assertThat(service.ownForm(owner).isDeclarationAccepted()).isTrue();
        assertThat(service.documents(f.actor,f.client)).hasSize(4);
        var submitted=submitOwn(owner);assertThat(service.ownWorkspace(owner,0,null).documents()).hasSize(4);
        assertThat(f.jdbc.queryForObject("select client_onboarding_complete(?,?)",Boolean.class,submitted,day)).isTrue();
    }
    @Test void imageTypesSizesAndLifetimeStorageLimitsAreEnforcedBeforePersistence() {
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));
        var oversized=new MockMultipartFile("file","large.png","image/png",Arrays.copyOf(image,DocumentForm.MAX_FILE_BYTES+1));
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),oversized)).hasMessage("kyc.error.file");
        var disguised=new MockMultipartFile("file","photo.jpg","image/jpeg","not an image".getBytes());
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),disguised)).hasMessage("kyc.error.file");
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),new MockMultipartFile("file","wrong.jpg","image/jpeg",image))).hasMessage("kyc.error.file");
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),new MockMultipartFile("file","document.pdf","application/pdf",image))).hasMessage("kyc.error.file");
        UUID last=null;DocumentForm retry=null;
        for(int i=0;i<DocumentForm.MAX_FILES;i++){retry=service.ownDocumentForm(owner);last=service.attachOwn(owner,retry,i==0?new MockMultipartFile("file","boundary.png","image/png",Arrays.copyOf(image,DocumentForm.MAX_FILE_BYTES)):image());}
        assertThat(service.ownDocumentForm(owner).getRemainingFiles()).isZero();assertThat(service.attachOwn(owner,retry,image())).isEqualTo(last);
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),image())).hasMessage("kyc.error.uploadLimit");
        service.saveOwn(owner,ownProfile(owner));assertThat(service.ownDocumentForm(owner).getRemainingFiles()).isZero();
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),image())).hasMessage("kyc.error.uploadLimit");
        assertThat(f.jdbc.queryForObject("select count(*) from client_onboarding_document where client_id=?",Integer.class,f.client)).isEqualTo(10);
    }
    @Test void theTotalByteBudgetIncludesLegacyUploads() {
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));var revision=service.ownDocumentForm(owner).getRevisionId();
        var records=f.context.getBean(com.sacco.mvp.clients.onboarding.repository.OnboardingRepository.class);
        OnboardingService target=org.springframework.test.util.AopTestUtils.getTargetObject(service);var storage=(StoredUploadStorageService)org.springframework.test.util.ReflectionTestUtils.getField(target,"uploads");
        var tx=new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        // Seed genuine old-limit files before linking them; permanent evidence is never edited.
        for(int i=0;i<2;i++)tx.execute(status->{var id=UUID.randomUUID();var upload=storage.store(null,"CLIENT_KYC",id.toString(),"IDENTITY","legacy.png","image/png",Arrays.copyOf(image,5*1024*1024));var form=new DocumentForm();form.setRevisionId(revision);records.document(owner,f.client,revision,id,upload.getId(),form,"0".repeat(64),day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));return id;});
        assertThat(service.ownDocumentForm(owner).getRemainingBytes()).isZero();assertThat(service.ownDocumentForm(owner).getRemainingFiles()).isEqualTo(8);
        assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),image())).hasMessage("kyc.error.uploadLimit");
    }
    @Test void concurrentUploadsCannotExceedTheLastAvailableSlot()throws Exception {
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));for(int i=0;i<9;i++)service.attachOwn(owner,service.ownDocumentForm(owner),image());
        long storedBefore=f.jdbc.queryForObject("select count(*) from stored_uploads where owner_type='CLIENT_KYC'",Long.class);
        var forms=List.of(service.ownDocumentForm(owner),service.ownDocumentForm(owner));
        try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();for(var form:forms)results.add(pool.submit(()->{latch.await();try{service.attachOwn(owner,form,image());return true;}catch(IllegalArgumentException expected){assertThat(expected).hasMessage("kyc.error.uploadLimit");return false;}}));latch.countDown();assertThat(results.get(0).get(30,TimeUnit.SECONDS)^results.get(1).get(30,TimeUnit.SECONDS)).isTrue();}
        assertThat(f.jdbc.queryForObject("select count(*) from client_onboarding_document where client_id=?",Integer.class,f.client)).isEqualTo(10);
        assertThat(f.jdbc.queryForObject("select count(*) from stored_uploads where owner_type='CLIENT_KYC'",Long.class)).isEqualTo(storedBefore+1);
    }
    @Test void selfSubmissionNeedsAcknowledgementAndDocumentsWithoutBranchConfiguration() {
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));
        assertThat(service.ownNotice(owner).english()).contains("identity checks", "loan assessment", "repayment administration");
        assertThat(f.jdbc.queryForObject("select count(*) from client_onboarding_notice where sacco_id=?",Integer.class,f.institution)).isZero();
        var submit=ownProfile(owner);submit.setAction("SUBMITTED");
        assertThatThrownBy(()->service.saveOwn(owner,submit)).hasMessage("kyc.error.acknowledgement");
        submit.setNoticeAcknowledged(true);submit.setDeclarationAccepted(true);
        assertThatThrownBy(()->service.saveOwn(owner,submit)).hasMessage("kyc.error.documents");
        submit.setNoticeId(UUID.randomUUID());assertThatThrownBy(()->service.saveOwn(owner,submit)).hasMessage("kyc.error.noticeChanged");
        var current=ownProfile(owner);current.setExpectedSequence(0);assertThatThrownBy(()->service.saveOwn(owner,current)).hasMessage("kyc.error.changed");
        for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY")){var doc=service.ownDocumentForm(owner);doc.setCategory(category);service.attachOwn(owner,doc,image());}
        var submitted=submitOwn(owner);var snapshot=service.latest(f.actor,f.client).orElseThrow().profile();
        assertThat(snapshot.getRetentionReference()).isEmpty();assertThat(snapshot.getRetentionReviewOn()).isNull();
        assertThat(snapshot.getNoticeEnglish()).isEqualTo(service.ownNotice(owner).english());
        assertThat(snapshot.isNoticeAcknowledged()).isTrue();assertThat(snapshot.getAcknowledgedAt()).isNotNull();
        assertThat(f.jdbc.queryForObject("select client_onboarding_complete(?,?)",Boolean.class,submitted,day)).isTrue();
        service.review(f.operator("B1"),f.client,decision(submitted));assertThat(gate()).isNull();
    }
    @Test void selfServiceCannotClearAStaffRecordedLegalHold() {
        var staffProfile=profile(f.client,"TZ-LEGAL-HOLD");staffProfile.getProfile().setLegalHoldReference("Existing institution hold reference");service.save(f.actor,f.client,staffProfile);
        var owner=owner(f.client);service.saveOwn(owner,ownProfile(owner));assertThat(service.latest(f.actor,f.client).orElseThrow().profile().getLegalHoldReference()).isEqualTo("Existing institution hold reference");
    }
    @Test void previousNoticeAndAcknowledgedProfileStayUnchangedAndReviewable() {
        UUID legacyId=legacyNotice(true,day.plusYears(2));UUID draft=draft(f.client,"TZ-LEGACY-REVIEW");documents(f.client,draft);
        var previous=service.latest(f.actor,f.client).orElseThrow().profile();previous.setNoticeId(legacyId);previous.setNoticeVersion("Legacy notice");
        previous.setNoticeEnglish("Legacy English notice");previous.setNoticeKiswahili("Taarifa ya awali");previous.setLawfulBasis("CONSENT");
        previous.setRetentionReference("Legacy retention reference");previous.setRetentionReviewOn(day.plusYears(2));previous.setConsentReference("Legacy signed document");previous.setConsentOn(day);
        previous.setSelfService(true);previous.setNoticeAcknowledged(true);previous.setDeclarationAccepted(true);previous.setAcknowledgedAt(day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));var owner=owner(f.client);
        UUID submitted=UUID.randomUUID();var records=f.context.getBean(com.sacco.mvp.clients.onboarding.repository.OnboardingRepository.class);
        String identity=f.jdbc.queryForObject("select identity_key from client_onboarding_revision where id=?",String.class,draft);
        new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->{
            records.revision(owner,f.client,submitted,2,"SUBMITTED",previous,identity,UUID.randomUUID(),"0".repeat(64),day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));
            records.copyDocuments(f.actor,f.client,draft,submitted);
        });
        String original=f.jdbc.queryForObject("select profile::text from client_onboarding_revision where id=?",String.class,submitted);
        service.review(f.operator("B1"),f.client,decision(submitted));assertThat(gate()).isNull();
        assertThat(service.staffNotice(f.actor).id()).isEqualTo(Notice.STANDARD_ID);
        assertThat(service.version(f.actor,f.client,2).profile().getNoticeEnglish()).isEqualTo("Legacy English notice");
        assertThat(f.jdbc.queryForObject("select profile::text from client_onboarding_revision where id=?",String.class,submitted)).isEqualTo(original);
        assertThat(f.jdbc.queryForObject("select english from client_onboarding_notice where id=?",String.class,legacyId)).isEqualTo("Legacy English notice");
    }
    @Test void databaseRejectsAnAlteredStandardMessageAndAssistedConsentComesFromEvidence() {
        UUID submitted=submitted(f.client,"TZ-STANDARD-EVIDENCE");var revision=service.latest(f.actor,f.client).orElseThrow();
        assertThat(revision.profile().getConsentReference()).startsWith("Client consent document / ");
        assertThat(revision.profile().isNoticeAcknowledged()).isFalse();assertThat(revision.profile().getAcknowledgedAt()).isNull();
        assertThat(f.jdbc.queryForObject("select client_onboarding_complete(?,?)",Boolean.class,submitted,day)).isTrue();
        var returned=decision(submitted);returned.setKind("RETURN");service.review(f.operator("B1"),f.client,returned);
        UUID altered=UUID.randomUUID();var records=f.context.getBean(com.sacco.mvp.clients.onboarding.repository.OnboardingRepository.class);var changed=revision.profile();changed.setNoticeEnglish("Use for anything");
        new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->{
            records.revision(f.actor,f.client,altered,3,"SUBMITTED",changed,"0".repeat(64),UUID.randomUUID(),"0".repeat(64),day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));
            records.copyDocuments(f.actor,f.client,submitted,altered);
        });
        assertThat(f.jdbc.queryForObject("select client_onboarding_complete(?,?)",Boolean.class,altered,day)).isFalse();
        assertThatThrownBy(()->service.review(f.operator("B1"),f.client,decision(altered))).isInstanceOf(DataAccessException.class);
    }
    @Test void clientOwnershipBranchAndFreshAccountStatusAreCheckedForEveryAction() {
        var first=owner(f.client);service.saveOwn(first,ownProfile(first));var doc=service.ownDocumentForm(first);UUID document=service.attachOwn(first,doc,image());
        var stranger=owner(otherClient("B1"));assertThatThrownBy(()->service.downloadOwn(stranger,document)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.ownWorkspace(stranger,0,1)).isInstanceOf(AccessDeniedException.class);
        doc.setRequestKey(UUID.randomUUID());assertThatThrownBy(()->service.attachOwn(stranger,doc,image())).hasMessage("kyc.error.changed");
        assertThatThrownBy(()->service.saveOwn(f.actor,new SelfForm())).isInstanceOf(AccessDeniedException.class);
        var moved=f.context.getBean(MemberDirectoryService.class).find(f.client).orElseThrow();moved.setStationId("B2");assertThatThrownBy(()->service.ownWorkspace(first,0,null)).isInstanceOf(AccessDeniedException.class);moved.setStationId("B1");moved.setStatus(MemberStatus.INACTIVE);assertThatThrownBy(()->service.downloadOwn(first,document)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void concurrentClientSavesCannotLoseChangesAndRejectedSubmissionsRemainInHistory()throws Exception {
        var owner=owner(f.client);var one=ownProfile(owner);var two=ownProfile(owner);
        try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();for(var form:List.of(one,two))results.add(pool.submit(()->{latch.await();try{service.saveOwn(owner,form);return true;}catch(IllegalArgumentException expected){assertThat(expected).hasMessage("kyc.error.changed");return false;}}));latch.countDown();assertThat(results.get(0).get(30,TimeUnit.SECONDS)^results.get(1).get(30,TimeUnit.SECONDS)).isTrue();}
        for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY")){var d=service.ownDocumentForm(owner);d.setCategory(category);service.attachOwn(owner,d,image());}
        UUID submitted=submitOwn(owner);var rejected=decision(submitted);rejected.setKind("REJECT");service.review(f.operator("B1"),f.client,rejected);assertThat(service.ownWorkspace(owner,0,null).client().state()).isEqualTo("REJECT");assertThat(service.ownWorkspace(owner,0,2).feedback().message()).isEqualTo(rejected.getClientMessage());
    }
    @Test void clientStatusContainsOnlyOwnOutcome(){
        var member=Member.builder().id(f.client).saccoId(f.institution).stationId("B1").memberNo(f.client.toString()).fullName("Synthetic client").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build();when(f.context.getBean(MemberDirectoryService.class).find(f.client)).thenReturn(Optional.of(member));
        var client=new AppUserPrincipal(member,Set.of(),false);assertThat(service.ownStatus(client).state()).isEqualTo("NOT_STARTED");assertThatThrownBy(()->service.ownStatus(f.actor)).isInstanceOf(AccessDeniedException.class);
    }
    UUID group(String branch) {
        UUID id=UUID.randomUUID();var maker=branch.equals("B1")?f.actor:f.operator(branch);
        f.jdbc.update("insert into lending_group(id,sacco_id,station_id,request_key,payload_hash,code,name,legal_form,identity_evidence,created_by,created_at) values(?,?,?,?,?,?,?,'INFORMAL','Synthetic evidence',?,now())",id,f.institution,branch,UUID.randomUUID(),"a".repeat(64),id.toString().substring(0,8),"Synthetic group "+id,maker.getMemberId());return id;
    }
    @Test void conditionalDetailsFollowExplicitAnswersAndKeepPriorRevisions() {
        var draft=profile(f.client,"TZ-CONDITIONAL");var p=draft.getProfile();
        p.setBusinessName("Existing shop");p.setMaritalStatus("MARRIED");p.setSpouseName("Existing spouse");
        service.save(f.actor,f.client,draft);
        assertThat(service.latest(f.actor,f.client).orElseThrow().profile().getHasBusiness()).isTrue();
        var edit=service.profileForm(f.actor,f.client);p=edit.getProfile();p.setHasBusiness(false);p.setDailySales(new java.math.BigDecimal("-1"));p.setMaritalStatus("SINGLE");p.setSpousePhone("irrelevant invalid phone");p.setOtherIncomeSources("Employment");service.save(f.actor,f.client,edit);
        var saved=service.latest(f.actor,f.client).orElseThrow().profile();
        assertThat(saved.getHasBusiness()).isFalse();assertThat(saved.getBusinessName()).isEmpty();assertThat(saved.getDailySales()).isNull();assertThat(saved.getSpouseName()).isEmpty();assertThat(saved.getSpousePhone()).isEmpty();assertThat(saved.getOtherIncomeSources()).isEqualTo("Employment");
        assertThat(service.version(f.actor,f.client,1).profile().getBusinessName()).isEqualTo("Existing shop");
        var owner=owner(f.client);var own=service.ownForm(owner);own.getProfile().setHasBusiness(true);own.getProfile().setBusinessName("New shop");own.getProfile().setMaritalStatus("SEPARATED");own.getProfile().setSpouseName("Recorded spouse");service.saveOwn(owner,own);
        var copy=service.ownForm(owner).getProfile();assertThat(copy.getHasBusiness()).isTrue();assertThat(copy.getBusinessName()).isEqualTo("New shop");assertThat(copy.getSpouseName()).isEqualTo("Recorded spouse");
    }
    @Test void extendedAssistedProfileCreatesOneIdentityWithOptionalEmailAndPreservesItsNumberOnCorrection() {
        UUID group=group("B1");var form=new CreateForm();var p=form.getProfile();p.setFirstName("Synthetic");p.setLastName("Borrower");p.setPhone(phone());p.setMaritalStatus("MARRIED");p.setGender("FEMALE");p.setDependants(2);p.setGroupId(group);p.setCity("Synthetic city");p.setBusinessName("Synthetic shop");p.setBusinessTin("123-456-789");p.setDailySales(new java.math.BigDecimal("125000.50"));p.setDailyExpenditure(new java.math.BigDecimal("75000.25"));p.setSpouseName("Synthetic spouse");p.setSpousePhone("0712345678");p.setBankName("Synthetic bank");p.setBankAccountName("Synthetic Borrower");p.setBankAccountNumber("1234567890");
        UUID id=service.create(f.actor,form);assertThat(service.create(f.actor,form)).isEqualTo(id);var c=service.client(f.actor,id);String number=c.number();assertThat(number).matches("CL-[0-9]{8,}");assertThat(c.portalEnabled()).isFalse();assertThat(c.state()).isEqualTo("DRAFT");assertThat(c.email()).isNull();
        var saved=service.latest(f.actor,id).orElseThrow().profile();assertThat(saved.getDailySales()).isEqualByComparingTo("125000.50");assertThat(saved.getDailyExpenditure()).isEqualByComparingTo("75000.25");assertThat(saved.getGroupId()).isEqualTo(group);assertThat(saved.getGroupName()).contains("Synthetic group");assertThat(saved.getSpousePhone()).isEqualTo("255712345678");assertThat(saved.getBankAccountNumber()).isEqualTo("1234567890");assertThat(saved.getMiddleName()).isEmpty();
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_member where client_id=?",Integer.class,id)).isZero();
        UUID otp=UUID.randomUUID();f.jdbc.update("insert into email_otp_tokens(id,email,purpose,member_id,code_hash,expires_at,created_at,resend_count) values(?,'synthetic@example.invalid','REGISTRATION',?,'!SYNTHETIC',now()+interval '10 minutes',now(),0)",otp,id);
        var edit=service.profileForm(f.actor,id);edit.getProfile().setFirstName("Corrected");edit.getProfile().setPhone(phone());edit.getProfile().setEmail("corrected-"+id+"@example.invalid");service.save(f.actor,id,edit);assertThat(service.client(f.actor,id).number()).isEqualTo(number);assertThat(service.client(f.actor,id).name()).isEqualToIgnoringCase("Corrected Borrower");assertThat(f.jdbc.queryForObject("select consumed_at from email_otp_tokens where id=?",OffsetDateTime.class,otp)).isNotNull();assertThat(service.version(f.actor,id,1).profile().getFullName()).isEqualToIgnoringCase("Synthetic Borrower");
        var invalid=service.profileForm(f.actor,id);invalid.getProfile().setDailySales(new java.math.BigDecimal("-1"));assertThatThrownBy(()->service.save(f.actor,id,invalid)).hasMessage("kyc.error.dailySales");
        invalid.getProfile().setDailySales(null);invalid.getProfile().setGroupId(group("B2"));assertThatThrownBy(()->service.save(f.actor,id,invalid)).hasMessage("kyc.error.groupId");
        assertThatThrownBy(()->service.profileForm(f.operator("B2"),id)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void ownRichProfileAndPhotosRemainPrivateAndDoNotReplaceRequiredEvidence() {
        var owner=owner(f.client);var form=ownProfile(owner);form.getProfile().setBusinessName("Own synthetic shop");form.getProfile().setBankAccountNumber("PRIVATE-12345");form.getProfile().setDailyExpenditure(null);form.getProfile().setDependants(0);form.getProfile().setGroupId(group("B1"));service.saveOwn(owner,form);
        var copy=service.ownForm(owner).getProfile();assertThat(copy.getBusinessName()).isEqualTo("Own synthetic shop");assertThat(copy.getBankAccountNumber()).isEqualTo("PRIVATE-12345");assertThat(copy.getDailyExpenditure()).isNull();assertThat(copy.getDependants()).isZero();
        var photo=service.ownDocumentForm(owner);photo.setCategory("PROFILE_PHOTO");UUID id=service.attachOwn(owner,photo,image());assertThat(service.ownPhoto(owner,id).bytes()).isEqualTo(image);assertThat(service.photo(f.actor,f.client,id).contentType()).isEqualTo("image/png");
        var spouse=service.ownDocumentForm(owner);spouse.setCategory("SPOUSE_PHOTO");service.attachOwn(owner,spouse,image());assertThatThrownBy(()->submitOwn(owner)).hasMessage("kyc.error.documents");
        var pdf=new MockMultipartFile("file","picture.pdf","application/pdf",new byte[]{1,2,3});assertThatThrownBy(()->service.attachOwn(owner,service.ownDocumentForm(owner),pdf)).hasMessage("kyc.error.file");
        photo.setExpiresOn(day.plusDays(1));photo.setRequestKey(UUID.randomUUID());assertThatThrownBy(()->service.attachOwn(owner,photo,image())).hasMessage("kyc.error.photoExpiry");
        for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY")){var doc=service.ownDocumentForm(owner);doc.setCategory(category);service.attachOwn(owner,doc,image());}
        UUID submitted=submitOwn(owner);service.review(f.operator("B1"),f.client,decision(submitted));assertThat(service.ownStatus(owner).state()).isEqualTo("VERIFY");
        assertThatThrownBy(()->service.ownPhoto(owner(otherClient("B1")),id)).hasMessage("kyc.error.photoFile");assertThatThrownBy(()->service.photo(f.operator("B2"),f.client,id)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.groups(owner,f.client,"",0)).extracting(GroupChoice::id).contains(copy.getGroupId());assertThat(service.groups(f.actor,f.client,"",0)).allSatisfy(g->assertThat(g.label()).contains("Synthetic group"));
    }

}
