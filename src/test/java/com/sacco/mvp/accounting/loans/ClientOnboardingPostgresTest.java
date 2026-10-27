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
    @BeforeEach void fixture(){f.fixture();clientPhone=phone();f.jdbc.update("update members set phone=? where id=?",clientPhone,f.client);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day);when(f.context.getBean(ApplicationClock.class).now()).thenReturn(day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
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
    ReviewForm decision(UUID revision){var form=new ReviewForm();form.setRevisionId(revision);form.setIdentityChecked(true);form.setAddressChecked(true);form.setActivityChecked(true);form.setContactChecked(true);form.setDuplicatesChecked(true);form.setReason("Synthetic independent evidence review");form.setScreening("CLEARED");form.setScreeningReference("Synthetic manually checked PEP and sanctions sources, dated result");form.setPolicyReference("Synthetic approved KYC policy");form.setCheckedOn(day);form.setReviewDue(day.plusMonths(6));return form;}
    void policy(){var controls=f.context.getBean(CreditControlService.class);var p=new PolicyForm();p.setExpectedRevision(controls.policy(f.actor).revision());p.setKycRequired(true);p.setReference("Synthetic approved branch KYC gate");p.setVerified(true);controls.configure(f.actor,p);}
    String gate(){return f.jdbc.queryForObject("select client_kyc_error(?,?,?,?)",String.class,f.client,f.institution,"B1",day);}
    UUID otherClient(String branch){var id=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,phone,status,position,is_member,password_hash,created_at) values(?,?,?,?,'Synthetic second client',?,'ACTIVE','MEMBER',true,'test-only',now())",id,f.institution,branch,id.toString(),phone());return id;}

    @Test void existingRegistrationIsUnverifiedAndAssistedCreationDoesNotFabricateOtpOrConsent(){
        assertThat(service.client(f.actor,f.client).state()).isEqualTo("NOT_STARTED");assertThat(gate()).isEqualTo("kyc.error.required");
        var form=new CreateForm();form.setNumber("KYC-"+UUID.randomUUID());form.setName("Synthetic New Client");form.setPhone("2557"+String.format("%08d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000)));form.setEmail(UUID.randomUUID()+"@example.invalid");
        UUID id=service.create(f.actor,form);assertThat(service.create(f.actor,form)).isEqualTo(id);assertThat(service.client(f.actor,id).state()).isEqualTo("NOT_STARTED");assertThat(f.jdbc.queryForObject("select phone_verified_at from members where id=?",OffsetDateTime.class,id)).isNull();assertThat(f.jdbc.queryForObject("select count(*) from client_onboarding_revision where client_id=?",Integer.class,id)).isZero();
        assertThat(f.jdbc.queryForObject("select signature_text from members where id=?",String.class,id)).isNull();assertThat(f.jdbc.queryForObject("select count(*) from sacco_settings where sacco_id=?",Integer.class,f.institution)).isZero();
    }
    @Test void draftsNeedCurrentEvidenceAndVerificationNeedsIndependentStaff(){
        var empty=new ProfileForm();UUID draft=service.save(f.actor,f.client,empty);assertThat(service.save(f.actor,f.client,empty)).isEqualTo(draft);
        f.jdbc.update("update members set phone=null where id=?",f.client);var missingContact=profile(f.client,"TZ-0000-1234");missingContact.setAction("SUBMITTED");assertThatThrownBy(()->service.save(f.actor,f.client,missingContact)).hasMessage("kyc.error.phone");f.jdbc.update("update members set phone=? where id=?",clientPhone,f.client);
        var form=profile(f.client,"TZ-0000-1234");form.setAction("SUBMITTED");assertThatThrownBy(()->service.save(f.actor,f.client,form)).hasMessage("kyc.error.documents");
        UUID submitted=submitted(f.client,"TZ-0000-1234");var review=decision(submitted);assertThatThrownBy(()->service.review(f.actor,f.client,review)).hasMessage("kyc.error.independent");
        var selfMember=Member.builder().id(f.client).saccoId(f.institution).stationId("B1").memberNo(f.client.toString()).memberAccount(true).fullName("Synthetic client").phone(clientPhone).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();f.jdbc.update("update members set position='ACCOUNTANT',staff_access_status='ACTIVE' where id=?",f.client);when(f.context.getBean(MemberDirectoryService.class).find(f.client)).thenReturn(Optional.of(selfMember));var selfReviewer=new AppUserPrincipal(selfMember,EnumSet.allOf(UserClaim.class),true);assertThatThrownBy(()->service.review(selfReviewer,f.client,review)).hasMessage("kyc.error.independent");
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
    @Test void clientStatusContainsOnlyOwnOutcome(){
        var member=Member.builder().id(f.client).saccoId(f.institution).stationId("B1").memberNo(f.client.toString()).fullName("Synthetic client").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build();when(f.context.getBean(MemberDirectoryService.class).find(f.client)).thenReturn(Optional.of(member));
        var client=new AppUserPrincipal(member,Set.of(),false);assertThat(service.ownStatus(client).state()).isEqualTo("NOT_STARTED");assertThatThrownBy(()->service.ownStatus(f.actor)).isInstanceOf(AccessDeniedException.class);
    }
}
