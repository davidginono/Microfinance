package com.sacco.mvp.platform.onboarding;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.platform.onboarding.dto.AdminSetupForm;
import com.sacco.mvp.platform.onboarding.repository.AdminSetupRepository;
import com.sacco.mvp.platform.onboarding.service.AdminSetupService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="MICROFINANCE_BOOTSTRAP_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:5432/microfinance_bootstrap_test")
class AdminSetupPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;AdminSetupService service;AuditService audit=mock(AuditService.class);
    BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    String temporary="SyntheticTemporarySecret!";
    @BeforeAll void start() {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_BOOTSTRAP_TEST_URL"),"postgres",System.getenv("MICROFINANCE_BOOTSTRAP_TEST_PASSWORD"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new JdbcTransactionManager(ds));
        service=new AdminSetupService(new AdminSetupRepository(jdbc),passwords,new NameSignatureService(),audit);
    }
    @BeforeEach void resetIdentity() {
        reset(audit);
        jdbc.update("update members set platform_setup_required=true,password_hash='!BOOTSTRAP_REQUIRED',email=null,phone=null where id=?",AdminSetupService.BOOTSTRAP_ID);
    }
    AppUserPrincipal actor() {return new AppUserPrincipal(Member.builder().id(AdminSetupService.BOOTSTRAP_ID).memberNo("ADM001").position(Position.ADMIN)
        .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).platformSetupRequired(true).build(),Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true);}
    AdminSetupForm form() {var f=new AdminSetupForm();f.setFullName("Synthetic Test Administrator");f.setEmail("admin@example.invalid");f.setPhone("255712345678");f.setCurrentPassword(temporary);f.setPassword("NewPermanentSecret!");f.setConfirmPassword(f.getPassword());return f;}
    String hash() {return jdbc.queryForObject("select password_hash from members where id=?",String.class,AdminSetupService.BOOTSTRAP_ID);}
    @Test void freshMigrationsHaveOneBlockedPlatformAdminAndNoTenants() {
        assertThat(jdbc.queryForObject("select count(*) from members",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from registered_saccos",Integer.class)).isZero();
        assertThat(hash()).isEqualTo("!BOOTSTRAP_REQUIRED");
    }
    @Test void restartDoesNotOverwriteEitherInitialOrChosenPassword() {
        tx.execute(s->service.provision(temporary));String initial=hash();
        assertThat(jdbc.queryForObject("select login_otp_enabled from user_settings where member_id=?",Boolean.class,AdminSetupService.BOOTSTRAP_ID)).isFalse();
        assertThat(passwords.matches(temporary,initial)).isTrue();
        boolean temporaryReset=tx.execute(s->service.provision("AnotherTemporarySecret!"));
        assertThat(temporaryReset).isFalse();assertThat(hash()).isEqualTo(initial);
        tx.executeWithoutResult(s->service.complete(actor(),form()));String chosen=hash();
        assertThat(passwords.matches(form().getPassword(),chosen)).isTrue();
        boolean permanentReset=tx.execute(s->service.provision(temporary));
        assertThat(permanentReset).isFalse();assertThat(hash()).isEqualTo(chosen);
        assertThat(jdbc.queryForObject("select platform_setup_required from members where id=?",Boolean.class,AdminSetupService.BOOTSTRAP_ID)).isFalse();
    }
    @Test void auditFailureRollsBackPasswordContactAndSetupCompletion() {
        tx.execute(s->service.provision(temporary));String before=hash();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->tx.executeWithoutResult(s->service.complete(actor(),form()))).hasMessage("Synthetic audit failure");
        assertThat(hash()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select email from members where id=?",String.class,AdminSetupService.BOOTSTRAP_ID)).isNull();
        assertThat(jdbc.queryForObject("select platform_setup_required from members where id=?",Boolean.class,AdminSetupService.BOOTSTRAP_ID)).isTrue();
    }
    @Test void competingCompletionsAllowOnlyOneChange()throws Exception {
        tx.execute(s->service.provision(temporary));var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action=()->{start.await();try {tx.executeWithoutResult(s->service.complete(actor(),form()));return true;}catch(org.springframework.security.access.AccessDeniedException e){return false;}};
            var first=executor.submit(action);var second=executor.submit(action);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
}
