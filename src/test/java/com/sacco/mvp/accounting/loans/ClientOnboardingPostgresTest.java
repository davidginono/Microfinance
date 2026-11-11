package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.PolicyForm;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
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
    ReviewForm decision(UUID revision){var form=new ReviewForm();form.setRevisionId(revision);form.setIdentityChecked(true);form.setAddressChecked(true);form.setActivityChecked(true);form.setContactChecked(true);form.setDuplicatesChecked(true);form.setReason("Synthetic independent evidence review");form.setClientMessage("Please provide a current address document.");form.setCheckedOn(day);return form;}
    void policy(){var controls=f.context.getBean(CreditControlService.class);var p=new PolicyForm();p.setExpectedRevision(controls.policy(f.actor).revision());p.setKycRequired(true);p.setReference("Synthetic approved branch KYC gate");p.setVerified(true);controls.configure(f.actor,p);}
    String gate(){return f.jdbc.queryForObject("select client_kyc_error(?,?,?,?)",String.class,f.client,f.institution,"B1",day);}
    UUID otherClient(String branch){var id=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,phone,status,position,is_member,password_hash,client_portal_enabled,created_at) values(?,?,?,?,'Synthetic second client',?,'ACTIVE','MEMBER',true,'!NO_PORTAL_ACCESS',false,now())",id,f.institution,branch,id.toString(),phone());return id;}

    UUID group(String branch) {
        UUID id=UUID.randomUUID();var maker=branch.equals("B1")?f.actor:f.operator(branch);
        f.jdbc.update("insert into lending_group(id,sacco_id,station_id,request_key,payload_hash,code,name,legal_form,identity_evidence,created_by,created_at) values(?,?,?,?,?,?,?,'INFORMAL','Synthetic evidence',?,now())",id,f.institution,branch,UUID.randomUUID(),"a".repeat(64),id.toString().substring(0,8),"Synthetic group "+id,maker.getMemberId());return id;
    }
    @Test void recordedProfileNeedsAnIndependentEvidenceCheckAndNeverGainsLoginAccess() {
        var form=profile(f.client,"SYNTHETIC-"+UUID.randomUUID());form.setAction("RECORDED");
        form.getProfile().setEmployeeNumber("EMP-1001");UUID draft=service.save(f.actor,f.client,form);
        documents(f.client,draft);var complete=service.profileForm(f.actor,f.client);complete.setAction("RECORDED");UUID revision=service.save(f.actor,f.client,complete);policy();
        assertThat(service.client(f.actor,f.client).state()).isEqualTo("RECORDED");
        assertThat(gate()).isNotNull();
        assertThat(f.jdbc.queryForObject("select employee_number from members where id=?",String.class,f.client)).isEqualTo("EMP-1001");
        assertThatThrownBy(()->service.review(f.actor,f.client,decision(revision))).hasMessage("kyc.error.independent");
        var reviewer=f.operator("B1");UUID checked=service.review(reviewer,f.client,decision(revision));
        assertThat(checked).isNotNull();assertThat(service.client(reviewer,f.client).state()).isEqualTo("VERIFY");assertThat(gate()).isNull();
        assertThat(f.jdbc.queryForObject("select client_portal_enabled from members where id=?",Boolean.class,f.client)).isFalse();
        assertThatThrownBy(()->service.review(f.operator("B2"),f.client,decision(revision))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void extendedAssistedProfileCreatesOneIdentityWithOptionalEmailAndPreservesItsNumberOnCorrection() {
        UUID group=group("B1");var form=new CreateForm();var p=form.getProfile();p.setFirstName("Synthetic");p.setLastName("Borrower");p.setPhone(phone());p.setMaritalStatus("MARRIED");p.setGender("FEMALE");p.setDependants(2);p.setGroupId(group);p.setCity("Synthetic city");p.setBusinessName("Synthetic shop");p.setBusinessTin("123-456-789");p.setDailySales(new java.math.BigDecimal("125000.50"));p.setDailyExpenditure(new java.math.BigDecimal("75000.25"));p.setSpouseName("Synthetic spouse");p.setSpousePhone("0712345678");p.setBankName("Synthetic bank");p.setBankAccountName("Synthetic Borrower");p.setBankAccountNumber("1234567890");
        UUID id=service.create(f.actor,form);assertThat(service.create(f.actor,form)).isEqualTo(id);var c=service.client(f.actor,id);String number=c.number();assertThat(number).matches("CL-[0-9]{8,}");assertThat(c.staffUser()).isFalse();assertThat(c.state()).isEqualTo("RECORDED");assertThat(c.email()).isNull();
        var saved=service.latest(f.actor,id).orElseThrow().profile();assertThat(saved.getDailySales()).isEqualByComparingTo("125000.50");assertThat(saved.getDailyExpenditure()).isEqualByComparingTo("75000.25");assertThat(saved.getGroupId()).isEqualTo(group);assertThat(saved.getGroupName()).contains("Synthetic group");assertThat(saved.getSpousePhone()).isEqualTo("255712345678");assertThat(saved.getBankAccountNumber()).isEqualTo("1234567890");assertThat(saved.getMiddleName()).isEmpty();
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_member where client_id=?",Integer.class,id)).isZero();
        var edit=service.profileForm(f.actor,id);edit.getProfile().setFirstName("Corrected");edit.getProfile().setPhone(phone());edit.getProfile().setEmail("corrected-"+id+"@example.invalid");service.save(f.actor,id,edit);assertThat(service.client(f.actor,id).number()).isEqualTo(number);assertThat(service.client(f.actor,id).name()).isEqualToIgnoringCase("Corrected Borrower");assertThat(service.version(f.actor,id,1).profile().getFullName()).isEqualToIgnoringCase("Synthetic Borrower");
        var invalid=service.profileForm(f.actor,id);invalid.getProfile().setDailySales(new java.math.BigDecimal("-1"));assertThatThrownBy(()->service.save(f.actor,id,invalid)).hasMessage("kyc.error.dailySales");
        invalid.getProfile().setDailySales(null);invalid.getProfile().setGroupId(group("B2"));assertThatThrownBy(()->service.save(f.actor,id,invalid)).hasMessage("kyc.error.groupId");
        assertThatThrownBy(()->service.profileForm(f.operator("B2"),id)).isInstanceOf(AccessDeniedException.class);
    }

}
