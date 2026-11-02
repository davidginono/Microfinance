package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.CreateForm;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
class ProvisionalClientPostgresTest {
    final ClientOnboardingPostgresTest d=new ClientOnboardingPostgresTest();
    MemberRegistrationService registration;
    @BeforeAll void start()throws Exception{d.start();registration=d.f.context.getBean(MemberRegistrationService.class);}
    @AfterAll void stop(){d.stop();}
    @BeforeEach void fixture(){d.fixture();}
    CreateForm createForm(){var f=new CreateForm();f.setName("Synthetic Provisional Client");f.setPhone(d.phone());f.setEmail(UUID.randomUUID()+"@example.invalid");return f;}
    Member member(UUID id){return d.f.context.getBean(MemberRepository.class).findById(id).orElseThrow();}
    AppUserPrincipal owner(UUID id){var m=member(id);when(d.f.context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),false);}
    MemberRegistrationForm publicForm(CreateForm source){var f=new MemberRegistrationForm();f.setFirstName("Synthetic");f.setMiddleName("Provisional");f.setLastName("Client");f.setSaccoId(d.f.institution);f.setStationId("B1");f.setEmail(source.getEmail());f.setPhone(source.getPhone());f.setUsername("client_"+UUID.randomUUID().toString().substring(0,8));f.setPassword("Synthetic-only-password-42!");return f;}

    @Test void assistedRegistrationHasAutomaticIdentityButNoLoginOrInventedEvidence(){
        var f=createForm();UUID id=d.service.create(d.f.actor,f);var m=member(id);
        assertThat(m.getMemberNo()).matches("CL-[0-9]{8,}");assertThat(m.isClientPortalEnabled()).isFalse();assertThat(m.getUsername()).isNull();assertThat(m.getPasswordHash()).isEqualTo("!NO_PORTAL_ACCESS");
        assertThat(m.getPhoneVerifiedAt()).isNull();assertThat(m.getSignatureText()).isNull();assertThat(d.service.client(d.f.actor,id).portalEnabled()).isFalse();
        assertThat(d.f.jdbc.queryForObject("select count(*) from user_settings where member_id=?",Integer.class,id)).isZero();
        var members=d.f.context.getBean(MemberRepository.class);assertThat(members.findUserAccessByScope(d.f.institution,"B1",id)).isEmpty();
        assertThat(members.findUserAccessPageByName(d.f.institution,"B1","synthetic provisional",org.springframework.data.domain.PageRequest.of(0,25)).getContent()).noneMatch(user->user.getId().equals(id));
        assertThat(d.service.create(d.f.actor,f)).isEqualTo(id);
        assertThatThrownBy(()->d.service.ownStatus(owner(id))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->d.f.jdbc.update("update members set password_hash='forged' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void activationRetainsClientNumberAndProfile(){
        var c=createForm();UUID id=d.service.create(d.f.actor,c);String number=member(id).getMemberNo();
        d.service.save(d.f.actor,id,d.profile(id,"SYNTHETIC-"+UUID.randomUUID()));
        var form=publicForm(c);assertThat(registration.validatePublicRegistration(form,registration.verifyExternalMember(form))).isEqualTo(id);var activated=registration.register(form,id);
        assertThat(activated.getId()).isEqualTo(id);assertThat(activated.getMemberNo()).isEqualTo(number);assertThat(activated.isClientPortalEnabled()).isTrue();
        assertThat(d.f.context.getBean(MemberRepository.class).findUserAccessByScope(d.f.institution,"B1",id)).isPresent();
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(form.getPassword(),member(id).getPasswordHash())).isTrue();
        var owner=owner(id);assertThat(d.service.ownStatus(owner).state()).isEqualTo("DRAFT");
        assertThat(member(id).getSignatureText()).isNull();assertThatThrownBy(()->registration.register(form,id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void activationCannotChangeContactIdentityOrBranch(){
        var c=createForm();UUID id=d.service.create(d.f.actor,c);var form=publicForm(c);form.setPhone(d.phone());
        assertThatThrownBy(()->registration.validatePublicRegistration(form,registration.verifyExternalMember(form))).isInstanceOf(IllegalStateException.class);
        form.setPhone(c.getPhone());form.setStationId("B2");assertThatThrownBy(()->registration.register(form)).isInstanceOf(IllegalStateException.class);
        form.setStationId("B1");form.setLastName("Different");assertThatThrownBy(()->registration.register(form)).isInstanceOf(IllegalStateException.class);
        assertThat(member(id).isClientPortalEnabled()).isFalse();
    }
    @Test void anUnboundOrDifferentRegistrationContextCannotActivateAnExistingClient(){
        var c=createForm();UUID id=d.service.create(d.f.actor,c);var form=publicForm(c);
        assertThatThrownBy(()->registration.register(form)).hasMessage("Registration details changed. Request a new code.");
        assertThatThrownBy(()->registration.register(form,UUID.randomUUID())).hasMessage("Registration details changed. Request a new code.");
        assertThat(member(id).isClientPortalEnabled()).isFalse();
    }
    @Test void activationAuditFailureRollsBackCredentialsAndPortalSettings(){
        var c=createForm();UUID id=d.service.create(d.f.actor,c);String number=member(id).getMemberNo();
        doThrow(new IllegalStateException("Synthetic audit unavailable")).when(d.f.context.getBean(AuditService.class)).logEvent(anyString(),eq(id),eq("CLIENT_PORTAL_ACTIVATED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThatThrownBy(()->registration.register(publicForm(c),id)).hasMessage("Synthetic audit unavailable");
        assertThat(member(id).isClientPortalEnabled()).isFalse();assertThat(member(id).getUsername()).isNull();assertThat(member(id).getMemberNo()).isEqualTo(number);
        assertThat(d.f.jdbc.queryForObject("select count(*) from user_settings where member_id=?",Integer.class,id)).isZero();
    }
    @Test void concurrentRegistrationRetriesCreateOneClient(){
        var c=createForm();var pool=Executors.newFixedThreadPool(4);
        try {var jobs=new ArrayList<Callable<UUID>>();for(int n=0;n<8;n++)jobs.add(()->d.service.create(d.f.actor,c));
            var ids=pool.invokeAll(jobs).stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).toList();assertThat(new HashSet<>(ids)).hasSize(1);
        }catch(InterruptedException e){throw new RuntimeException(e);}finally{pool.shutdownNow();}
    }
    @Test void concurrentNewRequestsWithTheSameContactsCannotCreateDuplicateClients()throws Exception {
        var first=createForm();var second=new CreateForm();second.setName(first.getName());second.setPhone(first.getPhone());second.setEmail(first.getEmail());var pool=Executors.newFixedThreadPool(2);
        try {var results=pool.invokeAll(List.<Callable<Boolean>>of(
            ()->{try{d.service.create(d.f.actor,first);return true;}catch(IllegalArgumentException e){return false;}},
            ()->{try{d.service.create(d.f.actor,second);return true;}catch(IllegalArgumentException e){return false;}}));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();assertThat(d.f.jdbc.queryForObject("select count(*) from members where email=?",Integer.class,first.getEmail())).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentActivationEnablesOnlyOneLoginAndNeverAllocatesAnotherNumber()throws Exception {
        var c=createForm();UUID id=d.service.create(d.f.actor,c);String number=member(id).getMemberNo();var pool=Executors.newFixedThreadPool(2);
        try {var first=publicForm(c);var second=publicForm(c);var results=pool.invokeAll(List.<Callable<Boolean>>of(
            ()->{try{registration.register(first,id);return true;}catch(IllegalStateException e){return false;}},
            ()->{try{registration.register(second,id);return true;}catch(IllegalStateException e){return false;}}));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();assertThat(member(id).getMemberNo()).isEqualTo(number);
            assertThat(d.f.jdbc.queryForObject("select count(*) from user_settings where member_id=?",Integer.class,id)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }

    @Test void assistedAndPublicRegistrationsShareTheSequenceUnderConcurrency()throws Exception {
        var pool=Executors.newFixedThreadPool(4);var jobs=new ArrayList<Callable<String>>();
        try {for(int n=0;n<12;n++){var c=createForm();boolean assisted=n%2==0;jobs.add(()->assisted?member(d.service.create(d.f.actor,c)).getMemberNo():registration.register(publicForm(c)).getMemberNo());}
            var numbers=new HashSet<String>();for(var future:pool.invokeAll(jobs)){String number=future.get();assertThat(number).matches("CL-[0-9]{8,}");numbers.add(number);}assertThat(numbers).hasSize(12);
        }finally{pool.shutdownNow();}
    }




}
