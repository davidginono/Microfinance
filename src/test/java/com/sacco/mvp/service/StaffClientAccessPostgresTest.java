package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a disposable loopback database, never the application's datasource. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_REGISTRATION_TEST_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_registration_test")
class StaffClientAccessPostgresTest {
    @Test void concurrentRetriesHaveOneIdentityAndAuditAndRollbackIsAtomic() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var members = context.getBean(MemberRepository.class);
            var claims = context.getBean(UserClaimService.class);
            var service = context.getBean(AdminService.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var admin = StaffClientAccessTest.staff(Position.MINOR_ADMIN);
            var borrower = StaffClientAccessTest.staff(Position.MANAGER);
            String institution = "SC-" + UUID.randomUUID();
            admin.setSaccoId(institution); borrower.setSaccoId(institution);
            admin.setMemberNo("STAFF-" + admin.getId()); admin.setStaffNo("A-" + admin.getId());
            borrower.setMemberNo("STAFF-" + borrower.getId()); borrower.setStaffNo("S-" + borrower.getId());
            admin.setEmail("admin-" + admin.getId() + "@example.test");
            borrower.setEmail("borrower-" + borrower.getId() + "@example.test");
            admin.setPhone(null); borrower.setPhone(null);
            var now = OffsetDateTime.now(); admin.setCreatedAt(now); borrower.setCreatedAt(now);
            tx.executeWithoutResult(s -> {
                context.getBean(RegisteredSaccoRepository.class).saveAndFlush(RegisteredSacco.builder().saccoId(institution).saccoName("Synthetic institution").active(true).createdAt(now).updatedAt(now).build());
                context.getBean(SaccoStationRepository.class).saveAndFlush(SaccoStation.builder().id(UUID.randomUUID()).saccoId(institution).stationId("B1").active(true).createdAt(now).updatedAt(now).build());
                members.saveAndFlush(admin); members.saveAndFlush(borrower);
                claims.updateClaims(admin.getId(), List.of(UserClaim.USER_ACCESS_UPDATE));
                claims.updateClaims(borrower.getId(), List.of(UserClaim.MANAGER_QUEUE_VIEW));
            });
            var actor = new AppUserPrincipal(admin, Set.of(UserClaim.USER_ACCESS_UPDATE), true);
            String oldNumber = borrower.getMemberNo();
            tx.executeWithoutResult(s -> {service.enableClientAccess(actor, borrower.getId()); s.setRollbackOnly();});
            assertThat(members.findById(borrower.getId()).orElseThrow().getMemberNo()).isEqualTo(oldNumber);
            assertThat(members.findById(borrower.getId()).orElseThrow().isMemberAccess()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id=? and action='STAFF_CLIENT_ACCESS_ENABLED'", Integer.class, borrower.getId())).isZero();

            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<String>>();
            try (var pool = Executors.newFixedThreadPool(6)) {
                for (int i=0; i<6; i++) futures.add(pool.submit(() -> {start.await(); return service.enableClientAccess(actor, borrower.getId());}));
                start.countDown();
                var numbers = new HashSet<String>();
                for (var future : futures) numbers.add(future.get(60, TimeUnit.SECONDS));
                assertThat(numbers).hasSize(1);
                assertThat(numbers.iterator().next()).matches("CL-[0-9]{8,}");
            }
            var saved = members.findById(borrower.getId()).orElseThrow();
            assertThat(saved.getId()).isEqualTo(borrower.getId());
            assertThat(saved.getPasswordHash()).isEqualTo("existing-hash");
            assertThat(saved.getStaffNo()).isEqualTo(borrower.getStaffNo());
            assertThat(saved.getActiveStaffRolesResolved()).containsExactly(Position.MANAGER);
            assertThat(claims.effectiveClaims(saved.getId(), saved.getStaffRolesResolved(), true)).containsExactly(UserClaim.MANAGER_QUEUE_VIEW);
            var logins = new com.sacco.mvp.security.AppUserDetailsService(new MemberDirectoryService(members),
                new StationAccessService(context.getBean(SaccoStationRepository.class)), claims);
            var clientLogin = (AppUserPrincipal) logins.loadClientByEmailOrUsername(saved.getEmail());
            var staffLogin = (AppUserPrincipal) logins.loadStaffByEmailOrUsername(saved.getEmail());
            assertThat(clientLogin.getMemberId()).isEqualTo(staffLogin.getMemberId());
            assertThat(clientLogin.isStaffSession()).isFalse();
            assertThat(clientLogin.getStaffRoles()).isEmpty();
            assertThat(clientLogin.getClaims()).doesNotContain(UserClaim.MANAGER_QUEUE_VIEW.name());
            assertThat(staffLogin.getClaims()).containsExactly(UserClaim.MANAGER_QUEUE_VIEW.name());
            var onboarding = new com.sacco.mvp.clients.onboarding.repository.OnboardingRepository(jdbc, context.getBean(ObjectMapper.class));
            assertThat(onboarding.client(clientLogin, saved.getId(), java.time.LocalDate.now()).state()).isEqualTo("NOT_STARTED");
            assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id=? and action='STAFF_CLIENT_ACCESS_ENABLED'", Integer.class, borrower.getId())).isEqualTo(1);
            var foreignActor = new AppUserPrincipal(Member.builder().id(admin.getId()).saccoId(institution).stationId("OTHER").position(Position.MINOR_ADMIN).staffAccessStatus(StaffAccessStatus.ACTIVE).build(), Set.of(UserClaim.USER_ACCESS_UPDATE), true);
            assertThatThrownBy(() -> service.enableClientAccess(foreignActor, borrower.getId())).hasMessage("staffClient.error.unavailable");

            // The administrator and target can be the same managed identity.
            var selfStart = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(4)) {
                var retries = new ArrayList<Future<String>>();
                for (int i=0; i<4; i++) retries.add(pool.submit(() -> {selfStart.await(); return service.enableClientAccess(actor, admin.getId());}));
                selfStart.countDown();
                var numbers = new HashSet<String>();
                for (var retry : retries) numbers.add(retry.get(60, TimeUnit.SECONDS));
                assertThat(numbers).hasSize(1);
            }
            assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id=? and action='STAFF_CLIENT_ACCESS_ENABLED'", Integer.class, admin.getId())).isEqualTo(1);
            assertThat(claims.effectiveClaims(admin.getId(), Set.of(Position.MINOR_ADMIN), true)).containsExactly(UserClaim.USER_ACCESS_UPDATE);
        }
    }

    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="com.sacco.mvp.repository")
    static class Config {
        @Bean(destroyMethod="close") DataSource dataSource() {var ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_REGISTRATION_TEST_URL"));ds.setUsername("postgres");ds.setMaximumPoolSize(8);return ds;}
        @Bean(initMethod="migrate") Flyway flyway(DataSource ds) {return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();}
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.sacco.mvp.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f) {return new JpaTransactionManager(f);}
        @Bean JdbcTemplate jdbc(DataSource ds) {return new JdbcTemplate(ds);}
        @Bean ObjectMapper json() {return JsonMapper.builder().findAndAddModules().build();}
        @Bean ApplicationClock clock() {return new ApplicationClock("Africa/Dar_es_Salaam");}
        @Bean AuditService audit(AuditLogRepository logs, ObjectMapper json, ApplicationClock clock) {return new AuditService(logs,json,clock);}
        @Bean UserClaimService claims(MemberAccessClaimRepository claims, UserSettingsRepository settings, ObjectMapper json) {return new UserClaimService(claims,settings,json);}
        @Bean RoleDirectoryService roles(MemberRepository members, UserClaimService claims) {return new RoleDirectoryService(members,claims);}
        @Bean SaccoRegistryService registry() {return mock(SaccoRegistryService.class);}
        @Bean AdminService admin(MemberRepository members, UserSettingsRepository settings, AuditService audit, UserClaimService claims, RoleDirectoryService roles, SaccoRegistryService registry, ObjectMapper json, ApplicationClock clock) {
            return new AdminService(members,null,settings,null,null,null,null,null,null,null,null,null,null,null,null,null,
                audit,null,null,claims,roles,null,registry,null,json,new NameSignatureService(),clock,null,null);
        }
    }
}
