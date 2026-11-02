package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.*;
import com.sacco.mvp.clients.portal.repository.ClientPortalRepository;
import com.sacco.mvp.clients.portal.service.ClientPortalService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
class ClientPortalPostgresTest {
    final ClientOnboardingPostgresTest f=new ClientOnboardingPostgresTest();
    ClientPortalService portal;ClientPortalRepository records;StationOtpDeliveryService delivery;AtomicReference<String> code=new AtomicReference<>();
    @BeforeAll void start()throws Exception{f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){
        f.fixture();records=new ClientPortalRepository(f.f.jdbc);delivery=mock(StationOtpDeliveryService.class);
        doAnswer(i->{code.set(i.getArgument(7,String.class));return null;}).when(delivery).deliver(anyString(),anyString(),nullable(String.class),anyString(),any(),anyString(),anyString(),anyString(),anyInt());
        var directory=f.f.context.getBean(MemberDirectoryService.class);when(directory.find(any(UUID.class))).thenAnswer(i->f.f.context.getBean(MemberRepository.class).findById(i.getArgument(0)));
        portal=new ClientPortalService(records,new BCryptPasswordEncoder(4),delivery,f.f.context.getBean(MemberRegistrationService.class),f.service,f.f.context.getBean(ApplicationClock.class),f.f.context.getBean(PlatformTransactionManager.class),f.f.context.getBean(ObjectMapper.class),f.f.context.getBean(AuditService.class));
    }
    EntryForm enrollment(){var e=new EntryForm();e.setIntent("REGISTER");e.setInstitution(f.f.institution);e.setBranch("B1");var p=e.getRegistration().getProfile();p.setFirstName("Synthetic");p.setLastName("Applicant");p.setPhone(f.phone());p.setEmail(UUID.randomUUID()+"@example.invalid");return e;}
    Session register(){var id=portal.request(enrollment(),"127.0.0.3");return portal.verify(id,code.get(),"127.0.0.3").orElseThrow();}
    @Test void selfRegistrationCreatesOnePermanentIdentityWithoutAUserAccountAndRejectsReuse(){
        var entry=enrollment();var p=entry.getRegistration().getProfile();p.setGender("FEMALE");p.setMaritalStatus("MARRIED");p.setDependants(2);p.setIdentityType("NATIONAL_ID");p.setIssuingCountry("TZ");p.setIdentityNumber("TZ-PUBLIC-12345");p.setBirthDate(LocalDate.of(1990,1,1));p.setIdentityNoExpiry(true);
        p.setCity("Synthetic city");p.setPostalAddress("Box 10");p.setAddress("Synthetic address");p.setBusinessName("Synthetic shop");p.setBusinessTin("123456789");p.setBusinessType("Retail");p.setBusinessLicence("LIC-10");p.setBusinessLocation("Synthetic market");p.setBusinessWard("Synthetic ward");p.setBusinessCity("Synthetic city");p.setDailySales(new java.math.BigDecimal("30000.00"));p.setDailyExpenditure(new java.math.BigDecimal("20000.00"));p.setOtherIncomeSources("Farm sales");p.setActivity("Retail");p.setActivityAddress("Synthetic market");p.setSourceOfFunds("Sales");
        p.setSpouseName("Synthetic spouse");p.setSpouseRelationship("Spouse");p.setSpousePhone(f.phone());p.setSpouseWork("Retail");p.setSpouseWorkLocation("Synthetic market");p.setBankName("Synthetic bank");p.setBankAccountName("Synthetic Applicant");p.setBankAccountNumber("1234567890");
        var id=portal.request(entry,"127.0.0.4");String otp=code.get();var session=portal.verify(id,otp,"127.0.0.4").orElseThrow();
        p.setFirstName("SYNTHETIC");p.setLastName("APPLICANT");
        assertThat(f.service.ownForm(portal.actor(session,false)).getProfile()).usingRecursiveComparison().isEqualTo(p);
        var member=f.f.context.getBean(MemberRepository.class).findById(session.clientId()).orElseThrow();assertThat(member.isClientPortalEnabled()).isFalse();assertThat(member.getUsername()).isNull();assertThat(member.getPasswordHash()).isEqualTo("!NO_PORTAL_ACCESS");assertThat(member.getMemberNo()).matches("CL-[0-9]{8,}");assertThat(member.getSignatureText()).isNull();assertThat(f.service.ownStatus(portal.actor(session,false)).state()).isEqualTo("DRAFT");
        assertThat(portal.verify(id,otp,"127.0.0.4")).isEmpty();assertThatThrownBy(()->portal.actor(session,true)).isInstanceOf(AccessDeniedException.class);
        var duplicate=portal.request(entry,"127.0.0.5");assertThat(portal.verify(duplicate,code.get(),"127.0.0.5")).isEmpty();assertThat(f.f.jdbc.queryForObject("select count(*) from members where phone=?",Integer.class,member.getPhone())).isEqualTo(1);
    }
    @Test void fiveFailedAttemptsArePersistedAndCodesCannotBeConsumedConcurrently()throws Exception{
        var id=portal.request(enrollment(),"127.0.0.6");String otp=code.get();String wrong=otp.equals("000000")?"111111":"000000";for(int i=0;i<5;i++)assertThat(portal.verify(id,wrong,"127.0.0.6")).isEmpty();assertThat(portal.verify(id,otp,"127.0.0.6")).isEmpty();assertThat(f.f.jdbc.queryForObject("select attempts from client_portal_challenge where id=?",Integer.class,id)).isEqualTo(5);
        var next=portal.request(enrollment(),"127.0.0.7");String nextOtp=code.get();try(var pool=Executors.newFixedThreadPool(2)){var ready=new CountDownLatch(1);var one=pool.submit(()->{ready.await();return portal.verify(next,nextOtp,"127.0.0.7");});var two=pool.submit(()->{ready.await();return portal.verify(next,nextOtp,"127.0.0.7");});ready.countDown();assertThat(one.get(30,TimeUnit.SECONDS).isPresent()^two.get(30,TimeUnit.SECONDS).isPresent()).isTrue();}
    }
    @Test void unverifiedProfilesCannotEnterTheLoanPortalAndContactChangesExpireAccess(){
        var session=register();var member=f.f.context.getBean(MemberRepository.class).findById(session.clientId()).orElseThrow();var e=new EntryForm();e.setClientNumber(member.getMemberNo());e.setIntent("LOAN");clearInvocations(delivery);var nonce=portal.request(e,"127.0.0.8");verifyNoInteractions(delivery);assertThat(portal.verify(nonce,"123456","127.0.0.8")).isEmpty();
        f.f.jdbc.update("update members set phone=? where id=?",f.phone(),member.getId());assertThatThrownBy(()->portal.actor(session,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void unknownNumbersFullBorrowerAccountsInactiveBranchesAndExpiredCodesNeverAuthenticate(){
        var full=new EntryForm();full.setClientNumber(UUID.randomUUID().toString());portal.request(full,"127.0.0.9");verifyNoInteractions(delivery);
        var e=enrollment();var id=portal.request(e,"127.0.0.10");f.f.jdbc.update("update client_portal_challenge set expires_at=created_at-interval '5 minutes',created_at=created_at-interval '10 minutes' where id=?",id);assertThat(portal.verify(id,code.get(),"127.0.0.10")).isEmpty();
        var session=register();f.f.jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B1'",f.f.institution);assertThatThrownBy(()->portal.actor(session,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void staffCannotCreateOrEditProfilesForFullBorrowerAccounts(){f.f.jdbc.update("update members set client_portal_enabled=true,password_hash='test-only' where id=?",f.f.client);assertThatThrownBy(()->f.service.profileForm(f.f.actor,f.f.client)).hasMessage("kyc.error.selfServiceOnly");assertThatThrownBy(()->f.service.save(f.f.actor,f.f.client,new com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.ProfileForm())).hasMessage("kyc.error.selfServiceOnly");}
    @Test void invalidProfileFieldsAndPrivateGroupSelectionsFailBeforeCodeDelivery(){
        var entry=enrollment();entry.getRegistration().getProfile().setDailySales(new java.math.BigDecimal("-0.01"));
        assertThatThrownBy(()->portal.request(entry,"127.0.0.61")).hasMessage("kyc.error.dailySales");
        entry.getRegistration().getProfile().setDailySales(null);entry.getRegistration().getProfile().setGroupId(UUID.randomUUID());
        assertThatThrownBy(()->portal.request(entry,"127.0.0.62")).hasMessage("kyc.error.groupId");verifyNoInteractions(delivery);
    }
}
